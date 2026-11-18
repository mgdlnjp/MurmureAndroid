package app.murmure.core

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

class ModelChecksumException(message: String) : IOException(message)
class InsufficientStorageException(message: String) : IOException(message)
class DownloadCancelledException : IOException("cancelled")
class DownloadBusyException : IllegalStateException("another download is in progress")

/** Téléchargement (avec reprise), vérification et suppression des modèles Whisper. */
object ModelManager {
  private const val TAG = "MurmureModels"
  private const val DIR_NAME = "whisper-models"
  private const val USER_AGENT = "Murmure-Android/0.1"
  private const val CONNECT_TIMEOUT_MS = 15_000
  private const val READ_TIMEOUT_MS = 30_000
  private const val MAX_REDIRECTS = 5
  private const val PROGRESS_INTERVAL_MS = 250L
  private const val STORAGE_MARGIN = 10L * 1024 * 1024

  private val lock = Any()
  @Volatile private var currentId: String? = null
  private val cancelFlag = AtomicBoolean(false)

  val downloadingId: String? get() = currentId

  fun dir(context: Context): File = File(context.filesDir, DIR_NAME).apply { mkdirs() }

  fun installedFile(context: Context, spec: ModelSpec): File = File(dir(context), spec.fileName)

  fun partFile(context: Context, spec: ModelSpec): File = File(dir(context), spec.fileName + ".part")

  fun isInstalled(context: Context, spec: ModelSpec): Boolean {
    val f = installedFile(context, spec)
    return f.isFile && f.length() == spec.sizeBytes
  }

  fun partialBytes(context: Context, spec: ModelSpec): Long {
    val p = partFile(context, spec)
    return if (p.isFile) p.length() else 0L
  }

  /**
   * En-tête Range pour reprendre un téléchargement (pur).
   * null si rien à reprendre ou si le .part est incohérent (plus grand que prévu).
   */
  fun rangeHeaderFor(partSize: Long, total: Long): String? {
    if (partSize <= 0L) return null
    if (partSize > total) return null
    return "bytes=$partSize-"
  }

  /** SHA-256 hexadécimal minuscule d'un flux (pur). */
  fun sha256Hex(input: InputStream): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(64 * 1024)
    while (true) {
      val read = input.read(buffer)
      if (read < 0) break
      digest.update(buffer, 0, read)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  /** Bloquant : à appeler sur un thread dédié. Lève en cas d'erreur. */
  fun download(context: Context, spec: ModelSpec, onProgress: (Long, Long) -> Unit) {
    synchronized(lock) {
      if (currentId != null) throw DownloadBusyException()
      currentId = spec.id
      cancelFlag.set(false)
    }
    try {
      doDownload(context, spec, onProgress)
    } catch (e: IOException) {
      throw failureFor(e, cancelFlag.get())
    } finally {
      synchronized(lock) { currentId = null }
    }
  }

  /**
   * Erreur à remonter (pur) : après une annulation (cancel ou delete du modèle en cours),
   * toute erreur d'E/S induite (.part supprimé, flux coupé, rename impossible) devient CANCELLED.
   */
  fun failureFor(e: IOException, cancelled: Boolean): IOException =
    if (cancelled && e !is DownloadCancelledException) DownloadCancelledException() else e

  fun cancel(id: String) {
    if (currentId == id) cancelFlag.set(true)
  }

  fun delete(context: Context, id: String) {
    val spec = ModelCatalog.byId(id) ?: return
    cancel(id)
    installedFile(context, spec).delete()
    partFile(context, spec).delete()
    WhisperEngine.onModelDeleted(id)
  }

  private fun doDownload(context: Context, spec: ModelSpec, onProgress: (Long, Long) -> Unit) {
    val part = partFile(context, spec)
    if (isInstalled(context, spec)) {
      onProgress(spec.sizeBytes, spec.sizeBytes)
      return
    }

    var already = if (part.isFile) part.length() else 0L
    val range = rangeHeaderFor(already, spec.sizeBytes)
    if (range == null && already > 0L) {
      // .part plus grand que prévu : incohérent, on repart de zéro.
      part.delete()
      already = 0L
    }

    val remaining = spec.sizeBytes - already
    if (dir(context).usableSpace < remaining + STORAGE_MARGIN) {
      throw InsufficientStorageException("not enough space")
    }

    if (already == spec.sizeBytes) {
      finalizeDownload(context, spec, part)
      onProgress(spec.sizeBytes, spec.sizeBytes)
      return
    }

    val connection = open(ModelCatalog.url(spec), range)
    try {
      val code = connection.responseCode
      val append = when {
        code == HttpURLConnection.HTTP_PARTIAL && range != null -> true
        code == HttpURLConnection.HTTP_OK -> false // serveur sans Range : repartir de zéro
        else -> throw IOException("HTTP $code")
      }
      if (!append) already = 0L

      connection.inputStream.use { input ->
        FileOutputStream(part, append).use { output ->
          val buffer = ByteArray(64 * 1024)
          var written = already
          var lastEmit = 0L
          while (true) {
            if (cancelFlag.get()) throw DownloadCancelledException()
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            written += read
            if (written > spec.sizeBytes) break
            val now = System.currentTimeMillis()
            if (now - lastEmit >= PROGRESS_INTERVAL_MS) {
              lastEmit = now
              onProgress(written, spec.sizeBytes)
            }
          }
          output.flush()
          output.fd.sync()
        }
      }
    } finally {
      connection.disconnect()
    }

    if (cancelFlag.get()) throw DownloadCancelledException()
    finalizeDownload(context, spec, part)
    onProgress(spec.sizeBytes, spec.sizeBytes)
  }

  private fun finalizeDownload(context: Context, spec: ModelSpec, part: File) {
    if (part.length() != spec.sizeBytes) {
      if (part.length() > spec.sizeBytes) {
        part.delete()
        throw ModelChecksumException("size mismatch")
      }
      // Flux interrompu sans erreur explicite : on garde le .part pour reprendre.
      throw IOException("incomplete download")
    }
    val hash = FileInputStream(part).use { sha256Hex(it) }
    if (!hash.equals(spec.sha256, ignoreCase = true)) {
      part.delete()
      throw ModelChecksumException("sha256 mismatch")
    }
    // Suppression demandée pendant la vérification : ne pas réinstaller le modèle.
    if (cancelFlag.get()) throw DownloadCancelledException()
    val target = installedFile(context, spec)
    target.delete()
    if (!part.renameTo(target)) throw IOException("rename failed")
    Log.i(TAG, "model ${spec.id} installed")
  }

  /** Ouvre la connexion en suivant manuellement les redirections (y compris inter-hôtes). */
  private fun open(startUrl: String, range: String?): HttpURLConnection {
    var url = URL(startUrl)
    repeat(MAX_REDIRECTS + 1) {
      val c = (url.openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = false
        connectTimeout = CONNECT_TIMEOUT_MS
        readTimeout = READ_TIMEOUT_MS
        setRequestProperty("User-Agent", USER_AGENT)
        setRequestProperty("Accept-Encoding", "identity")
        if (range != null) setRequestProperty("Range", range)
      }
      val code = c.responseCode
      if (code in 300..399 && code != HttpURLConnection.HTTP_NOT_MODIFIED) {
        val location = c.getHeaderField("Location")
        c.disconnect()
        if (location.isNullOrEmpty()) throw IOException("redirect without location")
        url = URL(url, location)
        return@repeat
      }
      return c
    }
    throw IOException("too many redirects")
  }
}
