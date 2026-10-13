package com.example.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import com.example.MainActivity
import com.example.R
import com.example.model.TimerState
import com.example.model.TimerStatus
import com.example.state.TimerRepository
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Manages the floating system overlay window that stays visible over
 * social media applications like TikTok, Instagram, YouTube, X, etc.
 *
 * Implements:
 * - Real-time countdown timer display with embedded pause/resume control
 * - Smooth, clamp-bounded dragging across the visible screen area
 * - Android-style drag-to-dismiss interaction with a bottom-center drop target
 * - Consistent coordinate mapping accounting for system insets (status/navigation bars)
 * - Safe overlay removal while preserving background timer execution
 */
class FloatingOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager

    // Timer Widget Overlay View & Layout Parameters
    private var overlayView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var isViewAttached = false

    // Dismiss Target Overlay View & Parameters
    private var dismissTargetView: View? = null
    private var dismissParams: WindowManager.LayoutParams? = null
    private var isDismissTargetAttached = false
    private var isInDismissZone = false

    // Fixed absolute screen coordinates of dismiss target center
    private var targetCenterX = 0f
    private var targetCenterY = 0f

    // Remembered coordinates during active session
    private var lastPosX = 80
    private var lastPosY = 220

    private var currentState: TimerState? = null

    data class ScreenBounds(
        val width: Int,
        val height: Int,
        val insetTop: Int,
        val insetBottom: Int
    )

    fun canDrawOverlays(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    fun showOrUpdate(state: TimerState) {
        // App-aware visibility:
        // Hide floating overlay if:
        // 1. Overlay permission is not granted
        // 2. Overlay was dismissed by user (isOverlayEnabled == false)
        // 3. Social Timer app is currently in foreground (isAppInForeground == true)
        // 4. Timer is IDLE (no active countdown session)
        if (!canDrawOverlays() || !state.isOverlayEnabled || state.isAppInForeground || state.status == TimerStatus.IDLE) {
            removeOverlay()
            return
        }

        val wm = windowManager ?: return

        if (overlayView == null || !isViewAttached) {
            val inflater = LayoutInflater.from(context)
            val view = inflater.inflate(R.layout.floating_timer_overlay, null)
            overlayView = view

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val screenBounds = getScreenBounds()
            val initialX = lastPosX.coerceIn(0, (screenBounds.width - 200).coerceAtLeast(0))
            val initialY = lastPosY.coerceIn(screenBounds.insetTop, (screenBounds.height - screenBounds.insetBottom - 100).coerceAtLeast(screenBounds.insetTop))

            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = initialX
                y = initialY
            }

            this.params = layoutParams

            attachTouchAndActionListeners(view, layoutParams)

            try {
                wm.addView(view, layoutParams)
                isViewAttached = true
            } catch (_: Exception) {
                isViewAttached = false
                overlayView = null
                return
            }
        }

        // Update view content
        renderState(state)
    }

    private fun renderState(state: TimerState) {
        currentState = state
        val view = overlayView ?: return

        val timerText = view.findViewById<TextView>(R.id.overlay_timer_text) ?: return
        val subText = view.findViewById<TextView>(R.id.overlay_sub_text)
        val statusDot = view.findViewById<View>(R.id.overlay_status_dot)
        val root = view.findViewById<View>(R.id.overlay_root)
        val actionBtn = view.findViewById<ImageButton>(R.id.overlay_btn_action)
        val divider = view.findViewById<View>(R.id.overlay_divider)

        when (state.status) {
            TimerStatus.COMPLETED -> {
                timerText.text = "00:00"
                subText?.visibility = View.VISIBLE
                subText?.text = "TIME'S UP"
                statusDot?.setBackgroundResource(R.drawable.bg_overlay_dot)
                root?.setBackgroundResource(R.drawable.bg_floating_overlay_alert)
                actionBtn?.visibility = View.GONE
                divider?.visibility = View.GONE
            }
            TimerStatus.PAUSED -> {
                timerText.text = state.remainingFormatted
                subText?.visibility = View.VISIBLE
                subText?.text = "PAUSED"
                statusDot?.setBackgroundResource(R.drawable.bg_overlay_dot_paused)
                root?.setBackgroundResource(R.drawable.bg_floating_overlay)
                divider?.visibility = View.GONE
                actionBtn?.visibility = View.VISIBLE
                actionBtn?.setImageResource(R.drawable.ic_overlay_play)
                actionBtn?.contentDescription = "Resume Timer"
            }
            TimerStatus.RUNNING -> {
                timerText.text = state.remainingFormatted
                subText?.visibility = View.GONE
                statusDot?.setBackgroundResource(R.drawable.bg_overlay_dot)
                root?.setBackgroundResource(R.drawable.bg_floating_overlay)
                divider?.visibility = View.GONE
                actionBtn?.visibility = View.VISIBLE
                actionBtn?.setImageResource(R.drawable.ic_overlay_pause)
                actionBtn?.contentDescription = "Pause Timer"
            }
            TimerStatus.IDLE -> {
                timerText.text = state.totalFormatted
                subText?.visibility = View.GONE
                statusDot?.setBackgroundResource(R.drawable.bg_overlay_dot)
                root?.setBackgroundResource(R.drawable.bg_floating_overlay)
                divider?.visibility = View.GONE
                actionBtn?.visibility = View.GONE
            }
        }
    }

    fun removeOverlay() {
        removeDismissTarget()
        val view = overlayView
        val wm = windowManager
        if (view != null && wm != null && isViewAttached) {
            try {
                wm.removeView(view)
            } catch (_: Exception) {
            }
        }
        overlayView = null
        params = null
        isViewAttached = false
        isInDismissZone = false
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachTouchAndActionListeners(view: View, lp: WindowManager.LayoutParams) {
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var touchStartTime = 0L
        var isDragging = false

        // Main pill body touch handler
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = lp.x
                    initialY = lp.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    touchStartTime = System.currentTimeMillis()
                    isDragging = false
                    isInDismissZone = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaMove = abs(event.rawX - initialTouchX) + abs(event.rawY - initialTouchY)
                    if (deltaMove > touchSlop || isDragging) {
                        if (!isDragging) {
                            isDragging = true
                            attachDismissTarget()
                        }
                        handleDragMovement(event, initialX, initialY, initialTouchX, initialTouchY, lp, view)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val totalDeltaX = abs(event.rawX - initialTouchX)
                    val totalDeltaY = abs(event.rawY - initialTouchY)
                    val totalDuration = System.currentTimeMillis() - touchStartTime

                    if (isDragging) {
                        handleDragRelease(view)
                    } else if (totalDeltaX < touchSlop && totalDeltaY < touchSlop && totalDuration < 300) {
                        // Clean tap on widget body -> Re-open MainActivity
                        openMainActivity()
                    }

                    isDragging = false
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    removeDismissTarget()
                    view.animate().alpha(1.0f).setDuration(100).start()
                    isDragging = false
                    true
                }
                else -> false
            }
        }

        // Embedded Pause / Resume button touch handler
        val actionBtn = view.findViewById<ImageButton>(R.id.overlay_btn_action)
        if (actionBtn != null) {
            actionBtn.setOnClickListener {
                triggerHapticTick()
                when (currentState?.status) {
                    TimerStatus.RUNNING -> TimerRepository.pauseTimer(context)
                    TimerStatus.PAUSED -> TimerRepository.resumeTimer(context)
                    else -> Unit
                }
            }

            var btnTouchDownX = 0f
            var btnTouchDownY = 0f
            var isBtnDragging = false

            actionBtn.setOnTouchListener { btn, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        btnTouchDownX = event.rawX
                        btnTouchDownY = event.rawY
                        initialX = lp.x
                        initialY = lp.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isBtnDragging = false
                        isInDismissZone = false
                        btn.isPressed = true
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaFromStart = abs(event.rawX - btnTouchDownX) + abs(event.rawY - btnTouchDownY)
                        if (deltaFromStart > touchSlop || isBtnDragging) {
                            if (!isBtnDragging) {
                                isBtnDragging = true
                                btn.isPressed = false
                                attachDismissTarget()
                            }
                            handleDragMovement(event, initialX, initialY, initialTouchX, initialTouchY, lp, view)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        btn.isPressed = false
                        if (isBtnDragging) {
                            handleDragRelease(view)
                            isBtnDragging = false
                        } else {
                            // Clean tap without drag -> Toggle pause/resume
                            btn.performClick()
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        btn.isPressed = false
                        removeDismissTarget()
                        view.animate().alpha(1.0f).setDuration(100).start()
                        isBtnDragging = false
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun handleDragMovement(
        event: MotionEvent,
        initialX: Int,
        initialY: Int,
        initialTouchX: Float,
        initialTouchY: Float,
        lp: WindowManager.LayoutParams,
        view: View
    ) {
        val wm = windowManager ?: return
        val screenBounds = getScreenBounds()
        val density = context.resources.displayMetrics.density

        val widgetWidth = if (view.width > 0) view.width else (150 * density).toInt()
        val widgetHeight = if (view.height > 0) view.height else (48 * density).toInt()

        val deltaX = (event.rawX - initialTouchX).toInt()
        val deltaY = (event.rawY - initialTouchY).toInt()

        // Clamp inside usable visible bounds including status and nav bars
        val minX = 0
        val maxX = (screenBounds.width - widgetWidth).coerceAtLeast(0)
        val minY = screenBounds.insetTop
        val maxY = (screenBounds.height - screenBounds.insetBottom - widgetHeight).coerceAtLeast(minY)

        val newX = (initialX + deltaX).coerceIn(minX, maxX)
        val newY = (initialY + deltaY).coerceIn(minY, maxY)

        lp.x = newX
        lp.y = newY
        lastPosX = newX
        lastPosY = newY

        if (isViewAttached) {
            try {
                wm.updateViewLayout(view, lp)
            } catch (_: Exception) {
            }
        }

        // Check if inside dismiss drop zone
        checkDismissZone(event, lp, view, widgetWidth, widgetHeight)
    }

    private fun handleDragRelease(view: View) {
        val willDismiss = isInDismissZone
        removeDismissTarget()
        view.animate().alpha(1.0f).setDuration(100).start()

        if (willDismiss) {
            performDismiss()
        }
    }

    /**
     * Attaches the dismiss target at the bottom-center of the visible screen area.
     * Uses Gravity.TOP or Gravity.START with explicitly measured absolute coordinates
     * to guarantee it never appears at the top or gets clipped by system bars.
     */
    private fun attachDismissTarget() {
        if (isDismissTargetAttached) return
        val wm = windowManager ?: return

        val inflater = LayoutInflater.from(context)
        val targetView = inflater.inflate(R.layout.floating_dismiss_target, null)
        dismissTargetView = targetView

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val screenBounds = getScreenBounds()
        val density = context.resources.displayMetrics.density

        // Pre-measure the target to get exact dimensions before adding to window manager
        targetView.measure(
            View.MeasureSpec.makeMeasureSpec(screenBounds.width, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(screenBounds.height, View.MeasureSpec.AT_MOST)
        )
        val targetW = targetView.measuredWidth.coerceAtLeast((72 * density).toInt())
        val targetH = targetView.measuredHeight.coerceAtLeast((72 * density).toInt())

        // Calculate bottom-center position in the EXACT same coordinate system as overlayView
        val targetX = (screenBounds.width - targetW) / 2
        val bottomMargin = (24 * density).toInt()
        val targetY = (screenBounds.height - screenBounds.insetBottom - targetH - bottomMargin)
            .coerceIn(screenBounds.insetTop + 100, screenBounds.height - targetH - (8 * density).toInt())

        // Save center coordinates for proximity detection
        targetCenterX = targetX + (targetW / 2f)
        targetCenterY = targetY + (targetH / 2f)

        // FLAG_NOT_TOUCHABLE is critical: ensures the dismiss target NEVER intercepts touch events
        // from the dragged timer widget.
        val targetParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = targetX
            y = targetY
        }
        this.dismissParams = targetParams

        try {
            wm.addView(targetView, targetParams)
            isDismissTargetAttached = true
            targetView.alpha = 0f
            targetView.scaleX = 0.85f
            targetView.scaleY = 0.85f
            targetView.animate()
                .alpha(1.0f)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(120)
                .start()
        } catch (_: Exception) {
            isDismissTargetAttached = false
            dismissTargetView = null
        }
    }

    private fun removeDismissTarget() {
        val wm = windowManager
        val targetView = dismissTargetView
        if (wm != null && targetView != null && isDismissTargetAttached) {
            try {
                wm.removeView(targetView)
            } catch (_: Exception) {
            }
        }
        dismissTargetView = null
        dismissParams = null
        isDismissTargetAttached = false
        isInDismissZone = false
    }

    private fun checkDismissZone(
        event: MotionEvent,
        lp: WindowManager.LayoutParams,
        widgetView: View,
        widgetWidth: Int,
        widgetHeight: Int
    ) {
        val density = context.resources.displayMetrics.density

        val widgetCenterX = lp.x + (widgetWidth / 2f)
        val widgetCenterY = lp.y + (widgetHeight / 2f)
        val touchX = event.rawX
        val touchY = event.rawY

        // Distance from widget center and finger touch to target center
        val distWidget = hypot((widgetCenterX - targetCenterX).toDouble(), (widgetCenterY - targetCenterY).toDouble())
        val distFinger = hypot((touchX - targetCenterX).toDouble(), (touchY - targetCenterY).toDouble())

        // Forgiving drop zone bounds
        val dropRadiusPx = 115f * density
        val inBottomArea = (widgetCenterY >= (targetCenterY - 65f * density)) && (abs(widgetCenterX - targetCenterX) <= 95f * density)
        val inFingerBottomArea = (touchY >= (targetCenterY - 55f * density)) && (abs(touchX - targetCenterX) <= 90f * density)

        val inZone = distWidget <= dropRadiusPx || distFinger <= dropRadiusPx || inBottomArea || inFingerBottomArea

        if (inZone != isInDismissZone) {
            isInDismissZone = inZone
            updateDismissTargetHighlight(inZone, widgetView)
        }
    }

    private fun updateDismissTargetHighlight(highlight: Boolean, widgetView: View) {
        val targetView = dismissTargetView ?: return
        val circle = targetView.findViewById<View>(R.id.dismiss_circle)
        val label = targetView.findViewById<TextView>(R.id.dismiss_label)

        if (highlight) {
            circle?.setBackgroundResource(R.drawable.bg_dismiss_target_active)
            circle?.animate()?.scaleX(1.22f)?.scaleY(1.22f)?.setDuration(100)?.start()
            label?.text = "Release to dismiss"
            widgetView.animate()?.alpha(0.65f)?.setDuration(100)?.start()
            triggerHapticTick()
        } else {
            circle?.setBackgroundResource(R.drawable.bg_dismiss_target_normal)
            circle?.animate()?.scaleX(1.0f)?.scaleY(1.0f)?.setDuration(100)?.start()
            label?.text = "Drag here to dismiss"
            widgetView.animate()?.alpha(1.0f)?.setDuration(100)?.start()
        }
    }

    private fun performDismiss() {
        isInDismissZone = false
        removeDismissTarget()
        removeOverlay()
        TimerRepository.dismissOverlay()
        triggerHapticTick()
    }

    private fun triggerHapticTick() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(30)
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun openMainActivity() {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun getScreenBounds(): ScreenBounds {
        val wm = windowManager
        val density = context.resources.displayMetrics.density

        if (wm == null) {
            return ScreenBounds(1080, 1920, (24 * density).toInt(), (48 * density).toInt())
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = wm.currentWindowMetrics
            val bounds = windowMetrics.bounds
            val insets = windowMetrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            return ScreenBounds(
                width = bounds.width(),
                height = bounds.height(),
                insetTop = insets.top.coerceAtLeast((24 * density).toInt()),
                insetBottom = insets.bottom.coerceAtLeast((32 * density).toInt())
            )
        } else {
            val display = wm.defaultDisplay
            val point = Point()
            @Suppress("DEPRECATION")
            display.getRealSize(point)

            val statusBarId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
            val statusBarHeight = if (statusBarId > 0) context.resources.getDimensionPixelSize(statusBarId) else (24 * density).toInt()

            val navBarId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
            val navBarHeight = if (navBarId > 0) context.resources.getDimensionPixelSize(navBarId) else (48 * density).toInt()

            return ScreenBounds(
                width = point.x,
                height = point.y,
                insetTop = statusBarHeight,
                insetBottom = navBarHeight
            )
        }
    }
}
