package io.pm.finlight.ui.viewmodel

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.IRecurringTransactionRepository
import io.pm.finlight.PotentialTransaction
import io.pm.finlight.TestApplication
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionRepository
import io.pm.finlight.utils.TestDispatcherProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.annotation.Config
import java.util.Calendar

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class LinkRecurringTransactionViewModelTest : BaseViewModelTest() {
    @Mock
    private lateinit var transactionRepository: TransactionRepository

    @Mock
    private lateinit var recurringTransactionRepository: IRecurringTransactionRepository

    @Captor
    private lateinit var intCaptor: ArgumentCaptor<Int>

    @Captor
    private lateinit var longCaptor: ArgumentCaptor<Long>

    private lateinit var viewModel: LinkRecurringTransactionViewModel

    private val potentialTxn =
        PotentialTransaction(
            sourceSmsId = 42L,
            smsSender = "RecurringSender",
            amount = 250.0,
            transactionType = "expense",
            merchantName = "Netflix",
            originalMessage = "Netflix subscription due",
            sourceSmsHash = "hash_netflix_42",
        )

    @Before
    override fun setup() {
        super.setup()
    }

    @Test
    fun `init calls findMatches and populates linkableTransactions`() =
        runTest {
            val mockTransactions =
                listOf(
                    Transaction(id = 1, description = "Txn 1", amount = 250.0, date = 0L, accountId = 1, categoryId = 1, notes = null),
                )
            `when`(transactionRepository.getAllTransactionsForRange(anyLong(), anyLong())).thenReturn(flowOf(mockTransactions))

            viewModel = LinkRecurringTransactionViewModel(transactionRepository, recurringTransactionRepository, potentialTxn, TestDispatcherProvider(testDispatcher))

            viewModel.linkableTransactions.test {
                assertEquals(mockTransactions, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            verify(transactionRepository).getAllTransactionsForRange(anyLong(), anyLong())
        }

    @Test
    fun `linkTransaction updates sms hash and rule lastRunDate`() =
        runTest {
            `when`(transactionRepository.getAllTransactionsForRange(anyLong(), anyLong())).thenReturn(flowOf(emptyList()))
            viewModel = LinkRecurringTransactionViewModel(transactionRepository, recurringTransactionRepository, potentialTxn, TestDispatcherProvider(testDispatcher))
            val selectedTxnId = 99
            var onCompleteCalled = false

            viewModel.linkTransaction(selectedTxnId) { onCompleteCalled = true }

            verify(transactionRepository).setSmsHash(selectedTxnId, potentialTxn.sourceSmsHash!!)
            verify(recurringTransactionRepository).updateLastRunDate(intCaptor.capture(), longCaptor.capture())

            val capturedRuleId = intCaptor.value
            val capturedTimestamp = longCaptor.value

            assertEquals(potentialTxn.sourceSmsId.toInt(), capturedRuleId)
            assertTrue("Timestamp should be recent", System.currentTimeMillis() - capturedTimestamp < 1000)
            assertTrue("onComplete lambda should be called", onCompleteCalled)
        }

    @Test
    fun `linkTransaction handles null sourceSmsHash gracefully`() =
        runTest {
            val txnWithoutHash = potentialTxn.copy(sourceSmsHash = null)
            `when`(transactionRepository.getAllTransactionsForRange(anyLong(), anyLong())).thenReturn(flowOf(emptyList()))
            viewModel = LinkRecurringTransactionViewModel(transactionRepository, recurringTransactionRepository, txnWithoutHash, TestDispatcherProvider(testDispatcher))
            val selectedTxnId = 99
            var onCompleteCalled = false

            viewModel.linkTransaction(selectedTxnId) { onCompleteCalled = true }

            verify(recurringTransactionRepository).updateLastRunDate(intCaptor.capture(), longCaptor.capture())
            assertEquals(potentialTxn.sourceSmsId.toInt(), intCaptor.value)
            assertTrue("onComplete lambda should be called", onCompleteCalled)
        }

    @Test
    fun `remindTomorrow updates rule lastRunDate to yesterday`() =
        runTest {
            `when`(transactionRepository.getAllTransactionsForRange(anyLong(), anyLong())).thenReturn(flowOf(emptyList()))
            viewModel = LinkRecurringTransactionViewModel(transactionRepository, recurringTransactionRepository, potentialTxn, TestDispatcherProvider(testDispatcher))
            var onCompleteCalled = false

            viewModel.remindTomorrow { onCompleteCalled = true }

            verify(recurringTransactionRepository).updateLastRunDate(intCaptor.capture(), longCaptor.capture())
            val capturedRuleId = intCaptor.value
            val capturedTimestamp = longCaptor.value

            val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
            val oneSecondInMillis = 1000

            assertEquals(potentialTxn.sourceSmsId.toInt(), capturedRuleId)
            assertTrue(
                "Captured timestamp should be approximately yesterday",
                kotlin.math.abs(capturedTimestamp - yesterday) < oneSecondInMillis,
            )
            assertTrue("onComplete lambda should be called", onCompleteCalled)
        }
}
