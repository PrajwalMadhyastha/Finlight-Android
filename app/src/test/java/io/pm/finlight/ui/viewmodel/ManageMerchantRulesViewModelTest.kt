package io.pm.finlight.ui.viewmodel

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.IMerchantRenameRuleRepository
import io.pm.finlight.ManageMerchantRulesViewModel
import io.pm.finlight.MerchantRenameRule
import io.pm.finlight.TestApplication
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class ManageMerchantRulesViewModelTest : BaseViewModelTest() {
    @Mock
    private lateinit var merchantRenameRuleRepository: IMerchantRenameRuleRepository

    private lateinit var viewModel: ManageMerchantRulesViewModel

    private val sampleRules =
        listOf(
            MerchantRenameRule(originalName = "ZOMATO MEDIA", newName = "Zomato"),
            MerchantRenameRule(originalName = "AMZN PAY", newName = "Amazon"),
            MerchantRenameRule(originalName = "SWIGGY BANGALORE", newName = "Swiggy"),
            MerchantRenameRule(originalName = "AMAZON RETAIL", newName = "Amazon"),
        )

    @Before
    override fun setup() {
        super.setup()
        initializeViewModel(sampleRules)
    }

    private fun initializeViewModel(rules: List<MerchantRenameRule>) {
        `when`(merchantRenameRuleRepository.getAllRules()).thenReturn(flowOf(rules))
        viewModel = ManageMerchantRulesViewModel(merchantRenameRuleRepository)
    }

    @Test
    fun `allRules flow emits rules from repository`() =
        runTest {
            viewModel.allRules.test {
                assertEquals(sampleRules, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `totalRulesCount emits correct number of rules`() =
        runTest {
            viewModel.totalRulesCount.test {
                assertEquals(sampleRules.size, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `filteredRules returns all rules sorted alphabetically by newName when search query is empty`() =
        runTest {
            viewModel.filteredRules.test {
                val rules = awaitItem()
                assertEquals(4, rules.size)
                // "Amazon" items first, then "Swiggy", then "Zomato"
                assertEquals("Amazon", rules[0].newName)
                assertEquals("Amazon", rules[1].newName)
                assertEquals("Swiggy", rules[2].newName)
                assertEquals("Zomato", rules[3].newName)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `filteredRules filters by raw merchant originalName case-insensitively`() =
        runTest {
            viewModel.filteredRules.test {
                awaitItem() // initial list

                viewModel.onSearchQueryChange("zomato media")
                val filtered = awaitItem()
                assertEquals(1, filtered.size)
                assertEquals("ZOMATO MEDIA", filtered[0].originalName)
                assertEquals("Zomato", filtered[0].newName)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `filteredRules filters by renamed merchant newName case-insensitively`() =
        runTest {
            viewModel.filteredRules.test {
                awaitItem() // initial list

                viewModel.onSearchQueryChange("amazon")
                val filtered = awaitItem()
                assertEquals(2, filtered.size)
                assertTrue(filtered.all { it.newName == "Amazon" })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `filteredRules trims search query whitespace`() =
        runTest {
            viewModel.filteredRules.test {
                awaitItem() // initial list

                viewModel.onSearchQueryChange("   swiggy   ")
                val filtered = awaitItem()
                assertEquals(1, filtered.size)
                assertEquals("Swiggy", filtered[0].newName)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `clearSearch resets search query to empty string`() =
        runTest {
            viewModel.onSearchQueryChange("test query")
            assertEquals("test query", viewModel.searchQuery.value)

            viewModel.clearSearch()
            assertEquals("", viewModel.searchQuery.value)
        }

    @Test
    fun `filteredRules returns empty list when no merchant matches query`() =
        runTest {
            viewModel.filteredRules.test {
                awaitItem() // initial list

                viewModel.onSearchQueryChange("non_existent_merchant_123")
                val filtered = awaitItem()
                assertEquals(0, filtered.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `deleteRule calls repository deleteByOriginalName`() =
        runTest {
            val ruleToDelete = MerchantRenameRule(originalName = "ZOMATO MEDIA", newName = "Zomato")
            viewModel.deleteRule(ruleToDelete)

            verify(merchantRenameRuleRepository).deleteByOriginalName("ZOMATO MEDIA")
        }

    @Test
    fun `updateRule calls repository insert with trimmed originalName and newName`() =
        runTest {
            viewModel.updateRule("  AMZN PAY  ", "  Amazon Pay  ")

            verify(merchantRenameRuleRepository).insert(
                MerchantRenameRule(
                    originalName = "AMZN PAY",
                    newName = "Amazon Pay",
                ),
            )
        }

    @Test
    fun `updateRule ignores blank originalName or blank newName`() =
        runTest {
            viewModel.updateRule("", "Amazon")
            viewModel.updateRule("AMZN PAY", "   ")

            verify(merchantRenameRuleRepository, never()).insert(any())
        }

    @Test
    fun `addRule calls repository insert with trimmed originalName and newName`() =
        runTest {
            viewModel.addRule("  UBER *TRIP  ", "  Uber  ")

            verify(merchantRenameRuleRepository).insert(
                MerchantRenameRule(
                    originalName = "UBER *TRIP",
                    newName = "Uber",
                ),
            )
        }

    @Test
    fun `addRule ignores blank inputs`() =
        runTest {
            viewModel.addRule("   ", "Uber")
            viewModel.addRule("UBER *TRIP", "  ")

            verify(merchantRenameRuleRepository, never()).insert(any())
        }

    @Test
    fun `addRule ignores identical originalName and newName`() =
        runTest {
            viewModel.addRule("Uber", "Uber")
            viewModel.addRule("  uber  ", "UBER")

            verify(merchantRenameRuleRepository, never()).insert(any())
        }
}
