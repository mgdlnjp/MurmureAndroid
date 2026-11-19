package app.murmure.core

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log

/** Capture micro 16 kHz mono PCM16 → float, avec arrêt automatique à 120 s. */
class AudioRecorder(private val onLimitReached: () -> Unit) {
  companion object {
    private const val TAG = "MurmureAudio"
    const val MAX_SECONDS = 120
  }

  private val mainHandler = Handler(Looper.getMainLooper())
  private val bufferLock = Any()
  /** Sérialise stop() (thread appelant) et release() (thread de lecture) d'un même AudioRecord. */
  private val recordLock = Any()
  private var record: AudioRecord? = null
  private var thread: Thread? = null
  @Volatile private var running = false
  private var samples = ShortArray(AudioUtils.SAMPLE_RATE * 10)
  private var count = 0

  val isRecording: Boolean get() = running

  @SuppressLint("MissingPermission")
  fun start(): Boolean {
    if (running) return true
    val minBuf = AudioRecord.getMinBufferSize(
      AudioUtils.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
    )
    if (minBuf <= 0) return false
    val bufferSize = maxOf(minBuf, AudioUtils.SAMPLE_RATE / 2 * 2)
    val r = createRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, bufferSize)
      ?: createRecord(MediaRecorder.AudioSource.MIC, bufferSize)
      ?: return false
    try {
      r.startRecording()
    } catch (e: Exception) {
      Log.w(TAG, "startRecording failed: ${e.javaClass.simpleName}")
      r.release()
      return false
    }
    if (r.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
      r.release()
      return false
    }
    synchronized(bufferLock) { count = 0 }
    record = r
    running = true
    thread = Thread({ readLoop(r, bufferSize / 2) }, "murmure-audio").apply { start() }
    return true
  }

  @SuppressLint("MissingPermission")
  private fun createRecord(source: Int, bufferSize: Int): AudioRecord? = try {
    val r = AudioRecord(source, AudioUtils.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
    if (r.state == AudioRecord.STATE_INITIALIZED) r else {
      r.release()
      null
    }
  } catch (e: SecurityException) {
    Log.w(TAG, "microphone permission denied")
    null
  } catch (e: Exception) {
    Log.w(TAG, "AudioRecord init failed: ${e.javaClass.simpleName}")
    null
  }

  /**
   * Boucle de lecture. Le thread de lecture est seul propriétaire de la libération de [r] :
   * release() n'est jamais appelé pendant un read() en cours, même si join() expire dans [shutdown].
   */
  private fun readLoop(r: AudioRecord, chunk: Int) {
    try {
      readSamples(r, chunk)
    } finally {
      synchronized(recordLock) {
        try {
          r.release()
        } catch (e: Exception) {
          Log.w(TAG, "release failed: ${e.javaClass.simpleName}")
        }
      }
    }
  }

  private fun readSamples(r: AudioRecord, chunk: Int) {
    val buf = ShortArray(maxOf(chunk, 1024))
    val max = AudioUtils.SAMPLE_RATE * MAX_SECONDS
    while (running) {
      val n = r.read(buf, 0, buf.size)
      if (n <= 0) {
        if (n < 0) break
        continue
      }
      var limit = false
      synchronized(bufferLock) {
        val toCopy = minOf(n, max - count)
        if (count + toCopy > samples.size) {
          samples = samples.copyOf(minOf(max, maxOf(samples.size * 2, count + toCopy)))
        }
        System.arraycopy(buf, 0, samples, count, toCopy)
        count += toCopy
        limit = count >= max
      }
      if (limit) {
        mainHandler.post { if (running) onLimitReached() }
        break
      }
    }
  }

  /** Arrête et retourne les échantillons float [-1, 1]. */
  fun stop(): FloatArray {
    shutdown()
    return synchronized(bufferLock) { AudioUtils.pcm16ToFloat(samples, count) }
  }

  /** Arrête sans conserver l'audio. */
  fun cancel() {
    shutdown()
    synchronized(bufferLock) { count = 0 }
  }

  private fun shutdown() {
    running = false
    val r = record
    record = null
    // stop() débloque read() ; si le thread de lecture a déjà libéré l'AudioRecord, stop() lève et c'est ignoré.
    synchronized(recordLock) {
      try {
        r?.stop()
      } catch (e: Exception) {
        // ignoré
      }
    }
    try {
      thread?.join(500)
    } catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
    }
    thread = null
    // Pas de release() ici : le thread de lecture s'en charge à sa sortie (voir readLoop).
  }
}
