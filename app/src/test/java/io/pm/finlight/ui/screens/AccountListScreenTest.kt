package io.pm.finlight.ui.screens

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.pm.finlight.Account
import io.pm.finlight.AccountWithBalance
import io.pm.finlight.CreditCardMetrics
import io.pm.finlight.TestApplication
import io.pm.finlight.ui.components.rememberSafeBankLogoPainter
import io.pm.finlight.ui.viewmodel.AccountViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class AccountListScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val navController: NavController = mockk(relaxed = true)

    @Test
    fun `AccountListScreen renders account named Bank of India without crashing`() {
        val mockViewModel: AccountViewModel =
            mockk(relaxed = true) {
                every { accountsWithBalance } returns
                    MutableStateFlow(
                        listOf(
                            AccountWithBalance(
                                account = Account(id = 1, name = "Bank of India", type = "Savings"),
                                balance = 25000.0,
                            ),
                            AccountWithBalance(
                                account = Account(id = 2, name = "BOI Savings", type = "Savings"),
                                balance = 15000.0,
                            ),
                        ),
                    )
                every { isSelectionModeActive } returns MutableStateFlow(false)
                every { selectedAccountIds } returns MutableStateFlow(emptySet())
                every { suggestedMerges } returns MutableStateFlow(emptyList())
                every { creditCardMetrics } returns MutableStateFlow(emptyMap())
                every { uiEvent } returns MutableSharedFlow()
            }

        composeTestRule.setContent {
            AccountListScreen(
                navController = navController,
                viewModel = mockViewModel,
            )
        }

        // Verify items display without throwing 'Unknown command for: R' or any other exception
        composeTestRule.onNodeWithText("Bank of India").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Bank of India Logo").assertIsDisplayed()
        composeTestRule.onNodeWithText("BOI Savings").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("BOI Savings Logo").assertIsDisplayed()
    }

    @Test
    fun `rememberSafeBankLogoPainter falls back cleanly when invalid or unmapped resource is provided`() {
        composeTestRule.setContent {
            Image(
                painter = rememberSafeBankLogoPainter(logoResId = -9999),
                contentDescription = "Fallback Logo",
            )
        }

        composeTestRule.onNodeWithContentDescription("Fallback Logo").assertIsDisplayed()
    }

    @Test
    fun `AccountListScreen renders credit card with metrics and utilization indicator`() {
        val mockViewModel: AccountViewModel =
            mockk(relaxed = true) {
                every { accountsWithBalance } returns
                    MutableStateFlow(
                        listOf(
                            AccountWithBalance(
                                account =
                                    Account(
                                        id = 10,
                                        name = "HDFC Regalia",
                                        type = "Credit Card",
                                        creditLimit = 100000.0,
                                        billingCycleDay = 15,
                                    ),
                                balance = -25000.0,
                            ),
                        ),
                    )
                every { isSelectionModeActive } returns MutableStateFlow(false)
                every { selectedAccountIds } returns MutableStateFlow(emptySet())
                every { suggestedMerges } returns MutableStateFlow(emptyList())
                every { creditCardMetrics } returns
                    MutableStateFlow(
                        mapOf(
                            10 to
                                CreditCardMetrics(
                                    creditLimit = 100000.0,
                                    billingCycleDay = 15,
                                    currentCycleSpend = 25000.0,
                                    availableLimit = 75000.0,
                                    utilizationPercentage = 25.0f,
                                    cycleStartDate = 1729036800000L,
                                    cycleEndDate = 1731628800000L,
                                    daysRemainingInCycle = 11,
                                ),
                        ),
                    )
                every { uiEvent } returns MutableSharedFlow()
            }

        composeTestRule.setContent {
            AccountListScreen(
                navController = navController,
                viewModel = mockViewModel,
            )
        }

        composeTestRule.onNodeWithText("HDFC Regalia").assertIsDisplayed()
        composeTestRule.onNodeWithText("25.0% used • 11d left").assertIsDisplayed()
    }
}
