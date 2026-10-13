package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.audio.AudioAlertManager
import com.example.model.BeepIntensity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AudioAlertTest {

    @Test
    fun testAudioAlertManagerInstantiation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = AudioAlertManager(context)
        assertNotNull(manager)
        assertFalse(manager.isPlaying())
    }

    @Test
    fun testStopWhenIdle() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = AudioAlertManager(context)
        manager.stop()
        assertFalse(manager.isPlaying())
    }

    @Test
    fun testPreviewAndStop() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = AudioAlertManager(context)
        manager.previewAlert(BeepIntensity.STANDARD)
        manager.stop()
        assertFalse(manager.isPlaying())
    }

    @Test
    fun testStartAlertAndStop() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = AudioAlertManager(context)
        manager.startAlert(BeepIntensity.STRONG, maxDurationSeconds = 2)
        manager.stop()
        assertFalse(manager.isPlaying())
    }
}
