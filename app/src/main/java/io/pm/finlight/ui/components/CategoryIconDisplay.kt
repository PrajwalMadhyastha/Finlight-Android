package io.pm.finlight.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.pm.finlight.Category
import io.pm.finlight.utils.CategoryIconHelper

@Composable
fun CategoryIconDisplay(
    iconKey: String?,
    colorKey: String?,
    name: String,
    modifier: Modifier = Modifier,
) {
    val baseModifier = if (modifier == Modifier) Modifier.size(48.dp) else modifier
    BoxWithConstraints(
        modifier =
            baseModifier
                .defaultMinSize(minWidth = 24.dp, minHeight = 24.dp)
                .clip(CircleShape)
                .background(CategoryIconHelper.getIconBackgroundColor(colorKey ?: "gray_light")),
        contentAlignment = Alignment.Center,
    ) {
        val isCompact = maxWidth <= 32.dp
        if (name == "Uncategorized") {
            Icon(
                imageVector = CategoryIconHelper.getIcon("help_outline"),
                contentDescription = name,
                tint = Color.Black,
                modifier = if (isCompact) Modifier.padding(4.dp) else Modifier.size(24.dp),
            )
        } else if (iconKey == "letter_default") {
            Text(
                text = name.firstOrNull()?.uppercase() ?: "?",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = if (isCompact) 12.sp else 22.sp,
            )
        } else {
            Icon(
                imageVector = CategoryIconHelper.getIcon(iconKey ?: "category"),
                contentDescription = name,
                tint = Color.Black,
                modifier = if (isCompact) Modifier.padding(4.dp) else Modifier.size(24.dp),
            )
        }
    }
}

@Composable
fun CategoryIconDisplay(
    category: Category,
    modifier: Modifier = Modifier,
) {
    CategoryIconDisplay(
        iconKey = category.iconKey,
        colorKey = category.colorKey,
        name = category.name,
        modifier = modifier,
    )
}
