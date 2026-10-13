package com.example

import com.example.model.BeepIntensity
import com.example.model.TimerState
import com.example.model.TimerStatus
import com.example.state.TimerRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerStateTest {

    @Test
    fun testTimeFormatting_minutesAndSeconds() {
        assertEquals("30:00", TimerState.formatTime(30 * 60 * 1000L))
        assertEquals("05:00", TimerState.formatTime(5 * 60 * 1000L))
        assertEquals("00:45", TimerState.formatTime(45 * 1000L))
        assertEquals("00:00", TimerState.formatTime(0L))
    }

    @Test
    fun testTimeFormatting_withHours() {
        assertEquals("1:00:00", TimerState.formatTime(3600 * 1000L))
        assertEquals("1:30:15", TimerState.formatTime((3600 + 1800 + 15) * 1000L))
    }

    @Test
    fun testProgressCalculation() {
        val total = 10000L
        val halfway = TimerState(totalDurationMillis = total, remainingMillis = 5000L)
        assertEquals(0.5f, halfway.progress, 0.01f)

        val completed = TimerState(totalDurationMillis = total, remainingMillis = 0L)
        assertEquals(1.0f, completed.progress, 0.01f)

        val fresh = TimerState(totalDurationMillis = total, remainingMillis = 10000L)
        assertEquals(0.0f, fresh.progress, 0.01f)
    }

    @Test
    fun testStateBooleans() {
        val runningState = TimerState(status = TimerStatus.RUNNING)
        assertTrue(runningState.isRunning)
        assertFalse(runningState.isPaused)
        assertFalse(runningState.isCompleted)

        val completedState = TimerState(status = TimerStatus.COMPLETED)
        assertTrue(completedState.isCompleted)
        assertFalse(completedState.isRunning)
    }

    @Test
    fun testOverlayDismissAndRestoreState() {
        val defaultState = TimerState()
        assertTrue(defaultState.isOverlayEnabled)

        val dismissedState = defaultState.copy(isOverlayEnabled = false)
        assertFalse(dismissedState.isOverlayEnabled)

        val restoredState = dismissedState.copy(isOverlayEnabled = true)
        assertTrue(restoredState.isOverlayEnabled)
    }

    @Test
    fun testAppForegroundState() {
        val defaultState = TimerState()
        assertFalse(defaultState.isAppInForeground)

        val foregroundState = defaultState.copy(isAppInForeground = true)
        assertTrue(foregroundState.isAppInForeground)

        val backgroundState = foregroundState.copy(isAppInForeground = false)
        assertFalse(backgroundState.isAppInForeground)
    }

    @Test
    fun testTimerRepositoryStateTransitions() {
        TimerRepository.setCustomDuration(45000L)
        val idleState = TimerRepository.timerState.value
        assertEquals(TimerStatus.IDLE, idleState.status)
        assertEquals(45000L, idleState.totalDurationMillis)

        // Cannot pause or resume when in IDLE
        TimerRepository.pauseTimer(null)
        assertEquals(TimerStatus.IDLE, TimerRepository.timerState.value.status)

        TimerRepository.resumeTimer(null)
        assertEquals(TimerStatus.IDLE, TimerRepository.timerState.value.status)

        // Reset resets remaining time to total duration and status to IDLE
        TimerRepository.resetTimer(null)
        assertEquals(TimerStatus.IDLE, TimerRepository.timerState.value.status)
        assertEquals(45000L, TimerRepository.timerState.value.remainingMillis)
    }
}
