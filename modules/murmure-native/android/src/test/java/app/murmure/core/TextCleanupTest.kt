package app.murmure.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TextCleanupTest {
  @Test fun trimsAndCollapsesSpaces() {
    assertEquals("Bonjour tout le monde.", TextCleanup.clean("   Bonjour   tout\n le  monde. "))
  }

  @Test fun removesMarkers() {
    assertEquals("", TextCleanup.clean(" [BLANK_AUDIO] "))
    assertEquals("", TextCleanup.clean("[Musique]"))
    assertEquals("", TextCleanup.clean("(musique)"))
    assertEquals("Salut toi", TextCleanup.clean("[inaudible] Salut [BLANK_AUDIO] toi"))
  }

  @Test fun onlyPunctuationIsEmpty() {
    assertEquals("", TextCleanup.clean(" ... "))
  }

  @Test fun keepsNormalText() {
    assertEquals("Hello, world!", TextCleanup.clean("Hello, world!"))
  }
}
