package io.pm.finlight.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.pm.finlight.R

/**
 * A data class to hold the title, content (with Markdown support), and an optional
 * visual aid for a single help topic.
 *
 * @param title The title to be displayed at the top of the help sheet.
 * @param content The main descriptive text. Can contain simple Markdown for formatting.
 * @param visual An optional drawable resource ID for a GIF or static image.
 */
data class HelpInfo(
    val title: String,
    val content: String,
    // @DrawableRes val visual: Int? = null // Temporarily commented out as requested.
)

/**
 * A centralized, scalable registry for all in-app help content.
 * Maps a unique screen/feature key to its corresponding [HelpInfo].
 * This makes adding, editing, and translating help text straightforward
 * without needing to modify individual screen composables.
 */
object HelpContentRegistry {
    val content = mapOf(
        "automation_settings" to HelpInfo(
            title = "About Automation",
            content = """
                This screen controls how Finlight automatically processes your SMS messages.

                - **Scan Inbox:** Manually trigger a scan of your SMS inbox to find new transactions.
                - **Manage Parse Rules:** View and delete the custom rules you've created for parsing tricky SMS formats.
                - **Manage Ignore List:** Tell the parser to permanently ignore messages from certain senders or with specific phrases.
                - **Debug SMS Parsing:** A tool to see exactly why a recent message was parsed or ignored.
            """.trimIndent()
        ),
        "data_settings" to HelpInfo(
            title = "About Security & Data",
            content = """
                Your data's security and portability are managed here.

                - **App Lock:** Secure the app with your device's biometrics (fingerprint/face).
                - **Automatic Daily Backup:** Your data is automatically backed up to your personal Google Drive. This backs up a compressed snapshot, not the live database.
                - **Create Backup Now:** Manually create a new snapshot and request a backup. This is useful before moving to a new device.
                - **Import/Export:** Create a full local backup (JSON) or a spreadsheet-friendly version of your transactions (CSV).
            """.trimIndent()
        ),
        "dashboard_customize" to HelpInfo(
            title = "Customizing Your Dashboard",
            content = """
                Tailor the dashboard to your needs.

                - **Toggle Visibility:** Use the switch to show or hide a card.
                - **Reorder Cards:** Long-press the drag handle (:::) and drag any card (except the main budget card) up or down to change its position.
            """.trimIndent(),
            // visual = R.drawable.help_gif_reorder // Temporarily commented out as requested.
        )
    )
}

/**
 * A themed ModalBottomSheet that displays help content from a [HelpInfo] object.
 *
 * @param info The [HelpInfo] object containing the content to display.
 * @param onDismiss The callback to be invoked when the sheet is dismissed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpBottomSheet(
    info: HelpInfo,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
        windowInsets = WindowInsets(0) // Edge-to-edge
    ) {
        GlassPanel {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(24.dp)
            ) {
                Text(
                    text = info.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                Spacer(Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp) // Limit height to prevent overly tall sheets
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MarkdownText(markdown = info.content)

                    // Temporarily commented out the visual/GIF display logic as requested.
                    /*
                    AnimatedVisibility(visible = info.visual != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Using AsyncImage from Coil to support GIFs in the future
                            AsyncImage(
                                model = info.visual,
                                contentDescription = "${info.title} Visual Aid",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    */
                }
            }
        }
    }
}

/**
 * A reusable help icon button that, when clicked, displays a modal bottom sheet
 * with contextual help content.
 *
 * @param helpKey The unique key used to look up the help content from the [HelpContentRegistry].
 */
@Composable
fun HelpActionIcon(helpKey: String) {
    var showSheet by remember { mutableStateOf(false) }
    val helpInfo = remember(helpKey) { HelpContentRegistry.content[helpKey] }

    IconButton(onClick = { showSheet = true }) {
        Icon(
            imageVector = Icons.Default.HelpOutline,
            contentDescription = "Help"
        )
    }

    if (showSheet && helpInfo != null) {
        HelpBottomSheet(
            info = helpInfo,
            onDismiss = { showSheet = false }
        )
    }
}

/**
 * A simple composable to render basic Markdown text.
 * Supports **bold** text and lines starting with `- ` as bullet points.
 */
@Composable
private fun MarkdownText(markdown: String) {
    val bullet = "•"
    Text(
        buildAnnotatedString {
            markdown.lines().forEach { line ->
                val trimmedLine = line.trim()
                val isListItem = trimmedLine.startsWith("- ")
                val lineWithoutBullet = if (isListItem) trimmedLine.substring(2) else trimmedLine

                if (isListItem) {
                    append("  $bullet  ")
                }

                var startIndex = 0
                val boldRegex = "\\*\\*(.*?)\\*\\*".toRegex()
                boldRegex.findAll(lineWithoutBullet).forEach { matchResult ->
                    val (boldText) = matchResult.destructured
                    val range = matchResult.range

                    // Append text before the bold part
                    append(lineWithoutBullet.substring(startIndex, range.first))

                    // Append bold text
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(boldText)
                    }

                    startIndex = range.last + 1
                }

                // Append any remaining text after the last bold part
                if (startIndex < lineWithoutBullet.length) {
                    append(lineWithoutBullet.substring(startIndex))
                }
                append("\n")
            }
        },
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}