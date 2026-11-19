package app.murmure.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioUtilsTest {
  @Test fun pcmConversion() {
    val out = AudioUtils.pcm16ToFloat(shortArrayOf(0, 16384, -32768, 32767, 99), 4)
    assertArrayEquals(floatArrayOf(0f, 0.5f, -1f, 32767f / 32768f), out, 1e-6f)
  }

  @Test fun pcmConversionClampsCount() {
    assertEquals(2, AudioUtils.pcm16ToFloat(shortArrayOf(1, 2), 10).size)
    assertEquals(0, AudioUtils.pcm16ToFloat(shortArrayOf(1, 2), -1).size)
  }

  @Test fun rms() {
    assertEquals(0f, AudioUtils.rms(FloatArray(0)), 0f)
    assertEquals(0.5f, AudioUtils.rms(floatArrayOf(0.5f, -0.5f, 0.5f, -0.5f)), 1e-6f)
  }

  @Test fun silenced() {
    assertTrue(AudioUtils.isSilenced(FloatArray(16000)))
    assertFalse(AudioUtils.isSilenced(floatArrayOf(0f, 0f, 0.0001f)))
  }

  @Test fun duration() {
    assertEquals(1.0, AudioUtils.durationSec(16000), 1e-9)
    assertEquals(0.4, AudioUtils.durationSec(6400), 1e-9)
    assertEquals(2.0, AudioUtils.durationSec(16000, 8000), 1e-9)
  }
}
