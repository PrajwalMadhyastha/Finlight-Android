package io.pm.finlight.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.pm.finlight.TransactionType
import io.pm.finlight.ui.theme.FinlightTheme

@Composable
fun TransactionTypeToggle(
    selectedType: TransactionType,
    onTypeSelected: (TransactionType) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(FinlightTheme.colors.glassSurface)
                .border(1.dp, FinlightTheme.colors.glassBorder, CircleShape)
                .padding(4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val types =
            listOf(
                TransactionType.EXPENSE to "Expense",
                TransactionType.INCOME to "Income",
            )

        types.forEach { (type, label) ->
            val isSelected = selectedType == type
            Button(
                onClick = { onTypeSelected(type) },
                enabled = enabled,
                modifier =
                    Modifier
                        .weight(1f)
                        .height(48.dp),
                shape = CircleShape,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        disabledContainerColor =
                            if (isSelected) {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                            } else {
                                Color.Transparent
                            },
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    ),
                elevation = null,
            ) {
                Text(label, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun TransactionTypeToggle(
    selectedType: String,
    onTypeSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TransactionTypeToggle(
        selectedType = TransactionType.fromStringOrNull(selectedType) ?: TransactionType.EXPENSE,
        onTypeSelected = { onTypeSelected(it.name.lowercase()) },
        modifier = modifier,
        enabled = enabled,
    )
}
