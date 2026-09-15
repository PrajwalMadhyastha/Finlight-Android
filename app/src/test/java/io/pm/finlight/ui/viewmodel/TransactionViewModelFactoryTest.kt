package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.ViewModel
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
import io.pm.finlight.TransactionViewModel
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.domain.usecase.ManageReimbursementUseCase
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
class TransactionViewModelFactoryTest {
    private lateinit var application: Application
    private lateinit var factory: TransactionViewModelFactory
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        db =
            Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        AppDatabase.setTestInstance(db)
        factory = TransactionViewModelFactory(application)
        ServiceLocator.reset()
    }

    @After
    fun tearDown() {
        db.close()
        ServiceLocator.reset()
    }

    @Test
    fun create_withTransactionViewModelClass_resolvesRepositoriesFromServiceLocator() {
        val mockTxnRepo: ITransactionRepository = mockk(relaxed = true)
        val mockAccountRepo: IAccountRepository = mockk(relaxed = true)
        val mockCategoryRepo: ICategoryRepository = mockk(relaxed = true)
        val mockTagRepo: ITagRepository = mockk(relaxed = true)
        val mockSmsRepo: ISmsRepository = mockk(relaxed = true)
        val mockManageReimbursementUseCase: ManageReimbursementUseCase = mockk(relaxed = true)

        ServiceLocator.setTransactionRepository(mockTxnRepo)
        ServiceLocator.setAccountRepository(mockAccountRepo)
        ServiceLocator.setCategoryRepository(mockCategoryRepo)
        ServiceLocator.setTagRepository(mockTagRepo)
        ServiceLocator.setSmsRepository(mockSmsRepo)
        ServiceLocator.setManageReimbursementUseCase(mockManageReimbursementUseCase)

        val viewModel = factory.create(TransactionViewModel::class.java)

        assertNotNull(viewModel)
        assertSame(mockTxnRepo, viewModel.transactionRepository)
        assertSame(mockAccountRepo, viewModel.accountRepository)
        assertSame(mockCategoryRepo, viewModel.categoryRepository)

        val tagField =
            TransactionViewModel::class.java.getDeclaredField("tagRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockTagRepo, tagField)

        val smsField =
            TransactionViewModel::class.java.getDeclaredField("smsRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockSmsRepo, smsField)

        val reimbursementUseCaseField =
            TransactionViewModel::class.java.getDeclaredField("manageReimbursementUseCase").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockManageReimbursementUseCase, reimbursementUseCaseField)
    }

    @Test
    fun create_withUnknownViewModelClass_throwsIllegalArgumentException() {
        class UnknownViewModel : ViewModel()

        assertThrows(IllegalArgumentException::class.java) {
            factory.create(UnknownViewModel::class.java)
        }
    }
}
