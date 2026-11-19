package app.murmure.core

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import kotlin.math.abs
import kotlin.math.roundToInt

enum class BubbleState { IDLE, RECORDING, TRANSCRIBING, ERROR }

/**
 * Bulle flottante (fenêtre TYPE_ACCESSIBILITY_OVERLAY). Ne prend jamais le focus :
 * le clavier du champ actif reste ouvert.
 */
class BubbleOverlay(
  private val context: Context,
  private val prefs: MurmurePrefs,
  private val onTap: () -> Unit,
  private val onLongPress: () -> Unit
) {
  companion object {
    private const val DIAMETER_DP = 56
    private const val MARGIN_DP = 12
    private const val DRAG_THRESHOLD_PX = 10
    private const val COLOR_ACCENT = 0xFFF97316.toInt()
    private const val COLOR_DANGER = 0xFFEF4444.toInt()
  }

  private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
  private val density = context.resources.displayMetrics.density
  private val sizePx = (DIAMETER_DP * density).roundToInt()
  private val marginPx = (MARGIN_DP * density).roundToInt()

  private val icon = ImageView(context).apply {
    setImageResource(R.drawable.murmure_bubble_mic)
    scaleType = ImageView.ScaleType.CENTER_INSIDE
    val pad = (14 * density).roundToInt()
    setPadding(pad, pad, pad, pad)
  }
  private val spinner = ProgressBar(context).apply {
    isIndeterminate = true
    indeterminateTintList = ColorStateList.valueOf(Color.WHITE)
    visibility = View.GONE
    val pad = (10 * density).roundToInt()
    setPadding(pad, pad, pad, pad)
  }
  private val root = FrameLayout(context).apply {
    setBackgroundResource(R.drawable.murmure_bubble_bg)
    backgroundTintList = ColorStateList.valueOf(COLOR_ACCENT)
    elevation = 6 * density
    addView(icon, FrameLayout.LayoutParams(sizePx, sizePx))
    addView(spinner, FrameLayout.LayoutParams(sizePx, sizePx))
    contentDescription = context.getString(R.string.murmure_bubble_start)
    isFocusable = false
    isFocusableInTouchMode = false
  }

  private val params = WindowManager.LayoutParams(
    WindowManager.LayoutParams.WRAP_CONTENT,
    WindowManager.LayoutParams.WRAP_CONTENT,
    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
    PixelFormat.TRANSLUCENT
  ).apply {
    gravity = Gravity.TOP or Gravity.START
  }

  private var attached = false
  private var pulse: ObjectAnimator? = null
  var state: BubbleState = BubbleState.IDLE
    private set

  init {
    installTouchHandler()
    restorePosition()
  }

  val isShown: Boolean get() = attached

  fun show() {
    if (attached) return
    clampToScreen()
    try {
      wm.addView(root, params)
      attached = true
    } catch (e: Exception) {
      attached = false
    }
  }

  fun hide() {
    if (!attached) return
    stopPulse()
    try {
      wm.removeView(root)
    } catch (e: Exception) {
      // déjà retirée
    }
    attached = false
  }

  fun setState(newState: BubbleState) {
    state = newState
    stopPulse()
    when (newState) {
      BubbleState.IDLE -> {
        root.backgroundTintList = ColorStateList.valueOf(COLOR_ACCENT)
        icon.setImageResource(R.drawable.murmure_bubble_mic)
        icon.visibility = View.VISIBLE
        spinner.visibility = View.GONE
        root.contentDescription = context.getString(R.string.murmure_bubble_start)
      }
      BubbleState.RECORDING -> {
        root.backgroundTintList = ColorStateList.valueOf(COLOR_DANGER)
        icon.setImageResource(R.drawable.murmure_bubble_stop)
        icon.visibility = View.VISIBLE
        spinner.visibility = View.GONE
        root.contentDescription = context.getString(R.string.murmure_bubble_stop)
        startPulse()
      }
      BubbleState.TRANSCRIBING -> {
        root.backgroundTintList = ColorStateList.valueOf(COLOR_ACCENT)
        icon.visibility = View.GONE
        spinner.visibility = View.VISIBLE
        root.contentDescription = context.getString(R.string.murmure_bubble_transcribing)
      }
      BubbleState.ERROR -> {
        root.backgroundTintList = ColorStateList.valueOf(COLOR_DANGER)
        icon.setImageResource(R.drawable.murmure_bubble_mic)
        icon.visibility = View.VISIBLE
        spinner.visibility = View.GONE
        root.contentDescription = context.getString(R.string.murmure_bubble_error)
      }
    }
  }

  /** Retour haptique léger. */
  fun haptic() {
    root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
  }

  /** Place la bulle au-dessus du clavier (si l'utilisateur ne l'a pas déplacée). */
  fun placeAboveKeyboard(imeTop: Int?) {
    if (prefs.bubblePosition != null) return
    val metrics = context.resources.displayMetrics
    params.x = metrics.widthPixels - sizePx - marginPx
    params.y = if (imeTop != null && imeTop > 0) imeTop - sizePx - marginPx else (metrics.heightPixels * 0.55f).roundToInt()
    clampToScreen()
    updateLayout()
  }

  /** Re-borne la position après rotation / changement de taille d'écran. */
  fun onConfigurationChanged() {
    restorePosition()
    clampToScreen()
    updateLayout()
  }

  private fun restorePosition() {
    val metrics = context.resources.displayMetrics
    val saved = prefs.bubblePosition
    if (saved != null) {
      params.x = (saved.first * (metrics.widthPixels - sizePx)).roundToInt()
      params.y = (saved.second * (metrics.heightPixels - sizePx)).roundToInt()
    } else {
      params.x = metrics.widthPixels - sizePx - marginPx
      params.y = (metrics.heightPixels * 0.55f).roundToInt()
    }
  }

  private fun clampToScreen() {
    val metrics = context.resources.displayMetrics
    params.x = params.x.coerceIn(0, maxOf(0, metrics.widthPixels - sizePx))
    params.y = params.y.coerceIn(0, maxOf(0, metrics.heightPixels - sizePx))
  }

  private fun updateLayout() {
    if (!attached) return
    try {
      wm.updateViewLayout(root, params)
    } catch (e: Exception) {
      // vue détachée entre-temps
    }
  }

  private fun savePosition() {
    val metrics = context.resources.displayMetrics
    val w = maxOf(1, metrics.widthPixels - sizePx)
    val h = maxOf(1, metrics.heightPixels - sizePx)
    prefs.bubblePosition = (params.x.toFloat() / w) to (params.y.toFloat() / h)
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun installTouchHandler() {
    val longPressMs = ViewConfiguration.getLongPressTimeout().toLong()
    var downX = 0f
    var downY = 0f
    var startX = 0
    var startY = 0
    var dragging = false
    var longPressed = false
    val longPressRunnable = Runnable {
      if (!dragging) {
        longPressed = true
        onLongPress()
      }
    }
    root.setOnTouchListener { v, event ->
      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          downX = event.rawX
          downY = event.rawY
          startX = params.x
          startY = params.y
          dragging = false
          longPressed = false
          v.postDelayed(longPressRunnable, longPressMs)
          true
        }
        MotionEvent.ACTION_MOVE -> {
          val dx = event.rawX - downX
          val dy = event.rawY - downY
          if (!dragging && (abs(dx) > DRAG_THRESHOLD_PX || abs(dy) > DRAG_THRESHOLD_PX)) {
            dragging = true
            v.removeCallbacks(longPressRunnable)
          }
          if (dragging) {
            params.x = startX + dx.roundToInt()
            params.y = startY + dy.roundToInt()
            clampToScreen()
            updateLayout()
          }
          true
        }
        MotionEvent.ACTION_UP -> {
          v.removeCallbacks(longPressRunnable)
          if (dragging) {
            savePosition()
          } else if (!longPressed) {
            v.performClick()
            onTap()
          }
          true
        }
        MotionEvent.ACTION_CANCEL -> {
          v.removeCallbacks(longPressRunnable)
          if (dragging) savePosition()
          true
        }
        else -> false
      }
    }
  }

  private fun startPulse() {
    pulse = ObjectAnimator.ofFloat(root, View.ALPHA, 1f, 0.6f).apply {
      duration = 600
      repeatMode = ValueAnimator.REVERSE
      repeatCount = ValueAnimator.INFINITE
      start()
    }
  }

  private fun stopPulse() {
    pulse?.cancel()
    pulse = null
    root.alpha = 1f
  }
}
