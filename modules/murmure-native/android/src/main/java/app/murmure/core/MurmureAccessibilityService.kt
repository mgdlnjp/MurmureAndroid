package app.murmure.core

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast

/**
 * Service d'accessibilité Murmure : suit le champ actif, affiche la bulle,
 * enregistre, transcrit localement et insère le texte.
 * Ne journalise jamais le texte dicté ni le contenu des champs.
 */
class MurmureAccessibilityService : AccessibilityService() {
  companion object {
    private const val TAG = "MurmureService"
    private const val CHANNEL_ID = "murmure_recording"
    private const val NOTIF_ID = 4201
    private const val HIDE_DEBOUNCE_MS = 150L
    private const val TAP_DEBOUNCE_MS = 300L
    private const val ERROR_DURATION_MS = 1500L
    private const val MIN_DURATION_SEC = 0.4
  }

  private val handler = Handler(Looper.getMainLooper())
  private lateinit var prefs: MurmurePrefs
  private var overlay: BubbleOverlay? = null
  private var recorder: AudioRecorder? = null
  private var state = BubbleState.IDLE
  private var targetNode: AccessibilityNodeInfo? = null
  private var targetPackage: String? = null
  private var lastTapAt = 0L
  private var preloaded = false
  private var inForeground = false
  private val transcriptionGuard = TranscriptionGuard()

  private val hideRunnable = Runnable { overlay?.hide() }
  private val errorResetRunnable = Runnable { setState(BubbleState.IDLE) }

  private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
    when (key) {
      MurmurePrefs.KEY_MODEL_ID -> WhisperEngine.release()
      MurmurePrefs.KEY_BUBBLE_ENABLED -> handler.post { recompute() }
    }
  }

  // region Cycle de vie

  override fun onServiceConnected() {
    super.onServiceConnected()
    prefs = MurmurePrefs(this)
    prefs.prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    transcriptionGuard.reopen()
    createNotificationChannel()
    overlay = BubbleOverlay(this, prefs, onTap = ::onBubbleTap, onLongPress = ::onBubbleLongPress)
    recorder = AudioRecorder(onLimitReached = { if (state == BubbleState.RECORDING) stopAndTranscribe() })
    MurmureNativeModule.notifyServiceStatusChanged()
    Log.i(TAG, "service connected")
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    if (event == null || !::prefs.isInitialized) return
    recompute()
  }

  override fun onInterrupt() {
    cleanup()
  }

  override fun onUnbind(intent: android.content.Intent?): Boolean {
    teardown()
    MurmureNativeModule.notifyServiceStatusChanged()
    return super.onUnbind(intent)
  }

  override fun onDestroy() {
    teardown()
    if (::prefs.isInitialized) prefs.prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
    super.onDestroy()
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    overlay?.onConfigurationChanged()
  }

  override fun onTrimMemory(level: Int) {
    super.onTrimMemory(level)
    @Suppress("DEPRECATION")
    val critical = level == TRIM_MEMORY_RUNNING_CRITICAL || level >= TRIM_MEMORY_COMPLETE
    if (critical && state == BubbleState.IDLE) WhisperEngine.release()
  }

  private fun cleanup() {
    // Toute transcription en file ou en cours sera ignorée à son retour (A4).
    transcriptionGuard.invalidate()
    handler.removeCallbacksAndMessages(null)
    recorder?.cancel()
    stopForegroundSafe()
    overlay?.hide()
    state = BubbleState.IDLE
    overlay?.setState(BubbleState.IDLE)
    targetNode = null
    targetPackage = null
    WhisperEngine.release()
    preloaded = false
  }

  /** Service désactivé / détruit : nettoyage puis abandon définitif de la bulle et du micro. */
  private fun teardown() {
    cleanup()
    transcriptionGuard.shutdown()
    overlay = null
    recorder = null
  }

  // endregion

  // region Visibilité

  private fun recompute() {
    val bubble = overlay ?: return
    val busy = state == BubbleState.RECORDING || state == BubbleState.TRANSCRIBING

    var imeTop: Int? = null
    val imeVisible = try {
      windows.any { w ->
        val isIme = w.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
        if (isIme) {
          val r = Rect()
          w.getBoundsInScreen(r)
          imeTop = r.top
        }
        isIme
      }
    } catch (e: Exception) {
      false
    }

    val focusedNode = try {
      findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
    } catch (e: Exception) {
      null
    }
    val focused = focusedNode?.let { fieldInfoOf(it, withText = false) }
    when (TargetPolicy.decide(focused, packageName, busy)) {
      TargetUpdate.REMEMBER -> {
        targetNode = focusedNode
        targetPackage = focused?.packageName
      }
      // Focus sur un élément non éditable, sensible, dans Murmure ou nulle part : la cible est oubliée,
      // l'insertion cherchera le focus au moment voulu (ou passera par le presse-papiers).
      TargetUpdate.FORGET -> {
        targetNode = null
        targetPackage = null
      }
      TargetUpdate.KEEP -> Unit
    }

    val keyguard = (getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isKeyguardLocked == true
    val visible = BubbleVisibility.decide(
      VisibilityInput(
        bubbleEnabled = prefs.bubbleEnabled,
        modelReady = isModelReady(),
        keyguardLocked = keyguard,
        imeVisible = imeVisible,
        focused = focused,
        ownPackage = packageName,
        busy = busy
      )
    )

    if (visible) {
      handler.removeCallbacks(hideRunnable)
      val wasShown = bubble.isShown
      bubble.show()
      if (!busy && (!wasShown || imeTop != null)) bubble.placeAboveKeyboard(imeTop)
      if (!preloaded && isModelReady()) {
        preloaded = true
        WhisperEngine.preload(this)
      }
    } else {
      handler.removeCallbacks(hideRunnable)
      handler.postDelayed(hideRunnable, HIDE_DEBOUNCE_MS)
    }
  }

  private fun isModelReady(): Boolean {
    val spec = ModelCatalog.byId(prefs.modelId) ?: return false
    return ModelManager.isInstalled(this, spec)
  }

  // endregion

  // region Machine d'états

  private fun setState(newState: BubbleState) {
    state = newState
    overlay?.setState(newState)
    if (newState == BubbleState.IDLE) recompute()
  }

  private fun showError(messageRes: Int) {
    toast(messageRes)
    handler.removeCallbacks(errorResetRunnable)
    state = BubbleState.ERROR
    overlay?.setState(BubbleState.ERROR)
    handler.postDelayed(errorResetRunnable, ERROR_DURATION_MS)
  }

  private fun onBubbleTap() {
    val now = SystemClock.elapsedRealtime()
    if (now - lastTapAt < TAP_DEBOUNCE_MS) return
    lastTapAt = now
    when (state) {
      BubbleState.IDLE, BubbleState.ERROR -> startRecording()
      BubbleState.RECORDING -> stopAndTranscribe()
      BubbleState.TRANSCRIBING -> Unit
    }
  }

  private fun onBubbleLongPress() {
    if (state != BubbleState.RECORDING) return
    recorder?.cancel()
    stopForegroundSafe()
    toast(R.string.murmure_toast_cancelled)
    setState(BubbleState.IDLE)
  }

  private fun startRecording() {
    handler.removeCallbacks(errorResetRunnable)
    if (!isModelReady()) {
      toast(R.string.murmure_toast_model_missing)
      openApp("model")
      return
    }
    if (!PermissionChecker.hasMicrophone(this)) {
      toast(R.string.murmure_toast_mic_denied)
      openApp("onboarding")
      return
    }
    startForegroundSafe()
    val started = recorder?.start() == true
    if (!started) {
      stopForegroundSafe()
      showError(R.string.murmure_toast_mic_failed)
      return
    }
    overlay?.haptic()
    setState(BubbleState.RECORDING)
  }

  private fun stopAndTranscribe() {
    val samples = recorder?.stop() ?: FloatArray(0)
    stopForegroundSafe()
    val duration = AudioUtils.durationSec(samples.size)
    if (duration < MIN_DURATION_SEC) {
      setState(BubbleState.IDLE)
      return
    }
    if (AudioUtils.isSilenced(samples)) {
      showError(R.string.murmure_toast_mic_silenced)
      return
    }
    overlay?.haptic()
    setState(BubbleState.TRANSCRIBING)
    val target = targetNode
    val origin = targetPackage
    val token = transcriptionGuard.begin()
    WhisperEngine.transcribe(this, samples, prefs.language) { result ->
      // Service désactivé ou dictée annulée entre-temps : rien à insérer ni à historiser.
      if (!transcriptionGuard.isCurrent(token)) return@transcribe
      result.fold(
        onSuccess = { t -> onTranscribed(t, duration, target, origin) },
        onFailure = { showError(R.string.murmure_toast_transcription_failed) }
      )
    }
  }

  private fun onTranscribed(t: Transcription, duration: Double, target: AccessibilityNodeInfo?, origin: String?) {
    val text = TextCleanup.clean(t.raw)
    if (text.isEmpty()) {
      toast(R.string.murmure_toast_nothing_heard)
      setState(BubbleState.IDLE)
      return
    }
    val outcome = try {
      TextInserter.insert(this, target, text, origin)
    } catch (e: Exception) {
      Log.w(TAG, "insert failed: ${e.javaClass.simpleName}")
      TextInserter.copyToClipboard(this, text)
      toast(R.string.murmure_toast_copied)
      InsertOutcome.CLIPBOARD_ONLY
    }
    try {
      HistoryStore.add(this, HistoryItem.create(text, t.raw.trim(), duration, t.language, origin, outcome, t.modelId))
      MurmureNativeModule.emitHistoryChanged()
    } catch (e: Exception) {
      Log.w(TAG, "history write failed: ${e.javaClass.simpleName}")
    }
    setState(BubbleState.IDLE)
  }

  // endregion

  // region Premier plan (micro)

  private fun createNotificationChannel() {
    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(CHANNEL_ID, getString(R.string.murmure_notification_channel), NotificationManager.IMPORTANCE_LOW)
    channel.setShowBadge(false)
    nm.createNotificationChannel(channel)
  }

  private fun buildNotification(): Notification =
    Notification.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.drawable.murmure_notification_icon)
      .setContentTitle(getString(R.string.murmure_notification_title))
      .setContentText(getString(R.string.murmure_notification_text))
      .setOngoing(true)
      .setCategory(Notification.CATEGORY_SERVICE)
      .build()

  private fun startForegroundSafe() {
    try {
      if (Build.VERSION.SDK_INT >= 29) {
        startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
      } else {
        startForeground(NOTIF_ID, buildNotification())
      }
      inForeground = true
    } catch (e: Exception) {
      // ForegroundServiceStartNotAllowedException (API 31+) / SecurityException : on continue sans FGS.
      Log.w(TAG, "startForeground refused: ${e.javaClass.simpleName}")
      inForeground = false
    }
  }

  private fun stopForegroundSafe() {
    if (!inForeground) return
    try {
      stopForeground(STOP_FOREGROUND_REMOVE)
    } catch (e: Exception) {
      Log.w(TAG, "stopForeground failed: ${e.javaClass.simpleName}")
    }
    inForeground = false
  }

  // endregion

  private fun openApp(route: String) {
    try {
      PermissionChecker.openAppIntent(this, route)?.let { startActivity(it) }
    } catch (e: Exception) {
      Log.w(TAG, "cannot open app: ${e.javaClass.simpleName}")
    }
  }

  private fun toast(resId: Int) {
    Toast.makeText(this, resId, Toast.LENGTH_LONG).show()
  }
}
