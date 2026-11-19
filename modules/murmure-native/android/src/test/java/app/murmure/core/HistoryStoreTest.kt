package app.murmure.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryStoreTest {
  private fun item(i: Int, language: String? = "fr", app: String? = "com.whatsapp") = HistoryItem(
    id = "id-$i", date = 1_700_000_000_000L + i, text = "Été « $i » \"quoted\"\n", raw = " été $i ",
    durationSec = 3.25, language = language, appPackage = app, outcome = "SET_TEXT", modelId = "small-q5_1"
  )

  @Test fun roundTrip() {
    val list = listOf(item(1), item(2, language = null, app = null), item(3, language = "en"))
    assertEquals(list, HistoryStore.fromJson(HistoryStore.toJson(list)))
  }

  @Test fun invalidJsonGivesEmptyList() {
    assertTrue(HistoryStore.fromJson("{not json").isEmpty())
    assertTrue(HistoryStore.fromJson("").isEmpty())
    assertTrue(HistoryStore.fromJson("{}").isEmpty())
  }

  @Test fun unknownValuesAreSanitized() {
    val json = """[{"id":"a","date":1,"text":"t","raw":"r","durationSec":1.5,"language":"de","appPackage":null,"outcome":"WAT","modelId":"m"},{"text":"no id"}]"""
    val parsed = HistoryStore.fromJson(json)
    assertEquals(1, parsed.size)
    assertEquals(null, parsed[0].language)
    assertEquals("NOTHING", parsed[0].outcome)
  }

  @Test fun capAt500NewestFirst() {
    var list = emptyList<HistoryItem>()
    for (i in 1..510) list = HistoryStore.prepend(list, item(i))
    assertEquals(HistoryStore.MAX_ENTRIES, list.size)
    assertEquals("id-510", list.first().id)
    assertEquals("id-11", list.last().id)
  }
}
