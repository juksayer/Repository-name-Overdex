package com.example.overdex.battle.observation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.R
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class DroidballNotificationDeviceTest {
    @Test fun stoppedAndRunningNotificationsExposeTheCorrectControls() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val idle = buildDroidballNotification(context, "droidball_observation",
            DroidballNotificationState.IDLE, registrationMode = false, sessionActive = false)
        assertEquals(listOf("Start", "Overdex"), idle.actions.map { it.title.toString() })
        assertEquals(0, idle.flags and Notification.FLAG_ONGOING_EVENT)
        assertTrue(idle.actions.all { it.actionIntent.isActivity })
        val running = buildDroidballNotification(context, "droidball_observation",
            DroidballNotificationState.IDLE, registrationMode = false,
            sessionId = "session-1", matchId = "match-1")
        assertEquals(listOf("Stop", "Next match", "Overdex"), running.actions.map { it.title.toString() })
        assertTrue(running.actions[0].actionIntent.isBroadcast)
        assertTrue(running.actions[1].actionIntent.isBroadcast)
        assertTrue(running.actions[2].actionIntent.isActivity)
        assertEquals(context.packageName, running.actions[0].actionIntent.creatorPackage)
        assertTrue(running.flags and Notification.FLAG_ONGOING_EVENT != 0)
        val next = buildDroidballNotification(context, "droidball_observation",
            DroidballNotificationState.IDLE, registrationMode = false,
            sessionId = "session-1", matchId = "match-2")
        assertNotEquals(running.actions[1].actionIntent, next.actions[1].actionIntent)
    }

    @Test fun systemNotificationChangesEyesInPlaceWithoutSound() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = "droidball_observation"
        manager.createNotificationChannel(NotificationChannel(channel, "Droidball Observation", NotificationManager.IMPORTANCE_LOW))
        val id = 10001
        val tag = "droidball-eyes-test"
        val preview = Bitmap.createBitmap(480, 240, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(preview)
        canvas.drawColor(Color.rgb(15, 18, 20))
        try {
            listOf(DroidballNotificationState.IDLE, DroidballNotificationState.OBSERVING).forEachIndexed { index, state ->
                val notification = buildDroidballNotification(context, channel, state, registrationMode = false)
                manager.notify(tag, id, notification)
                val posted = manager.activeNotifications.single { it.id == id && it.tag == tag }.notification
                val resource = if (state == DroidballNotificationState.IDLE) R.drawable.ic_droidball_idle else R.drawable.ic_droidball_observing
                assertEquals(resource, posted.smallIcon.resId)
                assertEquals(if (index == 0) "Droidball idle" else "Droidball observing", posted.extras.getString(Notification.EXTRA_TITLE))
                assertNull(notification.sound)
                assertNull(notification.vibrate)
                assertTrue(notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
                assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
                val drawable = requireNotNull(posted.smallIcon.loadDrawable(context))
                drawable.setBounds(index * 240 + 24, 24, index * 240 + 216, 216)
                drawable.draw(canvas)
            }
            File(context.cacheDir, "droidball-notification-eyes.png").outputStream().use {
                preview.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally { manager.cancel(tag, id); preview.recycle() }
    }
}
