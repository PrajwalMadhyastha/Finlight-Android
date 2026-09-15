package io.pm.finlight.di

import android.app.Application
import android.os.Build
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import io.pm.finlight.IAccountRepository
import io.pm.finlight.ICategoryRepository
import io.pm.finlight.ISmsRepository
import io.pm.finlight.ITagRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.TestApplication
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.domain.usecase.ManageReimbursementUseCase
import io.pm.finlight.domain.usecase.MergeAccountsUseCase
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class ServiceLocatorDomainRepositoriesTest {
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
    fun provideTransactionRepository_returnsSingletonInstance() {
        val repo1 = ServiceLocator.provideTransactionRepository(application)
        val repo2 = ServiceLocator.provideTransactionRepository(application)

        assertNotNull(repo1)
        assertSame(repo1, repo2)
    }

    @Test
    fun provideAccountRepository_returnsSingletonInstance() {
        val repo1 = ServiceLocator.provideAccountRepository(application)
        val repo2 = ServiceLocator.provideAccountRepository(application)

        assertNotNull(repo1)
        assertSame(repo1, repo2)
    }

    @Test
    fun provideCategoryRepository_returnsSingletonInstance() {
        val repo1 = ServiceLocator.provideCategoryRepository(application)
        val repo2 = ServiceLocator.provideCategoryRepository(application)

        assertNotNull(repo1)
        assertSame(repo1, repo2)
    }

    @Test
    fun provideTagRepository_returnsSingletonInstance() {
        val repo1 = ServiceLocator.provideTagRepository(application)
        val repo2 = ServiceLocator.provideTagRepository(application)

        assertNotNull(repo1)
        assertSame(repo1, repo2)
    }

    @Test
    fun provideSmsRepository_returnsSingletonInstance() {
        val repo1 = ServiceLocator.provideSmsRepository(application)
        val repo2 = ServiceLocator.provideSmsRepository(application)

        assertNotNull(repo1)
        assertSame(repo1, repo2)
    }

    @Test
    fun provideMergeAccountsUseCase_returnsSingletonInstance() {
        val useCase1 = ServiceLocator.provideMergeAccountsUseCase(application)
        val useCase2 = ServiceLocator.provideMergeAccountsUseCase(application)

        assertNotNull(useCase1)
        assertSame(useCase1, useCase2)
    }

    @Test
    fun provideManageReimbursementUseCase_returnsSingletonInstance() {
        val useCase1 = ServiceLocator.provideManageReimbursementUseCase(application)
        val useCase2 = ServiceLocator.provideManageReimbursementUseCase(application)

        assertNotNull(useCase1)
        assertSame(useCase1, useCase2)
    }

    @Test
    fun setTransactionRepository_overridesInstance() {
        val mockRepo: ITransactionRepository = mockk(relaxed = true)
        ServiceLocator.setTransactionRepository(mockRepo)
        assertSame(mockRepo, ServiceLocator.provideTransactionRepository(application))

        val mockRepo2: ITransactionRepository = mockk(relaxed = true)
        ServiceLocator.setTransactionRepository(mockRepo2)
        assertSame(mockRepo2, ServiceLocator.provideTransactionRepository(application))

        ServiceLocator.setTransactionRepository(null)
        val defaultRepo = ServiceLocator.provideTransactionRepository(application)
        assertNotNull(defaultRepo)
        assertNotSame(mockRepo2, defaultRepo)
    }

    @Test
    fun setAccountRepository_overridesInstance() {
        val mockRepo: IAccountRepository = mockk(relaxed = true)
        ServiceLocator.setAccountRepository(mockRepo)
        assertSame(mockRepo, ServiceLocator.provideAccountRepository(application))

        val mockRepo2: IAccountRepository = mockk(relaxed = true)
        ServiceLocator.setAccountRepository(mockRepo2)
        assertSame(mockRepo2, ServiceLocator.provideAccountRepository(application))

        ServiceLocator.setAccountRepository(null)
        val defaultRepo = ServiceLocator.provideAccountRepository(application)
        assertNotNull(defaultRepo)
        assertNotSame(mockRepo2, defaultRepo)
    }

    @Test
    fun setCategoryRepository_overridesInstance() {
        val mockRepo: ICategoryRepository = mockk(relaxed = true)
        ServiceLocator.setCategoryRepository(mockRepo)
        assertSame(mockRepo, ServiceLocator.provideCategoryRepository(application))

        val mockRepo2: ICategoryRepository = mockk(relaxed = true)
        ServiceLocator.setCategoryRepository(mockRepo2)
        assertSame(mockRepo2, ServiceLocator.provideCategoryRepository(application))

        ServiceLocator.setCategoryRepository(null)
        val defaultRepo = ServiceLocator.provideCategoryRepository(application)
        assertNotNull(defaultRepo)
        assertNotSame(mockRepo2, defaultRepo)
    }

    @Test
    fun setTagRepository_overridesInstance() {
        val mockRepo: ITagRepository = mockk(relaxed = true)
        ServiceLocator.setTagRepository(mockRepo)
        assertSame(mockRepo, ServiceLocator.provideTagRepository(application))

        val mockRepo2: ITagRepository = mockk(relaxed = true)
        ServiceLocator.setTagRepository(mockRepo2)
        assertSame(mockRepo2, ServiceLocator.provideTagRepository(application))

        ServiceLocator.setTagRepository(null)
        val defaultRepo = ServiceLocator.provideTagRepository(application)
        assertNotNull(defaultRepo)
        assertNotSame(mockRepo2, defaultRepo)
    }

    @Test
    fun setSmsRepository_overridesInstance() {
        val mockRepo: ISmsRepository = mockk(relaxed = true)
        ServiceLocator.setSmsRepository(mockRepo)
        assertSame(mockRepo, ServiceLocator.provideSmsRepository(application))

        val mockRepo2: ISmsRepository = mockk(relaxed = true)
        ServiceLocator.setSmsRepository(mockRepo2)
        assertSame(mockRepo2, ServiceLocator.provideSmsRepository(application))

        ServiceLocator.setSmsRepository(null)
        val defaultRepo = ServiceLocator.provideSmsRepository(application)
        assertNotNull(defaultRepo)
        assertNotSame(mockRepo2, defaultRepo)
    }

    @Test
    fun setMergeAccountsUseCase_overridesInstance() {
        val mockUseCase: MergeAccountsUseCase = mockk(relaxed = true)
        ServiceLocator.setMergeAccountsUseCase(mockUseCase)
        assertSame(mockUseCase, ServiceLocator.provideMergeAccountsUseCase(application))

        val mockUseCase2: MergeAccountsUseCase = mockk(relaxed = true)
        ServiceLocator.setMergeAccountsUseCase(mockUseCase2)
        assertSame(mockUseCase2, ServiceLocator.provideMergeAccountsUseCase(application))

        ServiceLocator.setMergeAccountsUseCase(null)
        val defaultUseCase = ServiceLocator.provideMergeAccountsUseCase(application)
        assertNotNull(defaultUseCase)
        assertNotSame(mockUseCase2, defaultUseCase)
    }

    @Test
    fun setManageReimbursementUseCase_overridesInstance() {
        val mockUseCase: ManageReimbursementUseCase = mockk(relaxed = true)
        ServiceLocator.setManageReimbursementUseCase(mockUseCase)
        assertSame(mockUseCase, ServiceLocator.provideManageReimbursementUseCase(application))

        val mockUseCase2: ManageReimbursementUseCase = mockk(relaxed = true)
        ServiceLocator.setManageReimbursementUseCase(mockUseCase2)
        assertSame(mockUseCase2, ServiceLocator.provideManageReimbursementUseCase(application))

        ServiceLocator.setManageReimbursementUseCase(null)
        val defaultUseCase = ServiceLocator.provideManageReimbursementUseCase(application)
        assertNotNull(defaultUseCase)
        assertNotSame(mockUseCase2, defaultUseCase)
    }

    @Test
    fun reset_clearsAllDomainRepositories() {
        val mockTxn: ITransactionRepository = mockk(relaxed = true)
        val mockAccount: IAccountRepository = mockk(relaxed = true)
        val mockCategory: ICategoryRepository = mockk(relaxed = true)
        val mockTag: ITagRepository = mockk(relaxed = true)
        val mockSms: ISmsRepository = mockk(relaxed = true)
        val mockMergeAccounts: MergeAccountsUseCase = mockk(relaxed = true)
        val mockManageReimbursement: ManageReimbursementUseCase = mockk(relaxed = true)

        ServiceLocator.setTransactionRepository(mockTxn)
        ServiceLocator.setAccountRepository(mockAccount)
        ServiceLocator.setCategoryRepository(mockCategory)
        ServiceLocator.setTagRepository(mockTag)
        ServiceLocator.setSmsRepository(mockSms)
        ServiceLocator.setMergeAccountsUseCase(mockMergeAccounts)
        ServiceLocator.setManageReimbursementUseCase(mockManageReimbursement)

        assertSame(mockTxn, ServiceLocator.provideTransactionRepository(application))
        assertSame(mockAccount, ServiceLocator.provideAccountRepository(application))
        assertSame(mockCategory, ServiceLocator.provideCategoryRepository(application))
        assertSame(mockTag, ServiceLocator.provideTagRepository(application))
        assertSame(mockSms, ServiceLocator.provideSmsRepository(application))
        assertSame(mockMergeAccounts, ServiceLocator.provideMergeAccountsUseCase(application))
        assertSame(mockManageReimbursement, ServiceLocator.provideManageReimbursementUseCase(application))

        ServiceLocator.reset()

        val newTxn = ServiceLocator.provideTransactionRepository(application)
        val newAccount = ServiceLocator.provideAccountRepository(application)
        val newCategory = ServiceLocator.provideCategoryRepository(application)
        val newTag = ServiceLocator.provideTagRepository(application)
        val newSms = ServiceLocator.provideSmsRepository(application)
        val newMergeAccounts = ServiceLocator.provideMergeAccountsUseCase(application)
        val newManageReimbursement = ServiceLocator.provideManageReimbursementUseCase(application)

        assertNotNull(newTxn)
        assertNotSame(mockTxn, newTxn)
        assertNotNull(newAccount)
        assertNotSame(mockAccount, newAccount)
        assertNotNull(newCategory)
        assertNotSame(mockCategory, newCategory)
        assertNotNull(newTag)
        assertNotSame(mockTag, newTag)
        assertNotNull(newSms)
        assertNotSame(mockSms, newSms)
        assertNotNull(newMergeAccounts)
        assertNotSame(mockMergeAccounts, newMergeAccounts)
        assertNotNull(newManageReimbursement)
        assertNotSame(mockManageReimbursement, newManageReimbursement)
    }
}
