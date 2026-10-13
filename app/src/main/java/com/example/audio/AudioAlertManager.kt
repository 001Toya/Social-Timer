package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.model.BeepIntensity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

/**
 * Generates an authentic, high-pitched digital countdown timer beep
 * using synthetic PCM audio and AudioTrack.
 *
 * Characteristics:
 * - High-pitched digital alarm tone (~2800 Hz Standard, ~3000 Hz Strong)
 * - Sharp repetitive burst cadence (BEEP-BEEP-BEEP ... BEEP-BEEP-BEEP)
 * - Uses AudioTrack MODE_STATIC for instant, reliable hardware looping without CPU spin loops
 * - Ensures alarm volume is audible on real devices and emulators
 * - Automatically shuts down after max duration or when stopped
 * - Strictly prevents overlapping playback instances
 */
class AudioAlertManager(private val context: Context) {

    companion object {
        private const val TAG = "AudioAlertManager"
        private const val SAMPLE_RATE = 44100
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val scope = CoroutineScope(Dispatchers.Default)

    private var activePlaybackJob: Job? = null
    private var activeAudioTrack: AudioTrack? = null

    @Volatile
    private var isPlayingAlert = false

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /**
     * Start playing the attention-grabbing digital timer alarm beep.
     * Guaranteed to stop automatically after maxDurationSeconds or when [stop] is called.
     */
    @Synchronized
    fun startAlert(
        intensity: BeepIntensity = BeepIntensity.STANDARD,
        maxDurationSeconds: Int = 12,
        onFinished: (() -> Unit)? = null
    ) {
        Log.d(TAG, "startAlert requested: intensity=$intensity, duration=${maxDurationSeconds}s")
        stop()
        isPlayingAlert = true

        activePlaybackJob = scope.launch {
            try {
                ensureAudibleAlarmVolume()
                triggerVibration()
                playAlarmInternal(intensity, maxDurationSeconds)
            } catch (e: Exception) {
                Log.e(TAG, "Error in startAlert: ${e.message}", e)
            } finally {
                withContext(Dispatchers.Main) {
                    stop()
                    onFinished?.invoke()
                }
            }
        }
    }

    /**
     * Play a short test version of the alarm (approx 2.5s) using the exact same audio path.
     */
    @Synchronized
    fun previewAlert(intensity: BeepIntensity) {
        Log.d(TAG, "previewAlert requested: intensity=$intensity")
        stop()
        isPlayingAlert = true

        activePlaybackJob = scope.launch {
            try {
                ensureAudibleAlarmVolume()
                triggerVibration()
                playAlarmInternal(intensity, durationSeconds = 2)
            } catch (e: Exception) {
                Log.e(TAG, "Error in previewAlert: ${e.message}", e)
            } finally {
                stop()
            }
        }
    }

    @Synchronized
    fun stop() {
        Log.d(TAG, "stop called: isPlayingAlert was $isPlayingAlert")
        isPlayingAlert = false
        activePlaybackJob?.cancel()
        activePlaybackJob = null

        try {
            activeAudioTrack?.let { track ->
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    try {
                        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                            track.pause()
                            track.flush()
                        }
                        track.stop()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error stopping track: ${e.message}")
                    }
                    track.release()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing track: ${e.message}")
        } finally {
            activeAudioTrack = null
        }

        cancelVibration()
    }

    fun isPlaying(): Boolean = isPlayingAlert

    /**
     * Ensures the device's alarm stream volume is not 0 (e.g. on fresh emulators or muted devices).
     * If 0, sets it to ~70% volume so the alarm is actually audible.
     */
    private fun ensureAudibleAlarmVolume() {
        val am = audioManager ?: return
        try {
            val currentVol = am.getStreamVolume(AudioManager.STREAM_ALARM)
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            Log.d(TAG, "Current STREAM_ALARM volume: $currentVol / $maxVol")
            if (currentVol == 0 && maxVol > 0) {
                val targetVol = (maxVol * 0.7f).toInt().coerceAtLeast(1)
                am.setStreamVolume(AudioManager.STREAM_ALARM, targetVol, 0)
                Log.d(TAG, "Adjusted STREAM_ALARM volume from 0 to $targetVol")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not adjust alarm volume: ${e.message}")
        }
    }

    /**
     * Common audio player method used by both full alert and preview.
     * Generates PCM beep pattern, loads into static AudioTrack, loops indefinitely,
     * and waits for durationSeconds before stopping.
     */
    private suspend fun playAlarmInternal(intensity: BeepIntensity, durationSeconds: Int) {
        val pcmData = generateBeepPattern(intensity)
        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSizeBytes = if (minBufferSize > 0) {
            maxOf(pcmData.size * 2, minBufferSize)
        } else {
            pcmData.size * 2
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        var track: AudioTrack? = null
        try {
            track = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSizeBytes)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            // In MODE_STATIC, track.state is STATE_NO_STATIC_DATA (2) until write() is called.
            // Only STATE_UNINITIALIZED (0) indicates a construction failure.
            if (track.state == AudioTrack.STATE_UNINITIALIZED) {
                Log.e(TAG, "AudioTrack construction failed: state=STATE_UNINITIALIZED (0)")
                fallbackTonePlayback(durationSeconds)
                return
            }

            // Load static PCM buffer into track memory. This transitions state from STATE_NO_STATIC_DATA (2) to STATE_INITIALIZED (1).
            val written = track.write(pcmData, 0, pcmData.size, AudioTrack.WRITE_BLOCKING)
            Log.d(TAG, "AudioTrack written $written of ${pcmData.size} shorts, state after write=${track.state}")

            if (written < 0 || track.state != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "AudioTrack data write failed (written=$written, state=${track.state})")
                fallbackTonePlayback(durationSeconds)
                return
            }

            // Loop continuously for the duration
            val loopResult = track.setLoopPoints(0, pcmData.size, -1)
            Log.d(TAG, "AudioTrack setLoopPoints result=$loopResult")
            track.setVolume(1.0f)

            activeAudioTrack = track

            track.play()
            Log.d(TAG, "AudioTrack playing successfully, playState=${track.playState}")

            // Play for the requested duration
            val startTime = System.currentTimeMillis()
            val targetDurationMs = durationSeconds * 1000L

            while (isPlayingAlert && (System.currentTimeMillis() - startTime) < targetDurationMs) {
                delay(100L)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during AudioTrack playback: ${e.message}", e)
            fallbackTonePlayback(durationSeconds)
        } finally {
            try {
                if (track?.state == AudioTrack.STATE_INITIALIZED) {
                    track.stop()
                    track.release()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Exception releasing track: ${e.message}")
            }
            if (activeAudioTrack == track) {
                activeAudioTrack = null
            }
            Log.d(TAG, "AudioTrack playback completed and released")
        }
    }

    /**
     * Fallback tone generator in case the device's AudioTrack fails or hardware is constrained.
     */
    private suspend fun fallbackTonePlayback(durationSeconds: Int) {
        Log.w(TAG, "Triggering fallback tone playback")
        var toneGen: android.media.ToneGenerator? = null
        try {
            toneGen = android.media.ToneGenerator(AudioManager.STREAM_ALARM, 100)
            val startTime = System.currentTimeMillis()
            val targetDurationMs = durationSeconds * 1000L
            while (isPlayingAlert && (System.currentTimeMillis() - startTime) < targetDurationMs) {
                toneGen.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200)
                delay(300L)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallback tone playback failed: ${e.message}")
        } finally {
            try {
                toneGen?.stopTone()
                toneGen?.release()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Synthesizes 1 full cycle of the high-pitched digital timer alarm:
     * High frequency ~2800Hz / ~3000Hz with pulse envelope.
     */
    private fun generateBeepPattern(intensity: BeepIntensity): ShortArray {
        val frequency = if (intensity == BeepIntensity.STRONG) 3000.0 else 2800.0

        val beepCount = if (intensity == BeepIntensity.STRONG) 4 else 3
        val beepDurationMs = if (intensity == BeepIntensity.STRONG) 120 else 100
        val pauseDurationMs = if (intensity == BeepIntensity.STRONG) 60 else 70
        val cycleEndPauseMs = if (intensity == BeepIntensity.STRONG) 450 else 600

        val totalMs = (beepCount * (beepDurationMs + pauseDurationMs)) + cycleEndPauseMs
        val totalSamples = (SAMPLE_RATE * totalMs) / 1000
        val buffer = ShortArray(totalSamples)

        var currentSample = 0

        for (i in 0 until beepCount) {
            // Beep tone with smooth fade-in/fade-out (3ms ramp) to eliminate speaker clicks
            val beepSamples = (SAMPLE_RATE * beepDurationMs) / 1000
            val fadeSamples = (SAMPLE_RATE * 3) / 1000

            for (s in 0 until beepSamples) {
                if (currentSample >= totalSamples) break
                val angle = 2.0 * PI * s * frequency / SAMPLE_RATE
                val rawSine = sin(angle)

                // Envelope calculation
                val envelope = when {
                    s < fadeSamples -> s.toDouble() / fadeSamples
                    s > beepSamples - fadeSamples -> (beepSamples - s).toDouble() / fadeSamples
                    else -> 1.0
                }

                val amplitude = (Short.MAX_VALUE * 0.95 * envelope).toInt()
                buffer[currentSample++] = (rawSine * amplitude).toInt().toShort()
            }

            // Pause between burst beeps
            val pauseSamples = (SAMPLE_RATE * pauseDurationMs) / 1000
            for (p in 0 until pauseSamples) {
                if (currentSample >= totalSamples) break
                buffer[currentSample++] = 0
            }
        }

        // Cycle end pause
        while (currentSample < totalSamples) {
            buffer[currentSample++] = 0
        }

        return buffer
    }

    private fun triggerVibration() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 150, 80, 150, 80, 150, 600)
                val amplitudes = intArrayOf(0, 220, 0, 220, 0, 255, 0)
                v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(longArrayOf(0, 150, 80, 150, 80, 150), -1)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed: ${e.message}")
        }
    }

    private fun cancelVibration() {
        try {
            vibrator?.cancel()
        } catch (_: Exception) {
        }
    }
}
