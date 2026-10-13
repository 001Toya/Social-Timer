package com.example.state

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.example.model.BeepIntensity
import com.example.model.TimerState
import com.example.model.TimerStatus
import com.example.service.TimerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Singleton repository holding the single source of truth for timer state.
 * Both the Compose UI and the Foreground Service observe and update this state.
 */
object TimerRepository {

    private val _timerState = MutableStateFlow(TimerState())
    val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

    // Realtime timestamp reference for monotonic, drift-free countdown
    @Volatile
    var targetEndTimeRealtime: Long = 0L
        private set

    fun setCustomDuration(durationMillis: Long) {
        val valid = durationMillis.coerceAtLeast(1000L)
        _timerState.update { current ->
            if (current.status == TimerStatus.IDLE) {
                current.copy(
                    totalDurationMillis = valid,
                    remainingMillis = valid
                )
            } else {
                current.copy(totalDurationMillis = valid)
            }
        }
    }

    fun dismissOverlay() {
        _timerState.update { it.copy(isOverlayEnabled = false) }
    }

    fun restoreOverlay() {
        _timerState.update { it.copy(isOverlayEnabled = true) }
    }

    fun setAppInForeground(inForeground: Boolean) {
        _timerState.update { it.copy(isAppInForeground = inForeground) }
    }

    fun setSoundEnabled(enabled: Boolean) {
        _timerState.update { it.copy(soundEnabled = enabled) }
    }

    fun setBeepIntensity(intensity: BeepIntensity) {
        _timerState.update { it.copy(beepIntensity = intensity) }
    }

    fun startTimer(context: Context, durationMillis: Long? = null) {
        val total = durationMillis ?: _timerState.value.totalDurationMillis
        val validDuration = total.coerceAtLeast(1000L)

        targetEndTimeRealtime = SystemClock.elapsedRealtime() + validDuration

        _timerState.update {
            it.copy(
                status = TimerStatus.RUNNING,
                totalDurationMillis = validDuration,
                remainingMillis = validDuration,
                isOverlayEnabled = true
            )
        }

        val serviceIntent = Intent(context, TimerService::class.java).apply {
            action = TimerService.ACTION_START
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    fun pauseTimer(context: Context? = null) {
        if (_timerState.value.status != TimerStatus.RUNNING) return

        val remaining = (targetEndTimeRealtime - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        _timerState.update {
            it.copy(
                status = TimerStatus.PAUSED,
                remainingMillis = remaining
            )
        }

        if (context != null) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = TimerService.ACTION_PAUSE
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
                try {
                    ContextCompat.startForegroundService(context, intent)
                } catch (_: Exception) {
                }
            }
        }
    }

    fun resumeTimer(context: Context? = null) {
        if (_timerState.value.status != TimerStatus.PAUSED) return

        val remaining = _timerState.value.remainingMillis
        if (remaining <= 0L) {
            markCompleted()
            return
        }

        targetEndTimeRealtime = SystemClock.elapsedRealtime() + remaining

        _timerState.update {
            it.copy(
                status = TimerStatus.RUNNING
            )
        }

        if (context != null) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = TimerService.ACTION_RESUME
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {
            }
        }
    }

    fun resetTimer(context: Context? = null) {
        val total = _timerState.value.totalDurationMillis
        _timerState.update {
            it.copy(
                status = TimerStatus.IDLE,
                remainingMillis = total
            )
        }

        if (context != null) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = TimerService.ACTION_RESET
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
                try {
                    ContextCompat.startForegroundService(context, intent)
                } catch (_: Exception) {
                }
            }
        }
    }

    fun stopTimer(context: Context? = null) {
        val total = _timerState.value.totalDurationMillis
        _timerState.update {
            it.copy(
                status = TimerStatus.IDLE,
                remainingMillis = total
            )
        }

        if (context != null) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = TimerService.ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
                try {
                    ContextCompat.startForegroundService(context, intent)
                } catch (_: Exception) {
                }
            }
        }
    }

    fun updateRemainingTime(remaining: Long) {
        _timerState.update {
            it.copy(remainingMillis = remaining)
        }
    }

    fun markCompleted() {
        _timerState.update {
            it.copy(
                status = TimerStatus.COMPLETED,
                remainingMillis = 0L
            )
        }
    }
}
