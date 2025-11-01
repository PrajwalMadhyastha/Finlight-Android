// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/utils/NotificationHelper.kt
// REASON: REFACTOR - The `showDailyReportNotification` function has been
// simplified. It no longer contains complex logic to build a title string from a
// percentage. Instead, it now accepts a pre-constructed, intelligent title
// directly from the `DailyReportWorker`, making this helper cleaner and more
// focused on its presentation role.
//
// REASON: FEATURE (Backup) - The `showAutoBackupNotification` function now
// accepts a timestamp and includes it in the notification body for clarity.
// =================================================================================
package io.pm.finlight.utils

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.google.gson.Gson
import io.pm.finlight.*
import io.pm.finlight.data.model.TimePeriod
import java.net.URLEncoder
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import androidx.core.graphics.createBitmap

object NotificationHelper {
    private const val DEEP_LINK_URI_EDIT = "app://finlight.pm.io/transaction_detail"
    private const val DEEP_LINK_URI_REPORT_BASE = "app://finlight.pm.io/report"
    private const val DEEP_LINK_URI_LINK_RECURRING = "app://finlight.pm.io/link_recurring"
    private const val DEEP_LINK_URI_ADD_RECURRING = "app://finlight.pm.io/add_recurring_transaction"
    private const val DEEP_LINK_URI_APPROVE = "app://finlight.pm.io/approve_transaction_screen"
    const val BACKUP_NOTIFICATION_ID = 99


    fun showAutoBackupNotification(context: Context, backupTimestamp: Long) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        // --- NEW: Format the timestamp ---
        val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        val formattedTime = sdf.format(Date(backupTimestamp))
        val contentText = "Your Finlight data was successfully backed up at $formattedTime."


        val builder = NotificationCompat.Builder(context, MainApplication.BACKUP_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle("Backup Complete")
            // --- UPDATED: Use the new content text ---
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            notify(BACKUP_NOTIFICATION_ID, builder.build())
        }
    }


    fun showTravelModeSmsNotification(
        context: Context,
        potentialTxn: PotentialTransaction,
        travelSettings: TravelModeSettings
    ) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val homeCurrencySymbol = CurrencyHelper.getCurrencySymbol("INR")
        val foreignCurrencyCode = travelSettings.currencyCode
        val contentTitle = "Transaction while traveling?"
        val contentText = "Was this transaction in $foreignCurrencyCode or $homeCurrencySymbol?"

        val foreignTxn = potentialTxn.copy(isForeignCurrency = true)
        val foreignJson = URLEncoder.encode(Gson().toJson(foreignTxn), "UTF-8")
        val foreignIntent = Intent(
            Intent.ACTION_VIEW,
            "$DEEP_LINK_URI_APPROVE?potentialTxnJson=$foreignJson".toUri()
        ).apply {
            `package` = context.packageName
        }
        val foreignPendingIntent: PendingIntent? = TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(foreignIntent)
            getPendingIntent(
                potentialTxn.sourceSmsId.toInt() + 1,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val homeTxn = potentialTxn.copy(isForeignCurrency = false)
        val homeJson = URLEncoder.encode(Gson().toJson(homeTxn), "UTF-8")
        val homeIntent = Intent(
            Intent.ACTION_VIEW,
            "$DEEP_LINK_URI_APPROVE?potentialTxnJson=$homeJson".toUri()
        ).apply {
            `package` = context.packageName
        }
        val homePendingIntent: PendingIntent? = TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(homeIntent)
            getPendingIntent(
                potentialTxn.sourceSmsId.toInt() + 2,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val builder = NotificationCompat.Builder(context, MainApplication.TRANSACTION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(0, "It was in $foreignCurrencyCode", foreignPendingIntent)
            .addAction(0, "It was in $homeCurrencySymbol", homePendingIntent)

        with(NotificationManagerCompat.from(context)) {
            notify(potentialTxn.sourceSmsId.toInt(), builder.build())
        }
    }


    fun showRichTransactionNotification(
        context: Context,
        details: TransactionDetails,
        monthlyTotal: Double,
        visitCount: Int
    ) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val intent = Intent(
            Intent.ACTION_VIEW,
            "$DEEP_LINK_URI_EDIT/${details.transaction.id}".toUri()
        ).apply {
            `package` = context.packageName
        }
        val pendingIntent: PendingIntent? = TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(intent)
            getPendingIntent(
                details.transaction.id,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        val amountStr = currencyFormat.format(details.transaction.amount)
        val title = "Finlight · ${details.accountName}"
        val contentText = "$amountStr at ${details.transaction.description}"

        val categoryBitmap = createCategoryIconBitmap(context, details)

        // --- NEW: List of emojis for personalization ---
        val emojis = listOf("🛍️", "✨", "🎉", "👍", "💸", "💳", "🛒", "✅", "💯", "🤩", "🚀", "🙌", "🔥")
        val randomEmoji = emojis.random()

        val inboxStyle = NotificationCompat.InboxStyle()
            // --- MODIFIED: Use the contentText for the big title ---
            .setBigContentTitle(contentText)
            .setSummaryText(details.categoryName ?: "Uncategorized")

        val totalLabel =
            if (details.transaction.transactionType == "income") "income this month" else "spent this month"
        inboxStyle.addLine("${currencyFormat.format(monthlyTotal)} $totalLabel")

        if (visitCount > 0) {
            val visitText = when (visitCount) {
                1 -> "This is your first visit here."
                2 -> "This is your 2nd visit here."
                3 -> "This is your 3rd visit here."
                else -> "This is your ${visitCount}th visit here."
            }
            // --- MODIFIED: Append the random emoji to the visit count line ---
            inboxStyle.addLine("$visitText $randomEmoji")
        }

        val builder =
            NotificationCompat.Builder(context, MainApplication.RICH_TRANSACTION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_logo)
                .setContentTitle(title)
                .setContentText(contentText)
                .setLargeIcon(categoryBitmap)
                .setPriority(NotificationCompat.PRIORITY_HIGH) // Retained as requested
                .setContentIntent(pendingIntent)
                .setStyle(inboxStyle)
                .setAutoCancel(true)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())
                .addAction(0, "View Details", pendingIntent)

        with(NotificationManagerCompat.from(context)) {
            notify(details.transaction.id, builder.build())
        }
    }

    @DrawableRes
    private fun getFallbackDrawableRes(iconKey: String?): Int {
        return when (iconKey) {
            "restaurant", "fastfood" -> R.drawable.ic_restaurant
            "shopping_cart", "shopping_bag" -> R.drawable.ic_shopping_cart
            "receipt_long", "schedule" -> R.drawable.ic_receipt_long
            "local_gas_station" -> R.drawable.ic_local_gas_station
            "travel_explore" -> R.drawable.ic_travel_explore
            "work", "business" -> R.drawable.ic_work
            "favorite", "fitness_center" -> R.drawable.ic_favorite
            "school" -> R.drawable.ic_school
            "directions_car" -> R.drawable.ic_directions_car
            "home", "house" -> R.drawable.ic_home
            "shield" -> R.drawable.ic_shield
            "star" -> R.drawable.ic_star
            "swap_horiz" -> R.drawable.ic_swap_horiz
            "trending_up" -> R.drawable.ic_trending_up
            "redo" -> R.drawable.ic_redo
            "add_card" -> R.drawable.ic_add_card
            "two_wheeler" -> R.drawable.ic_two_wheeler
            "credit_score" -> R.drawable.ic_credit_score
            "people" -> R.drawable.ic_people
            "group" -> R.drawable.ic_group
            "card_giftcard" -> R.drawable.ic_card_giftcard
            "pets" -> R.drawable.ic_pets
            "account_balance" -> R.drawable.ic_account_balance
            "more_horiz" -> R.drawable.ic_more_horiz
            else -> R.drawable.ic_help_outline
        }
    }


    private fun createCategoryIconBitmap(context: Context, details: TransactionDetails): Bitmap {
        val width = 128
        val height = (width * 1.2).toInt()
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)

        val colorKey = details.categoryColorKey ?: "gray_light"
        val backgroundColor = CategoryIconHelper.getIconBackgroundColor(colorKey).toArgb()
        val backgroundPaint = Paint().apply {
            color = backgroundColor
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawCircle(width / 2f, height / 2f, width / 2f, backgroundPaint)

        val iconKey = details.categoryIconKey

        if (iconKey != null && iconKey != "letter_default" && iconKey != "category") {
            val drawableResId = getFallbackDrawableRes(iconKey)
            val iconDrawable = ContextCompat.getDrawable(context, drawableResId)

            if (iconDrawable == null) {
                Log.e(
                    "NotificationHelper",
                    "Failed to load drawable for key '$iconKey' (Res ID: $drawableResId)"
                )
            }

            val iconSize = (width * 0.6).toInt()
            val iconLeft = (width - iconSize) / 2
            val iconTop = (height - iconSize) / 2
            iconDrawable?.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
            iconDrawable?.setTint(android.graphics.Color.BLACK)
            iconDrawable?.draw(canvas)
        } else {
            val categoryName = details.categoryName ?: "Other"
            val text = categoryName.firstOrNull()?.uppercase() ?: "?"
            val textPaint = Paint().apply {
                color = android.graphics.Color.BLACK
                textSize = width * 0.5f
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT,
                    android.graphics.Typeface.BOLD
                )
            }
            val textY = (height / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
            canvas.drawText(text, width / 2f, textY, textPaint)
        }

        return bitmap
    }


    fun showRecurringPatternDetectedNotification(
        context: Context,
        rule: RecurringTransaction
    ) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val deepLinkUri = "$DEEP_LINK_URI_ADD_RECURRING?ruleId=${rule.id}".toUri()

        val intent = Intent(Intent.ACTION_VIEW, deepLinkUri).apply {
            `package` = context.packageName
        }

        val notificationId = "pattern_${rule.id}".hashCode()
        val pendingIntent: PendingIntent? = TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(intent)
            getPendingIntent(
                notificationId,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val contentText =
            "We noticed a recurring ${rule.transactionType} for '${rule.description}'. We've created a rule for you. Tap to review."

        val builder = NotificationCompat.Builder(context, MainApplication.TRANSACTION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle("New Recurring Transaction Found")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(0, "Review Rule", pendingIntent)

        with(NotificationManagerCompat.from(context)) {
            notify(notificationId, builder.build())
        }
    }


    fun showRecurringTransactionDueNotification(
        context: Context,
        potentialTxn: PotentialTransaction
    ) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val json = Gson().toJson(potentialTxn)
        val encodedJson = URLEncoder.encode(json, "UTF-8")
        val deepLinkUri = "$DEEP_LINK_URI_LINK_RECURRING/$encodedJson".toUri()

        val intent = Intent(Intent.ACTION_VIEW, deepLinkUri).apply {
            `package` = context.packageName
        }

        val pendingIntent: PendingIntent? = TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(intent)
            getPendingIntent(
                potentialTxn.sourceSmsId.toInt(),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        val contentText =
            "Your payment of ${currencyFormat.format(potentialTxn.amount)} for ${potentialTxn.merchantName} is due. Tap to confirm."

        val builder = NotificationCompat.Builder(context, MainApplication.TRANSACTION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle("Recurring Payment Due")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(0, "Confirm Payment", pendingIntent)

        with(NotificationManagerCompat.from(context)) {
            notify(potentialTxn.sourceSmsId.toInt(), builder.build())
        }
    }


    private fun createEnhancedSummaryNotification(
        context: Context,
        channelId: String,
        notificationId: Int,
        title: String,
        totalExpenses: Double,
        topCategories: List<CategorySpending>,
        deepLinkUri: String
    ) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val intent = Intent(Intent.ACTION_VIEW, deepLinkUri.toUri())
        intent.`package` = context.packageName

        val pendingIntent: PendingIntent? = TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(intent)
            getPendingIntent(
                notificationId,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        val bigContentText = "You spent ${currencyFormat.format(totalExpenses)} in total."

        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle(title)
            .setSummaryText("Tap to review the details")

        if (topCategories.isNotEmpty()) {
            inboxStyle.addLine("Top spends:")
            for (category in topCategories.take(3)) { // Limit to top 3 for clarity
                inboxStyle.addLine("• ${category.categoryName}: ${currencyFormat.format(category.totalAmount)}")
            }
        } else {
            inboxStyle.addLine("No expenses recorded for this period.")
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle(title)
            .setContentText(bigContentText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setStyle(inboxStyle)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_view, "Review", pendingIntent)

        with(NotificationManagerCompat.from(context)) {
            notify(notificationId, builder.build())
        }
    }


    fun showDailyReportNotification(
        context: Context,
        title: String, // --- UPDATED: Receive the intelligent title directly
        totalExpenses: Double,
        topCategories: List<CategorySpending>,
        dateMillis: Long
    ) {
        val deepLinkUri = "$DEEP_LINK_URI_REPORT_BASE/${TimePeriod.DAILY}?date=$dateMillis"

        createEnhancedSummaryNotification(
            context,
            MainApplication.DAILY_REPORT_CHANNEL_ID,
            2,
            title,
            totalExpenses,
            topCategories,
            deepLinkUri
        )
    }

    fun showWeeklySummaryNotification(
        context: Context,
        totalExpenses: Double,
        percentageChange: Int?,
        topCategories: List<CategorySpending>
    ) {
        val title = when {
            percentageChange == null -> "Your Weekly Summary"
            percentageChange == 0 -> "Spends same as last week"
            percentageChange > 0 -> "Spends up by $percentageChange% this week"
            else -> "Spends down by ${abs(percentageChange)}% this week"
        }
        createEnhancedSummaryNotification(
            context,
            MainApplication.SUMMARY_CHANNEL_ID,
            3,
            title,
            totalExpenses,
            topCategories,
            "$DEEP_LINK_URI_REPORT_BASE/${TimePeriod.WEEKLY}"
        )
    }

    fun showMonthlySummaryNotification(
        context: Context,
        calendar: Calendar,
        totalExpenses: Double,
        percentageChange: Int?,
        topCategories: List<CategorySpending>
    ) {
        val monthName =
            calendar.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault()) ?: "Month"
        val title = when {
            percentageChange == null -> "Your $monthName Summary"
            percentageChange == 0 -> "Spends same as last month"
            percentageChange > 0 -> "Spends up by $percentageChange% in $monthName"
            else -> "Spends down by ${abs(percentageChange)}% in $monthName"
        }
        val deepLinkUri = "$DEEP_LINK_URI_REPORT_BASE/${TimePeriod.MONTHLY}?showPreviousMonth=true"

        createEnhancedSummaryNotification(
            context,
            MainApplication.MONTHLY_SUMMARY_CHANNEL_ID,
            4,
            title,
            totalExpenses,
            topCategories,
            deepLinkUri
        )
    }


    fun showAutoSaveConfirmationNotification(
        context: Context,
        transaction: Transaction
    ) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val detailIntent = Intent(
            Intent.ACTION_VIEW,
            "$DEEP_LINK_URI_EDIT/${transaction.id}".toUri(),
            context,
            MainActivity::class.java
        )

        val pendingIntent: PendingIntent? = TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(detailIntent)
            getPendingIntent(
                transaction.id,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val groupKey = "finlight_transaction_group_${transaction.id}"

        val builder = NotificationCompat.Builder(context, MainApplication.TRANSACTION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle("Transaction Auto-Saved")
            .setContentText("Saved ${transaction.description} (₹${"%.2f".format(transaction.amount)}). Tap to edit or categorize.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup(groupKey)
            .addAction(android.R.drawable.ic_menu_edit, "Edit", pendingIntent)

        with(NotificationManagerCompat.from(context)) {
            notify(transaction.id, builder.build())
        }
    }

    fun showTransactionNotification(
        context: Context,
        transaction: Transaction,
    ) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val detailUri = "$DEEP_LINK_URI_EDIT/${transaction.id}".toUri()

        val intent = Intent(Intent.ACTION_VIEW, detailUri).apply {
            `package` = context.packageName
        }

        val pendingIntent: PendingIntent? = TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(intent)
            getPendingIntent(
                transaction.id,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val typeText = transaction.transactionType.replaceFirstChar { it.uppercase() }
        val bigText =
            "$typeText of ₹${"%.2f".format(transaction.amount)} from ${transaction.description} detected. Tap to review and categorize."

        val builder = NotificationCompat.Builder(context, MainApplication.TRANSACTION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle("New Transaction Found")
            .setContentText("Tap to review a transaction from ${transaction.description}.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_notification_logo, "Review & Categorize", pendingIntent)

        with(NotificationManagerCompat.from(context)) {
            notify(transaction.id, builder.build())
        }
    }
}
