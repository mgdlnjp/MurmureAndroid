package app.murmure.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** Cycle 2 : ordre des tests de SetTextCheck (recommandation 3). */
class SetTextCheckOrderTest {
  @Test fun unchangedFieldIsFailedEvenIfItContainsDictation() {
    assertEquals(SetTextVerdict.FAILED, SetTextCheck.verdict(true, true, "Salut", "Salut", "Salut"))
    assertEquals(SetTextVerdict.FAILED, SetTextCheck.verdict(true, true, "Bonjour Salut", "Bonjour Salut", "Salut"))
  }

  @Test fun changedFieldContainingDictationIsConfirmed() {
    assertEquals(SetTextVerdict.CONFIRMED, SetTextCheck.verdict(true, true, "Salut", "Salut Salut", "Salut"))
  }

  @Test fun changedFieldWithoutDictationIsProbablyApplied() {
    assertEquals(SetTextVerdict.PROBABLY_APPLIED, SetTextCheck.verdict(true, true, "Bonjour", "Bonjour Sal", "Salut"))
  }

  @Test fun unreadableFieldIsProbablyApplied() {
    assertEquals(SetTextVerdict.PROBABLY_APPLIED, SetTextCheck.verdict(true, false, "Salut", "Salut", "Salut"))
  }
}

/** Cycle 2 : astérisques limités à une liste de mots (recommandation 8). */
class TextCleanupAsteriskTest {
  @Test fun keepsMultiplication() = assertEquals("2 * 3 * 4", TextCleanup.clean("2 * 3 * 4"))
  @Test fun keepsMarkdownEmphasis() = assertEquals("C'est *vraiment* urgent", TextCleanup.clean("C'est *vraiment* urgent"))
  @Test fun removesKnownAsteriskMarkers() {
    assertEquals("", TextCleanup.clean("*rires*"))
    assertEquals("", TextCleanup.clean("*Musique*"))
    assertEquals("Bon.", TextCleanup.clean("*soupir* Bon."))
    assertEquals("Hello", TextCleanup.clean("* laughs * Hello"))
  }
  @Test fun parenthesesStillFiltered() {
    assertEquals("Rendez-vous (mardi)", TextCleanup.clean("Rendez-vous (mardi) (musique)"))
  }
}

/** Cycle 2 : suppression pendant téléchargement → CANCELLED (recommandation 7). */
class ModelManagerFailureTest {
  @Test fun ioErrorAfterCancelBecomesCancelled() {
    val mapped = ModelManager.failureFor(IOException("incomplete download"), cancelled = true)
    assertTrue(mapped is DownloadCancelledException)
  }

  @Test fun checksumErrorAfterCancelBecomesCancelled() {
    assertTrue(ModelManager.failureFor(ModelChecksumException("sha256 mismatch"), cancelled = true) is DownloadCancelledException)
  }

  @Test fun errorsWithoutCancelAreKept() {
    val e = IOException("HTTP 500")
    assertSame(e, ModelManager.failureFor(e, cancelled = false))
    val s = InsufficientStorageException("not enough space")
    assertSame(s, ModelManager.failureFor(s, cancelled = false))
  }

  @Test fun cancelledIsKept() {
    val e = DownloadCancelledException()
    assertSame(e, ModelManager.failureFor(e, cancelled = true))
  }
}

/** Cycle 2 : marquage sensible du presse-papiers (recommandation 5). */
class ClipboardSensitiveKeyTest {
  @Test fun legacyKeyMatchesPlatformConstant() =
    assertEquals("android.content.extra.IS_SENSITIVE", LEGACY_CLIP_EXTRA_IS_SENSITIVE)
}
