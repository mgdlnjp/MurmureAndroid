package app.murmure.core

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class HistoryItem(
  val id: String,
  val date: Long,
  val text: String,
  val raw: String,
  val durationSec: Double,
  val language: String?,
  val appPackage: String?,
  val outcome: String,
  val modelId: String
) {
  fun toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "date" to date,
    "text" to text,
    "raw" to raw,
    "durationSec" to durationSec,
    "language" to language,
    "appPackage" to appPackage,
    "outcome" to outcome,
    "modelId" to modelId
  )

  companion object {
    fun create(
      text: String,
      raw: String,
      durationSec: Double,
      language: String?,
      appPackage: String?,
      outcome: InsertOutcome,
      modelId: String,
      now: Long = System.currentTimeMillis()
    ) = HistoryItem(UUID.randomUUID().toString(), now, text, raw, durationSec, language, appPackage, outcome.name, modelId)
  }
}

/**
 * Historique local (filesDir/history.json), plus récent en premier, 500 entrées max.
 * Le texte dicté n'est jamais journalisé.
 */
object HistoryStore {
  const val MAX_ENTRIES = 500
  private const val TAG = "MurmureHistory"
  private const val FILE_NAME = "history.json"
  private val LANGUAGES = setOf("fr", "en")
  private val OUTCOMES = setOf("SET_TEXT", "PASTED", "CLIPBOARD_ONLY", "NOTHING")

  private val lock = Any()

  /** Sérialisation pure. */
  fun toJson(list: List<HistoryItem>): String {
    val array = JSONArray()
    for (item in list) {
      val o = JSONObject()
      o.put("id", item.id)
      o.put("date", item.date)
      o.put("text", item.text)
      o.put("raw", item.raw)
      o.put("durationSec", item.durationSec)
      o.put("language", item.language ?: JSONObject.NULL)
      o.put("appPackage", item.appPackage ?: JSONObject.NULL)
      o.put("outcome", item.outcome)
      o.put("modelId", item.modelId)
      array.put(o)
    }
    return array.toString()
  }

  /** Désérialisation pure : lève JSONException si le JSON est invalide. */
  @Throws(JSONException::class)
  fun parse(s: String): List<HistoryItem> {
    val array = JSONArray(s)
    val out = ArrayList<HistoryItem>(array.length())
    for (i in 0 until array.length()) {
      val o = array.optJSONObject(i) ?: continue
      val id = o.optString("id", "")
      if (id.isEmpty()) continue
      val language = if (o.isNull("language")) null else o.optString("language").takeIf { it in LANGUAGES }
      val appPackage = if (o.isNull("appPackage")) null else o.optString("appPackage").ifEmpty { null }
      val outcome = o.optString("outcome", "NOTHING").takeIf { it in OUTCOMES } ?: "NOTHING"
      out.add(
        HistoryItem(
          id = id,
          date = o.optLong("date", 0L),
          text = o.optString("text", ""),
          raw = o.optString("raw", ""),
          durationSec = o.optDouble("durationSec", 0.0).let { if (it.isNaN()) 0.0 else it },
          language = language,
          appPackage = appPackage,
          outcome = outcome,
          modelId = o.optString("modelId", "")
        )
      )
    }
    return out
  }

  /** Désérialisation pure tolérante : JSON invalide → liste vide. */
  fun fromJson(s: String): List<HistoryItem> = try {
    parse(s)
  } catch (e: JSONException) {
    emptyList()
  }

  /** Ajoute en tête et tronque à [max] (pur). */
  fun prepend(list: List<HistoryItem>, item: HistoryItem, max: Int = MAX_ENTRIES): List<HistoryItem> =
    (listOf(item) + list).take(max)

  fun all(context: Context): List<HistoryItem> = synchronized(lock) { read(context) }

  fun add(context: Context, item: HistoryItem) = synchronized(lock) {
    write(context, prepend(read(context), item))
  }

  fun delete(context: Context, id: String) = synchronized(lock) {
    write(context, read(context).filterNot { it.id == id })
  }

  fun clear(context: Context) = synchronized(lock) {
    write(context, emptyList())
  }

  private fun file(context: Context) = File(context.filesDir, FILE_NAME)

  private fun read(context: Context): List<HistoryItem> {
    val f = file(context)
    if (!f.isFile) return emptyList()
    val content = try {
      f.readText(Charsets.UTF_8)
    } catch (e: Exception) {
      Log.w(TAG, "history unreadable")
      return emptyList()
    }
    return try {
      parse(content)
    } catch (e: JSONException) {
      Log.w(TAG, "history corrupted, reset")
      val corrupt = File(f.parentFile, "$FILE_NAME.corrupt")
      corrupt.delete()
      f.renameTo(corrupt)
      emptyList()
    }
  }

  /** Écriture atomique : fichier temporaire puis rename. */
  private fun write(context: Context, list: List<HistoryItem>) {
    val f = file(context)
    val tmp = File(f.parentFile, "$FILE_NAME.tmp")
    tmp.writeText(toJson(list), Charsets.UTF_8)
    if (!tmp.renameTo(f)) {
      f.delete()
      if (!tmp.renameTo(f)) Log.w(TAG, "history write failed")
    }
  }
}
