package app.murmure.core

data class ModelSpec(
  val id: String,
  val fileName: String,
  val sizeBytes: Long,
  val sha256: String,
  val labelFr: String,
  val labelEn: String
)

/** Modèles Whisper proposés (Hugging Face ggerganov/whisper.cpp, vérifiés le 2026-11-17). */
object ModelCatalog {
  const val DEFAULT_ID = "small-q5_1"
  private const val BASE_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/"

  val all: List<ModelSpec> = listOf(
    ModelSpec(
      id = "small-q5_1",
      fileName = "ggml-small-q5_1.bin",
      sizeBytes = 190085487L,
      sha256 = "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb",
      labelFr = "Small (recommandé)",
      labelEn = "Small (recommended)"
    ),
    ModelSpec(
      id = "base-q5_1",
      fileName = "ggml-base-q5_1.bin",
      sizeBytes = 59707625L,
      sha256 = "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898",
      labelFr = "Base (plus léger, plus rapide)",
      labelEn = "Base (lighter, faster)"
    )
  )

  fun byId(id: String): ModelSpec? = all.firstOrNull { it.id == id }

  fun url(spec: ModelSpec): String = BASE_URL + spec.fileName
}
