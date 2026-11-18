package app.murmure.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** Tester, cycle 2 : cas limites de l'ordre SetTextCheck (recommandation 3). */
class SetTextCheckCycle2EdgeCasesTest {
  @Test fun emptyFieldStillEmptyIsFailed() {
    assertEquals(SetTextVerdict.FAILED, SetTextCheck.verdict(true, true, null, "", "Salut"))
    assertEquals(SetTextVerdict.FAILED, SetTextCheck.verdict(true, true, null, null, "Salut"))
    assertEquals(SetTextVerdict.FAILED, SetTextCheck.verdict(true, true, "", null, "Salut"))
  }

  @Test fun emptyFieldReceivingDictationIsConfirmed() {
    assertEquals(SetTextVerdict.CONFIRMED, SetTextCheck.verdict(true, true, null, "Salut", "Salut"))
  }

  @Test fun actionRefusedIsFailedWhateverTheContent() {
    assertEquals(SetTextVerdict.FAILED, SetTextCheck.verdict(false, true, "a", "a Salut", "Salut"))
    assertEquals(SetTextVerdict.FAILED, SetTextCheck.verdict(false, false, "a", null, "Salut"))
  }

  @Test fun phoneMaskReformattingIsProbablyApplied() {
    assertEquals(SetTextVerdict.PROBABLY_APPLIED, SetTextCheck.verdict(true, true, "", "06 12 34 56 78", "0612345678"))
  }

  @Test fun truncatedByMaxLengthIsProbablyApplied() {
    assertEquals(SetTextVerdict.PROBABLY_APPLIED, SetTextCheck.verdict(true, true, "abc", "abc Sal", "Salut"))
  }
}

/** Tester, cycle 2 : astérisques et parenthèses (recommandation 8), non-régression des crochets. */
class TextCleanupCycle2EdgeCasesTest {
  @Test fun emphasisKeptMarkerRemovedOnSameLine() =
    assertEquals("C'est *important*", TextCleanup.clean("C'est *important* *rires*"))

  @Test fun multiplicationsAndEquationsKept() {
    assertEquals("5 * 2 = 10", TextCleanup.clean("5 * 2 = 10"))
    assertEquals("Prix : 3 * 4 euros", TextCleanup.clean("Prix : 3 * 4 euros (musique)"))
  }

  @Test fun markersAreCaseInsensitiveAndMultiword() {
    assertEquals("", TextCleanup.clean("*RIRES*"))
    assertEquals("", TextCleanup.clean("*rires et applaudissements*"))
    assertEquals("Ok", TextCleanup.clean("(Rires) Ok"))
    assertEquals("Ok", TextCleanup.clean("( musique ) Ok"))
  }

  @Test fun bracketsStillRemoved() {
    assertEquals("Bonjour", TextCleanup.clean("[BLANK_AUDIO] Bonjour [Musique]"))
  }

  @Test fun lonelyAsterisksGiveEmpty() {
    assertEquals("", TextCleanup.clean("*"))
    assertEquals("", TextCleanup.clean("**"))
    assertEquals("", TextCleanup.clean("   "))
  }

  @Test fun nonMarkerParenthesesKept() =
    assertEquals("Réunion (salle B) demain", TextCleanup.clean("Réunion (salle B) demain"))
}

/** Tester, cycle 2 : failureFor (recommandation 7). */
class ModelManagerFailureCycle2Test {
  @Test fun storageErrorAfterCancelBecomesCancelled() {
    assertTrue(ModelManager.failureFor(InsufficientStorageException("x"), cancelled = true) is DownloadCancelledException)
  }

  @Test fun checksumErrorWithoutCancelIsKept() {
    val e = ModelChecksumException("sha256 mismatch")
    assertTrue(ModelManager.failureFor(e, cancelled = false) === e)
  }

  @Test fun plainIoAfterCancelIsNotTheSameInstance() {
    val e = IOException("rename failed")
    val mapped = ModelManager.failureFor(e, cancelled = true)
    assertTrue(mapped is DownloadCancelledException)
    assertFalse(mapped === e)
  }
}

/** Tester, cycle 2 : clear() et copie corrompue (recommandation 4). */
class HistoryStoreClearCycle2Test {
  private lateinit var dir: File
  private lateinit var ctx: FakeContext

  @Before fun setUp() {
    dir = Files.createTempDirectory("murmure-hist-c2").toFile()
    ctx = FakeContext(dir)
  }

  @After fun tearDown() {
    dir.deleteRecursively()
  }

  @Test fun clearWithoutAnyFileDoesNotThrow() {
    HistoryStore.clear(ctx)
    assertTrue(HistoryStore.all(ctx).isEmpty())
    assertFalse(File(dir, "history.json.corrupt").exists())
  }

  @Test fun clearDeletesOrphanCorruptCopy() {
    File(dir, "history.json.corrupt").writeText("ancien texte dicté")
    HistoryStore.clear(ctx)
    assertFalse(File(dir, "history.json.corrupt").exists())
  }

  @Test fun secondCorruptionReplacesPreviousCopy() {
    File(dir, "history.json").writeText("{a")
    HistoryStore.all(ctx)
    File(dir, "history.json").writeText("{b")
    HistoryStore.all(ctx)
    assertEquals("{b", File(dir, "history.json.corrupt").readText())
  }

  @Test fun addAfterClearWorks() {
    File(dir, "history.json").writeText("{oops")
    HistoryStore.all(ctx)
    HistoryStore.clear(ctx)
    HistoryStore.add(ctx, HistoryItem.create("Salut", "Salut", 1.0, "fr", "com.app", InsertOutcome.SET_TEXT, "small-q5_1", now = 1L))
    assertEquals(1, HistoryStore.all(ctx).size)
    assertFalse(File(dir, "history.json.corrupt").exists())
  }
}
