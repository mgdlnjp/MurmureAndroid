package app.murmure.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TextMergeTest {
  @Test fun emptyField() {
    val r = TextMerge.merge("", false, 0, 0, "Bonjour")
    assertEquals(MergeResult("Bonjour", 7), r)
  }

  @Test fun nullField() {
    assertEquals(MergeResult("Hello", 5), TextMerge.merge(null, false, -1, -1, "Hello"))
  }

  @Test fun hintIsIgnored() {
    val r = TextMerge.merge("Écrire un message", true, 0, 0, "Salut")
    assertEquals(MergeResult("Salut", 5), r)
  }

  @Test fun appendAtEndAddsSpace() {
    val r = TextMerge.merge("Bonjour", false, 7, 7, "tout le monde")
    assertEquals(MergeResult("Bonjour tout le monde", 21), r)
  }

  @Test fun cursorInMiddle() {
    val r = TextMerge.merge("Je mange", false, 2, 2, "suis content et je")
    assertEquals("Je suis content et je mange", r.text)
    assertEquals("Je suis content et je".length, r.cursor) // l'espace existant n'est pas réinséré
  }

  @Test fun selectionIsReplaced() {
    val r = TextMerge.merge("Il fait froid aujourd'hui", false, 8, 13, "chaud")
    assertEquals("Il fait chaud aujourd'hui", r.text)
    assertEquals(13, r.cursor)
  }

  @Test fun invalidSelectionGoesToEnd() {
    val r = TextMerge.merge("Hello", false, -1, -1, "world")
    assertEquals(MergeResult("Hello world", 11), r)
    val out = TextMerge.merge("Hello", false, 42, 50, "world")
    assertEquals(MergeResult("Hello world", 11), out)
  }

  @Test fun noSpaceBeforePunctuation() {
    val r = TextMerge.merge("Bonjour", false, 7, 7, ", ça va ?")
    assertEquals("Bonjour, ça va ?", r.text)
  }

  @Test fun noDoubleSpaceAfterWhitespace() {
    val r = TextMerge.merge("Bonjour ", false, 8, 8, "toi")
    assertEquals("Bonjour toi", r.text)
  }

  @Test fun noSpaceAfterWhenFollowedByPunctuationOrSpace() {
    assertEquals("Salut toi.", TextMerge.merge("Salut.", false, 5, 5, "toi").text)
    assertEquals("Salut toi ici", TextMerge.merge("Salut ici", false, 5, 5, "toi").text)
  }
}
