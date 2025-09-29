// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/screens/ProfileScreen.kt
// REASON: FEATURE - Added a new "Travel History" item to the settings list.
// This provides a clear, logical entry point for the user to access the new
// historic trips feature.
// REFACTOR: The navigation for "Travel History" now points to the unified
// "currency_travel_settings" screen, reflecting the feature consolidation.
// REFACTOR: Removed the now-redundant "Travel History" entry point. All travel-
// related functionality is now accessed via the "Currency & Travel" screen.
// CLEANUP - Temporarily removed the entry points for "Savings Goals" and
// "Manage Recurring Rules" to disable these features from the UI pending
// future improvements.
// =================================================================================
package io.pm.finlight.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import io.pm.finlight.*
import io.pm.finlight.R
import io.pm.finlight.ui.components.*
import androidx.compose.ui.unit.dp

@Composable
fun ProfileScreen(
    navController: NavController,
    profileViewModel: ProfileViewModel = viewModel()
) {
    val userName by profileViewModel.userName.collectAsState()
    val savedProfilePictureUri by profileViewModel.profilePictureUri.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            GlassPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController.navigate("edit_profile") }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = savedProfilePictureUri ?: R.mipmap.ic_launcher,
                        contentDescription = "User Profile Picture",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(userName, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text("Edit Profile", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Profile",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            SettingsSection(title = "General") {
                SettingsActionItem(
                    text = "Manage Accounts",
                    subtitle = "View, add, or edit your financial accounts",
                    icon = Icons.Default.AccountBalanceWallet,
                    onClick = { navController.navigate("account_list") },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                SettingsActionItem(
                    text = "Manage Categories",
                    subtitle = "Add, edit, or remove transaction categories",
                    icon = Icons.Default.Category,
                    onClick = { navController.navigate("category_list") },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                SettingsActionItem(
                    text = "Manage Budgets",
                    subtitle = "Set and edit your monthly budgets",
                    icon = Icons.Default.Savings,
                    onClick = { navController.navigate("budget_screen") },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                SettingsActionItem(
                    text = "Manage Tags",
                    subtitle = "Create and organize custom tags",
                    icon = Icons.Default.NewLabel,
                    onClick = { navController.navigate("tag_management") },
                )
            }
        }

        item {
            SettingsSection(title = "Preferences") {
                SettingsActionItem(
                    text = "Theme & Appearance",
                    subtitle = "Change the look and feel of the app",
                    icon = Icons.Default.Palette,
                    onClick = { navController.navigate("appearance_settings") }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                SettingsActionItem(
                    text = "Automation",
                    subtitle = "Manage SMS parsing and rules",
                    icon = Icons.Default.AutoAwesome,
                    onClick = { navController.navigate("automation_settings") }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                SettingsActionItem(
                    text = "Currency & Travel",
                    subtitle = "Manage home currency, trips, and travel history",
                    icon = Icons.Default.Flight,
                    onClick = { navController.navigate("currency_travel_settings") }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                SettingsActionItem(
                    text = "Notifications",
                    subtitle = "Control reminders and summaries",
                    icon = Icons.Default.Notifications,
                    onClick = { navController.navigate("notification_settings") }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                SettingsActionItem(
                    text = "Security & Data",
                    subtitle = "Manage app lock, backups, and import/export",
                    icon = Icons.Default.Security,
                    onClick = { navController.navigate("data_settings") }
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
        )
        GlassPanel {
            Column {
                content()
            }
        }
    }
}