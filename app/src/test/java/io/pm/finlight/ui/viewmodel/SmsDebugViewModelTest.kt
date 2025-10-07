// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/SmsDebugViewModelTest.kt
// REASON: FIX (Flaky Test) - Resolved a race condition that caused intermittent
// `TurbineTimeoutCancellationException` and `AssertionError` failures when running
// as part of a large test suite. The test now uses `advanceUntilIdle()` *inside*
// the `test` block. This ensures the test explicitly waits for the coroutine
// launched in the ViewModel's `init` block to complete before awaiting the final
// state, making the test deterministic and robust against timing variations.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.*
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.ml.SmsClassifier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class SmsDebugViewModelTest : BaseViewModelTest() {

    @Mock private lateinit var application: Application
    @Mock private lateinit var transactionViewModel: TransactionViewModel
    @Mock private lateinit var smsRepository: SmsRepository
    @Mock private lateinit var db: AppDatabase
    @Mock private lateinit var smsClassifier: SmsClassifier

    // DAOs
    @Mock private lateinit var customSmsRuleDao: CustomSmsRuleDao
    @Mock private lateinit var merchantRenameRuleDao: MerchantRenameRuleDao
    @Mock private lateinit var ignoreRuleDao: IgnoreRuleDao
    @Mock private lateinit var merchantCategoryMappingDao: MerchantCategoryMappingDao
    @Mock private lateinit var smsParseTemplateDao: SmsParseTemplateDao
    @Mock private lateinit var transactionDao: TransactionDao

    private lateinit var viewModel: SmsDebugViewModel

    @Before
    override fun setup() {
        super.setup()

        // Mock DAO access from the AppDatabase mock
        `when`(db.customSmsRuleDao()).thenReturn(customSmsRuleDao)
        `when`(db.merchantRenameRuleDao()).thenReturn(merchantRenameRuleDao)
        `when`(db.ignoreRuleDao()).thenReturn(ignoreRuleDao)
        `when`(db.merchantCategoryMappingDao()).thenReturn(merchantCategoryMappingDao)
        `when`(db.smsParseTemplateDao()).thenReturn(smsParseTemplateDao)
        `when`(db.transactionDao()).thenReturn(transactionDao)

        `when`(application.applicationContext).thenReturn(application)
    }

    private fun setupDefaultDaoBehaviors() = runTest {
        `when`(customSmsRuleDao.getAllRules()).thenReturn(flowOf(emptyList()))
        `when`(merchantRenameRuleDao.getAllRules()).thenReturn(flowOf(emptyList()))
        // Use thenAnswer for suspend functions
        `when`(ignoreRuleDao.getEnabledRules()).thenAnswer { DEFAULT_IGNORE_PHRASES }
        `when`(smsParseTemplateDao.getAllTemplates()).thenAnswer { emptyList<SmsParseTemplate>() }
        `when`(transactionDao.getAllSmsHashes()).thenReturn(flowOf(emptyList()))
        // FIX: Use anyObject() for the non-nullable String parameter to avoid NPEs.
        `when`(merchantCategoryMappingDao.getCategoryIdForMerchant(anyObject())).thenAnswer { null }
    }

    private fun initializeViewModel() {
        viewModel = SmsDebugViewModel(
            application,
            smsRepository,
            db,
            smsClassifier,
            transactionViewModel
        )
    }

    @Test
    fun `refreshScan loads and parses sms messages correctly`() = runTest {
        // Arrange
        setupDefaultDaoBehaviors()
        val sms1 = SmsMessage(1, "Sender1", "Success: you spent Rs 100 on Food", 1L)
        val sms2 = SmsMessage(2, "Sender2", "Ignored by ML", 2L)
        val sms3 = SmsMessage(3, "Sender3", "Not parsed", 3L)
        val successTxnRule = CustomSmsRule(1, "spent Rs", "on (.+)", "spent Rs ([\\d,.]+)", null, null, null, null, 10, "")

        `when`(smsRepository.fetchAllSms(null)).thenReturn(listOf(sms1, sms2, sms3))
        `when`(smsClassifier.classify(sms1.body)).thenReturn(0.9f)
        `when`(smsClassifier.classify(sms2.body)).thenReturn(0.05f) // Should be ignored
        `when`(smsClassifier.classify(sms3.body)).thenReturn(0.9f)
        `when`(customSmsRuleDao.getAllRules()).thenReturn(flowOf(listOf(successTxnRule))) // This rule will parse sms1

        // Act
        initializeViewModel()

        // Assert
        viewModel.uiState.test {
            // 1. Await the initial state emitted when the ViewModel is created (isLoading=true).
            val initialState = awaitItem()
            assertTrue("Initial state should be loading", initialState.isLoading)

            // 2. Since the work is launched in a separate coroutine in 'init',
            // we need to advance the dispatcher to let that work complete.
            advanceUntilIdle()

            // 3. Now that the work is done, await the final state emission.
            val finalState = awaitItem()
            assertFalse("Final state should not be loading", finalState.isLoading)
            assertEquals(3, finalState.debugResults.size)

            assertTrue(finalState.debugResults.any { it.parseResult is ParseResult.Success })
            assertTrue(finalState.debugResults.any { it.parseResult is ParseResult.IgnoredByClassifier })
            assertTrue(finalState.debugResults.any { it.parseResult is ParseResult.NotParsed })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setFilter correctly filters the results`() = runTest {
        // Arrange
        setupDefaultDaoBehaviors()
        val sms1 = SmsMessage(1, "S1", "Success: spent Rs 100", 1L)
        val sms2 = SmsMessage(2, "S2", "Problem", 2L)
        val successTxnRule = CustomSmsRule(1, "spent Rs", null, "spent Rs ([\\d,.]+)", null, null, null, null, 10, "")

        `when`(smsRepository.fetchAllSms(null)).thenReturn(listOf(sms1, sms2))
        `when`(smsClassifier.classify(sms1.body)).thenReturn(0.9f)
        `when`(smsClassifier.classify(sms2.body)).thenReturn(0.9f)
        `when`(customSmsRuleDao.getAllRules()).thenReturn(flowOf(listOf(successTxnRule))) // Will parse sms1

        initializeViewModel()
        advanceUntilIdle()

        // Assert initial state (problematic)
        viewModel.filteredDebugResults.test {
            // The StateFlow will emit its initial value (emptyList), then the new value.
            // We skip the initial one to assert on the result of the async operation.
            skipItems(1)
            assertEquals(1, awaitItem().size)

            // Act: Change to ALL
            viewModel.setFilter(SmsDebugFilter.ALL)
            assertEquals(2, awaitItem().size)

            // Act: Change back to PROBLEMATIC
            viewModel.setFilter(SmsDebugFilter.PROBLEMATIC)
            assertEquals(1, awaitItem().size)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `runAutoImportAndRefresh imports newly parsed transaction`() = runTest {
        // Arrange: Initial state where sms1 is problematic, ensuring it fails generic parsing.
        setupDefaultDaoBehaviors()
        val sms1 = SmsMessage(1, "SENDER1", "Txn update from MyBank. Order 123 completed. Val: 20.", 1L)
        val sms1Hash = (sms1.sender.filter { it.isDigit() }.takeLast(10) + sms1.body.replace(Regex("\\s+"), " ").trim()).hashCode().toString()
        val successTxnRule = CustomSmsRule(
            id = 1,
            triggerPhrase = "Txn update from MyBank",
            amountRegex = "Val: ([\\d,.]+)",
            merchantRegex = "Order (\\d+)",
            accountRegex = null,
            merchantNameExample = "123",
            amountExample = "20",
            accountNameExample = null,
            priority = 10,
            sourceSmsBody = sms1.body
        )

        // Mocks for the INITIAL scan (no custom rules)
        `when`(smsRepository.fetchAllSms(null)).thenReturn(listOf(sms1))
        `when`(smsClassifier.classify(sms1.body)).thenReturn(0.9f)
        `when`(transactionDao.getAllSmsHashes()).thenReturn(flowOf(emptyList()))
        `when`(customSmsRuleDao.getAllRules()).thenReturn(flowOf(emptyList()))

        var capturedTxn: PotentialTransaction? = null

        initializeViewModel()

        // Use turbine to correctly await the result of the async init block.
        viewModel.uiState.test {
            // 1. Await initial loading state from init{}
            val initialState = awaitItem()
            assertTrue("Initial state should be loading", initialState.isLoading)

            // 2. Wait for the init{} coroutine to finish.
            advanceUntilIdle()

            // 3. Await the result of the initial scan.
            val loadedState = awaitItem()
            assertFalse("ViewModel should have finished loading", loadedState.isLoading)
            val initialResult = loadedState.debugResults.find { it.smsMessage.id == 1L }
            assertTrue(
                "Initial parse result should be NotParsed, but was ${initialResult?.parseResult}",
                initialResult?.parseResult is ParseResult.NotParsed
            )

            // Arrange for the REFRESH scan (with the new custom rule)
            `when`(customSmsRuleDao.getAllRules()).thenReturn(flowOf(listOf(successTxnRule)))
            `when`(transactionViewModel.autoSaveSmsTransaction(anyObject())).thenAnswer { invocation ->
                capturedTxn = invocation.getArgument(0)
                true
            }

            // Act
            viewModel.runAutoImportAndRefresh()

            // 4. Await the loading state during the refresh.
            val loadingState = awaitItem()
            assertTrue("ViewModel should be loading during refresh", loadingState.isLoading)

            // 5. Explicitly run the launched coroutine to completion.
            advanceUntilIdle()

            // 6. Await the final state after the refresh.
            val finalState = awaitItem()

            // 7. Assert the final state and side-effects.
            assertNotNull("autoSaveSmsTransaction should have been called", capturedTxn)
            assertEquals(sms1Hash, capturedTxn!!.sourceSmsHash)
            assertEquals("123", capturedTxn!!.merchantName)

            val finalResult = finalState.debugResults.find { it.smsMessage.id == 1L }
            assertTrue(
                "Final parse result should be Success, but was ${finalResult?.parseResult}",
                finalResult?.parseResult is ParseResult.Success
            )

            // 8. Clean up.
            cancelAndIgnoreRemainingEvents()
        }
    }
}