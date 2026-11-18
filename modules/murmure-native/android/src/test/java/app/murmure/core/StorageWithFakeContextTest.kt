package app.murmure.core

import android.content.ContextWrapper
import org.json.JSONException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files

/** Contexte minimal : seul filesDir est utilisé par HistoryStore et ModelManager. */
class FakeContext(private val dir: File) : ContextWrapper(null) {
  override fun getFilesDir(): File = dir
  override fun getApplicationContext() = this
}

class HistoryStoreFileTest {
  private lateinit var dir: File
  private lateinit var ctx: FakeContext

  @Before fun setUp() {
    dir = Files.createTempDirectory("murmure-hist").toFile()
    ctx = FakeContext(dir)
  }

  @After fun tearDown() {
    dir.deleteRecursively()
  }

  private fun item(i: Int) = HistoryItem.create("texte $i é 👍", " texte $i ", 1.5, "fr", "com.app", InsertOutcome.SET_TEXT, "small-q5_1", now = i.toLong())

  @Test fun emptyWhenNoFile() = assertTrue(HistoryStore.all(ctx).isEmpty())

  @Test fun addNewestFirstAndPersistsUnicode() {
    HistoryStore.add(ctx, item(1))
    HistoryStore.add(ctx, item(2))
    val all = HistoryStore.all(ctx)
    assertEquals(listOf("texte 2 é 👍", "texte 1 é 👍"), all.map { it.text })
    assertEquals("SET_TEXT", all[0].outcome)
    assertFalse("pas de fichier temporaire résiduel", File(dir, "history.json.tmp").exists())
  }

  @Test fun capAt500ThroughAdd() {
    val seed = (1..499).map { item(it) }.reversed()
    File(dir, "history.json").writeText(HistoryStore.toJson(seed))
    HistoryStore.add(ctx, item(500))
    HistoryStore.add(ctx, item(501))
    val all = HistoryStore.all(ctx)
    assertEquals(500, all.size)
    assertEquals(501L, all.first().date)
    assertEquals(2L, all.last().date)
  }

  @Test fun deleteAndClear() {
    HistoryStore.add(ctx, item(1))
    HistoryStore.add(ctx, item(2))
    val target = HistoryStore.all(ctx).last().id
    HistoryStore.delete(ctx, "unknown-id")
    assertEquals(2, HistoryStore.all(ctx).size)
    HistoryStore.delete(ctx, target)
    assertEquals(listOf(2L), HistoryStore.all(ctx).map { it.date })
    HistoryStore.clear(ctx)
    assertTrue(HistoryStore.all(ctx).isEmpty())
    assertEquals("[]", File(dir, "history.json").readText())
  }

  @Test fun corruptedFileIsRenamedAndAppCarriesOn() {
    File(dir, "history.json.corrupt").writeText("old")
    File(dir, "history.json").writeText("{oops")
    assertTrue(HistoryStore.all(ctx).isEmpty())
    assertFalse(File(dir, "history.json").exists())
    assertEquals("{oops", File(dir, "history.json.corrupt").readText())
    HistoryStore.add(ctx, item(1))
    assertEquals(1, HistoryStore.all(ctx).size)
  }

  // Cycle 2, recommandation 4 : clear() efface aussi la copie corrompue (texte dicté).
  @Test fun clearAlsoDeletesCorruptCopy() {
    File(dir, "history.json").writeText("{oops")
    assertTrue(HistoryStore.all(ctx).isEmpty())
    assertTrue(File(dir, "history.json.corrupt").exists())
    HistoryStore.clear(ctx)
    assertFalse(File(dir, "history.json.corrupt").exists())
    assertEquals("[]", File(dir, "history.json").readText())
  }

  @Test fun parseThrowsOnNonArray() {
    try {
      HistoryStore.parse("{}")
      fail("JSONException attendue")
    } catch (e: JSONException) {
      // ok
    }
  }

  @Test fun toleratesJunkElementsAndMissingFields() {
    val parsed = HistoryStore.fromJson("""[1, "x", null, {"id":"a"}, {"id":"b","language":"","appPackage":""}]""")
    assertEquals(listOf("a", "b"), parsed.map { it.id })
    assertEquals(0L, parsed[0].date)
    assertEquals("", parsed[0].text)
    assertEquals("NOTHING", parsed[0].outcome)
    assertEquals(null, parsed[0].language)
    assertEquals(null, parsed[1].language)
    assertEquals(null, parsed[1].appPackage)
  }

  @Test fun createGeneratesUniqueIds() {
    assertNotEquals(item(1).id, item(1).id)
    assertEquals("CLIPBOARD_ONLY", HistoryItem.create("a", "a", 1.0, null, null, InsertOutcome.CLIPBOARD_ONLY, "m").outcome)
  }

  @Test fun toMapHasAllJsKeys() {
    assertEquals(
      setOf("id", "date", "text", "raw", "durationSec", "language", "appPackage", "outcome", "modelId"),
      item(1).toMap().keys
    )
  }
}

class ModelManagerFileTest {
  private lateinit var dir: File
  private lateinit var ctx: FakeContext
  private val base = ModelCatalog.byId("base-q5_1")!!

  @Before fun setUp() {
    dir = Files.createTempDirectory("murmure-models").toFile()
    ctx = FakeContext(dir)
  }

  @After fun tearDown() {
    dir.deleteRecursively()
  }

  private fun sparse(f: File, size: Long) {
    RandomAccessFile(f, "rw").use { it.setLength(size) }
  }

  @Test fun paths() {
    assertEquals(File(dir, "whisper-models"), ModelManager.dir(ctx))
    assertEquals(File(dir, "whisper-models/ggml-base-q5_1.bin"), ModelManager.installedFile(ctx, base))
    assertEquals(File(dir, "whisper-models/ggml-base-q5_1.bin.part"), ModelManager.partFile(ctx, base))
  }

  @Test fun isInstalledRequiresExactSize() {
    assertFalse(ModelManager.isInstalled(ctx, base))
    sparse(ModelManager.installedFile(ctx, base), base.sizeBytes - 1)
    assertFalse(ModelManager.isInstalled(ctx, base))
    sparse(ModelManager.installedFile(ctx, base), base.sizeBytes)
    assertTrue(ModelManager.isInstalled(ctx, base))
  }

  @Test fun partialBytes() {
    assertEquals(0L, ModelManager.partialBytes(ctx, base))
    sparse(ModelManager.partFile(ctx, base), 1234)
    assertEquals(1234L, ModelManager.partialBytes(ctx, base))
  }

  @Test fun alreadyInstalledDownloadIsNoOpWithoutNetwork() {
    sparse(ModelManager.installedFile(ctx, base), base.sizeBytes)
    var last: Pair<Long, Long>? = null
    ModelManager.download(ctx, base) { b, t -> last = b to t }
    assertEquals(base.sizeBytes to base.sizeBytes, last)
    assertEquals(null, ModelManager.downloadingId)
  }

  @Test fun completePartWithWrongHashIsDeletedWithChecksumError() {
    // .part complet (taille exacte) mais contenu faux : vérification sha256 sans réseau.
    sparse(ModelManager.partFile(ctx, base), base.sizeBytes)
    try {
      ModelManager.download(ctx, base) { _, _ -> }
      fail("ModelChecksumException attendue")
    } catch (e: ModelChecksumException) {
      // ok
    }
    assertFalse(ModelManager.partFile(ctx, base).exists())
    assertFalse(ModelManager.installedFile(ctx, base).exists())
    assertEquals("le verrou de téléchargement doit être libéré", null, ModelManager.downloadingId)
  }

  @Test fun deleteRemovesFinalAndPart() {
    sparse(ModelManager.installedFile(ctx, base), 10)
    sparse(ModelManager.partFile(ctx, base), 10)
    ModelManager.delete(ctx, base.id)
    assertFalse(ModelManager.installedFile(ctx, base).exists())
    assertFalse(ModelManager.partFile(ctx, base).exists())
    ModelManager.delete(ctx, "unknown") // sans effet ni exception
  }

  @Test fun cancelWithoutDownloadIsHarmless() {
    ModelManager.cancel(base.id)
    sparse(ModelManager.installedFile(ctx, base), base.sizeBytes)
    ModelManager.download(ctx, base) { _, _ -> } // un cancel antérieur ne doit pas « coller »
  }
}
