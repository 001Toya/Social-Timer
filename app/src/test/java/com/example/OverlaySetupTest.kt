package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.state.PreferencesManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OverlaySetupTest {

    @Test
    fun testFreshInstallOverlaySetupNotCompleted() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("social_timer_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        assertFalse(PreferencesManager.isOverlaySetupCompleted(context))
    }

    @Test
    fun testOverlaySetupCompletedPersistence() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PreferencesManager.setOverlaySetupCompleted(context, true)

        assertTrue(PreferencesManager.isOverlaySetupCompleted(context))

        // Reset to false
        PreferencesManager.setOverlaySetupCompleted(context, false)
        assertFalse(PreferencesManager.isOverlaySetupCompleted(context))
    }
}
