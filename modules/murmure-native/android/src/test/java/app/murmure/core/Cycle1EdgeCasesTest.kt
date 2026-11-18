package app.murmure.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tester, cycle 1 : cas limites des corrections A2, A3 et A4. */
private const val OWN_PKG = "app.murmure.android"

private fun f(
  pkg: String? = "com.google.android.gm",
  editable: Boolean = true,
  password: Boolean = false,
  inputType: Int = 0x1
) = FieldInfo(pkg, editable, password, inputType, null, false, -1, -1)

class TargetPolicyEdgeCasesTest {
  @Test fun numberPasswordAndWebPasswordForgetTarget() {
    assertEquals(TargetUpdate.FORGET, TargetPolicy.decide(f(inputType = 0x12), OWN_PKG, busy = false)) // NUMBER|PASSWORD
    assertEquals(TargetUpdate.FORGET, TargetPolicy.decide(f(inputType = 0xE1), OWN_PKG, busy = false)) // TEXT|WEB_PASSWORD
    assertEquals(TargetUpdate.FORGET, TargetPolicy.decide(f(inputType = 0x91), OWN_PKG, busy = false)) // TEXT|VISIBLE_PASSWORD
  }

  @Test fun uriFieldIsNotSensitiveAndIsRemembered() {
    assertEquals(TargetUpdate.REMEMBER, TargetPolicy.decide(f(inputType = 0x11), OWN_PKG, busy = false))
  }

  @Test fun busyKeepsEvenOnSensitiveOrOwnApp() {
    assertEquals(TargetUpdate.KEEP, TargetPolicy.decide(f(password = true), OWN_PKG, busy = true))
    assertEquals(TargetUpdate.KEEP, TargetPolicy.decide(f(pkg = OWN_PKG), OWN_PKG, busy = true))
  }

  @Test fun appAThenAppBFieldSwitchesTargetToB() {
    var target: String? = null
    for (field in listOf(f(pkg = "com.a"), f(pkg = "com.b"))) {
      if (TargetPolicy.decide(field, OWN_PKG, busy = false) == TargetUpdate.REMEMBER) target = field.packageName
    }
    assertEquals("com.b", target)
  }

  @Test fun appAThenNonEditableInBThenBusyKeepsNothing() {
    // A, puis focus non éditable dans B (cible oubliée), puis dictée : la cible figée est null.
    var target: String? = null
    fun step(field: FieldInfo?, busy: Boolean) {
      when (TargetPolicy.decide(field, OWN_PKG, busy)) {
        TargetUpdate.REMEMBER -> target = field?.packageName
        TargetUpdate.FORGET -> target = null
        TargetUpdate.KEEP -> Unit
      }
    }
    step(f(pkg = "com.a"), busy = false)
    step(f(pkg = "com.b", editable = false), busy = false)
    step(null, busy = true)
    assertEquals(null, target)
  }
}

class SetTextCheckEdgeCasesTest {
  @Test fun refusedActionWinsOverRefreshFailure() {
    assertEquals(SetTextVerdict.FAILED, SetTextCheck.verdict(false, false, null, null, "Salut"))
  }

  @Test fun emojiAndAccentsAreConfirmed() {
    assertEquals(SetTextVerdict.CONFIRMED, SetTextCheck.verdict(true, true, "Été", "Été ok 👍 ça", "ok 👍 ça"))
  }

  @Test fun emptyFieldReceivingTextIsConfirmed() {
    assertEquals(SetTextVerdict.CONFIRMED, SetTextCheck.verdict(true, true, null, "Bonjour.", "Bonjour."))
  }

  @Test fun fieldClearedByAppIsProbablyAppliedNotPasted() {
    // Le champ a changé (vidé) après SET_TEXT : on ne recolle pas.
    assertEquals(SetTextVerdict.PROBABLY_APPLIED, SetTextCheck.verdict(true, true, "abc", "", "Salut"))
  }

  @Test fun caseChangedByFieldIsProbablyApplied() {
    // Champ qui force les majuscules : texte présent mais transformé.
    assertEquals(SetTextVerdict.PROBABLY_APPLIED, SetTextCheck.verdict(true, true, "", "SALUT", "Salut"))
  }

  /**
   * Cycle 2 (recommandation 3, ancien comportement O5 volontairement changé) : si le champ contenait
   * déjà le texte dicté et que l'app ignore SET_TEXT (contenu inchangé), le verdict est FAILED,
   * ce qui déclenche le repli par collage au lieu de perdre la dictée.
   */
  @Test fun unchangedFieldAlreadyContainingDictationIsFailed() {
    assertEquals(SetTextVerdict.FAILED, SetTextCheck.verdict(true, true, "ok merci", "ok merci", "ok"))
  }
}

class TranscriptionGuardEdgeCasesTest {
  @Test fun interruptDuringTranscriptionThenNewDictation() {
    // Annulation (cleanup) -> invalidate ; le service reste actif.
    val g = TranscriptionGuard()
    val first = g.begin()
    g.invalidate()
    val second = g.begin()
    assertFalse(g.isCurrent(first))
    assertTrue(g.isCurrent(second))
  }

  @Test fun teardownSequenceRejectsPendingResult() {
    // teardown = cleanup (invalidate) puis shutdown, appelé deux fois (onUnbind puis onDestroy).
    val g = TranscriptionGuard()
    val t = g.begin()
    g.invalidate(); g.shutdown()
    g.invalidate(); g.shutdown()
    assertFalse(g.isCurrent(t))
    g.reopen()
    assertFalse(g.isCurrent(t))
    assertTrue(g.isCurrent(g.begin()))
  }

  @Test fun reopenWithoutShutdownDoesNotReviveInvalidatedToken() {
    val g = TranscriptionGuard()
    val t = g.begin()
    g.invalidate()
    g.reopen()
    assertFalse(g.isCurrent(t))
  }

  @Test fun twoTokensFromSameGenerationAreBothCurrent() {
    // Deux begin() sans invalidation : documenté, les deux résultats seraient acceptés
    // (impossible en pratique : TRANSCRIBING bloque un nouveau tap).
    val g = TranscriptionGuard()
    val a = g.begin()
    val b = g.begin()
    assertTrue(g.isCurrent(a))
    assertTrue(g.isCurrent(b))
  }
}
