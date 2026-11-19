package app.murmure.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

data class Transcription(val raw: String, val language: String?, val modelId: String)

class ModelNotInstalledException : IllegalStateException("model not installed")

/** Singleton : chargement paresseux du modèle et file de transcription mono-thread. */
object WhisperEngine {
  private const val TAG = "MurmureEngine"

  private val executor: ExecutorService = Executors.newSingleThreadExecutor { r ->
    Thread(r, "murmure-whisper").apply { priority = Thread.MAX_PRIORITY }
  }
  private val mainHandler = Handler(Looper.getMainLooper())
  private val pending = AtomicInteger(0)
  private val lock = Any()

  private var ctx: Long = 0L
  private var loadedModelId: String? = null

  val isBusy: Boolean get() = pending.get() > 0

  val nThreads: Int get() = minOf(4, maxOf(1, Runtime.getRuntime().availableProcessors() - 1))

  /** Charge le modèle sélectionné s'il est installé ; recharge si la sélection a changé. Bloquant. */
  fun ensureLoaded(context: Context): Boolean = synchronized(lock) {
    val modelId = MurmurePrefs(context).modelId
    val spec = ModelCatalog.byId(modelId) ?: return false
    if (!ModelManager.isInstalled(context, spec)) {
      releaseLocked()
      return false
    }
    if (ctx != 0L && loadedModelId == modelId) return true
    releaseLocked()
    val handle = try {
      WhisperBridge.nativeInit(ModelManager.installedFile(context, spec).absolutePath)
    } catch (e: Throwable) {
      Log.e(TAG, "native init failed: ${e.javaClass.simpleName}")
      0L
    }
    if (handle == 0L) return false
    ctx = handle
    loadedModelId = modelId
    true
  }

  /** Préchargement en arrière-plan (première apparition de la bulle). */
  fun preload(context: Context) {
    val app = context.applicationContext
    executor.execute {
      try {
        ensureLoaded(app)
      } catch (e: Throwable) {
        Log.w(TAG, "preload failed: ${e.javaClass.simpleName}")
      }
    }
  }

  /** Transcrit sur le thread dédié ; callback sur le main thread. */
  fun transcribe(context: Context, samples: FloatArray, language: String, callback: (Result<Transcription>) -> Unit) {
    val app = context.applicationContext
    pending.incrementAndGet()
    executor.execute {
      val result = runCatching {
        synchronized(lock) {
          if (!ensureLoaded(app)) throw ModelNotInstalledException()
          val raw = WhisperBridge.nativeTranscribe(ctx, samples, language, nThreads)
          val lang = WhisperBridge.nativeLastLanguage(ctx).takeIf { it == "fr" || it == "en" }
          Transcription(raw, lang, loadedModelId ?: "")
        }
      }
      result.exceptionOrNull()?.let { Log.e(TAG, "transcription failed: ${it.javaClass.simpleName}") }
      mainHandler.post {
        pending.decrementAndGet()
        callback(result)
      }
    }
  }

  /** Libère le modèle (onTrimMemory critique, onUnbind, changement de modèle). */
  // Exécutées sur le thread dédié : jamais de blocage du main thread derrière un chargement / une transcription.
  fun release() {
    executor.execute { synchronized(lock) { releaseLocked() } }
  }

  fun onModelDeleted(id: String) {
    executor.execute {
      synchronized(lock) {
        if (loadedModelId == id) releaseLocked()
      }
    }
  }

  private fun releaseLocked() {
    if (ctx != 0L) {
      try {
        WhisperBridge.nativeFree(ctx)
      } catch (e: Throwable) {
        Log.w(TAG, "native free failed: ${e.javaClass.simpleName}")
      }
    }
    ctx = 0L
    loadedModelId = null
  }
}
