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

    @Mock protected lateinit var mergeTransactionsUseCase: io.pm.finlight.domain.usecase.MergeTransactionsUseCase

    @Mock protected lateinit var manageReimbursementUseCase: io.pm.finlight.domain.usecase.ManageReimbursementUseCase

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

    @Mock protected lateinit var merchantMappingDao: MerchantMappingDao

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
        whenever(db.merchantMappingDao()).thenReturn(merchantMappingDao)

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
            whenever(categoryRepository.getAllCategoriesSnapshot()).thenReturn(emptyList())
            whenever(tagRepository.allTags).thenReturn(flowOf(emptyList()))
            whenever(transactionRepository.getFirstTransactionDate()).thenReturn(flowOf(null))
            whenever(transactionRepository.getMonthlyTrends(anyLong())).thenReturn(flowOf(emptyList()))
            whenever(settingsRepository.getOverallBudgetForMonth(anyInt(), anyInt())).thenReturn(flowOf(0f))
            whenever(db.accountDao().findByName(anyString())).thenReturn(null)
            whenever(settingsRepository.getTravelModeSettings()).thenReturn(flowOf(null))
            whenever(settingsRepository.getCurrentTravelModeSettings()).thenReturn(null)
            whenever(settingsRepository.getPrivacyModeEnabled()).thenReturn(flowOf(false))
            whenever(transactionRepository.searchMerchants(anyString())).thenReturn(flowOf(emptyList()))
            whenever(transactionRepository.getRecentManualTransactions(anyInt())).thenReturn(flowOf(emptyList()))
            whenever(transactionRepository.getReimbursementsForExpense(anyInt())).thenReturn(flowOf(emptyList()))
            whenever(transactionQueryDao.getSmsHashesByIds(any())).thenReturn(emptyList())
            whenever(transactionQueryDao.existsBySmsHash(anyString())).thenReturn(false)
            whenever(deletedSmsHashDao.existsByHash(anyString())).thenReturn(false)
            whenever(deletedSmsHashDao.getAllHashes()).thenReturn(emptyList())
            whenever(merchantMappingRepository.allMappings).thenReturn(flowOf(emptyList()))
            whenever(transactionRepository.existsBySmsHash(anyString())).thenAnswer { invocation ->
                kotlinx.coroutines.runBlocking { transactionQueryDao.existsBySmsHash(invocation.getArgument(0)) }
            }
            whenever(transactionRepository.updateSmsHashByLegacy(anyString(), anyString())).thenAnswer { invocation ->
                kotlinx.coroutines.runBlocking { transactionWriteDao.updateSmsHashByLegacy(invocation.getArgument(0), invocation.getArgument(1)) }
            }
            whenever(transactionRepository.existsDeletedSmsHash(anyString())).thenAnswer { invocation ->
                kotlinx.coroutines.runBlocking { deletedSmsHashDao.existsByHash(invocation.getArgument(0)) }
            }
            whenever(transactionRepository.insertDeletedSmsHash(anyString())).thenAnswer { invocation ->
                kotlinx.coroutines.runBlocking { deletedSmsHashDao.insert(io.pm.finlight.data.db.entity.DeletedSmsHash(invocation.getArgument(0))) }
            }
            whenever(accountRepository.findOrCreateByName(anyString(), anyString())).thenAnswer { invocation ->
                val name = invocation.getArgument<String>(0)
                val type = invocation.getArgument<String>(1)
                kotlinx.coroutines.runBlocking {
                    val alias = accountAliasDao.findByAlias(name)
                    if (alias != null) {
                        val aliasedAccount = accountDao.getAccountByIdSync(alias.destinationAccountId)
                        if (aliasedAccount != null) return@runBlocking aliasedAccount
                        return@runBlocking io.pm.finlight.Account(id = alias.destinationAccountId, name = name, type = type)
                    }
                    val existing = accountDao.findByName(name)
                    if (existing != null) return@runBlocking existing
                    val newId = accountRepository.insert(io.pm.finlight.Account(name = name, type = type))
                    if (newId != -1L) {
                        accountRepository.getAccountByIdSync(newId.toInt()) ?: io.pm.finlight.Account(id = newId.toInt(), name = name, type = type)
                    } else {
                        accountDao.findByName(name) ?: io.pm.finlight.Account(name = name, type = type)
                    }
                }
            }
            whenever(accountRepository.findOrCreateByName(anyString(), any<AccountType>())).thenAnswer { invocation ->
                val name = invocation.getArgument<String>(0)
                val type = invocation.getArgument<AccountType>(1)
                kotlinx.coroutines.runBlocking {
                    accountRepository.findOrCreateByName(name, type.typeName)
                }
            }
        }
    }

    protected fun initializeViewModel() {
        val resolveTravelModeTagUseCase = io.pm.finlight.domain.usecase.ResolveTravelModeTagUseCase(tagRepository)
        val testDispatcherProvider = io.pm.finlight.utils.TestDispatcherProvider(testDispatcher)
        val autoSaveUseCase =
            io.pm.finlight.domain.usecase.AutoSaveSmsTransactionUseCase(
                transactionRepository = transactionRepository,
                accountRepository = accountRepository,
                merchantMappingRepository = merchantMappingRepository,
                travelSettingsRepository =
                    object : io.pm.finlight.ITravelSettingsRepository {
                        override suspend fun getCurrentTravelModeSettings(): io.pm.finlight.TravelModeSettings? =
                            settingsRepository.getCurrentTravelModeSettings()

                        override fun getTravelModeSettings(): kotlinx.coroutines.flow.Flow<io.pm.finlight.TravelModeSettings?> =
                            settingsRepository.getTravelModeSettings()

                        override suspend fun saveTravelModeSettings(settings: io.pm.finlight.TravelModeSettings?) =
                            settingsRepository.saveTravelModeSettings(settings)
                    },
                resolveTravelModeTagUseCase = resolveTravelModeTagUseCase,
                dispatcherProvider = testDispatcherProvider,
            )
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
                mergeTransactionsUseCase = mergeTransactionsUseCase,
                manageReimbursementUseCase = manageReimbursementUseCase,
                dispatcherProvider = testDispatcherProvider,
                autoSaveSmsTransactionUseCase = autoSaveUseCase,
            )
    }
}
