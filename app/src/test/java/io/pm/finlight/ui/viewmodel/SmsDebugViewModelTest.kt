// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/SmsDebugViewModelTest.kt
// REASON: REFACTOR (Testing) - The test class now extends `BaseViewModelTest`,
// inheriting all common setup logic and removing boilerplate for rules,
// dispatchers, and Mockito initialization. The tearDown method is now an override.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.os.Build
import android.widget.Toast
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.*
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.dao.*
import io.pm.finlight.ml.SmsClassifier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockedStatic
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

    private lateinit var smsParserMock: MockedStatic<SmsParser>
    private lateinit var toastMock: MockedStatic<Toast>


    private lateinit var viewModel: SmsDebugViewModel

    @Before
    override fun setup() {
        super.setup()

        smsParserMock = mockStatic(SmsParser::class.java)
        toastMock = mockStatic(Toast::class.java)
        `when`(Toast.makeText(any(), anyString(), anyInt())).thenReturn(mock(Toast::class.java))


        // Mock DAO access from the AppDatabase mock
        `when`(db.customSmsRuleDao()).thenReturn(customSmsRuleDao)
        `when`(db.merchantRenameRuleDao()).thenReturn(merchantRenameRuleDao)
        `when`(db.ignoreRuleDao()).thenReturn(ignoreRuleDao)
        `when`(db.merchantCategoryMappingDao()).thenReturn(merchantCategoryMappingDao)
        `when`(db.smsParseTemplateDao()).thenReturn(smsParseTemplateDao)
        `when`(db.transactionDao()).thenReturn(transactionDao)

        // Setup default mock behaviors for DAOs
        `when`(customSmsRuleDao.getAllRules()).thenReturn(flowOf(emptyList()))
        `when`(merchantRenameRuleDao.getAllRules()).thenReturn(flowOf(emptyList()))
        runBlocking {
            `when`(ignoreRuleDao.getEnabledRules()).thenReturn(emptyList())
            `when`(merchantCategoryMappingDao.getCategoryIdForMerchant(anyString())).thenReturn(null)
            `when`(smsParseTemplateDao.getAllTemplates()).thenReturn(emptyList())
        }
        `when`(transactionDao.getAllSmsHashes()).thenReturn(flowOf(emptyList()))

        `when`(application.applicationContext).thenReturn(application)
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

    @After
    override fun tearDown() {
        smsParserMock.close()
        toastMock.close()
        super.tearDown()
    }

    @Test
    @Ignore
    fun `refreshScan loads and parses sms messages correctly`() = runTest {
        // Arrange
        val sms1 = SmsMessage(1, "Sender1", "Success message", 1L)
        val sms2 = SmsMessage(2, "Sender2", "Ignored by ML", 2L)
        val sms3 = SmsMessage(3, "Sender3", "Not parsed", 3L)
        val successTxn = PotentialTransaction(1L, "Sender1", 10.0, "expense", "merchant", "Success message")

        `when`(smsRepository.fetchAllSms(null)).thenReturn(listOf(sms1, sms2, sms3))
        `when`(smsClassifier.classify(sms1.body)).thenReturn(0.9f)
        `when`(smsClassifier.classify(sms2.body)).thenReturn(0.05f) // Should be ignored
        `when`(smsClassifier.classify(sms3.body)).thenReturn(0.9f)

        smsParserMock.`when`<ParseResult?> { runBlocking { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } }.thenReturn(null)
        smsParserMock.`when`<ParseResult> { runBlocking { SmsParser.parseWithReason(eq(sms1), any(), any(), any(), any(), any(), any(), any()) } }.thenReturn(ParseResult.Success(successTxn))
        smsParserMock.`when`<ParseResult> { runBlocking { SmsParser.parseWithReason(eq(sms3), any(), any(), any(), any(), any(), any(), any()) } }.thenReturn(ParseResult.NotParsed("reason"))

        // Act
        initializeViewModel()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(false, state.isLoading)
            assertEquals(3, state.debugResults.size)

            assertTrue(state.debugResults.any { it.parseResult is ParseResult.Success })
            assertTrue(state.debugResults.any { it.parseResult is ParseResult.IgnoredByClassifier })
            assertTrue(state.debugResults.any { it.parseResult is ParseResult.NotParsed })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @Ignore
    fun `setFilter correctly filters the results`() = runTest {
        // Arrange
        val sms1 = SmsMessage(1, "S1", "Success", 1L)
        val sms2 = SmsMessage(2, "S2", "Problem", 2L)
        val successTxn = PotentialTransaction(1L, "S1", 10.0, "expense", "merchant", "Success")

        `when`(smsRepository.fetchAllSms(null)).thenReturn(listOf(sms1, sms2))
        `when`(smsClassifier.classify(anyString())).thenReturn(0.9f)
        smsParserMock.`when`<ParseResult?> { runBlocking { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } }.thenReturn(null)
        smsParserMock.`when`<ParseResult> { runBlocking { SmsParser.parseWithReason(eq(sms1), any(), any(), any(), any(), any(), any(), any()) } }.thenReturn(ParseResult.Success(successTxn))
        smsParserMock.`when`<ParseResult> { runBlocking { SmsParser.parseWithReason(eq(sms2), any(), any(), any(), any(), any(), any(), any()) } }.thenReturn(ParseResult.NotParsed("reason"))

        initializeViewModel()

        // Assert initial state (problematic)
        viewModel.filteredDebugResults.test {
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
    @Ignore
    fun `runAutoImportAndRefresh imports newly parsed transaction`() = runTest {
        // Arrange: Initial state where sms1 is problematic
        val sms1 = SmsMessage(1, "SENDER1", "This will be fixed", 1L, )
        val sms1Hash = (sms1.sender.filter { it.isDigit() }.takeLast(10) + sms1.body.replace(Regex("\\s+"), " ").trim()).hashCode().toString()
        val successTxn1 = PotentialTransaction(1L, "SENDER1", 20.0, "expense", "New Merchant", "This will be fixed", sourceSmsHash = sms1Hash)


        `when`(smsRepository.fetchAllSms(null)).thenReturn(listOf(sms1))
        `when`(smsClassifier.classify(sms1.body)).thenReturn(0.9f)
        smsParserMock.`when`<ParseResult?> { runBlocking { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } }.thenReturn(null)
        smsParserMock.`when`<ParseResult> { runBlocking { SmsParser.parseWithReason(any(), any(), any(), any(), any(), any(), any(), any()) } }.thenReturn(ParseResult.NotParsed("reason"))
        `when`(transactionDao.getAllSmsHashes()).thenReturn(flowOf(emptyList())) // No transactions imported yet

        initializeViewModel()
        viewModel.uiState.test { awaitItem() } // Consume initial state

        // Arrange for the refresh: Now sms1 parses successfully
        smsParserMock.`when`<ParseResult?> { runBlocking { SmsParser.parseWithOnlyCustomRules(eq(sms1), any(), any(), any(), any()) } }.thenReturn(ParseResult.Success(successTxn1))
        `when`(transactionViewModel.autoSaveSmsTransaction(successTxn1)).thenReturn(true)


        // Act
        viewModel.runAutoImportAndRefresh()
        advanceUntilIdle()


        // Assert
        verify(transactionViewModel).autoSaveSmsTransaction(successTxn1)
        viewModel.uiState.test {
            val finalState = awaitItem()
            val resultForSms1 = finalState.debugResults.find { it.smsMessage.id == 1L }
            assertTrue(resultForSms1?.parseResult is ParseResult.Success)
            cancelAndIgnoreRemainingEvents()
        }
    }
}