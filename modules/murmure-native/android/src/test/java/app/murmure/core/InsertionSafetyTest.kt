package app.murmure.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val OWN = "app.murmure.android"

private fun field(
  pkg: String? = "com.whatsapp",
  editable: Boolean = true,
  password: Boolean = false,
  inputType: Int = 0x1
) = FieldInfo(pkg, editable, password, inputType, null, false, -1, -1)

/** Cycle 1 — A2 : la cible est oubliée dès que le focus n'est plus un champ utilisable. */
class TargetPolicyTest {
  @Test fun editableFieldInOtherAppIsRemembered() {
    assertEquals(TargetUpdate.REMEMBER, TargetPolicy.decide(field(), OWN, busy = false))
  }

  @Test fun noFocusForgetsTarget() {
    assertEquals(TargetUpdate.FORGET, TargetPolicy.decide(null, OWN, busy = false))
  }

  @Test fun nonEditableFocusForgetsTarget() {
    assertEquals(TargetUpdate.FORGET, TargetPolicy.decide(field(editable = false), OWN, busy = false))
  }

  @Test fun passwordFieldForgetsTarget() {
    assertEquals(TargetUpdate.FORGET, TargetPolicy.decide(field(password = true), OWN, busy = false))
    assertEquals(TargetUpdate.FORGET, TargetPolicy.decide(field(inputType = 0x81), OWN, busy = false))
  }

  @Test fun ownAppForgetsTarget() {
    assertEquals(TargetUpdate.FORGET, TargetPolicy.decide(field(pkg = OWN), OWN, busy = false))
  }

  @Test fun unknownPackageForgetsTarget() {
    assertEquals(TargetUpdate.FORGET, TargetPolicy.decide(field(pkg = null), OWN, busy = false))
  }

  @Test fun busyKeepsTargetWhateverTheFocus() {
    assertEquals(TargetUpdate.KEEP, TargetPolicy.decide(null, OWN, busy = true))
    assertEquals(TargetUpdate.KEEP, TargetPolicy.decide(field(pkg = "com.other"), OWN, busy = true))
    assertEquals(TargetUpdate.KEEP, TargetPolicy.decide(field(editable = false), OWN, busy = true))
  }

  @Test fun switchingAppThenLosingFocusDoesNotKeepOldApp() {
    // Scénario du rapport : champ de l'app A, puis app B sans champ exposé (focus null).
    var target: String? = null
    fun apply(f: FieldInfo?) {
      when (TargetPolicy.decide(f, OWN, busy = false)) {
        TargetUpdate.REMEMBER -> target = f?.packageName
        TargetUpdate.FORGET -> target = null
        TargetUpdate.KEEP -> Unit
      }
    }
    apply(field(pkg = "com.app.a"))
    assertEquals("com.app.a", target)
    apply(null)
    assertEquals(null, target)
  }
}

/** Cycle 1 — A3 : pas de collage (donc pas de doublon) si SET_TEXT a vraisemblablement agi. */
class SetTextCheckTest {
  @Test fun actionRefusedFallsBack() {
    assertEquals(SetTextVerdict.FAILED, SetTextCheck.verdict(false, true, "abc", "abc", "Salut"))
  }

  @Test fun textPresentIsConfirmed() {
    assertEquals(SetTextVerdict.CONFIRMED, SetTextCheck.verdict(true, true, "Bonjour", "Bonjour Salut", "Salut"))
  }

  @Test fun refreshFailedIsProbablyApplied() {
    assertEquals(SetTextVerdict.PROBABLY_APPLIED, SetTextCheck.verdict(true, false, "Bonjour", null, "Salut"))
  }

  @Test fun truncatedFieldIsProbablyApplied() {
    // maxLength=10 : le champ garde « Bonjour Sa ».
    assertEquals(SetTextVerdict.PROBABLY_APPLIED, SetTextCheck.verdict(true, true, "Bonjour", "Bonjour Sa", "Salut"))
  }

  @Test fun reformattedFieldIsProbablyApplied() {
    assertEquals(
      SetTextVerdict.PROBABLY_APPLIED,
      SetTextCheck.verdict(true, true, "", "06 12 34 56 78", "0612345678")
    )
  }

  @Test fun unchangedFieldFallsBack() {
    assertEquals(SetTextVerdict.FAILED, SetTextCheck.verdict(true, true, "Bonjour", "Bonjour", "Salut"))
  }

  @Test fun nullAndEmptyAreTheSameUnchangedState() {
    assertEquals(SetTextVerdict.FAILED, SetTextCheck.verdict(true, true, null, "", "Salut"))
    assertEquals(SetTextVerdict.FAILED, SetTextCheck.verdict(true, true, "", null, "Salut"))
  }

  @Test fun hintReplacedIsConfirmed() {
    assertEquals(SetTextVerdict.CONFIRMED, SetTextCheck.verdict(true, true, "Écrire un message", "Salut", "Salut"))
  }
}

/** Cycle 1 — A4 : un résultat de transcription arrivé après désactivation est ignoré. */
class TranscriptionGuardTest {
  @Test fun currentTokenIsAccepted() {
    val g = TranscriptionGuard()
    val t = g.begin()
    assertTrue(g.isCurrent(t))
  }

  @Test fun invalidateRejectsPendingResult() {
    val g = TranscriptionGuard()
    val t = g.begin()
    g.invalidate()
    assertFalse(g.isCurrent(t))
  }

  @Test fun newTranscriptionAfterInvalidateIsAccepted() {
    val g = TranscriptionGuard()
    val old = g.begin()
    g.invalidate()
    val fresh = g.begin()
    assertFalse(g.isCurrent(old))
    assertTrue(g.isCurrent(fresh))
  }

  @Test fun shutdownRejectsEverythingUntilReopen() {
    val g = TranscriptionGuard()
    val t = g.begin()
    g.shutdown()
    assertFalse(g.isCurrent(t))
    assertFalse(g.isCurrent(g.begin()))
    g.reopen()
    assertFalse(g.isCurrent(t))
    assertTrue(g.isCurrent(g.begin()))
  }
}
