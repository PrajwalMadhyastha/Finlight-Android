package io.pm.finlight.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import java.text.NumberFormat
import java.util.*

/**
 * A composable that displays a currency amount, automatically obscuring it
 * with placeholders when Privacy Mode is enabled.
 *
 * @param amount The numerical amount to display.
 * @param isPrivacyMode A boolean indicating if privacy mode is active.
 * @param modifier The Modifier to be applied to this layout node.
 * @param color The color of the text.
 * @param style The text style to be applied to the text.
 * @param prefix A string to prepend to the formatted amount (e.g., "Total: ").
 * @param isCurrency A boolean to indicate if the amount is a currency value.
 */
@Composable
fun PrivacyAwareText(
    amount: Number,
    isPrivacyMode: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    style: TextStyle = LocalTextStyle.current,
    prefix: String = "",
    isCurrency: Boolean = true
) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale("en", "IN")) }

    val textToShow = if (isPrivacyMode) {
        prefix + if (isCurrency) "₹ ****.**" else "****"
    } else {
        prefix + if (isCurrency) {
            currencyFormat.format(amount)
        } else {
            numberFormat.format(amount)
        }
    }

    Text(
        text = textToShow,
        modifier = modifier,
        color = color,
        fontWeight = fontWeight,
        textAlign = textAlign,
        style = style
    )
}