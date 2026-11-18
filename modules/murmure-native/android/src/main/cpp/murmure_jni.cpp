// Pont JNI entre WhisperBridge.kt et whisper.cpp.
// Aucun état global mutable : tout est porté par MurmureContext (handle Java = pointeur).
// Le texte transcrit n'est jamais journalisé.

#include <jni.h>
#include <android/log.h>

#include <string>
#include <vector>

#include "whisper.h"

#define LOG_TAG "MurmureWhisper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

struct MurmureContext {
    whisper_context *ctx = nullptr;
    std::string lastLanguage;
};

MurmureContext *fromHandle(jlong handle) {
    return reinterpret_cast<MurmureContext *>(handle);
}

void throwRuntime(JNIEnv *env, const char *message) {
    jclass cls = env->FindClass("java/lang/RuntimeException");
    if (cls != nullptr) {
        env->ThrowNew(cls, message);
    }
}

// Détection restreinte à fr/en : on compare uniquement ces deux probabilités.
// Retourne "" si la détection échoue (whisper choisira alors lui-même).
std::string detectFrOrEn(whisper_context *ctx, const float *samples, int n, int nThreads) {
    if (whisper_pcm_to_mel(ctx, samples, n, nThreads) != 0) {
        LOGE("whisper_pcm_to_mel failed");
        return "";
    }
    std::vector<float> probs(static_cast<size_t>(whisper_lang_max_id() + 1), 0.0f);
    if (whisper_lang_auto_detect(ctx, 0, nThreads, probs.data()) < 0) {
        LOGE("whisper_lang_auto_detect failed");
        return "";
    }
    const int fr = whisper_lang_id("fr");
    const int en = whisper_lang_id("en");
    if (fr < 0 || en < 0) {
        return "";
    }
    return probs[static_cast<size_t>(fr)] >= probs[static_cast<size_t>(en)] ? "fr" : "en";
}

std::string jstringToStd(JNIEnv *env, jstring value) {
    if (value == nullptr) return "";
    const char *chars = env->GetStringUTFChars(value, nullptr);
    std::string out = chars != nullptr ? chars : "";
    if (chars != nullptr) env->ReleaseStringUTFChars(value, chars);
    return out;
}

// NewStringUTF attend du « modified UTF-8 » : on passe par un tableau d'octets + String(bytes, UTF-8)
// pour supporter correctement les caractères hors BMP (émojis) éventuels.
jstring utf8ToJString(JNIEnv *env, const std::string &text) {
    jbyteArray bytes = env->NewByteArray(static_cast<jsize>(text.size()));
    if (bytes == nullptr) return nullptr;
    env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(text.size()),
                            reinterpret_cast<const jbyte *>(text.data()));
    jclass stringClass = env->FindClass("java/lang/String");
    jmethodID ctor = env->GetMethodID(stringClass, "<init>", "([BLjava/lang/String;)V");
    jstring charset = env->NewStringUTF("UTF-8");
    auto result = static_cast<jstring>(env->NewObject(stringClass, ctor, bytes, charset));
    env->DeleteLocalRef(bytes);
    env->DeleteLocalRef(charset);
    env->DeleteLocalRef(stringClass);
    return result;
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_app_murmure_core_WhisperBridge_nativeInit(JNIEnv *env, jobject /* thiz */, jstring modelPath) {
    const std::string path = jstringToStd(env, modelPath);
    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    whisper_context *ctx = whisper_init_from_file_with_params(path.c_str(), cparams);
    if (ctx == nullptr) {
        LOGE("whisper_init_from_file_with_params failed");
        return 0;
    }
    auto *wrapper = new MurmureContext();
    wrapper->ctx = ctx;
    LOGI("model loaded");
    return reinterpret_cast<jlong>(wrapper);
}

JNIEXPORT jstring JNICALL
Java_app_murmure_core_WhisperBridge_nativeTranscribe(JNIEnv *env, jobject /* thiz */, jlong handle,
                                                     jfloatArray samples, jstring language, jint nThreads) {
    MurmureContext *wrapper = fromHandle(handle);
    if (wrapper == nullptr || wrapper->ctx == nullptr) {
        throwRuntime(env, "Whisper context not initialized");
        return nullptr;
    }
    wrapper->lastLanguage.clear();

    const jsize n = env->GetArrayLength(samples);
    std::vector<float> pcm(static_cast<size_t>(n));
    env->GetFloatArrayRegion(samples, 0, n, pcm.data());

    const int threads = nThreads > 0 ? nThreads : 1;
    std::string lang = jstringToStd(env, language);
    if (lang != "fr" && lang != "en") {
        lang = detectFrOrEn(wrapper->ctx, pcm.data(), static_cast<int>(n), threads);
    }

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.language = lang.empty() ? "auto" : lang.c_str();
    params.detect_language = false;
    params.translate = false;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_special = false;
    params.print_timestamps = false;
    params.no_timestamps = true;
    params.single_segment = false;
    params.n_threads = threads;

    if (whisper_full(wrapper->ctx, params, pcm.data(), static_cast<int>(n)) != 0) {
        throwRuntime(env, "whisper_full failed");
        return nullptr;
    }

    if (!lang.empty()) {
        wrapper->lastLanguage = lang;
    } else {
        const int id = whisper_full_lang_id(wrapper->ctx);
        const char *detected = id >= 0 ? whisper_lang_str(id) : nullptr;
        if (detected != nullptr) {
            const std::string d = detected;
            if (d == "fr" || d == "en") wrapper->lastLanguage = d;
        }
    }

    std::string text;
    const int segments = whisper_full_n_segments(wrapper->ctx);
    for (int i = 0; i < segments; ++i) {
        const char *segment = whisper_full_get_segment_text(wrapper->ctx, i);
        if (segment != nullptr) text += segment;
    }
    return utf8ToJString(env, text);
}

JNIEXPORT jstring JNICALL
Java_app_murmure_core_WhisperBridge_nativeLastLanguage(JNIEnv *env, jobject /* thiz */, jlong handle) {
    MurmureContext *wrapper = fromHandle(handle);
    if (wrapper == nullptr) return env->NewStringUTF("");
    return env->NewStringUTF(wrapper->lastLanguage.c_str());
}

JNIEXPORT void JNICALL
Java_app_murmure_core_WhisperBridge_nativeFree(JNIEnv * /* env */, jobject /* thiz */, jlong handle) {
    MurmureContext *wrapper = fromHandle(handle);
    if (wrapper == nullptr) return;
    if (wrapper->ctx != nullptr) whisper_free(wrapper->ctx);
    delete wrapper;
}

JNIEXPORT jstring JNICALL
Java_app_murmure_core_WhisperBridge_nativeSystemInfo(JNIEnv *env, jobject /* thiz */) {
    const char *info = whisper_print_system_info();
    return env->NewStringUTF(info != nullptr ? info : "");
}

} // extern "C"
