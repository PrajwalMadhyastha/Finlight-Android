package io.pm.finlight.di

import android.app.Application
import android.os.Build
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import io.pm.finlight.BudgetRepository
import io.pm.finlight.IBudgetRepository
import io.pm.finlight.IMerchantRenameRuleRepository
import io.pm.finlight.IRecurringTransactionRepository
import io.pm.finlight.ISplitTransactionRepository
import io.pm.finlight.MerchantRenameRuleRepository
import io.pm.finlight.RecurringTransactionRepository
import io.pm.finlight.SplitTransactionRepository
import io.pm.finlight.TestApplication
import io.pm.finlight.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class ServiceLocatorTest {
    private lateinit var application: Application
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        db =
            Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        AppDatabase.setTestInstance(db)
        ServiceLocator.reset()
    }

    @After
    fun tearDown() {
        db.close()
        ServiceLocator.reset()
    }

    @Test
    fun provideBudgetRepository_returnsSingletonInstance() {
        val repo1 = ServiceLocator.provideBudgetRepository(application)
        val repo2 = ServiceLocator.provideBudgetRepository(application)

        assertNotNull(repo1)
        assertSame(repo1, repo2)
        assertTrue(repo1 is BudgetRepository)
    }

    @Test
    fun provideRecurringTransactionRepository_returnsSingletonInstance() {
        val repo1 = ServiceLocator.provideRecurringTransactionRepository(application)
        val repo2 = ServiceLocator.provideRecurringTransactionRepository(application)

        assertNotNull(repo1)
        assertSame(repo1, repo2)
        assertTrue(repo1 is RecurringTransactionRepository)
    }

    @Test
    fun provideSplitTransactionRepository_returnsSingletonInstance() {
        val repo1 = ServiceLocator.provideSplitTransactionRepository(application)
        val repo2 = ServiceLocator.provideSplitTransactionRepository(application)

        assertNotNull(repo1)
        assertSame(repo1, repo2)
        assertTrue(repo1 is SplitTransactionRepository)
    }

    @Test
    fun provideMerchantRenameRuleRepository_returnsSingletonInstance() {
        val repo1 = ServiceLocator.provideMerchantRenameRuleRepository(application)
        val repo2 = ServiceLocator.provideMerchantRenameRuleRepository(application)

        assertNotNull(repo1)
        assertSame(repo1, repo2)
        assertTrue(repo1 is MerchantRenameRuleRepository)
    }

    @Test
    fun provideBudgetRepository_withResolvedDb_usesSuppliedDatabase() {
        val repo = ServiceLocator.provideBudgetRepository(application, db)
        assertNotNull(repo)
        assertSame(repo, ServiceLocator.provideBudgetRepository(application))
    }

    @Test
    fun provideRecurringTransactionRepository_withResolvedDb_usesSuppliedDatabase() {
        val repo = ServiceLocator.provideRecurringTransactionRepository(application, db)
        assertNotNull(repo)
        assertSame(repo, ServiceLocator.provideRecurringTransactionRepository(application))
    }

    @Test
    fun provideSplitTransactionRepository_withResolvedDb_usesSuppliedDatabase() {
        val repo = ServiceLocator.provideSplitTransactionRepository(application, db)
        assertNotNull(repo)
        assertSame(repo, ServiceLocator.provideSplitTransactionRepository(application))
    }

    @Test
    fun provideMerchantRenameRuleRepository_withResolvedDb_usesSuppliedDatabase() {
        val repo = ServiceLocator.provideMerchantRenameRuleRepository(application, db)
        assertNotNull(repo)
        assertSame(repo, ServiceLocator.provideMerchantRenameRuleRepository(application))
    }

    @Test
    fun setBudgetRepository_overridesInstance() {
        val mockRepo: IBudgetRepository = mockk(relaxed = true)
        ServiceLocator.setBudgetRepository(mockRepo)
        assertSame(mockRepo, ServiceLocator.provideBudgetRepository(application))

        val mockRepo2: IBudgetRepository = mockk(relaxed = true)
        ServiceLocator.setBudgetRepository(mockRepo2)
        assertSame(mockRepo2, ServiceLocator.provideBudgetRepository(application))

        ServiceLocator.setBudgetRepository(null)
        val defaultRepo = ServiceLocator.provideBudgetRepository(application)
        assertNotNull(defaultRepo)
        assertNotSame(mockRepo2, defaultRepo)
    }

    @Test
    fun setRecurringTransactionRepository_overridesInstance() {
        val mockRepo: IRecurringTransactionRepository = mockk(relaxed = true)
        ServiceLocator.setRecurringTransactionRepository(mockRepo)
        assertSame(mockRepo, ServiceLocator.provideRecurringTransactionRepository(application))

        val mockRepo2: IRecurringTransactionRepository = mockk(relaxed = true)
        ServiceLocator.setRecurringTransactionRepository(mockRepo2)
        assertSame(mockRepo2, ServiceLocator.provideRecurringTransactionRepository(application))

        ServiceLocator.setRecurringTransactionRepository(null)
        val defaultRepo = ServiceLocator.provideRecurringTransactionRepository(application)
        assertNotNull(defaultRepo)
        assertNotSame(mockRepo2, defaultRepo)
    }

    @Test
    fun setSplitTransactionRepository_overridesInstance() {
        val mockRepo: ISplitTransactionRepository = mockk(relaxed = true)
        ServiceLocator.setSplitTransactionRepository(mockRepo)
        assertSame(mockRepo, ServiceLocator.provideSplitTransactionRepository(application))

        val mockRepo2: ISplitTransactionRepository = mockk(relaxed = true)
        ServiceLocator.setSplitTransactionRepository(mockRepo2)
        assertSame(mockRepo2, ServiceLocator.provideSplitTransactionRepository(application))

        ServiceLocator.setSplitTransactionRepository(null)
        val defaultRepo = ServiceLocator.provideSplitTransactionRepository(application)
        assertNotNull(defaultRepo)
        assertNotSame(mockRepo2, defaultRepo)
    }

    @Test
    fun setMerchantRenameRuleRepository_overridesInstance() {
        val mockRepo: IMerchantRenameRuleRepository = mockk(relaxed = true)
        ServiceLocator.setMerchantRenameRuleRepository(mockRepo)
        assertSame(mockRepo, ServiceLocator.provideMerchantRenameRuleRepository(application))

        val mockRepo2: IMerchantRenameRuleRepository = mockk(relaxed = true)
        ServiceLocator.setMerchantRenameRuleRepository(mockRepo2)
        assertSame(mockRepo2, ServiceLocator.provideMerchantRenameRuleRepository(application))

        ServiceLocator.setMerchantRenameRuleRepository(null)
        val defaultRepo = ServiceLocator.provideMerchantRenameRuleRepository(application)
        assertNotNull(defaultRepo)
        assertNotSame(mockRepo2, defaultRepo)
    }

    @Test
    fun reset_clearsAllFourNewDomainRepositories() {
        val mockBudget: IBudgetRepository = mockk(relaxed = true)
        val mockRecurring: IRecurringTransactionRepository = mockk(relaxed = true)
        val mockSplit: ISplitTransactionRepository = mockk(relaxed = true)
        val mockRename: IMerchantRenameRuleRepository = mockk(relaxed = true)

        ServiceLocator.setBudgetRepository(mockBudget)
        ServiceLocator.setRecurringTransactionRepository(mockRecurring)
        ServiceLocator.setSplitTransactionRepository(mockSplit)
        ServiceLocator.setMerchantRenameRuleRepository(mockRename)

        assertSame(mockBudget, ServiceLocator.provideBudgetRepository(application))
        assertSame(mockRecurring, ServiceLocator.provideRecurringTransactionRepository(application))
        assertSame(mockSplit, ServiceLocator.provideSplitTransactionRepository(application))
        assertSame(mockRename, ServiceLocator.provideMerchantRenameRuleRepository(application))

        ServiceLocator.reset()

        val newBudget = ServiceLocator.provideBudgetRepository(application)
        val newRecurring = ServiceLocator.provideRecurringTransactionRepository(application)
        val newSplit = ServiceLocator.provideSplitTransactionRepository(application)
        val newRename = ServiceLocator.provideMerchantRenameRuleRepository(application)

        assertNotNull(newBudget)
        assertNotSame(mockBudget, newBudget)
        assertNotNull(newRecurring)
        assertNotSame(mockRecurring, newRecurring)
        assertNotNull(newSplit)
        assertNotSame(mockSplit, newSplit)
        assertNotNull(newRename)
        assertNotSame(mockRename, newRename)
    }

    @Test
    fun concurrentAccess_returnsSameSingletonInstances() =
        runBlocking {
            val budgetInstances =
                (1..10).map {
                    async(Dispatchers.Default) {
                        ServiceLocator.provideBudgetRepository(application)
                    }
                }.awaitAll()

            val recurringInstances =
                (1..10).map {
                    async(Dispatchers.Default) {
                        ServiceLocator.provideRecurringTransactionRepository(application)
                    }
                }.awaitAll()

            val splitInstances =
                (1..10).map {
                    async(Dispatchers.Default) {
                        ServiceLocator.provideSplitTransactionRepository(application)
                    }
                }.awaitAll()

            val renameInstances =
                (1..10).map {
                    async(Dispatchers.Default) {
                        ServiceLocator.provideMerchantRenameRuleRepository(application)
                    }
                }.awaitAll()

            budgetInstances.forEach { assertSame(budgetInstances.first(), it) }
            recurringInstances.forEach { assertSame(recurringInstances.first(), it) }
            splitInstances.forEach { assertSame(splitInstances.first(), it) }
            renameInstances.forEach { assertSame(renameInstances.first(), it) }
        }
}
