package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.*
import io.pm.finlight.*
import io.pm.finlight.core.*
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.dao.*
import io.pm.finlight.data.db.entity.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.times
import org.robolectric.annotation.Config
import org.mockito.Mockito.`when` as whenever

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
abstract class TransactionViewModelBaseSetup : BaseViewModelTest() {
    protected val applicationContext: Application = ApplicationProvider.getApplicationContext()

    // Mocks for all dependencies
    @Mock protected lateinit var db: AppDatabase

    @Mock protected lateinit var transactionRepository: TransactionRepository

    @Mock protected lateinit var accountRepository: AccountRepository

    @Mock protected lateinit var categoryRepository: CategoryRepository

    @Mock protected lateinit var tagRepository: TagRepository

    @Mock protected lateinit var settingsRepository: SettingsRepository

    @Mock protected lateinit var smsRepository: SmsRepository

    @Mock protected lateinit var merchantRenameRuleRepository: MerchantRenameRuleRepository

    @Mock protected lateinit var merchantCategoryMappingRepository: MerchantCategoryMappingRepository

    @Mock protected lateinit var merchantMappingRepository: MerchantMappingRepository

    @Mock protected lateinit var splitTransactionRepository: SplitTransactionRepository

    @Mock protected lateinit var smsParseTemplateDao: SmsParseTemplateDao

    // Mocks for DAOs used by the ViewModel and internal logic
    @Mock protected lateinit var accountDao: AccountDao

    @Mock protected lateinit var categoryDao: CategoryDao

    @Mock protected lateinit var tagDao: TagDao

    @Mock protected lateinit var transactionWriteDao: TransactionWriteDao

    @Mock protected lateinit var transactionQueryDao: TransactionQueryDao

    @Mock protected lateinit var transactionAnalyticsDao: TransactionAnalyticsDao

    @Mock protected lateinit var transactionReimbursementDao: TransactionReimbursementDao

    @Mock protected lateinit var customSmsRuleDao: CustomSmsRuleDao

    @Mock protected lateinit var ignoreRuleDao: IgnoreRuleDao

    @Mock protected lateinit var splitTransactionDao: SplitTransactionDao

    @Mock protected lateinit var merchantCategoryMappingDao: MerchantCategoryMappingDao

    @Mock protected lateinit var merchantRenameRuleDao: MerchantRenameRuleDao

    @Mock protected lateinit var accountAliasDao: AccountAliasDao

    @Mock protected lateinit var deletedSmsHashDao: io.pm.finlight.data.db.dao.DeletedSmsHashDao

    protected lateinit var viewModel: TransactionViewModel

    @Before
    override fun setup() {
        super.setup()

        // Mock DAO access from the AppDatabase mock
        whenever(db.accountDao()).thenReturn(accountDao)
        whenever(db.categoryDao()).thenReturn(categoryDao)
        whenever(db.tagDao()).thenReturn(tagDao)
        whenever(db.transactionQueryDao()).thenReturn(transactionQueryDao)
        whenever(db.transactionWriteDao()).thenReturn(transactionWriteDao)
        whenever(db.transactionAnalyticsDao()).thenReturn(transactionAnalyticsDao)
        whenever(db.transactionReimbursementDao()).thenReturn(transactionReimbursementDao)
        whenever(db.customSmsRuleDao()).thenReturn(customSmsRuleDao)
        whenever(db.ignoreRuleDao()).thenReturn(ignoreRuleDao)
        whenever(db.merchantCategoryMappingDao()).thenReturn(merchantCategoryMappingDao)
        whenever(db.merchantRenameRuleDao()).thenReturn(merchantRenameRuleDao)
        whenever(db.smsParseTemplateDao()).thenReturn(smsParseTemplateDao)
        whenever(db.splitTransactionDao()).thenReturn(splitTransactionDao)
        whenever(db.accountAliasDao()).thenReturn(accountAliasDao)
        whenever(db.deletedSmsHashDao()).thenReturn(deletedSmsHashDao)

        // Setup default mock behaviors for ViewModel initialization
        setupDefaultMocks()

        // Create the ViewModel instance with all mocked dependencies
        initializeViewModel()
    }

    protected fun setupDefaultMocks() {
        runTest {
            whenever(merchantRenameRuleRepository.getAliasesAsMap()).thenReturn(flowOf(emptyMap()))
            whenever(
                transactionRepository.getTransactionDetailsForRange(anyLong(), anyLong(), anyOrNull(), anyOrNull(), anyOrNull()),
            ).thenReturn(flowOf(emptyList()))
            whenever(transactionRepository.getFinancialSummaryForRangeFlow(anyLong(), anyLong())).thenReturn(flowOf(null))
            whenever(
                transactionRepository.getSpendingByCategoryForMonth(anyLong(), anyLong(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()),
            ).thenReturn(flowOf(emptyList()))
            whenever(
                transactionRepository.getSpendingByMerchantForMonth(anyLong(), anyLong(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()),
            ).thenReturn(flowOf(emptyList()))
            whenever(accountRepository.allAccounts).thenReturn(flowOf(emptyList()))
            whenever(categoryRepository.allCategories).thenReturn(flowOf(emptyList()))
            whenever(tagRepository.allTags).thenReturn(flowOf(emptyList()))
            whenever(transactionRepository.getFirstTransactionDate()).thenReturn(flowOf(null))
            whenever(transactionRepository.getMonthlyTrends(anyLong())).thenReturn(flowOf(emptyList()))
            whenever(settingsRepository.getOverallBudgetForMonth(anyInt(), anyInt())).thenReturn(flowOf(0f))
            whenever(db.accountDao().findByName(anyString())).thenReturn(null)
            whenever(settingsRepository.getTravelModeSettings()).thenReturn(flowOf(null))
            whenever(settingsRepository.getPrivacyModeEnabled()).thenReturn(flowOf(false))
            whenever(transactionRepository.searchMerchants(anyString())).thenReturn(flowOf(emptyList()))
            whenever(transactionRepository.getRecentManualTransactions(anyInt())).thenReturn(flowOf(emptyList()))
            whenever(transactionRepository.getReimbursementsForExpense(anyInt())).thenReturn(flowOf(emptyList()))
            whenever(transactionQueryDao.getSmsHashesByIds(any())).thenReturn(emptyList())
        }
    }

    protected fun initializeViewModel() {
        val resolveTravelModeTagUseCase = io.pm.finlight.domain.usecase.ResolveTravelModeTagUseCase(settingsRepository, tagRepository)
        viewModel =
            TransactionViewModel(
                application = applicationContext,
                db = db,
                transactionRepository = transactionRepository,
                accountRepository = accountRepository,
                categoryRepository = categoryRepository,
                tagRepository = tagRepository,
                settingsRepository = settingsRepository,
                smsRepository = smsRepository,
                merchantRenameRuleRepository = merchantRenameRuleRepository,
                merchantCategoryMappingRepository = merchantCategoryMappingRepository,
                merchantMappingRepository = merchantMappingRepository,
                splitTransactionRepository = splitTransactionRepository,
                smsParseTemplateDao = smsParseTemplateDao,
                resolveTravelModeTagUseCase = resolveTravelModeTagUseCase,
            )
    }
}
