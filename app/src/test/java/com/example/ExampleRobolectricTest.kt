package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Social Timer", appName)
  }

  @Test
  fun `test audio manager checks`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    am.setStreamVolume(android.media.AudioManager.STREAM_ALARM, 5, 0)
    println("AUDIO DIAGNOSTIC: ringerMode=${am.ringerMode}, streamAlarmVol=${am.getStreamVolume(android.media.AudioManager.STREAM_ALARM)}, streamMusicVol=${am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)}")
  }
}
