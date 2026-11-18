package app.murmure.core

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

enum class InsertOutcome { SET_TEXT, PASTED, CLIPBOARD_ONLY, NOTHING }

/** Construit un FieldInfo à partir d'un nœud (lecture du texte uniquement si [withText]). */
fun fieldInfoOf(node: AccessibilityNodeInfo, withText: Boolean = false): FieldInfo {
  val text = if (withText) node.text?.toString() else null
  // isShowingHintText et hintText sont API 26 (= minSdk) ; garde conservée par robustesse.
  val showingHint = if (Build.VERSION.SDK_INT >= 26) node.isShowingHintText else false
  return FieldInfo(
    packageName = node.packageName?.toString(),
    isEditable = node.isEditable,
    isPassword = node.isPassword,
    inputType = node.inputType,
    text = text,
    isShowingHint = showingHint,
    selectionStart = node.textSelectionStart,
    selectionEnd = node.textSelectionEnd
  )
}

/** Chaîne de ClipDescription.EXTRA_IS_SENSITIVE (API 33), reconnue par Gboard sur les versions antérieures. */
internal const val LEGACY_CLIP_EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

/** Insertion du texte dicté : SET_TEXT fusionné → collage → presse-papiers seul. */
object TextInserter {
  private const val MAX_SET_TEXT_LENGTH = 10_000

  fun insert(service: AccessibilityService, target: AccessibilityNodeInfo?, text: String, originPackage: String? = null): InsertOutcome {
    if (text.isEmpty()) return InsertOutcome.NOTHING

    var node = target?.takeIf { it.refresh() }
    if (node == null) node = service.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

    if (node == null) return clipboardOnly(service, text)

    val info = fieldInfoOf(node, withText = true)
    if (info.isSensitive()) {
      toast(service, R.string.murmure_toast_protected_field)
      return InsertOutcome.NOTHING
    }
    // Le focus a changé d'application pendant la transcription : on n'insère pas ailleurs.
    if (originPackage != null && info.packageName != null && info.packageName != originPackage) {
      return clipboardOnly(service, text)
    }

    if (info.isEditable && (info.text?.length ?: 0) + text.length <= MAX_SET_TEXT_LENGTH) {
      when (trySetText(node, info, text)) {
        SetTextVerdict.CONFIRMED -> return InsertOutcome.SET_TEXT
        // Le champ a vraisemblablement reçu le texte (reformaté, tronqué ou illisible) :
        // pas de collage (doublon) ni de copie silencieuse ; la dictée reste dans l'historique.
        SetTextVerdict.PROBABLY_APPLIED -> return InsertOutcome.SET_TEXT
        SetTextVerdict.FAILED -> Unit
      }
    }

    copyToClipboard(service, text)
    if (node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) return InsertOutcome.PASTED

    toast(service, R.string.murmure_toast_copied)
    return InsertOutcome.CLIPBOARD_ONLY
  }

  private fun trySetText(node: AccessibilityNodeInfo, info: FieldInfo, text: String): SetTextVerdict {
    val merged = TextMerge.merge(info.text, info.isShowingHint, info.selectionStart, info.selectionEnd, text)
    val args = Bundle().apply {
      putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, merged.text)
    }
    if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return SetTextVerdict.FAILED
    val sel = Bundle().apply {
      putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, merged.cursor)
      putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, merged.cursor)
    }
    node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel)
    val refreshed = node.refresh()
    val after = if (refreshed) node.text?.toString() else null
    return SetTextCheck.verdict(actionOk = true, refreshed = refreshed, before = info.text, after = after, inserted = text)
  }

  private fun clipboardOnly(context: Context, text: String): InsertOutcome {
    copyToClipboard(context, text)
    toast(context, R.string.murmure_toast_copied)
    return InsertOutcome.CLIPBOARD_ONLY
  }

  /**
   * Copie de repli : le texte dicté est marqué sensible pour qu'Android 13+ ne l'affiche pas
   * dans l'aperçu du presse-papiers et que les claviers ne l'archivent pas dans leur historique.
   */
  fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    val clip = ClipData.newPlainText("Murmure", text)
    val key = if (Build.VERSION.SDK_INT >= 33) ClipDescription.EXTRA_IS_SENSITIVE else LEGACY_CLIP_EXTRA_IS_SENSITIVE
    clip.description.extras = PersistableBundle().apply { putBoolean(key, true) }
    cm.setPrimaryClip(clip)
  }

  private fun toast(context: Context, resId: Int) {
    Toast.makeText(context, resId, Toast.LENGTH_LONG).show()
  }
}
