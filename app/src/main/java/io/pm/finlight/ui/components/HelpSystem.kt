package io.pmfinlight.ui.components

import androidx.annotation.DrawableRes
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
    @DrawableRes val visual: Int? = null
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
            visual = R.drawable.help_gif_reorder // Placeholder for now
        )
    )
}