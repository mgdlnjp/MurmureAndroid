package app.murmure.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cas limites complémentaires (Tester) sur la logique pure. */
class TextMergeEdgeCasesTest {
  @Test fun reversedSelectionIsNormalized() {
    assertEquals(MergeResult("Il fait chaud aujourd'hui", 13), TextMerge.merge("Il fait froid aujourd'hui", false, 13, 8, "chaud"))
  }

  @Test fun halfInvalidSelectionGoesToEnd() {
    assertEquals(MergeResult("Hello world", 11), TextMerge.merge("Hello", false, 2, -1, "world"))
    assertEquals(MergeResult("Hello world", 11), TextMerge.merge("Hello", false, 5, 6, "world"))
  }

  @Test fun insertAtStartAddsTrailingSpaceOnly() {
    assertEquals(MergeResult("Bonjour monde", 8), TextMerge.merge("monde", false, 0, 0, "Bonjour"))
  }

  @Test fun insertInsideWordSeparatesBothSides() {
    assertEquals(MergeResult("abc X def", 6), TextMerge.merge("abcdef", false, 3, 3, "X"))
  }

  @Test fun hintWithCursorAtEndOfHint() {
    // Le curseur rapporté sur l'indice ne doit pas faire planter ni conserver l'indice.
    assertEquals(MergeResult("Salut", 5), TextMerge.merge("Message", true, 7, 7, "Salut"))
  }

  @Test fun nullTextWithHintFlag() {
    assertEquals(MergeResult("Salut", 5), TextMerge.merge(null, true, 0, 0, "Salut"))
  }

  @Test fun fullSelectionReplaced() {
    assertEquals(MergeResult("new", 3), TextMerge.merge("old text", false, 0, 8, "new"))
  }

  @Test fun ellipsisNotPrecededBySpace() {
    assertEquals("Bon…et alors", TextMerge.merge("Bon", false, 3, 3, "…et alors").text)
  }

  @Test fun beforeQuestionMarkNoTrailingSpace() {
    assertEquals(MergeResult("Ça va bien?", 10), TextMerge.merge("Ça va?", false, 5, 5, "bien"))
  }

  @Test fun newlineCountsAsWhitespace() {
    assertEquals(MergeResult("a x\nb", 3), TextMerge.merge("a\nb", false, 1, 1, "x"))
    assertEquals(MergeResult("a\nx", 3), TextMerge.merge("a\n", false, 2, 2, "x"))
  }

  @Test fun emojiSurrogatesBeforeCursor() {
    val r = TextMerge.merge("Hi 👍", false, 5, 5, "ok")
    assertEquals("Hi 👍 ok", r.text)
    assertEquals(r.text.length, r.cursor)
  }

  @Test fun existingTextPreservedLongInput() {
    val existing = "x".repeat(5000)
    val r = TextMerge.merge(existing, false, 2500, 2500, "mot")
    assertEquals(5000 + 3 + 2, r.text.length)
    assertTrue(r.text.startsWith("x".repeat(2500) + " mot "))
  }

  @Test fun cursorAlwaysWithinBounds() {
    val cases = listOf(-5 to -5, 0 to 0, 3 to 3, 10 to 2, 100 to 100, 2 to 100)
    for ((s, e) in cases) {
      val r = TextMerge.merge("abcdef", false, s, e, "Z")
      assertTrue("cursor ${r.cursor} for $s,$e", r.cursor in 0..r.text.length)
      assertTrue(r.text.contains("Z"))
    }
  }
}

class TextCleanupEdgeCasesTest {
  @Test fun leadingSpaceFromWhisper() = assertEquals("Bonjour.", TextCleanup.clean(" Bonjour."))
  @Test fun markerBeforePunctuation() = assertEquals("Bonjour.", TextCleanup.clean("Bonjour [BLANK_AUDIO]."))
  @Test fun englishMarkers() {
    assertEquals("Hello", TextCleanup.clean("[Music] Hello"))
    assertEquals("", TextCleanup.clean("(applause)"))
    assertEquals("", TextCleanup.clean("(Rires)"))
    assertEquals("C'est drôle", TextCleanup.clean("(Rires) C'est drôle"))
    assertEquals("", TextCleanup.clean("*rires*"))
    assertEquals("", TextCleanup.clean("[ Silence ]"))
  }
  @Test fun keepsRealParentheses() =
    assertEquals("Rendez-vous (mardi) à 10h", TextCleanup.clean("Rendez-vous (mardi) à 10h"))
  @Test fun keepsFrenchSpaceBeforeQuestionMark() = assertEquals("Ça va ?", TextCleanup.clean("Ça va ?"))
  @Test fun emptyAndBlank() {
    assertEquals("", TextCleanup.clean(""))
    assertEquals("", TextCleanup.clean(" \n\t "))
    assertEquals("", TextCleanup.clean("♪ ♪"))
  }
  @Test fun digitsOnlyKept() = assertEquals("42", TextCleanup.clean(" 42 "))
  @Test fun multipleMarkersOnly() = assertEquals("", TextCleanup.clean("[BLANK_AUDIO] (musique) [inaudible]"))
}

class FieldInfoEdgeCasesTest {
  private fun f(type: Int, pwd: Boolean = false, editable: Boolean = true) =
    FieldInfo("com.example", editable, pwd, type, null, false, 0, 0)

  @Test fun passwordVariationsWithFlags() {
    assertTrue(f(0x01 or 0x80 or 0x4000).isSensitive())    // + CAP_SENTENCES
    assertTrue(f(0x01 or 0x80 or 0x20000).isSensitive())   // + MULTI_LINE
    assertTrue(f(0x01 or 0xE0 or 0x80000).isSensitive())   // web password + NO_SUGGESTIONS
    assertTrue(f(0x02 or 0x10 or 0x1000).isSensitive())    // number password signé
    assertTrue(f(0x02 or 0x10 or 0x2000).isSensitive())    // number password décimal
  }

  @Test fun nonPasswordVariations() {
    assertFalse(f(0).isSensitive())               // TYPE_NULL
    assertFalse(f(0x04 or 0x10).isSensitive())    // DATETIME / DATE
    assertFalse(f(0x03).isSensitive())            // PHONE
    assertFalse(f(0x01 or 0xD0).isSensitive())    // WEB_EMAIL_ADDRESS
    assertFalse(f(0x01 or 0xA0).isSensitive())    // FILTER
    assertFalse(f(0x01 or 0x70).isSensitive())    // POSTAL_ADDRESS
    assertFalse(f(0x01 or 0x60).isSensitive())    // PERSON_NAME
  }

  @Test fun passwordFlagWinsEvenIfNotEditable() = assertTrue(f(0, pwd = true, editable = false).isSensitive())
}

class BubbleVisibilityEdgeCasesTest {
  private val own = "app.murmure.android"
  private val field = FieldInfo("com.example", true, false, 0x01, null, false, 0, 0)
  private val base = VisibilityInput(true, true, false, true, field, own, false)

  @Test fun noFocusNoIme() = assertFalse(BubbleVisibility.decide(base.copy(focused = null, imeVisible = false)))
  @Test fun sensitiveEvenWithIme() =
    assertFalse(BubbleVisibility.decide(base.copy(focused = field.copy(inputType = 0x02 or 0x10))))
  @Test fun lockedWithImeOnly() =
    assertFalse(BubbleVisibility.decide(base.copy(focused = null, keyguardLocked = true)))
  @Test fun ownAppEvenWithIme() =
    assertFalse(BubbleVisibility.decide(base.copy(focused = field.copy(packageName = own, isEditable = true))))
  @Test fun busyInOwnApp() =
    assertTrue(BubbleVisibility.decide(base.copy(busy = true, focused = field.copy(packageName = own))))
  @Test fun unknownPackageEditable() =
    assertTrue(BubbleVisibility.decide(base.copy(focused = field.copy(packageName = null), imeVisible = false)))
  @Test fun disabledAndModelMissing() =
    assertFalse(BubbleVisibility.decide(base.copy(bubbleEnabled = false, modelReady = false)))
  @Test fun nonEditableFocusedWithIme() =
    assertTrue(BubbleVisibility.decide(base.copy(focused = field.copy(isEditable = false), imeVisible = true)))
}

class AudioUtilsEdgeCasesTest {
  @Test fun zeroRate() = assertEquals(0.0, AudioUtils.durationSec(16000, 0), 0.0)
  @Test fun negativeRate() = assertEquals(0.0, AudioUtils.durationSec(16000, -1), 0.0)
  @Test fun maxRecording() = assertEquals(120.0, AudioUtils.durationSec(16000 * 120), 1e-9)
  @Test fun minDurationBoundary() {
    assertTrue(AudioUtils.durationSec(6399) < 0.4)
    assertFalse(AudioUtils.durationSec(6400) < 0.4)
  }
  @Test fun emptyIsSilenced() = assertTrue(AudioUtils.isSilenced(FloatArray(0)))
  @Test fun negativeSampleNotSilenced() = assertFalse(AudioUtils.isSilenced(floatArrayOf(0f, -1e-5f)))
  @Test fun fullScaleRms() = assertEquals(1f, AudioUtils.rms(floatArrayOf(1f, -1f)), 1e-6f)
  @Test fun pcmZeroCount() = assertArrayEquals(FloatArray(0), AudioUtils.pcm16ToFloat(shortArrayOf(1, 2, 3), 0), 0f)
  @Test fun pcmRangeWithinMinusOneOne() {
    val out = AudioUtils.pcm16ToFloat(shortArrayOf(Short.MIN_VALUE, Short.MAX_VALUE), 2)
    assertTrue(out.all { it >= -1f && it < 1f })
  }
}

class ModelCatalogEdgeCasesTest {
  @Test fun hashesAreLowercaseHex64() {
    for (m in ModelCatalog.all) {
      assertTrue(m.sha256, Regex("^[0-9a-f]{64}$").matches(m.sha256))
      assertTrue(m.fileName.endsWith(".bin"))
      assertTrue(m.labelFr.isNotBlank() && m.labelEn.isNotBlank())
    }
  }
  @Test fun idsMatchJsModelIdType() = assertEquals(setOf("small-q5_1", "base-q5_1"), ModelCatalog.all.map { it.id }.toSet())
  @Test fun uniqueIdsAndFiles() {
    assertEquals(ModelCatalog.all.size, ModelCatalog.all.map { it.id }.toSet().size)
    assertEquals(ModelCatalog.all.size, ModelCatalog.all.map { it.fileName }.toSet().size)
  }
  @Test fun byIdIsCaseSensitiveAndRejectsEmpty() {
    assertEquals(null, ModelCatalog.byId(""))
    assertEquals(null, ModelCatalog.byId("SMALL-Q5_1"))
  }
  @Test fun rangeHeaderLargeValues() {
    assertEquals("bytes=3000000000-", ModelManager.rangeHeaderFor(3_000_000_000L, 4_000_000_000L))
    assertEquals(null, ModelManager.rangeHeaderFor(1L, 0L))
  }
}
