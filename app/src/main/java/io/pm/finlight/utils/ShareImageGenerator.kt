// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/utils/ShareImageGenerator.kt
// REASON: FIX - Resolved a runtime crash by attaching the ComposeView to the
// Activity's window before rendering.
// FEATURE - The snapshot content now correctly renders transaction tags.
// UX REFINEMENT - Implemented a weighted column layout for the generated image
// to provide better spacing and prevent awkward text wrapping, especially for
// columns with variable content length like Description and Category.
// FIX - Replaced `intent.setType()` with the correct Kotlin property access
// syntax: `intent.apply { type = ... }`.
// FIX (Layout) - The composable now iterates over an ordered list of fields
// instead of an unordered set. This guarantees a stable and logical column
// order in the generated image, resolving the issue where columns like
// "Amount" and "Category" appeared jumbled.
// =================================================================================
package io.pm.finlight.utils // <-- UPDATED PACKAGE

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import io.pm.finlight.BuildConfig
import io.pm.finlight.Tag
import io.pm.finlight.TransactionDetails
import io.pm.finlight.ui.components.ShareableField
import io.pm.finlight.ui.theme.PersonalFinanceAppTheme
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

object ShareImageGenerator {

    /**
     * A data class to bundle a transaction with its associated tags for rendering.
     */
    data class TransactionSnapshotData(
        val details: TransactionDetails,
        val tags: List<Tag>
    )

    /**
     * Renders the TransactionSnapshotContent composable to a Bitmap.
     */
    private fun createBitmapFromComposable(
        context: Context,
        transactionsWithData: List<TransactionSnapshotData>,
        fields: Set<ShareableField>
    ): Bitmap {
        val activity = context as? Activity
            ?: throw IllegalArgumentException("A valid Activity context is required to generate an image.")

        val root = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)

        val composeView = ComposeView(context).apply {
            setContent {
                PersonalFinanceAppTheme {
                    TransactionSnapshotContent(transactionsWithData = transactionsWithData, fields = fields)
                }
            }
        }

        val container = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            addView(composeView)
        }

        try {
            root.addView(container)
            container.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            container.layout(0, 0, container.measuredWidth, container.measuredHeight)

            val bitmap = Bitmap.createBitmap(container.measuredWidth, container.measuredHeight, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            container.draw(canvas)
            return bitmap
        } finally {
            root.removeView(container)
        }
    }

    /**
     * Saves the bitmap to a file and triggers the Android share sheet.
     */
    fun shareTransactionsAsImage(
        context: Context,
        transactionsWithData: List<TransactionSnapshotData>,
        fields: Set<ShareableField>
    ) {
        val bitmap = createBitmapFromComposable(context, transactionsWithData, fields)

        val cachePath = File(context.cacheDir, "images")
        cachePath.mkdirs()
        val file = File(cachePath, "transaction_snapshot.png")
        val fileOutputStream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream)
        fileOutputStream.close()

        val contentUri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.provider", file)

        // --- FIX: Use Kotlin property access syntax for 'type' ---
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Transactions"))
    }
}

/**
 * Determines the appropriate layout weight for each shareable field.
 */
private fun getFieldWeight(field: ShareableField): Float {
    return when (field) {
        ShareableField.Description -> 2.5f
        ShareableField.Tags -> 1.8f
        ShareableField.Category -> 1.5f
        ShareableField.Date -> 1.2f
        ShareableField.Amount -> 1.0f
    }
}

/**
 * The Composable that defines the layout of the shareable image.
 */
@Composable
private fun TransactionSnapshotContent(
    transactionsWithData: List<ShareImageGenerator.TransactionSnapshotData>,
    fields: Set<ShareableField>
) {
    val totalAmount = transactionsWithData.sumOf {
        if (it.details.transaction.transactionType == "income") it.details.transaction.amount else -it.details.transaction.amount
    }
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val dateFormat = remember { SimpleDateFormat("dd MMM, yyyy", Locale.getDefault()) }

    // --- FIX: Create an ordered list of fields to display ---
    val orderedFields = remember(fields) {
        ShareableField.entries.filter { it in fields }
    }

    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
            .width(IntrinsicSize.Max)
    ) {
        Text(
            "Transaction Summary",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "Generated by Finlight",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // --- UPDATED: Iterate over the ordered list ---
            orderedFields.forEach { field ->
                Text(
                    text = field.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(getFieldWeight(field)),
                    textAlign = if (field == ShareableField.Amount) TextAlign.End else TextAlign.Start
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        transactionsWithData.forEach { data ->
            val details = data.details
            val tags = data.tags
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // --- UPDATED: Iterate over the ordered list ---
                orderedFields.forEach { field ->
                    val text = when (field) {
                        ShareableField.Date -> dateFormat.format(Date(details.transaction.date))
                        ShareableField.Description -> details.transaction.description
                        ShareableField.Amount -> currencyFormat.format(details.transaction.amount)
                        ShareableField.Category -> details.categoryName ?: "N/A"
                        ShareableField.Tags -> tags.joinToString(", ") { it.name }.ifEmpty { "-" }
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(getFieldWeight(field)),
                        textAlign = if (field == ShareableField.Amount) TextAlign.End else TextAlign.Start,
                        fontSize = 12.sp
                    )
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                "Total:",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 16.dp)
            )
            Text(
                currencyFormat.format(totalAmount),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}