package app.murmure.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldInfoTest {
  private fun field(inputType: Int, isPassword: Boolean = false) =
    FieldInfo("com.example", true, isPassword, inputType, null, false, 0, 0)

  @Test fun textPassword() = assertTrue(field(0x01 or 0x80).isSensitive())
  @Test fun visiblePassword() = assertTrue(field(0x01 or 0x90).isSensitive())
  @Test fun webPassword() = assertTrue(field(0x01 or 0xE0).isSensitive())
  @Test fun numberPassword() = assertTrue(field(0x02 or 0x10).isSensitive())
  @Test fun isPasswordFlag() = assertTrue(field(0x01, isPassword = true).isSensitive())

  @Test fun normalText() {
    assertFalse(field(0x01).isSensitive())
    assertFalse(field(0x01 or 0x20).isSensitive()) // email address
    assertFalse(field(0x01 or 0x4000).isSensitive()) // cap sentences flag
    assertFalse(field(0x02).isSensitive()) // number
    assertFalse(field(0x01 or 0x10).isSensitive()) // URI (même valeur que NUMBER_PASSWORD, classe TEXT)
  }
}
