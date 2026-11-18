package app.murmure.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream

class ModelManagerLogicTest {
  @Test fun rangeHeader() {
    assertNull(ModelManager.rangeHeaderFor(0, 100))
    assertNull(ModelManager.rangeHeaderFor(-5, 100))
    assertEquals("bytes=42-", ModelManager.rangeHeaderFor(42, 100))
    assertEquals("bytes=100-", ModelManager.rangeHeaderFor(100, 100))
    assertNull(ModelManager.rangeHeaderFor(101, 100))
  }

  @Test fun sha256KnownVector() {
    assertEquals(
      "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
      ModelManager.sha256Hex(ByteArrayInputStream("abc".toByteArray()))
    )
  }

  @Test fun sha256Empty() {
    assertEquals(
      "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
      ModelManager.sha256Hex(ByteArrayInputStream(ByteArray(0)))
    )
  }
}
