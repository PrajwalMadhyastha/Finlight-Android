package io.pm.finlight.ui.screens.addtransaction

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.pm.finlight.R
import io.pm.finlight.TravelModeSettings
import io.pm.finlight.TripType
import io.pm.finlight.utils.CurrencyHelper
import java.text.NumberFormat

@Composable
fun AmountComposer(
    amount: String,
    onAmountChange: (String) -> Unit,
    focusRequester: FocusRequester,
    description: String,
    onDescriptionChange: (String) -> Unit,
    onDescriptionClick: () -> Unit,
    isTravelMode: Boolean,
    travelModeSettings: TravelModeSettings?,
    highlightDescription: Boolean,
    isDescriptionEntered: Boolean,
    hasInteractedWithNumpad: Boolean,
    modifier: Modifier = Modifier,
) {
    val currentTravelSettings = travelModeSettings
    val currencySymbol =
        if (isTravelMode && currentTravelSettings?.tripType == TripType.INTERNATIONAL) {
            CurrencyHelper.getCurrencySymbol(currentTravelSettings.currencyCode)
        } else {
            "₹"
        }
    val highlightColor = MaterialTheme.colorScheme.primary

    val animatedBorderColor by animateColorAsState(
        targetValue = if (highlightDescription) highlightColor else Color.Transparent,
        animationSpec = tween(durationMillis = 300, easing = EaseOutCubic),
        label = "HighlightBorderAnimation",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth(),
    ) {
        BasicTextField(
            value = description,
            onValueChange = onDescriptionChange,
            modifier =
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(2.dp, animatedBorderColor, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("description_text_field"),
            textStyle =
                MaterialTheme.typography.titleLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                ),
            singleLine = true,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (description.isEmpty()) {
                            Text(
                                text = if (hasInteractedWithNumpad) "What did you spend on?" else "Paid to...",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center,
                            )
                        }
                        innerTextField()
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = onDescriptionClick,
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.cd_search_predictions),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            },
        )

        Spacer(Modifier.height(8.dp))

        val textStyle =
            MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

        BasicTextField(
            value = amount,
            onValueChange = onAmountChange,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .testTag("amount_text_field"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = textStyle,
            singleLine = true,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = currencySymbol,
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Box(contentAlignment = Alignment.Center) {
                        if (amount.isEmpty()) {
                            Text(
                                "0",
                                style = textStyle.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)),
                            )
                        }
                        innerTextField()
                    }
                }
            },
        )

        if (isTravelMode && currentTravelSettings?.tripType == TripType.INTERNATIONAL) {
            val enteredAmount = amount.toDoubleOrNull() ?: 0.0
            val convertedAmount = enteredAmount * (currentTravelSettings.conversionRate?.toDouble() ?: 1.0)
            val homeSymbol = "₹"
            Text(
                text = "≈ $homeSymbol${NumberFormat.getInstance().format(convertedAmount)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
