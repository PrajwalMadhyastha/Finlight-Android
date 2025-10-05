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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowApplication
import org.robolectric.shadows.ShadowNotificationManager

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class NotificationHelperTest : BaseViewModelTest() {

    private lateinit var context: Application
    private lateinit var shadowNotificationManager: ShadowNotificationManager

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowNotificationManager = shadowOf(notificationManager)

        // Explicitly grant the POST_NOTIFICATIONS permission for the test environment.
        val shadowApplication = shadowOf(context as Application)
        shadowApplication.grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        // Create notification channels required by the helper
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val backupChannel = NotificationChannel(
                MainApplication.BACKUP_CHANNEL_ID,
                "Data Backups",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(backupChannel)
        }
    }

    @Test
    fun `showAutoBackupNotification creates and posts a notification correctly`() {
        // Arrange
        val expectedTitle = "Backup Complete"
        val expectedText = "Your Finlight data was successfully backed up."
        val notificationId = NotificationHelper.BACKUP_NOTIFICATION_ID

        // Act
        NotificationHelper.showAutoBackupNotification(context)

        // Assert
        val postedNotification = shadowNotificationManager.getNotification(notificationId)
        assertNotNull("Notification should have been posted", postedNotification)

        // Access the 'extras' Bundle directly from the Notification object
        val extras = postedNotification.extras
        assertEquals("Notification title should match", expectedTitle, extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Notification text should match", expectedText, extras.getString(Notification.EXTRA_TEXT))
        assertEquals("Notification channel ID should match", MainApplication.BACKUP_CHANNEL_ID, postedNotification.channelId)
        assertTrue("Notification should be auto-cancel", (postedNotification.flags and Notification.FLAG_AUTO_CANCEL) != 0)
    }
}
