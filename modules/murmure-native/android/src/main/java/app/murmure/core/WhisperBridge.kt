package app.murmure.core

/** Fonctions JNI de libmurmure_whisper.so (voir src/main/cpp/murmure_jni.cpp). */
object WhisperBridge {
  init {
    System.loadLibrary("murmure_whisper")
  }

  /** Charge le modèle ; 0 si échec. */
  external fun nativeInit(modelPath: String): Long

  /** language : "auto" (détection restreinte fr/en), "fr" ou "en". Lève RuntimeException si whisper_full échoue. */
  external fun nativeTranscribe(ctx: Long, samples: FloatArray, language: String, nThreads: Int): String

  /** Langue utilisée par le dernier nativeTranscribe ("" si inconnue). */
  external fun nativeLastLanguage(ctx: Long): String

  external fun nativeFree(ctx: Long)

  external fun nativeSystemInfo(): String
}
