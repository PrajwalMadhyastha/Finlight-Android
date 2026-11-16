package io.pm.finlight.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.pm.finlight.Category

private fun Modifier.categoryGridItem(
    backgroundColor: Color = Color.Transparent,
    onClick: () -> Unit,
) = this
    .clip(RoundedCornerShape(12.dp))
    .background(backgroundColor)
    .clickable(onClick = onClick)
    .padding(vertical = 12.dp)

@Composable
fun CategorySelectionGrid(
    categories: List<Category>,
    selectedCategoryId: Int? = null,
    onCategorySelected: (Category) -> Unit,
    onAddNew: (() -> Unit)? = null,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 85.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(categories, key = { it.id }) { category ->
            CategoryGridItem(
                category = category,
                isSelected = category.id == selectedCategoryId,
                onSelected = { onCategorySelected(category) },
            )
        }
        if (onAddNew != null) {
            item {
                AddNewCategoryGridItem(onAddNew = onAddNew)
            }
        }
    }
}

@Composable
private fun CategoryGridItem(
    category: Category,
    isSelected: Boolean = false,
    onSelected: () -> Unit,
) {
    val backgroundColor =
        if (isSelected) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        } else {
            Color.Transparent
        }

    Column(
        modifier =
            Modifier.categoryGridItem(
                backgroundColor = backgroundColor,
                onClick = onSelected,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CategoryIconDisplay(category)
        Text(
            category.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color =
                if (isSelected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
        )
    }
}

@Composable
private fun AddNewCategoryGridItem(onAddNew: () -> Unit) {
    Column(
        modifier = Modifier.categoryGridItem(onClick = onAddNew).height(80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.AddCircleOutline,
            contentDescription = "Create New",
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "New",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
