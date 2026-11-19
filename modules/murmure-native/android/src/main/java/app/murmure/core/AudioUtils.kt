package app.murmure.core

import kotlin.math.sqrt

/** Utilitaires audio purs (16 kHz mono float). */
object AudioUtils {
  const val SAMPLE_RATE = 16000

  fun pcm16ToFloat(src: ShortArray, n: Int): FloatArray {
    val count = n.coerceIn(0, src.size)
    val out = FloatArray(count)
    for (i in 0 until count) out[i] = src[i] / 32768.0f
    return out
  }

  fun rms(samples: FloatArray): Float {
    if (samples.isEmpty()) return 0f
    var sum = 0.0
    for (s in samples) sum += (s * s).toDouble()
    return sqrt(sum / samples.size).toFloat()
  }

  /** Tous les échantillons nuls : micro coupé par le système (Android 11+ en arrière-plan). */
  fun isSilenced(samples: FloatArray): Boolean = samples.all { it == 0f }

  fun durationSec(sampleCount: Int, rate: Int = SAMPLE_RATE): Double =
    if (rate <= 0) 0.0 else sampleCount.toDouble() / rate
}
