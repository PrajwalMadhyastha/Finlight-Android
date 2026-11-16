package io.pm.finlight.ui.components.pickers

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.pm.finlight.Account
import io.pm.finlight.isCreditCard
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.components.rememberSafeBankLogoPainter
import java.text.NumberFormat
import java.util.Locale

@Composable
fun AccountPickerBottomSheet(
    accounts: List<Account>,
    selectedAccountId: Int? = null,
    onAccountSelected: (Account) -> Unit,
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
    title: String = "Select Account",
    onAddNew: (() -> Unit)? = null,
    onRenameAccount: ((accountId: Int, newName: String) -> Unit)? = null,
) {
    var editingAccountId by rememberSaveable { mutableStateOf<Int?>(null) }
    var editingName by rememberSaveable { mutableStateOf("") }

    val orderedAccounts =
        remember(accounts, selectedAccountId) {
            if (selectedAccountId != null) {
                val current = accounts.filter { it.id == selectedAccountId }
                val others = accounts.filter { it.id != selectedAccountId }
                current + others
            } else {
                accounts
            }
        }

    Column(
        modifier =
            modifier
                .navigationBarsPadding()
                .fillMaxHeight(),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurface,
        )

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(orderedAccounts, key = { it.id }) { account ->
                val isEditing = editingAccountId == account.id
                val isCurrent = account.id == selectedAccountId
                val focusRequester = remember { FocusRequester() }

                GlassPanel(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp)),
                    backgroundColor =
                        if (isCurrent) {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        } else {
                            null
                        },
                ) {
                    if (isEditing) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = editingName,
                                onValueChange = { editingName = it },
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .focusRequester(focusRequester),
                                singleLine = true,
                                label = { Text("Account Name") },
                            )
                            IconButton(
                                onClick = {
                                    onRenameAccount?.invoke(account.id, editingName)
                                    editingAccountId = null
                                    editingName = ""
                                },
                                enabled = editingName.isNotBlank(),
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Save Name",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            IconButton(onClick = {
                                editingAccountId = null
                                editingName = ""
                            }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Cancel Edit",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        LaunchedEffect(account.id) {
                            focusRequester.requestFocus()
                        }
                    } else {
                        ListItem(
                            headlineContent = {
                                Text(
                                    account.name,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color =
                                        if (isCurrent) {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                )
                            },
                            supportingContent =
                                if (isCurrent || (account.isCreditCard && (account.creditLimit ?: 0.0) > 0.0)) {
                                    {
                                        val currencyFormat =
                                            remember {
                                                NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"))
                                            }
                                        val hasLimit = account.isCreditCard && (account.creditLimit ?: 0.0) > 0.0
                                        when {
                                            isCurrent && hasLimit -> {
                                                Text(
                                                    "Currently Selected • Limit: ${currencyFormat.format(account.creditLimit)}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                )
                                            }
                                            isCurrent -> {
                                                Text(
                                                    "Currently Selected",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                )
                                            }
                                            hasLimit -> {
                                                Text(
                                                    "Limit: ${currencyFormat.format(account.creditLimit)}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    null
                                },
                            leadingContent = {
                                Image(
                                    painter = rememberSafeBankLogoPainter(account.name),
                                    contentDescription = "${account.name} Logo",
                                    modifier = Modifier.size(40.dp),
                                )
                            },
                            trailingContent =
                                if (onRenameAccount != null) {
                                    {
                                        IconButton(onClick = {
                                            editingAccountId = account.id
                                            editingName = account.name
                                        }) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Edit Account Name",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                } else {
                                    null
                                },
                            modifier = Modifier.clickable { onAccountSelected(account) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }

            if (onAddNew != null) {
                item {
                    GlassPanel(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp)),
                    ) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    "Create New Account",
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            },
                            leadingContent = {
                                Icon(
                                    Icons.Default.AddCircleOutline,
                                    contentDescription = "Create New Account",
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            modifier = Modifier.clickable { onAddNew() },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
        }
    }
}
