package app.murmure.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ModelCatalogTest {
  @Test fun twoModels() {
    assertEquals(listOf("small-q5_1", "base-q5_1"), ModelCatalog.all.map { it.id })
    assertEquals("small-q5_1", ModelCatalog.DEFAULT_ID)
    assertNotNull(ModelCatalog.byId(ModelCatalog.DEFAULT_ID))
    assertNull(ModelCatalog.byId("large"))
  }

  @Test fun smallSpec() {
    val s = ModelCatalog.byId("small-q5_1")!!
    assertEquals("ggml-small-q5_1.bin", s.fileName)
    assertEquals(190085487L, s.sizeBytes)
    assertEquals("ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb", s.sha256)
    assertEquals("https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin", ModelCatalog.url(s))
  }

  @Test fun baseSpec() {
    val b = ModelCatalog.byId("base-q5_1")!!
    assertEquals("ggml-base-q5_1.bin", b.fileName)
    assertEquals(59707625L, b.sizeBytes)
    assertEquals("422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898", b.sha256)
    assertEquals("https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin", ModelCatalog.url(b))
  }
}
