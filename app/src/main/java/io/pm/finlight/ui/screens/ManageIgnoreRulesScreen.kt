// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/screens/ManageIgnoreRulesScreen.kt
// REASON: FEATURE - The UI is now capable of managing both sender and body-based
// ignore rules. A segmented button has been added to allow the user to select
// the rule type. The list items now display the rule type and pattern, providing
// clear context for each rule.
// FIX (UI) - Removed the local Scaffold and TopAppBar from this screen. The main
// NavHost now provides a centralized TopAppBar, and this change removes the
// duplicate, resolving a UI bug.
// =================================================================================
package io.pm.finlight.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.pm.finlight.IgnoreRule
import io.pm.finlight.ManageIgnoreRulesViewModel
import io.pm.finlight.RuleType
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.components.HelpActionIcon
import io.pm.finlight.ui.theme.PopupSurfaceDark
import io.pm.finlight.ui.theme.PopupSurfaceLight

// Helper function to determine if a color is 'dark' based on luminance.
private fun Color.isDark() = (red * 0.299 + green * 0.587 + blue * 0.114) < 0.5

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ManageIgnoreRulesScreen(
    navController: NavController, // Added NavController
    viewModel: ManageIgnoreRulesViewModel = viewModel()
) {
    val rules by viewModel.allRules.collectAsState()
    var newPattern by remember { mutableStateOf("") }
    var ruleToDelete by remember { mutableStateOf<IgnoreRule?>(null) }
    var selectedRuleType by remember { mutableStateOf(RuleType.BODY_PHRASE) }

    val (defaultRules, customRules) = rules.partition { it.isDefault }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Ignore SMS messages based on the sender's name or phrases in the message body. Use '*' as a wildcard for sender patterns.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            GlassPanel {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = selectedRuleType == RuleType.BODY_PHRASE,
                            onClick = { selectedRuleType = RuleType.BODY_PHRASE },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) {
                            Text("Body Phrase")
                        }
                        SegmentedButton(
                            selected = selectedRuleType == RuleType.SENDER,
                            onClick = { selectedRuleType = RuleType.SENDER },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) {
                            Text("Sender")
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newPattern,
                            onValueChange = { newPattern = it },
                            label = { Text(if (selectedRuleType == RuleType.BODY_PHRASE) "Phrase to ignore" else "Sender pattern to ignore") },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                cursorColor = MaterialTheme.colorScheme.primary,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            )
                        )
                        Button(
                            onClick = {
                                viewModel.addIgnoreRule(newPattern, selectedRuleType)
                                newPattern = "" // Clear input
                            },
                            enabled = newPattern.isNotBlank()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Rule")
                        }
                    }
                }
            }
        }

        if (customRules.isNotEmpty()) {
            item {
                Text(
                    "Your Custom Rules",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(customRules, key = { "custom-${it.id}" }) { rule ->
                GlassPanel(modifier = Modifier.animateItemPlacement()) {
                    ListItem(
                        headlineContent = { Text(rule.pattern, color = MaterialTheme.colorScheme.onSurface) },
                        supportingContent = {
                            Text(
                                text = if (rule.type == RuleType.SENDER) "Sender Rule" else "Body Phrase Rule",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { ruleToDelete = rule }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete rule",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }

        if (defaultRules.isNotEmpty()) {
            item {
                Text(
                    "Default App Rules",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(defaultRules, key = { "default-${it.id}" }) { rule ->
                GlassPanel(modifier = Modifier.animateItemPlacement()) {
                    ListItem(
                        headlineContent = { Text(rule.pattern, color = MaterialTheme.colorScheme.onSurface) },
                        supportingContent = {
                            Text(
                                text = if (rule.type == RuleType.SENDER) "Sender Rule" else "Body Phrase Rule",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = rule.isEnabled,
                                onCheckedChange = { isEnabled ->
                                    viewModel.updateIgnoreRule(rule.copy(isEnabled = isEnabled))
                                }
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }


    if (ruleToDelete != null) {
        val isThemeDark = MaterialTheme.colorScheme.background.isDark()
        val popupContainerColor = if (isThemeDark) PopupSurfaceDark else PopupSurfaceLight

        AlertDialog(
            onDismissRequest = { ruleToDelete = null },
            title = { Text("Delete Ignore Rule?") },
            text = { Text("Are you sure you want to delete the rule for \"${ruleToDelete!!.pattern}\"?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteIgnoreRule(ruleToDelete!!)
                        ruleToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { ruleToDelete = null }) { Text("Cancel") }
            },
            containerColor = popupContainerColor
        )
    }
}