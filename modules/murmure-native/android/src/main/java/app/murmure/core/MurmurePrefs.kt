package app.murmure.core

import android.content.Context
import android.content.SharedPreferences

/** Réglages partagés entre l'app (module JS) et le service d'accessibilité. */
class MurmurePrefs(context: Context) {
  val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

  var bubbleEnabled: Boolean
    get() = prefs.getBoolean(KEY_BUBBLE_ENABLED, true)
    set(value) = prefs.edit().putBoolean(KEY_BUBBLE_ENABLED, value).apply()

  var modelId: String
    get() = prefs.getString(KEY_MODEL_ID, null)?.takeIf { ModelCatalog.byId(it) != null } ?: ModelCatalog.DEFAULT_ID
    set(value) = prefs.edit().putString(KEY_MODEL_ID, value).apply()

  var language: String
    get() = prefs.getString(KEY_LANGUAGE, null)?.takeIf { it in LANGUAGES } ?: "auto"
    set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

  var onboardingDone: Boolean
    get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
    set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply()

  /** Position de la bulle en fractions d'écran (0–1), null si jamais déplacée. */
  var bubblePosition: Pair<Float, Float>?
    get() {
      if (!prefs.contains(KEY_BUBBLE_X) || !prefs.contains(KEY_BUBBLE_Y)) return null
      return prefs.getFloat(KEY_BUBBLE_X, 0f) to prefs.getFloat(KEY_BUBBLE_Y, 0f)
    }
    set(value) {
      val editor = prefs.edit()
      if (value == null) {
        editor.remove(KEY_BUBBLE_X).remove(KEY_BUBBLE_Y)
      } else {
        editor.putFloat(KEY_BUBBLE_X, value.first.coerceIn(0f, 1f))
          .putFloat(KEY_BUBBLE_Y, value.second.coerceIn(0f, 1f))
      }
      editor.apply()
    }

  companion object {
    const val NAME = "murmure_prefs"
    const val KEY_BUBBLE_ENABLED = "bubble_enabled"
    const val KEY_MODEL_ID = "model_id"
    const val KEY_LANGUAGE = "language"
    const val KEY_ONBOARDING_DONE = "onboarding_done"
    const val KEY_BUBBLE_X = "bubble_x"
    const val KEY_BUBBLE_Y = "bubble_y"
    val LANGUAGES = setOf("auto", "fr", "en")
  }
}
