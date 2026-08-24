// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/utils/NotificationHelperTest.kt
//
// REASON: FIX (Test) - Updated the `showAutoBackupNotification` test to pass
// a mock timestamp to the function, aligning it with the new method signature
// and resolving the build error.
// =================================================================================
package io.pm.finlight.utils

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
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
import io.pm.finlight.TransactionType
import io.pm.finlight.RecurringTransaction
import io.pm.finlight.RecurringPattern
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
import org.robolectric.shadows.ShadowNotificationManager
import org.robolectric.shadows.ShadowPendingIntent
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            val channels =
                listOf(
                    NotificationChannel(MainApplication.BACKUP_CHANNEL_ID, "Backups", NotificationManager.IMPORTANCE_LOW),
                    NotificationChannel(MainApplication.DAILY_REPORT_CHANNEL_ID, "Daily Reports", NotificationManager.IMPORTANCE_DEFAULT),
                    NotificationChannel(
                        MainApplication.RICH_TRANSACTION_CHANNEL_ID,
                        "Rich Transactions",
                        NotificationManager.IMPORTANCE_HIGH,
                    ),
                    NotificationChannel(MainApplication.TRANSACTION_CHANNEL_ID, "Transactions", NotificationManager.IMPORTANCE_DEFAULT),
                    NotificationChannel(MainApplication.GOALS_CHANNEL_ID, "Goals", NotificationManager.IMPORTANCE_DEFAULT),
                )
            channels.forEach(notificationManager::createNotificationChannel)
        }
    }

    @Test
    fun `showAutoBackupNotification creates and posts a notification correctly`() {
        // Arrange
        val expectedTitle = "Backup Complete"
        val notificationId = NotificationHelper.BACKUP_NOTIFICATION_ID
        // --- FIX: Pass a mock timestamp ---
        val backupTime = System.currentTimeMillis()
        val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        val formattedTime = sdf.format(Date(backupTime))
        val expectedText = "Your Finlight data was successfully backed up at $formattedTime."

        // Act
        NotificationHelper.showAutoBackupNotification(context, backupTime)

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
        val details =
            TransactionDetails(
                transaction = Transaction(id = transactionId, description = "Test Coffee", amount = 4.56, transactionType = TransactionType.EXPENSE, date = 0L, accountId = 1, categoryId = 1, notes = null, originalDescription = "Test Coffee"),
                images = emptyList(),
                accountName = "Test Account",
                categoryName = "Food",
                categoryIconKey = "restaurant",
                categoryColorKey = "red_light",
                tagNames = null,
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
        val potentialTxn =
            PotentialTransaction(
                456L,
                "RecurringSender",
                599.0,
                "expense",
                "Netflix",
                "Recurring payment for Netflix",
                date = System.currentTimeMillis(),
            )
        val gson = Gson()
        val encodedJson = java.net.URLEncoder.encode(gson.toJson(potentialTxn), "UTF-8")
        val rule = RecurringTransaction(1, "Amazon Prime", 1499.0, "expense", "Yearly", System.currentTimeMillis(), 1, 1, null)
        NotificationHelper.showRecurringTransactionDueNotification(context, rule, 101)

        val expectedUri = "app://finlight.pm.io/confirm_pending_transaction/101/${rule.id}"

        // Assert
        val notification = shadowNotificationManager.getNotification(101)
        assertNotNull(notification)
        assertEquals("Recurring Payment Due", notification.extras.getString(Notification.EXTRA_TITLE))

        val contentPI = notification.contentIntent
        val contentIntent = shadowOf(contentPI).savedIntent
        assertEquals(expectedUri, contentIntent.data.toString())
        assertEquals("Confirm Payment", notification.actions[0].title)
        val actionPI = notification.actions[0].actionIntent
        val actionIntent = shadowOf(actionPI).savedIntent
        assertEquals(expectedUri, actionIntent.data.toString())
    }

    @Test
    fun `showTravelModeSmsNotification_buildsCorrectlyWithTwoActions`() {
        // Arrange
        val potentialTxn =
            PotentialTransaction(789L, "TravelSender", 50.0, "expense", "Uber", "Uber ride", date = System.currentTimeMillis())
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

    @Test
    fun `showRecurringPatternDetectedNotification_buildsCorrectly`() {
        // Arrange
        val rule =
            io.pm.finlight.RecurringTransaction(
                id = 1,
                description = "Netflix",
                amount = 199.0,
                transactionType = "expense",
                recurrenceInterval = "MONTHLY",
                startDate = System.currentTimeMillis(),
                accountId = 1,
                categoryId = null,
            )
        val expectedUri = "app://finlight.pm.io/dashboard"

        // Act
        NotificationHelper.showRecurringPatternDetectedNotification(context, RecurringPattern(smsSignature = "hash", description = "Spotify", amount = 10.0, transactionType = "expense", accountId = 1, categoryId = null, occurrences = 3, firstSeen = 0L, lastSeen = 0L))

        // Assert
        val notification = shadowNotificationManager.getNotification("hash".hashCode())
        assertNotNull(notification)
        assertEquals("New Recurring Pattern Detected", notification.extras.getString(Notification.EXTRA_TITLE))

        val contentPI = notification.contentIntent
        val contentIntent = shadowOf(contentPI).savedIntent
        assertEquals(expectedUri, contentIntent.data.toString())
    }

    @Test
    fun `showWeeklySummaryNotification_buildsCorrectly`() {
        // Arrange
        val totalExpenses = 5000.0
        val percentageChange = 10 // +10%
        val topCategories =
            listOf(
                CategorySpending("Food", 2000.0, "red_light", "restaurant"),
                CategorySpending("Travel", 1500.0, "blue_light", "travel_explore"),
            )
        val expectedUri = "app://finlight.pm.io/report/WEEKLY"

        // Act
        NotificationHelper.showWeeklySummaryNotification(context, totalExpenses, percentageChange, topCategories)

        // Assert
        val notification = shadowNotificationManager.getNotification(3) // ID for Weekly Summary
        assertNotNull(notification)
        assertEquals("Spends up by 10% this week", notification.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(notification.extras.getString(Notification.EXTRA_TEXT)?.contains("You spent ₹5,000.00 in total.") == true)

        val contentPI = notification.contentIntent
        val contentIntent = shadowOf(contentPI).savedIntent
        assertEquals(expectedUri, contentIntent.data.toString())

        // Check inbox style lines for categories
        val inboxLines = notification.extras.getCharSequenceArray(NotificationCompat.EXTRA_TEXT_LINES)
        assertNotNull(inboxLines)
        assertTrue(inboxLines!!.any { it.toString().contains("• Food: ₹2,000.00") })
    }

    @Test
    fun `showMonthlySummaryNotification_buildsCorrectly`() {
        // Arrange
        val calendar = java.util.Calendar.getInstance()
        calendar.set(2023, java.util.Calendar.JANUARY, 1) // January
        val totalExpenses = 20000.0
        val percentageChange = -5 // -5%
        val topCategories = emptyList<CategorySpending>()
        val expectedUri = "app://finlight.pm.io/report/MONTHLY?showPreviousMonth=true"

        // Act
        NotificationHelper.showMonthlySummaryNotification(context, calendar, totalExpenses, percentageChange, topCategories)

        // Assert
        val notification = shadowNotificationManager.getNotification(4) // ID for Monthly Summary
        assertNotNull(notification)
        assertEquals("Spends down by 5% in January", notification.extras.getString(Notification.EXTRA_TITLE))

        val contentPI = notification.contentIntent
        val contentIntent = shadowOf(contentPI).savedIntent
        assertEquals(expectedUri, contentIntent.data.toString())

        val inboxLines = notification.extras.getCharSequenceArray(NotificationCompat.EXTRA_TEXT_LINES)
        assertNotNull(inboxLines)
        // Should show "No expenses recorded" since topCategories is empty
        assertTrue(inboxLines!!.any { it.toString().contains("No expenses recorded for this period.") })
    }

    @Test
    fun `showAutoSaveConfirmationNotification_buildsCorrectly`() {
        // Arrange
        val transaction =
            Transaction(
                id = 101,
                description = "Uber Ride",
                amount = 150.0,
                transactionType = TransactionType.EXPENSE,
                date = System.currentTimeMillis(),
                accountId = 1,
                categoryId = 1,
                notes = null,
            )
        val expectedUri = "app://finlight.pm.io/transaction_detail/101"

        // Act
        NotificationHelper.showAutoSaveConfirmationNotification(context, transaction)

        // Assert
        val notification = shadowNotificationManager.getNotification(101)
        assertNotNull(notification)
        assertEquals("Transaction Auto-Saved", notification.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(notification.extras.getString(Notification.EXTRA_TEXT)?.contains("Saved Uber Ride (₹150.00)") == true)

        val contentPI = notification.contentIntent
        val contentIntent = shadowOf(contentPI).savedIntent
        assertEquals(expectedUri, contentIntent.data.toString())

        assertEquals("finlight_transaction_group_101", notification.group)
        assertEquals("Edit", notification.actions[0].title)
    }

    @Test
    fun `showTransactionNotification_buildsCorrectly`() {
        // Arrange
        val transaction =
            Transaction(
                id = 202,
                description = "Salary",
                amount = 50000.0,
                transactionType = TransactionType.INCOME,
                date = System.currentTimeMillis(),
                accountId = 1,
                categoryId = 1,
                notes = null,
            )
        val expectedUri = "app://finlight.pm.io/transaction_detail/202"

        // Act
        NotificationHelper.showTransactionNotification(context, transaction)

        // Assert
        val notification = shadowNotificationManager.getNotification(202)
        assertNotNull(notification)
        assertEquals("New Transaction Found", notification.extras.getString(Notification.EXTRA_TITLE))

        val bigText = notification.extras.getString(NotificationCompat.EXTRA_BIG_TEXT)
        assertTrue(bigText?.contains("Income of ₹50000.00 from Salary detected") == true)

        val contentPI = notification.contentIntent
        val contentIntent = shadowOf(contentPI).savedIntent
        assertEquals(expectedUri, contentIntent.data.toString())
        assertEquals("Review & Categorize", notification.actions[0].title)
    }

    @Test
    fun `showAutoBackupNotification_whenPermissionDenied_doesNotPostNotification`() {
        // Arrange
        val shadowApplication = shadowOf(context)
        shadowApplication.denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val backupTime = System.currentTimeMillis()

        // Act
        NotificationHelper.showAutoBackupNotification(context, backupTime)

        // Assert
        val postedNotification = shadowNotificationManager.getNotification(NotificationHelper.BACKUP_NOTIFICATION_ID)
        assertTrue("Notification should NOT have been posted", postedNotification == null)
    }

    @Test
    fun `showRichTransactionNotification_withDifferentVisitCounts`() {
        // Arrange
        val transactionId = 123
        val baseDetails =
            TransactionDetails(
                transaction = Transaction(id = transactionId, description = "Test Coffee", amount = 4.56, transactionType = TransactionType.EXPENSE, date = 0L, accountId = 1, categoryId = 1, notes = null, originalDescription = "Test Coffee"),
                images = emptyList(),
                accountName = "Test Account",
                categoryName = "Food",
                categoryIconKey = "restaurant",
                categoryColorKey = "red_light",
                tagNames = null,
            )

        // Test visitCount = 1
        NotificationHelper.showRichTransactionNotification(context, baseDetails, 789.0, 1)
        var inboxLines =
            shadowNotificationManager.getNotification(
                transactionId,
            ).extras.getCharSequenceArray(NotificationCompat.EXTRA_TEXT_LINES)
        assertTrue(inboxLines!![1].toString().contains("This is your first visit here."))

        // Test visitCount = 2
        NotificationHelper.showRichTransactionNotification(context, baseDetails, 789.0, 2)
        inboxLines = shadowNotificationManager.getNotification(transactionId).extras.getCharSequenceArray(NotificationCompat.EXTRA_TEXT_LINES)
        assertTrue(inboxLines!![1].toString().contains("This is your 2nd visit here."))

        // Test visitCount = 5
        NotificationHelper.showRichTransactionNotification(context, baseDetails, 789.0, 5)
        inboxLines = shadowNotificationManager.getNotification(transactionId).extras.getCharSequenceArray(NotificationCompat.EXTRA_TEXT_LINES)
        assertTrue(inboxLines!![1].toString().contains("This is your 5th visit here."))

        // Test visitCount = 0 (Should only have 1 line in inbox style)
        NotificationHelper.showRichTransactionNotification(context, baseDetails, 789.0, 0)
        inboxLines = shadowNotificationManager.getNotification(transactionId).extras.getCharSequenceArray(NotificationCompat.EXTRA_TEXT_LINES)
        assertEquals(1, inboxLines!!.size)
    }

    @Test
    fun `showRichTransactionNotification_withIncomeType`() {
        // Arrange
        val transactionId = 123
        val details =
            TransactionDetails(
                transaction =
                    Transaction(
                        id = transactionId,
                        description = "Salary",
                        amount = 50000.0,
                        transactionType = TransactionType.INCOME,
                        date = 0L,
                        accountId = 1,
                        categoryId = 1,
                        notes = null,
                    ),
                images = emptyList(),
                accountName = "Test Account",
                categoryName = "Salary",
                categoryIconKey = "account_balance",
                categoryColorKey = "green_light",
                tagNames = null,
            )

        // Act
        NotificationHelper.showRichTransactionNotification(context, details, 50000.0, 3)

        // Assert
        val notification = shadowNotificationManager.getNotification(transactionId)
        val inboxLines = notification.extras.getCharSequenceArray(NotificationCompat.EXTRA_TEXT_LINES)
        assertTrue(inboxLines!![0].toString().contains("income this month"))
        assertEquals(1, inboxLines.size) // No visit count line for income even if visitCount > 0
    }

    @Test
    fun `showRichTransactionNotification_withMissingIconKey`() {
        // Arrange
        val transactionId = 123
        val details =
            TransactionDetails(
                transaction =
                    Transaction(
                        id = transactionId,
                        description = "Test Coffee",
                        amount = 4.56,
                        transactionType = TransactionType.EXPENSE,
                        date = 0L,
                        accountId = 1,
                        categoryId = 1,
                        notes = null,
                    ),
                images = emptyList(),
                accountName = "Test Account",
                categoryName = "Food",
                // Will trigger letter icon
                categoryIconKey = null,
                categoryColorKey = "red_light",
                tagNames = null,
            )

        // Act
        NotificationHelper.showRichTransactionNotification(context, details, 789.0, 0)

        // Assert
        val notification = shadowNotificationManager.getNotification(transactionId)
        assertNotNull(notification.getLargeIcon()) // Should still have an icon (the generated bitmap)
    }

    @Test
    fun `showWeeklySummaryNotification_withEdgePercentageChanges`() {
        // No change (0%)
        NotificationHelper.showWeeklySummaryNotification(context, 1000.0, 0, emptyList())
        var notification = shadowNotificationManager.getNotification(3)
        assertEquals("Spends same as last week", notification.extras.getString(Notification.EXTRA_TITLE))

        // Null change
        NotificationHelper.showWeeklySummaryNotification(context, 1000.0, null, emptyList())
        notification = shadowNotificationManager.getNotification(3)
        assertEquals("Your Weekly Summary", notification.extras.getString(Notification.EXTRA_TITLE))
    }

    @Test
    fun `showMonthlySummaryNotification_withEdgePercentageChanges`() {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(2023, java.util.Calendar.MARCH, 1) // March

        // No change (0%)
        NotificationHelper.showMonthlySummaryNotification(context, calendar, 1000.0, 0, emptyList())
        var notification = shadowNotificationManager.getNotification(4)
        assertEquals("Spends same as last month", notification.extras.getString(Notification.EXTRA_TITLE))

        // Null change
        NotificationHelper.showMonthlySummaryNotification(context, calendar, 1000.0, null, emptyList())
        notification = shadowNotificationManager.getNotification(4)
        assertEquals("Your March Summary", notification.extras.getString(Notification.EXTRA_TITLE))
    }

    @Test
    fun `showDailyReportNotification_withEmptyCategories`() {
        // Act
        NotificationHelper.showDailyReportNotification(context, "Today's Summary", 0.0, emptyList(), System.currentTimeMillis())

        // Assert
        val notification = shadowNotificationManager.getNotification(2)
        val inboxLines = notification.extras.getCharSequenceArray(NotificationCompat.EXTRA_TEXT_LINES)
        assertTrue(inboxLines!!.any { it.toString().contains("No expenses recorded for this period.") })
    }

    @Test
    fun `showRichTransactionNotification_withVisitCount3`() {
        // Arrange
        val transactionId = 123
        val details =
            TransactionDetails(
                transaction = Transaction(id = transactionId, description = "Test Coffee", amount = 4.56, transactionType = TransactionType.EXPENSE, date = 0L, accountId = 1, categoryId = 1, notes = null, originalDescription = "Test Coffee"),
                images = emptyList(),
                accountName = "Test Account",
                categoryName = "Food",
                categoryIconKey = "restaurant",
                categoryColorKey = "red_light",
                tagNames = null,
            )

        // Act
        NotificationHelper.showRichTransactionNotification(context, details, 789.0, 3)

        // Assert
        val notification = shadowNotificationManager.getNotification(transactionId)
        val inboxLines = notification.extras.getCharSequenceArray(NotificationCompat.EXTRA_TEXT_LINES)
        assertTrue(inboxLines!![1].toString().contains("This is your 3rd visit here."))
    }

    @Test
    fun `showRichTransactionNotification_withLetterDefaultIconKey`() {
        // Arrange
        val transactionId = 321
        val details =
            TransactionDetails(
                transaction =
                    Transaction(
                        id = transactionId,
                        description = "Misc",
                        amount = 1.0,
                        transactionType = TransactionType.EXPENSE,
                        date = 0L,
                        accountId = 1,
                        categoryId = 1,
                        notes = null,
                    ),
                images = emptyList(),
                accountName = "Test Account",
                categoryName = "Other",
                categoryIconKey = "letter_default",
                categoryColorKey = "gray_light",
                tagNames = null,
            )

        // Act
        NotificationHelper.showRichTransactionNotification(context, details, 1.0, 0)

        // Assert
        val notification = shadowNotificationManager.getNotification(transactionId)
        assertNotNull(notification.getLargeIcon())
    }

    @Test
    fun `showRichTransactionNotification_withCategoryIconKey`() {
        // Arrange
        val transactionId = 322
        val details =
            TransactionDetails(
                transaction =
                    Transaction(
                        id = transactionId,
                        description = "Misc",
                        amount = 1.0,
                        transactionType = TransactionType.EXPENSE,
                        date = 0L,
                        accountId = 1,
                        categoryId = 1,
                        notes = null,
                    ),
                images = emptyList(),
                accountName = "Test Account",
                categoryName = "Other",
                categoryIconKey = "category",
                categoryColorKey = "gray_light",
                tagNames = null,
            )

        // Act
        NotificationHelper.showRichTransactionNotification(context, details, 1.0, 0)

        // Assert
        val notification = shadowNotificationManager.getNotification(transactionId)
        assertNotNull(notification.getLargeIcon())
    }

    @Test
    fun `showWeeklySummaryNotification_withNegativeChange`() {
        // Act
        NotificationHelper.showWeeklySummaryNotification(context, 1000.0, -15, emptyList())

        // Assert
        val notification = shadowNotificationManager.getNotification(3)
        assertEquals("Spends down by 15% this week", notification.extras.getString(Notification.EXTRA_TITLE))
    }

    @Test
    fun `showMonthlySummaryNotification_withPositiveChange`() {
        // Arrange
        val calendar = java.util.Calendar.getInstance()
        calendar.set(2023, java.util.Calendar.APRIL, 1) // April

        // Act
        NotificationHelper.showMonthlySummaryNotification(context, calendar, 5000.0, 20, emptyList())

        // Assert
        val notification = shadowNotificationManager.getNotification(4)
        assertEquals("Spends up by 20% in April", notification.extras.getString(Notification.EXTRA_TITLE))
    }

    @Test
    fun `getFallbackDrawableRes_coversVariousIcons`() {
        val iconsToTest =
            listOf(
                "fastfood", "shopping_cart", "local_gas_station", "travel_explore",
                "work", "school", "directions_car", "home", "shield", "star",
                "swap_horiz", "trending_up", "redo", "add_card", "two_wheeler",
                "credit_score", "pets", "account_balance", "more_horiz", "unknown_key",
            )

        iconsToTest.forEachIndexed { index, iconKey ->
            val transactionId = 1000 + index
            val details =
                TransactionDetails(
                    transaction =
                        Transaction(
                            id = transactionId,
                            description = "Icon Test",
                            amount = 1.0,
                            transactionType = TransactionType.EXPENSE,
                            date = 0L,
                            accountId = 1,
                            categoryId = 1,
                            notes = null,
                        ),
                    images = emptyList(),
                    accountName = "Test Account",
                    categoryName = "Test Category",
                    categoryIconKey = iconKey,
                    categoryColorKey = "blue_light",
                    tagNames = null,
                )

            NotificationHelper.showRichTransactionNotification(context, details, 1.0, 0)
            val notification = shadowNotificationManager.getNotification(transactionId)
            assertNotNull("Icon should not be null for $iconKey", notification.getLargeIcon())
        }
    }

    @Config(sdk = [Build.VERSION_CODES.R])
    @Test
    fun `showNotification_onOlderSdk_worksWithoutPermissionCheck`() {
        // Arrange
        val transaction =
            Transaction(
                id = 505,
                description = "Old SDK Test",
                amount = 10.0,
                transactionType = TransactionType.EXPENSE,
                date = System.currentTimeMillis(),
                accountId = 1,
                categoryId = 1,
                notes = null,
            )

        // Act
        NotificationHelper.showTransactionNotification(context, transaction)

        // Assert
        val notification = shadowNotificationManager.getNotification(505)
        assertNotNull(notification)
    }

    @Test
    fun `showTravelModeSmsNotification_whenPermissionDenied_doesNotPostNotification`() {
        // Arrange
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val potentialTxn =
            PotentialTransaction(789L, "TravelSender", 50.0, "expense", "Uber", "Uber ride", date = System.currentTimeMillis())
        val travelSettings = TravelModeSettings(true, "US Trip", TripType.INTERNATIONAL, 0L, Long.MAX_VALUE, "USD", 83.5f)

        // Act
        NotificationHelper.showTravelModeSmsNotification(context, potentialTxn, travelSettings)

        // Assert
        val notification = shadowNotificationManager.getNotification(potentialTxn.sourceSmsId.toInt())
        assertTrue("Notification should NOT have been posted", notification == null)
    }

    @Test
    fun `showRecurringPatternDetectedNotification_whenPermissionDenied_doesNotPost`() {
        // Arrange
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val rule =
            io.pm.finlight.RecurringTransaction(
                id = 8,
                description = "Test",
                amount = 1.0,
                transactionType = "expense",
                recurrenceInterval = "DAILY",
                startDate = 0L,
                accountId = 1,
                categoryId = null,
            )

        // Act
        NotificationHelper.showRecurringPatternDetectedNotification(context, RecurringPattern(smsSignature = "hash", description = "Spotify", amount = 10.0, transactionType = "expense", accountId = 1, categoryId = null, occurrences = 3, firstSeen = 0L, lastSeen = 0L))

        // Assert
        val notification = shadowNotificationManager.getNotification("hash".hashCode())
        assertTrue(notification == null)
    }

    @Test
    fun `showVariableBillAnomalyNotification_buildsCorrectly`() {
        // Arrange
        val rule = RecurringTransaction(id = 201, description = "Electricity Bill", amount = 1500.0, transactionType = "expense", recurrenceInterval = "Monthly", startDate = 0L, accountId = 1, categoryId = 1)

        // Act
        NotificationHelper.showVariableBillAnomalyNotification(context, rule, 2500.0, 1500.0)

        // Assert
        val notification = shadowNotificationManager.getNotification(201 + 5000)
        assertNotNull(notification)
        assertEquals("Anomaly in Variable Bill", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Electricity Bill charged ₹2500.0 (usually ₹1500.0).", notification.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(NotificationCompat.PRIORITY_HIGH, notification.priority)
    }

    @Test
    fun `showAutoApprovedPaymentNotification_buildsCorrectly`() {
        // Arrange
        val rule = RecurringTransaction(id = 202, description = "Internet", amount = 999.0, transactionType = "expense", recurrenceInterval = "Monthly", startDate = 0L, accountId = 1, categoryId = 1)

        // Act
        NotificationHelper.showAutoApprovedPaymentNotification(context, rule)

        // Assert
        val notificationId = "auto_${rule.id}".hashCode()
        val notification = shadowNotificationManager.getNotification(notificationId)
        assertNotNull(notification)
        assertEquals("Recurring Payment Saved", notification.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(notification.extras.getString(Notification.EXTRA_TEXT)?.contains("Auto-approved: Internet") == true)
        assertEquals(NotificationCompat.PRIORITY_LOW, notification.priority)
    }

    @Test
    fun `showSuspiciousAmountNotification_buildsCorrectly`() {
        // Arrange
        val transaction = Transaction(id = 305, description = "Unknown Merchant", amount = 50000.0, transactionType = TransactionType.EXPENSE, date = 0L, accountId = 1, categoryId = 1, notes = null)
        val expectedUri = "app://finlight.pm.io/transaction_detail/305"
        val reason = "Amount is unusually high"

        // Act
        NotificationHelper.showSuspiciousAmountNotification(context, transaction, reason)

        // Assert
        val notification = shadowNotificationManager.getNotification(305)
        assertNotNull(notification)
        assertEquals("⚠️ Suspicious Amount Detected", notification.extras.getString(Notification.EXTRA_TITLE))

        val contentPI = notification.contentIntent
        val contentIntent = shadowOf(contentPI).savedIntent
        assertEquals(expectedUri, contentIntent.data.toString())

        assertEquals("Review Now", notification.actions[0].title)
    }

    @Test
    fun `showSuspiciousAmountNotification_whenPermissionDenied_doesNotPost`() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val transaction = Transaction(id = 306, description = "Test", amount = 100.0, transactionType = TransactionType.EXPENSE, date = 0L, accountId = 1, categoryId = 1, notes = null)
        NotificationHelper.showSuspiciousAmountNotification(context, transaction, "reason")
        val notification = shadowNotificationManager.getNotification(306)
        assertTrue(notification == null)
    }

    @Test
    fun `showVariableBillAnomalyNotification_whenPermissionDenied_doesNotPost`() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val rule = RecurringTransaction(id = 205, description = "Test", amount = 100.0, transactionType = "expense", recurrenceInterval = "Monthly", startDate = 0L, accountId = 1, categoryId = 1)
        NotificationHelper.showVariableBillAnomalyNotification(context, rule, 150.0, 100.0)
        val notification = shadowNotificationManager.getNotification(205 + 5000)
        assertTrue(notification == null)
    }

    @Test
    fun `showAutoApprovedPaymentNotification_whenPermissionDenied_doesNotPost`() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val rule = RecurringTransaction(id = 206, description = "Test", amount = 100.0, transactionType = "expense", recurrenceInterval = "Monthly", startDate = 0L, accountId = 1, categoryId = 1)
        NotificationHelper.showAutoApprovedPaymentNotification(context, rule)
        val notificationId = "auto_${rule.id}".hashCode()
        val notification = shadowNotificationManager.getNotification(notificationId)
        assertTrue(notification == null)
    }

    @Test
    fun `showGoalSurplusNotification_buildsCorrectly`() {
        // GoalWithAccountName: id, name, targetAmount, savedAmount, targetDate, accountId, accountName, notes, iconEmoji, priority
        val topGoal = io.pm.finlight.GoalWithAccountName(1, "Vacation", 1000.0, 500.0, null, 1, "Savings", null, "🌴", 0)
        NotificationHelper.showGoalSurplusNotification(context, 200.0, topGoal)

        val notificationId = "surplus_nudge".hashCode()
        val notification = shadowNotificationManager.getNotification(notificationId)
        assertNotNull(notification)
        assertEquals("Budget Surplus! 🎉", notification.extras.getString(Notification.EXTRA_TITLE))
        // Check big text contains goal name
        val bigText = notification.extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString()
        assertTrue(bigText?.contains("Vacation") == true)

        val contentPI = notification.contentIntent
        assertNotNull(contentPI)
    }

    @Test
    fun `showGoalSurplusNotification_buildsCorrectly_noActiveGoals`() {
        NotificationHelper.showGoalSurplusNotification(context, 300.0, null)

        val notificationId = "surplus_nudge".hashCode()
        val notification = shadowNotificationManager.getNotification(notificationId)
        assertNotNull(notification)
        assertEquals("Budget Surplus! 🎉", notification.extras.getString(Notification.EXTRA_TITLE))
        // When no goal, big text should mention "staying under budget"
        val bigText = notification.extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString()
        assertTrue(bigText?.contains("staying under budget") == true)
    }

    // --- Goal Milestone Notification Tests ---

    @Test
    fun `showGoalMilestoneNotification_goalReached_postsCorrectNotification`() {
        // Act
        NotificationHelper.showGoalMilestoneNotification(context, "Vacation Fund", 100)

        // Assert
        val notificationId = "milestone_Vacation Fund".hashCode()
        val notification = shadowNotificationManager.getNotification(notificationId)
        assertNotNull(notification)
        assertEquals("Goal Reached! 🥳", notification.extras.getString(Notification.EXTRA_TITLE))
        val text = notification.extras.getString(Notification.EXTRA_TEXT)
        assertTrue(text?.contains("Congratulations") == true)
        assertTrue(text?.contains("Vacation Fund") == true)
    }

    @Test
    fun `showGoalMilestoneNotification_milestoneBelow100_postsCorrectNotification`() {
        // Act
        NotificationHelper.showGoalMilestoneNotification(context, "New Car", 50)

        // Assert
        val notificationId = "milestone_New Car".hashCode()
        val notification = shadowNotificationManager.getNotification(notificationId)
        assertNotNull(notification)
        assertEquals("Milestone Reached! 🚀", notification.extras.getString(Notification.EXTRA_TITLE))
        val text = notification.extras.getString(Notification.EXTRA_TEXT)
        assertTrue(text?.contains("50%") == true)
        assertTrue(text?.contains("New Car") == true)
    }

    @Test
    fun `showGoalMilestoneNotification_whenPermissionDenied_doesNotPost`() {
        // Arrange
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        // Act
        NotificationHelper.showGoalMilestoneNotification(context, "Emergency Fund", 75)

        // Assert
        val notificationId = "milestone_Emergency Fund".hashCode()
        val notification = shadowNotificationManager.getNotification(notificationId)
        assertTrue(notification == null)
    }

    // --- Auto-Save Confirmation Notification Tests ---

    @Test
    fun `showAutoSaveConfirmationNotification_postsCorrectNotification`() {
        // Arrange
        val txn =
            Transaction(
                id = 42,
                description = "Swiggy",
                amount = 250.0,
                transactionType = TransactionType.EXPENSE,
                date = 0L,
                accountId = 1,
                categoryId = null,
                notes = null,
            )

        // Act
        NotificationHelper.showAutoSaveConfirmationNotification(context, txn)

        // Assert
        val notification = shadowNotificationManager.getNotification(42)
        assertNotNull(notification)
        assertEquals("Transaction Auto-Saved", notification.extras.getString(Notification.EXTRA_TITLE))
        val text = notification.extras.getString(Notification.EXTRA_TEXT)
        assertTrue(text?.contains("Swiggy") == true)
        assertTrue(text?.contains("250.00") == true)
    }

    @Test
    fun `showAutoSaveConfirmationNotification_whenPermissionDenied_doesNotPost`() {
        // Arrange
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val txn =
            Transaction(
                id = 43,
                description = "Test",
                amount = 100.0,
                transactionType = TransactionType.EXPENSE,
                date = 0L,
                accountId = 1,
                categoryId = null,
                notes = null,
            )

        // Act
        NotificationHelper.showAutoSaveConfirmationNotification(context, txn)

        // Assert
        assertTrue(shadowNotificationManager.getNotification(43) == null)
    }

    // --- Show Transaction Notification Tests ---

    @Test
    fun `showTransactionNotification_postsCorrectNotification`() {
        // Arrange
        val txn =
            Transaction(
                id = 55,
                description = "Amazon",
                amount = 999.0,
                transactionType = TransactionType.EXPENSE,
                date = 0L,
                accountId = 1,
                categoryId = null,
                notes = null,
            )

        // Act
        NotificationHelper.showTransactionNotification(context, txn)

        // Assert
        val notification = shadowNotificationManager.getNotification(55)
        assertNotNull(notification)
        assertEquals("New Transaction Found", notification.extras.getString(Notification.EXTRA_TITLE))
        val bigText = notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        assertTrue(bigText?.contains("Amazon") == true)
        assertTrue(bigText?.contains("999.00") == true)
    }

    @Test
    fun `showTransactionNotification_whenPermissionDenied_doesNotPost`() {
        // Arrange
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val txn =
            Transaction(
                id = 56,
                description = "Zomato",
                amount = 300.0,
                transactionType = TransactionType.EXPENSE,
                date = 0L,
                accountId = 1,
                categoryId = null,
                notes = null,
            )

        // Act
        NotificationHelper.showTransactionNotification(context, txn)

        // Assert
        assertTrue(shadowNotificationManager.getNotification(56) == null)
    }

    // --- NEW: Smart Transaction Merge Notification Tests ---
    @Test
    fun `showMergeTransactionNotification_buildsCorrectly`() {
        val childTxn = Transaction(id = 10, description = "Amazon", amount = 50.0, transactionType = TransactionType.EXPENSE, date = 0L, accountId = 1, categoryId = 1, notes = null)
        val parentTxn = Transaction(id = 9, description = "Amazon", amount = 100.0, transactionType = TransactionType.EXPENSE, date = 0L, accountId = 1, categoryId = 1, notes = null)

        NotificationHelper.showMergeTransactionNotification(context, childTxn, parentTxn)

        val notificationId = 10010
        val notification = shadowNotificationManager.getNotification(notificationId)
        assertNotNull(notification)
        assertEquals("Merge Transactions?", notification.extras.getString(Notification.EXTRA_TITLE))

        assertEquals(2, notification.actions.size)
        assertEquals("Merge", notification.actions[0].title)
        assertEquals("Dismiss", notification.actions[1].title)

        val contentPI = notification.contentIntent
        assertNotNull("Content intent should be set", contentPI)
        val contentIntent = shadowOf(contentPI).savedIntent
        assertEquals("app://finlight.pm.io/transaction_detail/10", contentIntent.data.toString())
    }

    @Test
    fun `showTransactionNotification_buildsAndPostsCorrectlyForExpenseAndIncome`() {
        val expenseTxn = Transaction(id = 501, description = "Mystery Merchant", amount = 120.0, transactionType = TransactionType.EXPENSE, date = 0L, accountId = 1, categoryId = 1, notes = null)
        NotificationHelper.showTransactionNotification(context, expenseTxn)

        val expenseNotification = shadowNotificationManager.getNotification(501)
        assertNotNull(expenseNotification)
        assertEquals("New Transaction Found", expenseNotification.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(expenseNotification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString().contains("Expense of ₹120.00 from Mystery Merchant detected"))

        val incomeTxn = Transaction(id = 502, description = "Mystery Payer", amount = 2500.0, transactionType = TransactionType.INCOME, date = 0L, accountId = 1, categoryId = 1, notes = null)
        NotificationHelper.showTransactionNotification(context, incomeTxn)

        val incomeNotification = shadowNotificationManager.getNotification(502)
        assertNotNull(incomeNotification)
        assertEquals("New Transaction Found", incomeNotification.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(incomeNotification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString().contains("Income of ₹2500.00 from Mystery Payer detected"))
    }

    @Test
    fun `showRichTransactionNotification_withTransferType`() {
        val details =
            TransactionDetails(
                Transaction(id = 601, description = "Transfer to Wallet", amount = 500.0, transactionType = TransactionType.TRANSFER, date = 0L, accountId = 1, categoryId = 1, notes = null),
                emptyList(),
                "Bank",
                "Transfer",
                "icon",
                "color",
                null,
            )
        NotificationHelper.showRichTransactionNotification(context, details, 1500.0, 1)

        val notification = shadowNotificationManager.getNotification(601)
        assertNotNull(notification)
        val lines = notification.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        assertNotNull(lines)
        assertTrue(lines!!.any { it.contains("spent this month") })
        assertTrue(lines.any { it.contains("first visit here") })
    }
}
