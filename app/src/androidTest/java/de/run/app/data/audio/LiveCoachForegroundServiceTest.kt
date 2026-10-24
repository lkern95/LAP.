package de.run.app.data.audio

import android.Manifest
import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveCoachForegroundServiceTest {
    @Test
    fun notificationRemainsActiveDuringBackgroundCoachAndCanBeStopped() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val notifications = context.getSystemService(NotificationManager::class.java)
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.POST_NOTIFICATIONS
        )

        LiveCoachForegroundService.start(context)
        awaitNotification(notifications, expected = true)
        assertTrue(notifications.activeNotifications.any { it.id == LiveCoachForegroundService.NOTIFICATION_ID })

        LiveCoachForegroundService.pause(context)
        Thread.sleep(300)
        val paused = notifications.activeNotifications.first { it.id == LiveCoachForegroundService.NOTIFICATION_ID }
        assertTrue(paused.notification.extras.getCharSequence("android.text")?.contains("Pausiert") == true)

        LiveCoachForegroundService.stop(context)
        awaitNotification(notifications, expected = false)
    }

    private fun awaitNotification(manager: NotificationManager, expected: Boolean) {
        repeat(30) {
            val visible = manager.activeNotifications.any { it.id == LiveCoachForegroundService.NOTIFICATION_ID }
            if (visible == expected) return
            Thread.sleep(100)
        }
        assertTrue(manager.activeNotifications.any { it.id == LiveCoachForegroundService.NOTIFICATION_ID } == expected)
    }
}
