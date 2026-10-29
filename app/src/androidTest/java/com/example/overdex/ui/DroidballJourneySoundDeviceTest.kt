package com.example.overdex.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.media.DroidballJourneySoundPlayer
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated audio backend check: no capture, battle, navigation, or trainer-data writes. */
@RunWith(AndroidJUnit4::class)
class DroidballJourneySoundDeviceTest {
    @After fun stopEffects() = DroidballJourneySoundPlayer.stop()

    @Test fun effectsRegisterForMediaVolumeAndDisallowRecording() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        // Android omits SoundPool state changes from its public active-player list.
        // The runner can inspect registered players without granting the app permissions.
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.adoptShellPermissionIdentity("android.permission.MODIFY_AUDIO_ROUTING")
        try {
            DroidballJourneySoundPlayer.prepare(context)
            DroidballJourneySoundPlayer.startRoll()
            val deadline = SystemClock.elapsedRealtime() + 4000L
            var found = false
            var lastReport = ""
            while (SystemClock.elapsedRealtime() < deadline && !found) {
                val configurations = audio.activePlaybackConfigurations
                lastReport = configurations.joinToString { it.audioAttributes.toString() }
                found = configurations.any { config ->
                    config.audioAttributes.let { attributes ->
                        attributes.usage == AudioAttributes.USAGE_MEDIA &&
                            attributes.contentType == AudioAttributes.CONTENT_TYPE_SONIFICATION &&
                            attributes.allowedCapturePolicy == AudioAttributes.ALLOW_CAPTURE_BY_NONE
                    }
                }
                if (!found) SystemClock.sleep(25L)
            }
            Log.i("DROIDBALL_SOUND_TEST", lastReport)
            assertTrue("Android registers media/sonification with capture disabled: $lastReport", found)
            DroidballJourneySoundPlayer.stopRoll()
            // Exercise a loaded one-shot and immediate cancellation through Android's backend.
            DroidballJourneySoundPlayer.skid()
            DroidballJourneySoundPlayer.impact()
            DroidballJourneySoundPlayer.stop()
        } finally {
            automation.dropShellPermissionIdentity()
        }
    }
}
