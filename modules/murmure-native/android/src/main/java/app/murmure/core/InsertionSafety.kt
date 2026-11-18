package app.murmure.core

/** Que faire de la cible mémorisée après un événement d'accessibilité. */
enum class TargetUpdate { REMEMBER, FORGET, KEEP }

/**
 * Suivi du champ cible (logique pure, testable en JVM).
 * Pendant une dictée, la cible est figée. Sinon, elle n'est conservée que si le champ
 * focalisé est éditable, non sensible et hors de Murmure ; tout autre focus l'oublie.
 */
object TargetPolicy {
  fun decide(focused: FieldInfo?, ownPackage: String, busy: Boolean): TargetUpdate {
    if (busy) return TargetUpdate.KEEP
    if (focused == null) return TargetUpdate.FORGET
    if (!focused.isEditable || focused.isSensitive()) return TargetUpdate.FORGET
    if (focused.packageName == null || focused.packageName == ownPackage) return TargetUpdate.FORGET
    return TargetUpdate.REMEMBER
  }
}

/** Résultat de ACTION_SET_TEXT après vérification. */
enum class SetTextVerdict {
  /** Le texte dicté est bien présent dans le champ. */
  CONFIRMED,

  /** L'action a réussi et le champ a changé (ou n'a pas pu être relu) : ne surtout pas recoller. */
  PROBABLY_APPLIED,

  /** Rien n'a été appliqué : le repli par collage est sûr. */
  FAILED
}

/** Vérification de l'insertion directe (logique pure), pour éviter toute double insertion. */
object SetTextCheck {
  /**
   * @param actionOk valeur de retour de performAction(ACTION_SET_TEXT)
   * @param refreshed valeur de retour de node.refresh() après l'action
   * @param before texte du champ avant l'action (null si vide)
   * @param after texte du champ relu après l'action (ignoré si [refreshed] est faux)
   * @param inserted texte dicté
   */
  fun verdict(actionOk: Boolean, refreshed: Boolean, before: String?, after: String?, inserted: String): SetTextVerdict {
    if (!actionOk) return SetTextVerdict.FAILED
    if (!refreshed) return SetTextVerdict.PROBABLY_APPLIED
    val now = after.orEmpty()
    // Contenu inchangé : SET_TEXT a été ignoré, même si le champ contenait déjà le texte dicté.
    if (now == before.orEmpty()) return SetTextVerdict.FAILED
    if (now.contains(inserted)) return SetTextVerdict.CONFIRMED
    // Champ reformaté ou tronqué (maxLength, masque) : le texte a changé, donc SET_TEXT a agi.
    return SetTextVerdict.PROBABLY_APPLIED
  }
}

/**
 * Jeton de génération : une transcription lancée avant une désactivation du service
 * (ou une annulation) ne doit rien insérer ni historiser. Utilisé sur le main thread.
 */
class TranscriptionGuard {
  private var generation = 0
  private var active = true

  /** Démarre une transcription ; renvoie le jeton à vérifier dans le callback. */
  fun begin(): Int = generation

  /** Invalide toutes les transcriptions en cours. */
  fun invalidate() {
    generation++
  }

  /** Service détruit : plus aucun résultat ne sera accepté. */
  fun shutdown() {
    active = false
    generation++
  }

  /** Service (re)connecté. */
  fun reopen() {
    active = true
  }

  fun isCurrent(token: Int): Boolean = active && token == generation
}
