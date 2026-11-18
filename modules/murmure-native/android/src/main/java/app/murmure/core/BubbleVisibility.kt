package app.murmure.core

data class VisibilityInput(
  val bubbleEnabled: Boolean,
  val modelReady: Boolean,
  val keyguardLocked: Boolean,
  val imeVisible: Boolean,
  val focused: FieldInfo?,
  val ownPackage: String,
  val busy: Boolean
)

/** Règles de visibilité de la bulle (logique pure). */
object BubbleVisibility {
  fun decide(i: VisibilityInput): Boolean {
    // Jamais masquée en cours de dictée (enregistrement / transcription).
    if (i.busy) return true
    if (!i.bubbleEnabled) return false
    if (i.keyguardLocked) return false
    if (i.focused?.packageName == i.ownPackage) return false
    if (i.focused?.isSensitive() == true) return false
    // modelReady=false → affichée quand même : le tap expliquera « modèle à télécharger ».
    return i.focused?.isEditable == true || i.imeVisible
  }
}
