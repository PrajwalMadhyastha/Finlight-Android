package io.pm.finlight.utils

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.CategorySpending
import io.pm.finlight.MainApplication
import io.pm.finlight.PotentialTransaction
import io.pm.finlight.TestApplication
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionDetails
import io.pm.finlight.TravelModeSettings
import io.pm.finlight.TripType
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
import org.robolectric.shadows.ShadowPendingIntent
import java.net.URLDecoder

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

        val shadowApplication = shadowOf(context)
        shadowApplication.grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        // Create all necessary notification channels for the tests
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                NotificationChannel(MainApplication.BACKUP_CHANNEL_ID, "Backups", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(MainApplication.DAILY_REPORT_CHANNEL_ID, "Daily Reports", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(MainApplication.RICH_TRANSACTION_CHANNEL_ID, "Rich Transactions", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(MainApplication.TRANSACTION_CHANNEL_ID, "Transactions", NotificationManager.IMPORTANCE_DEFAULT)
            )
            channels.forEach(notificationManager::createNotificationChannel)
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

        val extras = postedNotification.extras
        assertEquals("Notification title should match", expectedTitle, extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Notification text should match", expectedText, extras.getString(Notification.EXTRA_TEXT))
        assertEquals("Notification channel ID should match", MainApplication.BACKUP_CHANNEL_ID, postedNotification.channelId)
        assertTrue("Notification should be auto-cancel", (postedNotification.flags and Notification.FLAG_AUTO_CANCEL) != 0)
    }

    @Test
    fun `showDailyReportNotification_buildsCorrectlyWithDeepLink`() {
        // Arrange
        val dateMillis = System.currentTimeMillis()
        val expectedUri = "app://finlight.pm.io/report/DAILY?date=$dateMillis"

        // Act
        NotificationHelper.showDailyReportNotification(context, "Test Daily Report", 123.45, emptyList(), dateMillis)

        // Assert
        val notification = shadowNotificationManager.getNotification(2) // Daily Report ID is 2
        assertNotNull("Notification should not be null", notification)
        assertEquals("Test Daily Report", notification.extras.getString(Notification.EXTRA_TITLE))

        val pendingIntent = notification.contentIntent
        assertNotNull("PendingIntent should not be null", pendingIntent)
        val shadowPendingIntent: ShadowPendingIntent = shadowOf(pendingIntent)
        val savedIntent = shadowPendingIntent.savedIntent
        assertEquals("Intent data URI should match the deep link", expectedUri, savedIntent.data.toString())
    }

    @Test
    fun `showRichTransactionNotification_buildsCorrectlyWithActionsAndDeepLink`() {
        // Arrange
        val transactionId = 123
        val details = TransactionDetails(
            transaction = Transaction(id = transactionId, description = "Test Coffee", amount = 4.56, transactionType = "expense", date = 0L, accountId = 1, categoryId = 1, notes = null, originalDescription = "Test Coffee"),
            images = emptyList(),
            accountName = "Test Account",
            categoryName = "Food",
            categoryIconKey = "restaurant",
            categoryColorKey = "red_light",
            tagNames = null
        )
        val expectedUri = "app://finlight.pm.io/transaction_detail/$transactionId"

        // Act
        NotificationHelper.showRichTransactionNotification(context, details, 789.0, 3)

        // Assert
        val notification = shadowNotificationManager.getNotification(transactionId)
        assertNotNull(notification)
        assertEquals("Finlight · Test Account", notification.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(notification.extras.getString(Notification.EXTRA_TEXT)?.contains("4.56 at Test Coffee") ?: false)

        // Check content intent
        val contentPI = notification.contentIntent
        val contentIntent = shadowOf(contentPI).savedIntent
        assertEquals(expectedUri, contentIntent.data.toString())

        // Check action intent
        assertEquals(1, notification.actions.size)
        assertEquals("View Details", notification.actions[0].title)
        val actionPI = notification.actions[0].actionIntent
        val actionIntent = shadowOf(actionPI).savedIntent
        assertEquals(expectedUri, actionIntent.data.toString())

        // Check inbox style lines
        val inboxLines = notification.extras.getCharSequenceArray(NotificationCompat.EXTRA_TEXT_LINES)
        assertNotNull(inboxLines)
        assertEquals(2, inboxLines!!.size)
        assertTrue(inboxLines[0].toString().contains("₹789.00 spent this month"))
        assertTrue(inboxLines[1].toString().contains("This is your 3rd visit here."))
    }

    @Test
    fun `showRecurringTransactionDueNotification_buildsCorrectlyWithDeepLinkAndAction`() {
        // Arrange
        val potentialTxn = PotentialTransaction(456L, "RecurringSender", 599.0, "expense", "Netflix", "Recurring payment for Netflix", date = System.currentTimeMillis())
        val gson = Gson()
        val encodedJson = java.net.URLEncoder.encode(gson.toJson(potentialTxn), "UTF-8")
        val expectedUri = "app://finlight.pm.io/link_recurring/$encodedJson"

        // Act
        NotificationHelper.showRecurringTransactionDueNotification(context, potentialTxn)

        // Assert
        val notification = shadowNotificationManager.getNotification(potentialTxn.sourceSmsId.toInt())
        assertNotNull(notification)
        assertEquals("Recurring Payment Due", notification.extras.getString(Notification.EXTRA_TITLE))

        val contentPI = notification.contentIntent
        val contentIntent = shadowOf(contentPI).savedIntent
        assertEquals(expectedUri, contentIntent.data.toString())

        assertEquals(1, notification.actions.size)
        assertEquals("Confirm Payment", notification.actions[0].title)
        val actionPI = notification.actions[0].actionIntent
        val actionIntent = shadowOf(actionPI).savedIntent
        assertEquals(expectedUri, actionIntent.data.toString())
    }

    @Test
    fun `showTravelModeSmsNotification_buildsCorrectlyWithTwoActions`() {
        // Arrange
        val potentialTxn = PotentialTransaction(789L, "TravelSender", 50.0, "expense", "Uber", "Uber ride", date = System.currentTimeMillis())
        val travelSettings = TravelModeSettings(true, "US Trip", TripType.INTERNATIONAL, 0L, Long.MAX_VALUE, "USD", 83.5f)

        // Act
        NotificationHelper.showTravelModeSmsNotification(context, potentialTxn, travelSettings)

        // Assert
        val notification = shadowNotificationManager.getNotification(potentialTxn.sourceSmsId.toInt())
        assertNotNull(notification)
        assertEquals("Transaction while traveling?", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(2, notification.actions.size)

        // Action 1: Foreign Currency
        val foreignAction = notification.actions.find { it.title == "It was in USD" }
        assertNotNull(foreignAction)
        val foreignPI = foreignAction!!.actionIntent
        val foreignIntent = shadowOf(foreignPI).savedIntent
        val foreignUri = foreignIntent.data.toString()
        assertTrue(foreignUri.startsWith("app://finlight.pm.io/approve_transaction_screen?potentialTxnJson="))
        val foreignJson = URLDecoder.decode(foreignUri.substringAfter("potentialTxnJson="), "UTF-8")
        val foreignTxn = Gson().fromJson(foreignJson, PotentialTransaction::class.java)
        assertEquals(true, foreignTxn.isForeignCurrency)

        // Action 2: Home Currency
        val homeAction = notification.actions.find { it.title == "It was in ₹" }
        assertNotNull(homeAction)
        val homePI = homeAction!!.actionIntent
        val homeIntent = shadowOf(homePI).savedIntent
        val homeUri = homeIntent.data.toString()
        assertTrue(homeUri.startsWith("app://finlight.pm.io/approve_transaction_screen?potentialTxnJson="))
        val homeJson = URLDecoder.decode(homeUri.substringAfter("potentialTxnJson="), "UTF-8")
        val homeTxn = Gson().fromJson(homeJson, PotentialTransaction::class.java)
        assertEquals(false, homeTxn.isForeignCurrency)
    }
}