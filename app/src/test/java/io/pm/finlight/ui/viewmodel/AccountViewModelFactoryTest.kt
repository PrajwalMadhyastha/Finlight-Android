package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import io.pm.finlight.IAccountRepository
import io.pm.finlight.ISettingsRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.TestApplication
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.domain.usecase.MergeAccountsUseCase
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class AccountViewModelFactoryTest {
    private lateinit var application: Application
    private lateinit var factory: AccountViewModelFactory
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        db =
            Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        AppDatabase.setTestInstance(db)
        factory = AccountViewModelFactory(application)
        ServiceLocator.reset()
    }

    @After
    fun tearDown() {
        db.close()
        ServiceLocator.reset()
    }

    @Test
    fun create_withAccountViewModelClass_resolvesDependenciesFromServiceLocator() {
        val mockSettingsRepo: ISettingsRepository = mockk(relaxed = true)
        val mockTxnRepo: ITransactionRepository = mockk(relaxed = true)
        val mockAccountRepo: IAccountRepository = mockk(relaxed = true)
        val mockMergeUseCase: MergeAccountsUseCase = mockk(relaxed = true)

        ServiceLocator.setSettingsRepository(mockSettingsRepo)
        ServiceLocator.setTransactionRepository(mockTxnRepo)
        ServiceLocator.setAccountRepository(mockAccountRepo)
        ServiceLocator.setMergeAccountsUseCase(mockMergeUseCase)

        val viewModel = factory.create(AccountViewModel::class.java)

        assertNotNull(viewModel)

        val repoField =
            AccountViewModel::class.java.getDeclaredField("repository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockAccountRepo, repoField)

        val txnRepoField =
            AccountViewModel::class.java.getDeclaredField("transactionRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockTxnRepo, txnRepoField)

        val settingsRepoField =
            AccountViewModel::class.java.getDeclaredField("settingsRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockSettingsRepo, settingsRepoField)

        val mergeUseCaseField =
            AccountViewModel::class.java.getDeclaredField("mergeAccountsUseCase").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockMergeUseCase, mergeUseCaseField)
    }

    @Test
    fun create_withUnknownViewModelClass_throwsIllegalArgumentException() {
        class UnknownViewModel : ViewModel()

        assertThrows(IllegalArgumentException::class.java) {
            factory.create(UnknownViewModel::class.java)
        }
    }
}
