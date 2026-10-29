package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.Account
import io.pm.finlight.AccountRepository
import io.pm.finlight.AccountType
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.CategoryRepository
import io.pm.finlight.ICustomSmsRuleRepository
import io.pm.finlight.IIgnoreRuleRepository
import io.pm.finlight.ISmsParseTemplateRepository
import io.pm.finlight.MerchantCategoryMappingRepository
import io.pm.finlight.MerchantMappingRepository
import io.pm.finlight.MerchantRenameRuleRepository
import io.pm.finlight.SettingsRepository
import io.pm.finlight.SmsRepository
import io.pm.finlight.SplitTransactionRepository
import io.pm.finlight.TagRepository
import io.pm.finlight.TestApplication
import io.pm.finlight.TransactionRepository
import io.pm.finlight.TransactionViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.robolectric.annotation.Config
import org.mockito.Mockito.`when` as whenever

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
abstract class TransactionViewModelBaseSetup : BaseViewModelTest() {
    protected val applicationContext: Application = ApplicationProvider.getApplicationContext()

    // Mocks for all repository dependencies
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

    @Mock protected lateinit var customSmsRuleRepository: ICustomSmsRuleRepository

    @Mock protected lateinit var ignoreRuleRepository: IIgnoreRuleRepository

    @Mock protected lateinit var smsParseTemplateRepository: ISmsParseTemplateRepository

    @Mock protected lateinit var mergeTransactionsUseCase: io.pm.finlight.domain.usecase.MergeTransactionsUseCase

    @Mock protected lateinit var manageReimbursementUseCase: io.pm.finlight.domain.usecase.ManageReimbursementUseCase

    protected lateinit var viewModel: TransactionViewModel

    @Before
    override fun setup() {
        super.setup()

        // Setup default mock behaviors for ViewModel initialization
        setupDefaultMocks()

        // Create the ViewModel instance with all mocked dependencies
        initializeViewModel()
    }

    protected fun setupDefaultMocks() {
        runTest {
            whenever(merchantRenameRuleRepository.getAliasesAsMap()).thenReturn(flowOf(emptyMap()))
            whenever(merchantRenameRuleRepository.getAllRules()).thenReturn(flowOf(emptyList()))
            whenever(merchantCategoryMappingRepository.getAllMappings()).thenReturn(flowOf(emptyList()))
            whenever(merchantCategoryMappingRepository.getCategoryIdForMerchant(anyString())).thenReturn(null)
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
            whenever(accountRepository.findByName(anyString())).thenReturn(null)
            whenever(settingsRepository.getTravelModeSettings()).thenReturn(flowOf(null))
            whenever(settingsRepository.getCurrentTravelModeSettings()).thenReturn(null)
            whenever(settingsRepository.getPrivacyModeEnabled()).thenReturn(flowOf(false))
            whenever(transactionRepository.searchMerchants(anyString())).thenReturn(flowOf(emptyList()))
            whenever(transactionRepository.getRecentManualTransactions(anyInt())).thenReturn(flowOf(emptyList()))
            whenever(transactionRepository.getReimbursementsForExpense(anyInt())).thenReturn(flowOf(emptyList()))
            whenever(transactionRepository.getSmsHashesByIds(any())).thenReturn(emptyList())
            whenever(transactionRepository.existsBySmsHash(anyString())).thenReturn(false)
            whenever(transactionRepository.existsDeletedSmsHash(anyString())).thenReturn(false)
            whenever(merchantMappingRepository.allMappings).thenReturn(flowOf(emptyList()))
            whenever(customSmsRuleRepository.getAllRules()).thenReturn(flowOf(emptyList()))
            whenever(ignoreRuleRepository.getEnabledRules()).thenReturn(emptyList())
            whenever(smsParseTemplateRepository.getAllTemplates()).thenReturn(emptyList())
            whenever(smsParseTemplateRepository.getTemplatesBySignature(anyString())).thenReturn(emptyList())
            whenever(accountRepository.insert(any())).thenReturn(1L)
            whenever(accountRepository.getAccountByIdSync(anyInt())).thenAnswer { invocation ->
                val id = invocation.getArgument<Int>(0)
                Account(id = id, name = "Account $id", type = "Bank")
            }
            whenever(accountRepository.findOrCreateByName(anyString(), anyString())).thenAnswer { invocation ->
                val name = invocation.getArgument<String>(0)
                val type = invocation.getArgument<String>(1)
                kotlinx.coroutines.runBlocking {
                    val existing = accountRepository.findByName(name)
                    if (existing != null) return@runBlocking existing
                    val newId = accountRepository.insert(Account(name = name, type = type)) ?: 1L
                    if (newId != -1L) {
                        accountRepository.getAccountByIdSync(newId.toInt()) ?: Account(id = newId.toInt(), name = name, type = type)
                    } else {
                        accountRepository.findByName(name) ?: Account(name = name, type = type)
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
                resolveTravelModeTagUseCase = resolveTravelModeTagUseCase,
                mergeTransactionsUseCase = mergeTransactionsUseCase,
                manageReimbursementUseCase = manageReimbursementUseCase,
                dispatcherProvider = testDispatcherProvider,
                autoSaveSmsTransactionUseCase = autoSaveUseCase,
                customSmsRuleRepository = customSmsRuleRepository,
                ignoreRuleRepository = ignoreRuleRepository,
                smsParseTemplateRepository = smsParseTemplateRepository,
            )
    }
}
