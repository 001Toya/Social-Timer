package com.example.model

enum class TimerStatus {
    IDLE,
    RUNNING,
    PAUSED,
    COMPLETED
}

enum class BeepIntensity {
    STANDARD,
    STRONG
}

data class TimerState(
    val status: TimerStatus = TimerStatus.IDLE,
    val totalDurationMillis: Long = 30 * 60 * 1000L, // Default: 30 minutes
    val remainingMillis: Long = 30 * 60 * 1000L,
    val soundEnabled: Boolean = true,
    val beepIntensity: BeepIntensity = BeepIntensity.STANDARD,
    val isOverlayEnabled: Boolean = true,
    val isAppInForeground: Boolean = false
) {
    val isRunning: Boolean get() = status == TimerStatus.RUNNING
    val isPaused: Boolean get() = status == TimerStatus.PAUSED
    val isCompleted: Boolean get() = status == TimerStatus.COMPLETED
    val isIdle: Boolean get() = status == TimerStatus.IDLE

    val progress: Float
        get() {
            if (totalDurationMillis <= 0L) return 0f
            val elapsed = (totalDurationMillis - remainingMillis).coerceAtLeast(0L)
            return (elapsed.toFloat() / totalDurationMillis.toFloat()).coerceIn(0f, 1f)
        }

    val remainingFormatted: String
        get() = formatTime(remainingMillis)

    val totalFormatted: String
        get() = formatTime(totalDurationMillis)

    companion object {
        fun formatTime(millis: Long): String {
            val totalSeconds = (millis.coerceAtLeast(0L) + 999L) / 1000L
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60

            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }
    }
}
