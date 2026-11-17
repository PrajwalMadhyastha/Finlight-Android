package io.pm.finlight.ui.screens.transactiondetail

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NewLabel
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.gson.Gson
import io.pm.finlight.PotentialTransaction
import io.pm.finlight.Tag
import io.pm.finlight.TransactionDetails
import io.pm.finlight.TransactionImage
import io.pm.finlight.TransactionType
import io.pm.finlight.TransactionViewModel
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.components.TransactionTypeToggle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.net.URLEncoder

@Composable
fun TransactionPropertiesCard(
    details: TransactionDetails,
    hasMerged: Boolean = false,
    hasReimbursements: Boolean = false,
    onTypeSelected: (TransactionType) -> Unit,
    onExcludeToggled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPanel(modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                TransactionTypeToggle(
                    selectedType = details.transaction.transactionType,
                    onTypeSelected = onTypeSelected,
                    enabled = !details.transaction.isSplit && !hasMerged && !hasReimbursements,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onExcludeToggled(!details.transaction.isExcluded) }
                        .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Exclude from Totals",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "Will not affect budgets or reports.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Switch(
                    checked = details.transaction.isExcluded,
                    onCheckedChange = onExcludeToggled,
                )
            }
        }
    }
}

@Composable
fun NotesRow(
    details: TransactionDetails,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Notes,
                contentDescription = "Notes",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Notes",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Icon(
                Icons.Default.Edit,
                contentDescription = "Edit",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            details.transaction.notes ?: "Tap to add",
            fontWeight = if (details.transaction.notes.isNullOrBlank()) FontWeight.Normal else FontWeight.SemiBold,
            color = if (details.transaction.notes.isNullOrBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 40.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagsRow(
    selectedTags: Set<Tag>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(Icons.Default.NewLabel, contentDescription = "Tags", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(modifier = Modifier.weight(1f)) {
            Text("Tags", color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            if (selectedTags.isEmpty()) {
                Text("Tap to add", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    selectedTags.forEach { tag ->
                        AssistChip(onClick = {}, label = { Text(tag.name) })
                    }
                }
            }
        }
        Icon(
            Icons.Default.Edit,
            contentDescription = "Edit",
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun TransactionActionsCard(
    images: List<TransactionImage>,
    onAddClick: () -> Unit,
    onViewClick: (Uri) -> Unit,
    onDeleteClick: (TransactionImage) -> Unit,
    sourceSmsId: Long?,
    onFixParsingClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPanel(modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Actions",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ActionItem(
                    icon = Icons.Default.AddAPhoto,
                    label = "Attach",
                    onClick = onAddClick,
                    modifier = Modifier.weight(1f),
                )

                if (sourceSmsId != null) {
                    ActionItem(
                        icon = Icons.Default.Build,
                        label = "Fix Parsing",
                        onClick = onFixParsingClick,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }

            if (images.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

                Text(
                    "Attachments (${images.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(images) { image ->
                        val context = LocalContext.current
                        val imageRequest =
                            remember(image.imageUri) {
                                ImageRequest.Builder(context)
                                    .data(File(image.imageUri))
                                    .size(256, 256)
                                    .crossfade(true)
                                    .build()
                            }
                        Box {
                            AsyncImage(
                                model = imageRequest,
                                contentDescription = "Attachment",
                                contentScale = ContentScale.Crop,
                                modifier =
                                    Modifier
                                        .size(90.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onViewClick(File(image.imageUri).toUri()) }
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                            )
                            IconButton(
                                onClick = { onDeleteClick(image) },
                                modifier =
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(24.dp)
                                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f), CircleShape),
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun OriginalSmsCard(
    originalSms: String,
    modifier: Modifier = Modifier,
) {
    GlassPanel(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Message,
                    contentDescription = "Original SMS",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Original SMS Message",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = originalSms,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp,
            )
        }
    }
}

fun navigateToRuleCreation(
    context: Context,
    navController: NavController,
    viewModel: TransactionViewModel,
    scope: CoroutineScope,
    details: TransactionDetails,
) {
    scope.launch {
        val smsMessage = viewModel.getOriginalSmsMessage(details.transaction.sourceSmsId!!)
        if (smsMessage != null) {
            val potentialTxn =
                PotentialTransaction(
                    sourceSmsId = smsMessage.id,
                    smsSender = smsMessage.sender,
                    amount = details.transaction.amount,
                    transactionType = details.transaction.transactionType.name.lowercase(),
                    merchantName = details.transaction.description,
                    originalMessage = smsMessage.body,
                    sourceSmsHash = details.transaction.sourceSmsHash,
                )
            val json = Gson().toJson(potentialTxn)
            val encodedJson = URLEncoder.encode(json, "UTF-8")
            navController.navigate("rule_creation_screen?potentialTransactionJson=$encodedJson")
        } else {
            Toast.makeText(context, "Original SMS not found.", Toast.LENGTH_SHORT).show()
        }
    }
}
