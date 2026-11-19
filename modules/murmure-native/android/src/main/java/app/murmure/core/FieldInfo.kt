package app.murmure.core

/**
 * Instantané du champ focalisé, extrait d'un AccessibilityNodeInfo.
 * Logique pure (sans dépendance Android) pour rester testable en JVM.
 */
data class FieldInfo(
  val packageName: String?,
  val isEditable: Boolean,
  val isPassword: Boolean,
  val inputType: Int,
  val text: String?,
  val isShowingHint: Boolean,
  val selectionStart: Int,
  val selectionEnd: Int
)

/** Valeurs d'android.text.InputType recopiées en dur pour les tests JVM. */
internal object InputTypes {
  const val TYPE_MASK_CLASS = 0x0000000f
  const val TYPE_MASK_VARIATION = 0x00000ff0
  const val TYPE_CLASS_TEXT = 0x00000001
  const val TYPE_CLASS_NUMBER = 0x00000002
  const val TYPE_TEXT_VARIATION_PASSWORD = 0x00000080
  const val TYPE_TEXT_VARIATION_VISIBLE_PASSWORD = 0x00000090
  const val TYPE_TEXT_VARIATION_WEB_PASSWORD = 0x000000e0
  const val TYPE_NUMBER_VARIATION_PASSWORD = 0x00000010
}

/** Vrai pour un champ mot de passe / sensible : la bulle n'y apparaît jamais, rien n'y est inséré. */
fun FieldInfo.isSensitive(): Boolean {
  if (isPassword) return true
  val cls = inputType and InputTypes.TYPE_MASK_CLASS
  val variation = inputType and InputTypes.TYPE_MASK_VARIATION
  return when (cls) {
    InputTypes.TYPE_CLASS_TEXT -> variation == InputTypes.TYPE_TEXT_VARIATION_PASSWORD ||
      variation == InputTypes.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
      variation == InputTypes.TYPE_TEXT_VARIATION_WEB_PASSWORD
    InputTypes.TYPE_CLASS_NUMBER -> variation == InputTypes.TYPE_NUMBER_VARIATION_PASSWORD
    else -> false
  }
}
