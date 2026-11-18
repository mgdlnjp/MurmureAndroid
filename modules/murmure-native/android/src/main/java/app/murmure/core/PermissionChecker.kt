package app.murmure.core

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.TextUtils

/** Statut des autorisations et intents vers les écrans de réglages. */
object PermissionChecker {
  fun hasMicrophone(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

  fun hasOverlay(context: Context): Boolean = Settings.canDrawOverlays(context)

  fun hasNotifications(context: Context): Boolean =
    if (Build.VERSION.SDK_INT < 33) true
    else context.checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED

  fun isAccessibilityEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
      ?: return false
    val expected = ComponentName(context, MurmureAccessibilityService::class.java)
    val splitter = TextUtils.SimpleStringSplitter(':')
    splitter.setString(enabled)
    for (entry in splitter) {
      val cn = ComponentName.unflattenFromString(entry) ?: continue
      if (cn == expected) return true
    }
    return false
  }

  fun status(context: Context): Map<String, Any> = mapOf(
    "microphone" to hasMicrophone(context),
    "overlay" to hasOverlay(context),
    "accessibility" to isAccessibilityEnabled(context),
    "notifications" to hasNotifications(context),
    "sdkInt" to Build.VERSION.SDK_INT
  )

  fun accessibilitySettingsIntent(): Intent =
    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

  fun overlaySettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

  fun appDetailsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

  /** Ouvre l'app Murmure sur une route expo-router (ex. "model", "onboarding"). */
  fun openAppIntent(context: Context, route: String): Intent? {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("murmure://$route")).setPackage(context.packageName)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    if (intent.resolveActivity(context.packageManager) != null) return intent
    return context.packageManager.getLaunchIntentForPackage(context.packageName)
      ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  }
}
