package app.murmure.core

data class MergeResult(val text: String, val cursor: Int)

/** Fusion du texte dicté avec le contenu existant du champ, au curseur (logique pure). */
object TextMerge {
  private const val LEADING_PUNCTUATION = ".,;:!?)…"

  fun merge(existing: String?, isShowingHint: Boolean, selStart: Int, selEnd: Int, insert: String): MergeResult {
    val base = if (existing == null || isShowingHint) "" else existing
    val len = base.length

    var start = selStart
    var end = selEnd
    if (start < 0 || end < 0 || start > len || end > len) {
      start = len
      end = len
    }
    if (start > end) {
      val tmp = start
      start = end
      end = tmp
    }

    val before = base.substring(0, start)
    val after = base.substring(end)

    val sb = StringBuilder()
    val prev = before.lastOrNull()
    val startsWithPunctuation = insert.firstOrNull()?.let { LEADING_PUNCTUATION.indexOf(it) >= 0 } ?: false
    if (prev != null && !prev.isWhitespace() && !startsWithPunctuation) sb.append(' ')
    sb.append(insert)
    val next = after.firstOrNull()
    if (next != null && !next.isWhitespace() && !isPunctuation(next)) sb.append(' ')

    val inserted = sb.toString()
    return MergeResult(before + inserted + after, before.length + inserted.length)
  }

  private fun isPunctuation(c: Char): Boolean = LEADING_PUNCTUATION.indexOf(c) >= 0 || c == '\'' || c == '"' ||
    c == '-' || c == '(' || c == ']' || c == '}' || c == '»' || c == '«'
}
