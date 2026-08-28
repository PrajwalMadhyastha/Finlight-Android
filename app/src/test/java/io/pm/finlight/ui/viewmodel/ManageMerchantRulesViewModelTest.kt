package io.pm.finlight.ui.viewmodel

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.Category
import io.pm.finlight.ICategoryRepository
import io.pm.finlight.IMerchantCategoryMappingRepository
import io.pm.finlight.IMerchantRenameRuleRepository
import io.pm.finlight.ManageMerchantRulesViewModel
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.MerchantCategoryMapping
import io.pm.finlight.MerchantRenameRule
import io.pm.finlight.TestApplication
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Mock
    private lateinit var transactionRepository: ITransactionRepository

    @Mock
    private lateinit var categoryMappingRepository: IMerchantCategoryMappingRepository

    @Mock
    private lateinit var categoryRepository: ICategoryRepository

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

    private fun initializeViewModel(
        rules: List<MerchantRenameRule>,
        txCounts: Map<String, Int> = emptyMap(),
        mappings: List<MerchantCategoryMapping> = emptyList(),
        categories: List<Category> = emptyList(),
    ) {
        `when`(merchantRenameRuleRepository.getAllRules()).thenReturn(flowOf(rules))
        `when`(transactionRepository.getTransactionCountsByOriginalDescription()).thenReturn(flowOf(txCounts))
        `when`(categoryMappingRepository.getAllMappings()).thenReturn(flowOf(mappings))
        `when`(categoryRepository.allCategories).thenReturn(flowOf(categories))

        viewModel =
            ManageMerchantRulesViewModel(
                merchantRenameRuleRepository = merchantRenameRuleRepository,
                transactionRepository = transactionRepository,
                categoryMappingRepository = categoryMappingRepository,
                categoryRepository = categoryRepository,
            )
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
    fun `deleteRuleAndSync deletes rule and reverts transaction descriptions`() =
        runTest {
            val ruleToDelete = MerchantRenameRule(originalName = "ZOMATO MEDIA", newName = "Zomato")
            viewModel.deleteRuleAndSync(ruleToDelete)

            verify(merchantRenameRuleRepository).deleteByOriginalName("ZOMATO MEDIA")
            verify(transactionRepository).updateDescriptionByOriginalDescription("ZOMATO MEDIA", "ZOMATO MEDIA")
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
    fun `updateRuleAndSync updates rule and syncs matching transactions`() =
        runTest {
            viewModel.updateRuleAndSync("  AMZN PAY  ", "  Amazon Pay  ")

            verify(merchantRenameRuleRepository).insert(
                MerchantRenameRule(
                    originalName = "AMZN PAY",
                    newName = "Amazon Pay",
                ),
            )
            verify(transactionRepository).updateDescriptionByOriginalDescription("AMZN PAY", "Amazon Pay")
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

    @Test
    fun `uiRules emits transaction impact counts and linked categories`() =
        runTest {
            val rules =
                listOf(
                    MerchantRenameRule(originalName = "SWIGGY BANGALORE", newName = "Swiggy"),
                    MerchantRenameRule(originalName = "AMAZON PAY", newName = "Amazon Pay"),
                )
            val txCounts =
                mapOf(
                    "swiggy bangalore" to 14,
                    "amazon pay" to 5,
                )
            val foodCat = Category(id = 4, name = "Food & Drinks", iconKey = "restaurant", colorKey = "orange_light")
            val mappings =
                listOf(
                    MerchantCategoryMapping(parsedName = "SWIGGY BANGALORE", categoryId = 4),
                )
            val categories = listOf(foodCat)

            initializeViewModel(
                rules = rules,
                txCounts = txCounts,
                mappings = mappings,
                categories = categories,
            )

            viewModel.uiRules.test {
                val uiItems = awaitItem()
                assertEquals(2, uiItems.size)

                val swiggyItem = uiItems.find { it.rule.originalName == "SWIGGY BANGALORE" }
                assertNotNull(swiggyItem)
                assertEquals(14, swiggyItem!!.transactionCount)
                assertEquals("Food & Drinks", swiggyItem.linkedCategory?.name)

                val amazonItem = uiItems.find { it.rule.originalName == "AMAZON PAY" }
                assertNotNull(amazonItem)
                assertEquals(5, amazonItem!!.transactionCount)
                assertNull(amazonItem.linkedCategory)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `uiRules detects collisions between ambiguous merchant rename rules`() =
        runTest {
            val rules =
                listOf(
                    MerchantRenameRule(originalName = "AMAZON PAY", newName = "Amazon Pay"),
                    MerchantRenameRule(originalName = "AMAZON INDIA", newName = "Amazon"),
                )

            initializeViewModel(rules = rules)

            viewModel.uiRules.test {
                val uiItems = awaitItem()
                assertEquals(2, uiItems.size)

                val payItem = uiItems.find { it.rule.originalName == "AMAZON PAY" }
                assertNotNull(payItem)
                assertEquals(1, payItem!!.conflictingRules.size)
                assertEquals("AMAZON INDIA", payItem.conflictingRules.first().originalName)

                val indiaItem = uiItems.find { it.rule.originalName == "AMAZON INDIA" }
                assertNotNull(indiaItem)
                assertEquals(1, indiaItem!!.conflictingRules.size)
                assertEquals("AMAZON PAY", indiaItem.conflictingRules.first().originalName)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `selecting rule updates selectedRuleForTransactions and emits matching transaction details`() =
        runTest {
            val rule = MerchantRenameRule(originalName = "SWIGGY BANGALORE", newName = "Swiggy")
            val txn =
                io.pm.finlight.Transaction(
                    id = 101,
                    amount = 250.0,
                    description = "Swiggy",
                    originalDescription = "SWIGGY BANGALORE",
                    date = 1000L,
                    accountId = 1,
                    categoryId = 2,
                    notes = null,
                )
            val details =
                io.pm.finlight.TransactionDetails(
                    transaction = txn,
                    images = emptyList(),
                    accountName = "Test Bank",
                    categoryName = "Food & Drinks",
                    categoryIconKey = "restaurant",
                    categoryColorKey = "orange_light",
                    tagNames = null,
                )

            `when`(transactionRepository.getTransactionsByOriginalDescription("SWIGGY BANGALORE"))
                .thenReturn(flowOf(listOf(details)))

            initializeViewModel(rules = listOf(rule))

            viewModel.selectedRuleTransactions.test {
                assertEquals(emptyList<io.pm.finlight.TransactionDetails>(), awaitItem())

                viewModel.selectRuleForTransactions(rule)
                val txns = awaitItem()
                assertEquals(1, txns.size)
                assertEquals(101, txns.first().transaction.id)
                assertEquals("SWIGGY BANGALORE", txns.first().transaction.originalDescription)

                viewModel.clearSelectedRuleForTransactions()
                assertEquals(emptyList<io.pm.finlight.TransactionDetails>(), awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }
}
