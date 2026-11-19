package app.murmure.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import expo.modules.kotlin.Promise
import expo.modules.kotlin.exception.Exceptions
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import java.io.IOException
import java.lang.ref.WeakReference

/** API JS « MurmureNative » (Expo Modules API). */
class MurmureNativeModule : Module() {
  companion object {
    private const val TAG = "MurmureModule"
    const val EVENT_DOWNLOAD_PROGRESS = "onDownloadProgress"
    const val EVENT_DOWNLOAD_END = "onDownloadEnd"
    const val EVENT_HISTORY_CHANGED = "onHistoryChanged"
    const val EVENT_SETTINGS_CHANGED = "onSettingsChanged"

    /** Référence faible : le service peut notifier l'app si elle est ouverte. */
    @Volatile private var instance: WeakReference<MurmureNativeModule>? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun emitHistoryChanged() {
      instance?.get()?.safeSend(EVENT_HISTORY_CHANGED, emptyMap())
    }

    fun notifyServiceStatusChanged() {
      // L'app relit le statut au retour au premier plan ; on émet aussi les réglages pour forcer un rafraîchissement.
      val module = instance?.get() ?: return
      val ctx = module.appContext.reactContext ?: return
      module.safeSend(EVENT_SETTINGS_CHANGED, module.settingsMap(MurmurePrefs(ctx)))
    }
  }

  private val context: Context
    get() = appContext.reactContext?.applicationContext ?: throw Exceptions.ReactContextLost()

  private fun safeSend(name: String, body: Map<String, Any?>) {
    try {
      sendEvent(name, body)
    } catch (e: Exception) {
      Log.w(TAG, "event $name not delivered: ${e.javaClass.simpleName}")
    }
  }

  private fun settingsMap(prefs: MurmurePrefs): Map<String, Any?> = mapOf(
    "bubbleEnabled" to prefs.bubbleEnabled,
    "modelId" to prefs.modelId,
    "language" to prefs.language,
    "onboardingDone" to prefs.onboardingDone
  )

  private fun modelsList(): List<Map<String, Any?>> {
    val ctx = context
    val selected = MurmurePrefs(ctx).modelId
    val downloading = ModelManager.downloadingId
    return ModelCatalog.all.map { spec ->
      mapOf(
        "id" to spec.id,
        "labelFr" to spec.labelFr,
        "labelEn" to spec.labelEn,
        "sizeBytes" to spec.sizeBytes.toDouble(),
        "installed" to ModelManager.isInstalled(ctx, spec),
        "partialBytes" to ModelManager.partialBytes(ctx, spec).toDouble(),
        "selected" to (spec.id == selected),
        "downloading" to (spec.id == downloading)
      )
    }
  }

  private fun errorCode(e: Throwable): String = when (e) {
    is DownloadCancelledException -> "CANCELLED"
    is ModelChecksumException -> "CHECKSUM"
    is InsufficientStorageException -> "INSUFFICIENT_STORAGE"
    is IOException -> "NETWORK"
    else -> "UNKNOWN"
  }

  override fun definition() = ModuleDefinition {
    Name("MurmureNative")

    Events(EVENT_DOWNLOAD_PROGRESS, EVENT_DOWNLOAD_END, EVENT_HISTORY_CHANGED, EVENT_SETTINGS_CHANGED)

    OnCreate {
      instance = WeakReference(this@MurmureNativeModule)
    }

    OnDestroy {
      if (instance?.get() === this@MurmureNativeModule) instance = null
    }

    Function("getPermissionStatus") {
      PermissionChecker.status(context)
    }

    Function("openAccessibilitySettings") {
      context.startActivity(PermissionChecker.accessibilitySettingsIntent())
    }

    Function("openOverlaySettings") {
      context.startActivity(PermissionChecker.overlaySettingsIntent(context))
    }

    Function("openAppDetailsSettings") {
      context.startActivity(PermissionChecker.appDetailsIntent(context))
    }

    Function("getSettings") {
      settingsMap(MurmurePrefs(context))
    }

    Function("setSettings") { patch: Map<String, Any?> ->
      val prefs = MurmurePrefs(context)
      (patch["bubbleEnabled"] as? Boolean)?.let { prefs.bubbleEnabled = it }
      (patch["modelId"] as? String)?.let { id ->
        if (ModelCatalog.byId(id) != null && id != prefs.modelId) {
          prefs.modelId = id
          WhisperEngine.release()
        }
      }
      (patch["language"] as? String)?.let { if (it in MurmurePrefs.LANGUAGES) prefs.language = it }
      (patch["onboardingDone"] as? Boolean)?.let { prefs.onboardingDone = it }
      val result = settingsMap(prefs)
      safeSend(EVENT_SETTINGS_CHANGED, result)
      result
    }

    Function("getModels") {
      modelsList()
    }

    AsyncFunction("downloadModel") { id: String, promise: Promise ->
      val spec = ModelCatalog.byId(id)
      if (spec == null) {
        promise.reject("UNKNOWN", "Unknown model $id", null)
        return@AsyncFunction
      }
      if (ModelManager.downloadingId != null) {
        promise.reject("UNKNOWN", "A download is already running", null)
        return@AsyncFunction
      }
      val ctx = context
      Thread({
        try {
          ModelManager.download(ctx, spec) { bytes, total ->
            safeSend(EVENT_DOWNLOAD_PROGRESS, mapOf("modelId" to id, "bytes" to bytes.toDouble(), "total" to total.toDouble()))
          }
          safeSend(EVENT_DOWNLOAD_END, mapOf("modelId" to id, "ok" to true))
          mainHandler.post { promise.resolve(null) }
        } catch (e: Throwable) {
          val code = errorCode(e)
          Log.w(TAG, "download ${spec.id} failed: $code")
          safeSend(EVENT_DOWNLOAD_END, mapOf("modelId" to id, "ok" to false, "error" to code))
          mainHandler.post { promise.reject(code, "Download failed: $code", null) }
        }
      }, "murmure-download").start()
    }

    Function("cancelDownload") { id: String ->
      ModelManager.cancel(id)
    }

    Function("deleteModel") { id: String ->
      ModelManager.delete(context, id)
    }

    Function("getHistory") {
      HistoryStore.all(context).map { it.toMap() }
    }

    Function("deleteHistoryItem") { id: String ->
      HistoryStore.delete(context, id)
      safeSend(EVENT_HISTORY_CHANGED, emptyMap())
    }

    Function("clearHistory") {
      HistoryStore.clear(context)
      safeSend(EVENT_HISTORY_CHANGED, emptyMap())
    }

    Function("copyToClipboard") { text: String ->
      TextInserter.copyToClipboard(context, text)
    }
  }
}
