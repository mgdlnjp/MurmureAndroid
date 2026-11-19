package app.murmure.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleVisibilityTest {
  private val own = "app.murmure.android"
  private val field = FieldInfo("com.whatsapp", true, false, 0x01, null, false, 0, 0)
  private val base = VisibilityInput(
    bubbleEnabled = true, modelReady = true, keyguardLocked = false,
    imeVisible = true, focused = field, ownPackage = own, busy = false
  )

  @Test fun visibleOnEditableField() = assertTrue(BubbleVisibility.decide(base))
  @Test fun hiddenWhenDisabled() = assertFalse(BubbleVisibility.decide(base.copy(bubbleEnabled = false)))
  @Test fun hiddenWhenLocked() = assertFalse(BubbleVisibility.decide(base.copy(keyguardLocked = true)))
  @Test fun hiddenInOwnApp() = assertFalse(BubbleVisibility.decide(base.copy(focused = field.copy(packageName = own))))
  @Test fun hiddenOnPassword() = assertFalse(BubbleVisibility.decide(base.copy(focused = field.copy(isPassword = true))))
  @Test fun hiddenOnSensitiveInputType() =
    assertFalse(BubbleVisibility.decide(base.copy(focused = field.copy(inputType = 0x01 or 0x80))))

  @Test fun visibleWithImeOnly() =
    assertTrue(BubbleVisibility.decide(base.copy(focused = null, imeVisible = true)))

  @Test fun visibleWithEditableWithoutIme() =
    assertTrue(BubbleVisibility.decide(base.copy(imeVisible = false)))

  @Test fun hiddenWithoutFieldNorIme() =
    assertFalse(BubbleVisibility.decide(base.copy(focused = field.copy(isEditable = false), imeVisible = false)))

  @Test fun visibleEvenIfModelMissing() = assertTrue(BubbleVisibility.decide(base.copy(modelReady = false)))

  @Test fun busyHasPriority() {
    val busy = base.copy(busy = true, bubbleEnabled = false, keyguardLocked = true, imeVisible = false,
      focused = field.copy(isPassword = true))
    assertTrue(BubbleVisibility.decide(busy))
  }
}
