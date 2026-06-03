package io.pm.finlight

import android.Manifest
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TravelModeTests {
    private val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain =
        RuleChain
            .outerRule(DisableOnboardingRule())
            .around(DisableAppLockRule())
            .around(ClearDatabaseRule())
            .around(SeedDatabaseRule())
            .around(
                GrantPermissionRule.grant(
                    Manifest.permission.READ_SMS,
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.POST_NOTIFICATIONS,
                ),
            )
            .around(composeTestRule)

    @Test
    fun test_createInternationalTrip_showsCurrencyConversion() {
        // 1. Navigate to Profile -> Currency & Travel
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithText("Profile").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Profile").performClick()

        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithText("Currency & Travel").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Currency & Travel").performScrollTo().performClick()

        // Wait for screen to load
        composeTestRule.waitForIdle()

        // 2. Enter Trip Name
        composeTestRule.onNodeWithTag("trip_name_input").performTextInput("My US Trip")

        // 3. Select International
        composeTestRule.onNodeWithTag("international_trip_button").performClick()

        // 4. Select Foreign Currency (USD)
        composeTestRule.onNodeWithTag("foreign_currency_button").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Australia (AUD)", substring = true, ignoreCase = true).performClick()

        composeTestRule.onNodeWithTag("conversion_rate_input").performScrollTo().performTextReplacement("83")

        // 6. Select Start Date (today)
        composeTestRule.onNodeWithTag("start_date_button").performScrollTo().performClick()
        composeTestRule.onNodeWithText("OK").performClick()

        // 7. Select End Date (today)
        composeTestRule.onNodeWithTag("end_date_button").performScrollTo().performClick()
        composeTestRule.onNodeWithText("OK").performClick()

        // 8. Save and Activate Plan
        composeTestRule.onNodeWithText("Save and Activate Plan").performScrollTo().performClick()

        // 9. Navigate to Transactions
        composeTestRule.waitUntil(timeoutMillis = 2000) {
            composeTestRule.onAllNodesWithTag("nav_item_Transactions").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("nav_item_Transactions").performClick()

        // 10. Open Add Transaction
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithContentDescription("Add Transaction").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Add Transaction").performClick()

        // Wait for Add Transaction screen
        composeTestRule.waitForIdle()

        // 11. Enter Amount "10"
        composeTestRule.onNodeWithTag("amount_text_field").performTextReplacement("10")

        // 12. Verify that conversion text is shown (Wait for DataStore to emit)
        try {
            composeTestRule.waitUntil(timeoutMillis = 5000) {
                composeTestRule.onAllNodesWithText("≈ ₹", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: Throwable) {
            composeTestRule.onRoot().printToLog("UI_TREE")
            throw e
        }
        composeTestRule.onNodeWithText("≈ ₹", substring = true).assertIsDisplayed()
    }
}
