package io.pm.finlight

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.di.ServiceLocator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Proxy

@RunWith(AndroidJUnit4::class)
class ServiceLocatorRepositorySwapTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setup() {
        ServiceLocator.reset()
    }

    @After
    fun tearDown() {
        ServiceLocator.reset()
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> createFakeRepository(): T {
        return Proxy.newProxyInstance(
            T::class.java.classLoader,
            arrayOf(T::class.java),
        ) { _, method, _ ->
            when (method.returnType) {
                Boolean::class.javaPrimitiveType -> false
                Int::class.javaPrimitiveType -> 0
                Long::class.javaPrimitiveType -> 0L
                Float::class.javaPrimitiveType -> 0f
                Double::class.javaPrimitiveType -> 0.0
                Flow::class.java -> emptyFlow<Any>()
                List::class.java -> emptyList<Any>()
                else -> null
            }
        } as T
    }

    @Test
    fun serviceLocator_swapsAccountRepositoryForTestingInAndroidTest() {
        val mockAccountRepo: IAccountRepository = createFakeRepository()
        ServiceLocator.setAccountRepository(mockAccountRepo)

        val resolved = ServiceLocator.provideAccountRepository(context)
        assertSame(mockAccountRepo, resolved)
    }

    @Test
    fun serviceLocator_swapsTransactionRepositoryForTestingInAndroidTest() {
        val mockTxnRepo: ITransactionRepository = createFakeRepository()
        ServiceLocator.setTransactionRepository(mockTxnRepo)

        val resolved = ServiceLocator.provideTransactionRepository(context)
        assertSame(mockTxnRepo, resolved)
    }

    @Test
    fun serviceLocator_swapsCategoryRepositoryForTestingInAndroidTest() {
        val mockCategoryRepo: ICategoryRepository = createFakeRepository()
        ServiceLocator.setCategoryRepository(mockCategoryRepo)

        val resolved = ServiceLocator.provideCategoryRepository(context)
        assertSame(mockCategoryRepo, resolved)
    }

    @Test
    fun serviceLocator_swapsTagRepositoryForTestingInAndroidTest() {
        val mockTagRepo: ITagRepository = createFakeRepository()
        ServiceLocator.setTagRepository(mockTagRepo)

        val resolved = ServiceLocator.provideTagRepository(context)
        assertSame(mockTagRepo, resolved)
    }

    @Test
    fun serviceLocator_swapsSmsRepositoryForTestingInAndroidTest() {
        val mockSmsRepo: ISmsRepository = createFakeRepository()
        ServiceLocator.setSmsRepository(mockSmsRepo)

        val resolved = ServiceLocator.provideSmsRepository(context)
        assertSame(mockSmsRepo, resolved)
    }

    @Test
    fun serviceLocator_swapsBudgetRepositoryForTestingInAndroidTest() {
        val mockBudgetRepo: IBudgetRepository = createFakeRepository()
        ServiceLocator.setBudgetRepository(mockBudgetRepo)

        val resolved = ServiceLocator.provideBudgetRepository(context)
        assertSame(mockBudgetRepo, resolved)
    }

    @Test
    fun serviceLocator_swapsRecurringTransactionRepositoryForTestingInAndroidTest() {
        val mockRecurringRepo: IRecurringTransactionRepository = createFakeRepository()
        ServiceLocator.setRecurringTransactionRepository(mockRecurringRepo)

        val resolved = ServiceLocator.provideRecurringTransactionRepository(context)
        assertSame(mockRecurringRepo, resolved)
    }

    @Test
    fun serviceLocator_swapsSplitTransactionRepositoryForTestingInAndroidTest() {
        val mockSplitRepo: ISplitTransactionRepository = createFakeRepository()
        ServiceLocator.setSplitTransactionRepository(mockSplitRepo)

        val resolved = ServiceLocator.provideSplitTransactionRepository(context)
        assertSame(mockSplitRepo, resolved)
    }

    @Test
    fun serviceLocator_swapsMerchantRenameRuleRepositoryForTestingInAndroidTest() {
        val mockRenameRepo: IMerchantRenameRuleRepository = createFakeRepository()
        ServiceLocator.setMerchantRenameRuleRepository(mockRenameRepo)

        val resolved = ServiceLocator.provideMerchantRenameRuleRepository(context)
        assertSame(mockRenameRepo, resolved)
    }

    @Test
    fun serviceLocator_swapsMerchantCategoryMappingRepositoryForTestingInAndroidTest() {
        val mockCategoryMappingRepo: IMerchantCategoryMappingRepository = createFakeRepository()
        ServiceLocator.setMerchantCategoryMappingRepository(mockCategoryMappingRepo)

        val resolved = ServiceLocator.provideMerchantCategoryMappingRepository(context)
        assertSame(mockCategoryMappingRepo, resolved)
    }

    @Test
    fun serviceLocator_reset_restoresDefaultRepositories() {
        val mockAccountRepo: IAccountRepository = createFakeRepository()
        val mockTxnRepo: ITransactionRepository = createFakeRepository()
        val mockBudgetRepo: IBudgetRepository = createFakeRepository()
        val mockRecurringRepo: IRecurringTransactionRepository = createFakeRepository()
        val mockSplitRepo: ISplitTransactionRepository = createFakeRepository()
        val mockRenameRepo: IMerchantRenameRuleRepository = createFakeRepository()
        val mockCategoryMappingRepo: IMerchantCategoryMappingRepository = createFakeRepository()

        ServiceLocator.setAccountRepository(mockAccountRepo)
        ServiceLocator.setTransactionRepository(mockTxnRepo)
        ServiceLocator.setBudgetRepository(mockBudgetRepo)
        ServiceLocator.setRecurringTransactionRepository(mockRecurringRepo)
        ServiceLocator.setSplitTransactionRepository(mockSplitRepo)
        ServiceLocator.setMerchantRenameRuleRepository(mockRenameRepo)
        ServiceLocator.setMerchantCategoryMappingRepository(mockCategoryMappingRepo)

        assertSame(mockAccountRepo, ServiceLocator.provideAccountRepository(context))
        assertSame(mockTxnRepo, ServiceLocator.provideTransactionRepository(context))
        assertSame(mockBudgetRepo, ServiceLocator.provideBudgetRepository(context))
        assertSame(mockRecurringRepo, ServiceLocator.provideRecurringTransactionRepository(context))
        assertSame(mockSplitRepo, ServiceLocator.provideSplitTransactionRepository(context))
        assertSame(mockRenameRepo, ServiceLocator.provideMerchantRenameRuleRepository(context))
        assertSame(mockCategoryMappingRepo, ServiceLocator.provideMerchantCategoryMappingRepository(context))

        ServiceLocator.reset()

        val restoredAccountRepo = ServiceLocator.provideAccountRepository(context)
        val restoredTxnRepo = ServiceLocator.provideTransactionRepository(context)
        val restoredBudgetRepo = ServiceLocator.provideBudgetRepository(context)
        val restoredRecurringRepo = ServiceLocator.provideRecurringTransactionRepository(context)
        val restoredSplitRepo = ServiceLocator.provideSplitTransactionRepository(context)
        val restoredRenameRepo = ServiceLocator.provideMerchantRenameRuleRepository(context)
        val restoredCategoryMappingRepo = ServiceLocator.provideMerchantCategoryMappingRepository(context)

        assertNotNull(restoredAccountRepo)
        assertNotNull(restoredTxnRepo)
        assertNotNull(restoredBudgetRepo)
        assertNotNull(restoredRecurringRepo)
        assertNotNull(restoredSplitRepo)
        assertNotNull(restoredRenameRepo)
        assertNotNull(restoredCategoryMappingRepo)
        assertNotSame(mockAccountRepo, restoredAccountRepo)
        assertNotSame(mockTxnRepo, restoredTxnRepo)
        assertNotSame(mockBudgetRepo, restoredBudgetRepo)
        assertNotSame(mockRecurringRepo, restoredRecurringRepo)
        assertNotSame(mockSplitRepo, restoredSplitRepo)
        assertNotSame(mockRenameRepo, restoredRenameRepo)
        assertNotSame(mockCategoryMappingRepo, restoredCategoryMappingRepo)
    }

    @Test
    fun serviceLocator_swappedRepositoryReflectedInComposeUI() {
        val fakeRepo =
            object : IAccountRepository by createFakeRepository() {
                override val accountsWithBalance: Flow<List<AccountWithBalance>> = emptyFlow()
                override val allAccounts: Flow<List<Account>> =
                    flowOf(listOf(Account(id = 999, name = "Swap Test Account", type = "Bank")))
            }

        ServiceLocator.setAccountRepository(fakeRepo)

        composeTestRule.setContent {
            val repo = ServiceLocator.provideAccountRepository(context)
            val accounts by repo.allAccounts.collectAsState(initial = emptyList())
            Column {
                accounts.forEach { account ->
                    Text(text = account.name)
                }
            }
        }

        composeTestRule.onNodeWithText("Swap Test Account").assertIsDisplayed()
    }
}
