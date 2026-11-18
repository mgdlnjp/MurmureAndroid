package app.murmure.core

/** Nettoyage de la sortie brute de Whisper (logique pure). */
object TextCleanup {
  // Marqueurs non vocaux : [BLANK_AUDIO], [Musique], (musique), [inaudible], *rires*…
  private val MARKERS = Regex("""\[[^\]]*]|\((?:musique|music|applaudissements|applause|rires|laughter|silence|inaudible|bruit|noise|blank_audio)[^)]*\)|\*[^*]{1,40}\*""", RegexOption.IGNORE_CASE)
  private val SPACES = Regex("""\s+""")
  private val SPACE_BEFORE_PUNCT = Regex("""\s+([,.)])""")

  fun clean(raw: String): String {
    val withoutMarkers = MARKERS.replace(raw, " ")
    val collapsed = SPACES.replace(withoutMarkers, " ").trim()
    val tidy = SPACE_BEFORE_PUNCT.replace(collapsed, "$1")
    // Rien d'autre que de la ponctuation → vide.
    return if (tidy.none { it.isLetterOrDigit() }) "" else tidy
  }
}
