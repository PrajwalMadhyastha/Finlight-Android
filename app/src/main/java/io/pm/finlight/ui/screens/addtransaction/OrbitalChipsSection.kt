package io.pm.finlight.ui.screens.addtransaction

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.NewLabel
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.pm.finlight.Account
import io.pm.finlight.Category
import io.pm.finlight.Tag
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.utils.CategoryIconHelper
import io.pm.finlight.utils.FormatUtils
import java.util.Date
import java.util.Locale

@Composable
fun OrbitalChipsSection(
    selectedCategory: Category?,
    selectedAccount: Account?,
    selectedDateTime: Date,
    notes: String,
    tags: Set<Tag>,
    imageCount: Int,
    onCategoryClick: () -> Unit,
    onAccountClick: () -> Unit,
    onDateClick: () -> Unit,
    onNotesClick: () -> Unit,
    onTagsClick: () -> Unit,
    onAttachmentClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OrbitalChips(
            selectedCategory = selectedCategory,
            selectedAccount = selectedAccount,
            selectedDateTime = selectedDateTime,
            onCategoryClick = onCategoryClick,
            onAccountClick = onAccountClick,
            onDateClick = onDateClick,
        )
        Spacer(Modifier.height(24.dp))
        ActionRow(
            notes = notes,
            tags = tags,
            imageCount = imageCount,
            onNotesClick = onNotesClick,
            onTagsClick = onTagsClick,
            onAttachmentClick = onAttachmentClick,
        )
    }
}

@Composable
fun OrbitalChips(
    selectedCategory: Category?,
    selectedAccount: Account?,
    selectedDateTime: Date,
    onCategoryClick: () -> Unit,
    onAccountClick: () -> Unit,
    onDateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DetailChip(
            icon = selectedCategory?.let { CategoryIconHelper.getIcon(it.iconKey) } ?: Icons.Default.Category,
            text = selectedCategory?.name ?: "Category",
            onClick = onCategoryClick,
            modifier = Modifier.testTag("category_select_chip"),
        )
        DetailChip(
            icon = Icons.Default.AccountBalanceWallet,
            text = selectedAccount?.name ?: "Account",
            onClick = onAccountClick,
            modifier = Modifier.testTag("account_select_chip"),
        )
        DetailChip(
            icon = Icons.Default.CalendarToday,
            text = FormatUtils.getFormatter("dd MMM", Locale.getDefault()).format(selectedDateTime),
            onClick = onDateClick,
            modifier = Modifier.testTag("date_select_chip"),
        )
    }
}

@Composable
fun DetailChip(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPanel(
        modifier =
            modifier
                .fillMaxWidth(0.8f)
                .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun ActionRow(
    notes: String,
    tags: Set<Tag>,
    imageCount: Int,
    onNotesClick: () -> Unit,
    onTagsClick: () -> Unit,
    onAttachmentClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
    ) {
        ActionIcon(
            icon = Icons.AutoMirrored.Filled.Notes,
            text = "Notes",
            isHighlighted = notes.isNotBlank(),
            onClick = onNotesClick,
        )
        ActionIcon(
            icon = Icons.Default.NewLabel,
            text = "Tags",
            isHighlighted = tags.isNotEmpty(),
            onClick = onTagsClick,
        )
        ActionIcon(
            icon = Icons.Default.Attachment,
            text = "Attach",
            isHighlighted = imageCount > 0,
            badgeCount = imageCount,
            onClick = onAttachmentClick,
        )
    }
}

@Composable
fun ActionIcon(
    icon: ImageVector,
    text: String,
    isHighlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0,
) {
    val color = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        BadgedBox(badge = {
            if (badgeCount > 0) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) { Text("$badgeCount") }
            }
        }) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = color,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}
