package io.pm.finlight.utils

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.MainApplication
import io.pm.finlight.TestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNotificationManager

/**
 * Compatibility tests for NotificationHelper specifically running on older Android SDKs.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.S], application = TestApplication::class) // Android 12 (API 31)
class NotificationHelperCompatibilityTest : BaseViewModelTest() {

    private lateinit var context: Application
    private lateinit var shadowNotificationManager: ShadowNotificationManager

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowNotificationManager = shadowOf(notificationManager)

        // IMPORTANTE: Do NOT grant Manifest.permission.POST_NOTIFICATIONS here.
        // On API 31, this permission doesn't exist/isn't required, so the helper 
        // should skip the check and still post the notification.

        // Create the backup channel for testing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(MainApplication.BACKUP_CHANNEL_ID, "Backups", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }
    }

    @Test
    fun `showAutoBackupNotification successfully posts notification on API 31 without explicit permission`() {
        // Arrange
        val notificationId = NotificationHelper.BACKUP_NOTIFICATION_ID
        val backupTime = System.currentTimeMillis()

        // Act
        NotificationHelper.showAutoBackupNotification(context, backupTime)

        // Assert
        // On API 31, the permission check in NotificationHelper should assume 'true' 
        // because it's guarded by SDK >= 33.
        val postedNotification = shadowNotificationManager.getNotification(notificationId)
        assertNotNull("Notification should have been posted on API 31", postedNotification)
        assertEquals("Notification title should match", "Backup Complete", postedNotification.extras.getString(Notification.EXTRA_TITLE))
    }
}
