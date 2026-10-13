package com.example.ui

import android.content.Context
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioAlertManager
import com.example.model.BeepIntensity
import com.example.model.TimerState
import com.example.state.PreferencesManager
import com.example.state.TimerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CustomTimeInput(
    val hours: Int = 0,
    val minutes: Int = 30,
    val seconds: Int = 0
) {
    val totalMillis: Long
        get() = ((hours * 3600L) + (minutes * 60L) + seconds) * 1000L
}

class TimerViewModel : ViewModel() {

    val timerState: StateFlow<TimerState> = TimerRepository.timerState

    private val _customInput = MutableStateFlow(CustomTimeInput(hours = 0, minutes = 30, seconds = 0))
    val customInput: StateFlow<CustomTimeInput> = _customInput.asStateFlow()

    private val _isOverlaySetupCompleted = MutableStateFlow(false)
    val isOverlaySetupCompleted: StateFlow<Boolean> = _isOverlaySetupCompleted.asStateFlow()

    private var audioAlertManager: AudioAlertManager? = null

    fun initAudio(context: Context) {
        if (audioAlertManager == null) {
            audioAlertManager = AudioAlertManager(context.applicationContext)
        }
    }

    fun initOverlaySetupState(context: Context) {
        val saved = PreferencesManager.isOverlaySetupCompleted(context)
        val hasPermission = Settings.canDrawOverlays(context)
        if (hasPermission && !saved) {
            PreferencesManager.setOverlaySetupCompleted(context, true)
            _isOverlaySetupCompleted.value = true
        } else {
            _isOverlaySetupCompleted.value = saved || hasPermission
        }
    }

    fun checkOverlayPermission(context: Context) {
        if (Settings.canDrawOverlays(context)) {
            PreferencesManager.setOverlaySetupCompleted(context, true)
            _isOverlaySetupCompleted.value = true
        } else {
            val saved = PreferencesManager.isOverlaySetupCompleted(context)
            _isOverlaySetupCompleted.value = saved
        }
    }

    fun selectPreset(minutes: Int) {
        val totalMillis = minutes * 60 * 1000L
        _customInput.value = CustomTimeInput(
            hours = minutes / 60,
            minutes = minutes % 60,
            seconds = 0
        )
        TimerRepository.setCustomDuration(totalMillis)
    }

    fun updateHours(h: Int) {
        val safeH = h.coerceIn(0, 23)
        _customInput.update { it.copy(hours = safeH) }
        syncInputToRepository()
    }

    fun updateMinutes(m: Int) {
        val safeM = m.coerceIn(0, 59)
        _customInput.update { it.copy(minutes = safeM) }
        syncInputToRepository()
    }

    fun updateSeconds(s: Int) {
        val safeS = s.coerceIn(0, 59)
        _customInput.update { it.copy(seconds = safeS) }
        syncInputToRepository()
    }

    private fun syncInputToRepository() {
        val totalMillis = _customInput.value.totalMillis
        if (totalMillis > 0) {
            TimerRepository.setCustomDuration(totalMillis)
        }
    }

    fun startTimer(context: Context) {
        TimerRepository.startTimer(context)
    }

    fun pauseTimer(context: Context) {
        TimerRepository.pauseTimer(context)
    }

    fun resumeTimer(context: Context) {
        TimerRepository.resumeTimer(context)
    }

    fun resetTimer(context: Context) {
        TimerRepository.resetTimer(context)
    }

    fun stopTimer(context: Context) {
        TimerRepository.stopTimer(context)
    }

    fun setSoundEnabled(enabled: Boolean) {
        TimerRepository.setSoundEnabled(enabled)
    }

    fun setBeepIntensity(intensity: BeepIntensity) {
        TimerRepository.setBeepIntensity(intensity)
    }

    fun restoreOverlay() {
        TimerRepository.restoreOverlay()
    }

    fun testAlarmSound(context: Context? = null) {
        if (audioAlertManager == null && context != null) {
            audioAlertManager = AudioAlertManager(context.applicationContext)
        }
        val intensity = timerState.value.beepIntensity
        audioAlertManager?.previewAlert(intensity)
    }

    override fun onCleared() {
        super.onCleared()
        audioAlertManager?.stop()
    }
}
