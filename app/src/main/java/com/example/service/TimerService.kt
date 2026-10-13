package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.audio.AudioAlertManager
import com.example.model.TimerState
import com.example.model.TimerStatus
import com.example.overlay.FloatingOverlayManager
import com.example.state.TimerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TimerService : Service() {

    companion object {
        const val ACTION_START = "com.example.action.START"
        const val ACTION_PAUSE = "com.example.action.PAUSE"
        const val ACTION_RESUME = "com.example.action.RESUME"
        const val ACTION_RESET = "com.example.action.RESET"
        const val ACTION_STOP = "com.example.action.STOP"

        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "social_timer_channel"
        private const val WAKELOCK_TAG = "SocialTimer::CountdownWakeLock"
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var tickerJob: Job? = null

    private lateinit var overlayManager: FloatingOverlayManager
    private lateinit var audioAlertManager: AudioAlertManager
    private lateinit var notificationManager: NotificationManager
    private var wakeLock: PowerManager.WakeLock? = null

    private var lastNotificationSecond = -1L

    override fun onCreate() {
        super.onCreate()
        overlayManager = FloatingOverlayManager(this)
        audioAlertManager = AudioAlertManager(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)?.apply {
            setReferenceCounted(false)
        }

        // React immediately to app foreground/background transitions or overlay toggles
        serviceScope.launch {
            TimerRepository.timerState.collect { state ->
                overlayManager.showOrUpdate(state)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START -> handleStart()
            ACTION_PAUSE -> handlePause()
            ACTION_RESUME -> handleResume()
            ACTION_RESET -> handleReset()
            ACTION_STOP -> handleStop()
        }

        return START_NOT_STICKY
    }

    private fun handleStart() {
        acquireWakeLock()
        audioAlertManager.stop()

        startForegroundWithNotification(TimerRepository.timerState.value)
        overlayManager.showOrUpdate(TimerRepository.timerState.value)

        startTicker()
    }

    private fun handlePause() {
        if (TimerRepository.timerState.value.status == TimerStatus.RUNNING) {
            TimerRepository.pauseTimer(null)
        }
        tickerJob?.cancel()
        tickerJob = null
        audioAlertManager.stop()

        val state = TimerRepository.timerState.value
        overlayManager.showOrUpdate(state)
        updateNotification(state)
    }

    private fun handleResume() {
        if (TimerRepository.timerState.value.status == TimerStatus.PAUSED) {
            TimerRepository.resumeTimer(null)
        }
        acquireWakeLock()
        audioAlertManager.stop()

        val state = TimerRepository.timerState.value
        startForegroundWithNotification(state)
        overlayManager.showOrUpdate(state)

        startTicker()
    }

    private fun handleReset() {
        if (TimerRepository.timerState.value.status != TimerStatus.IDLE) {
            TimerRepository.resetTimer(null)
        }
        tickerJob?.cancel()
        tickerJob = null
        audioAlertManager.stop()
        releaseWakeLock()

        val state = TimerRepository.timerState.value
        overlayManager.showOrUpdate(state)
        updateNotification(state)
    }

    private fun handleStop() {
        if (TimerRepository.timerState.value.status != TimerStatus.IDLE) {
            TimerRepository.stopTimer(null)
        }
        tickerJob?.cancel()
        tickerJob = null
        audioAlertManager.stop()
        releaseWakeLock()

        overlayManager.removeOverlay()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        lastNotificationSecond = -1L

        tickerJob = serviceScope.launch {
            while (isActive) {
                val targetEnd = TimerRepository.targetEndTimeRealtime
                val remaining = (targetEnd - SystemClock.elapsedRealtime()).coerceAtLeast(0L)

                TimerRepository.updateRemainingTime(remaining)
                val currentState = TimerRepository.timerState.value

                overlayManager.showOrUpdate(currentState)

                // Update notification once every second to avoid overhead
                val currentSecond = remaining / 1000L
                if (currentSecond != lastNotificationSecond) {
                    lastNotificationSecond = currentSecond
                    updateNotification(currentState)
                }

                if (remaining <= 0L) {
                    onTimerCompleted()
                    break
                }

                delay(200L)
            }
        }
    }

    private fun onTimerCompleted() {
        TimerRepository.markCompleted()
        val completedState = TimerRepository.timerState.value

        overlayManager.showOrUpdate(completedState)
        updateNotification(completedState)

        if (completedState.soundEnabled) {
            audioAlertManager.startAlert(
                intensity = completedState.beepIntensity,
                maxDurationSeconds = 12,
                onFinished = {
                    // Auto-cleanup after alert finishes
                    overlayManager.removeOverlay()
                    handleStop()
                }
            )
        } else {
            // If sound disabled, keep completed notification and overlay visible briefly (8s) then cleanup
            serviceScope.launch {
                delay(8000L)
                overlayManager.removeOverlay()
                handleStop()
            }
        }
    }

    private fun startForegroundWithNotification(state: TimerState) {
        val notification = buildNotification(state)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(state: TimerState) {
        val notification = buildNotification(state)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(state: TimerState): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(contentPendingIntent)
            .setOngoing(state.status == TimerStatus.RUNNING)
            .setOnlyAlertOnce(state.status != TimerStatus.COMPLETED)
            .setCategory(if (state.status == TimerStatus.COMPLETED) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_STATUS)

        when (state.status) {
            TimerStatus.COMPLETED -> {
                builder.setContentTitle(getString(R.string.times_up_title))
                    .setContentText(getString(R.string.times_up_message))
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .addAction(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        getString(R.string.action_stop),
                        createServicePendingIntent(ACTION_STOP, 1)
                    )
            }
            TimerStatus.PAUSED -> {
                builder.setContentTitle("Social Timer (Paused)")
                    .setContentText("Remaining: ${state.remainingFormatted}")
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .addAction(
                        android.R.drawable.ic_media_play,
                        getString(R.string.action_resume),
                        createServicePendingIntent(ACTION_RESUME, 2)
                    )
                    .addAction(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        getString(R.string.action_stop),
                        createServicePendingIntent(ACTION_STOP, 3)
                    )
            }
            TimerStatus.RUNNING -> {
                builder.setContentTitle("Social Timer Active")
                    .setContentText("Time remaining: ${state.remainingFormatted}")
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .addAction(
                        android.R.drawable.ic_media_pause,
                        getString(R.string.action_pause),
                        createServicePendingIntent(ACTION_PAUSE, 4)
                    )
                    .addAction(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        getString(R.string.action_stop),
                        createServicePendingIntent(ACTION_STOP, 5)
                    )
            }
            TimerStatus.IDLE -> {
                builder.setContentTitle("Social Timer")
                    .setContentText("Session: ${state.totalFormatted}")
                    .setPriority(NotificationCompat.PRIORITY_LOW)
            }
        }

        return builder.build()
    }

    private fun createServicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, TimerService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_desc)
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld == false) {
                // Keep wake lock for max 4 hours to prevent battery drain if forgotten
                wakeLock?.acquire(4 * 60 * 60 * 1000L)
            }
        } catch (_: Exception) {
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tickerJob?.cancel()
        serviceScope.cancel()
        audioAlertManager.stop()
        overlayManager.removeOverlay()
        releaseWakeLock()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
