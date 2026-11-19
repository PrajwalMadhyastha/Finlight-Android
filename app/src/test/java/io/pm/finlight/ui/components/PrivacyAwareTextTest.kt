package io.pm.finlight.ui.components

import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.TestApplication
import java.text.NumberFormat
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class PrivacyAwareTextTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun unmaskedCurrencyAmount_displaysFormattedCurrencyString() {
        val testAmount = 15000
        val expected =
            NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")).apply {
                maximumFractionDigits = 0
            }.format(testAmount)

        composeTestRule.setContent {
            PrivacyAwareText(
                amount = testAmount,
                isPrivacyMode = false,
                isCurrency = true,
            )
        }

        composeTestRule.onNodeWithText(expected).assertIsDisplayed()
    }

    @Test
    fun unmaskedNonCurrencyAmount_displaysFormattedNumberString() {
        val testAmount = 9999
        val expected =
            NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN")).apply {
                maximumFractionDigits = 0
            }.format(testAmount)

        composeTestRule.setContent {
            PrivacyAwareText(
                amount = testAmount,
                isPrivacyMode = false,
                isCurrency = false,
            )
        }

        composeTestRule.onNodeWithText(expected).assertIsDisplayed()
    }

    @Test
    fun unmaskedAmountWithPrefix_displaysPrefixWithFormattedAmount() {
        val testAmount = 750
        val prefix = "Total: "
        val formatted =
            NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")).apply {
                maximumFractionDigits = 0
            }.format(testAmount)

        composeTestRule.setContent {
            PrivacyAwareText(
                amount = testAmount,
                isPrivacyMode = false,
                prefix = prefix,
            )
        }

        composeTestRule.onNodeWithText(prefix + formatted).assertIsDisplayed()
    }
}
