package io.pm.finlight.data.repository

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.*
import io.pm.finlight.data.TransactionRunner
import io.pm.finlight.data.db.dao.TransactionWriteDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class SplitTransactionRepositoryTest : BaseViewModelTest() {
    @Mock
    private lateinit var splitTransactionDao: SplitTransactionDao

    @Mock
    private lateinit var transactionWriteDao: TransactionWriteDao

    private lateinit var repository: SplitTransactionRepository

    @Before
    override fun setup() {
        super.setup()
        repository = SplitTransactionRepository(splitTransactionDao, transactionWriteDao)
    }

    @Test
    fun `getSplitsForParent flow proxies call to DAO`() =
        runTest {
            // Arrange
            val parentId = 1
            val mockSplits =
                listOf(
                    SplitTransactionDetails(
                        SplitTransaction(id = 1, parentTransactionId = parentId, amount = 50.0, categoryId = 1, notes = "Lunch"),
                        "Food",
                        "restaurant",
                        "red",
                    ),
                )
            `when`(splitTransactionDao.getSplitsForParent(parentId)).thenReturn(flowOf(mockSplits))

            // Act & Assert
            repository.getSplitsForParent(parentId).test {
                assertEquals(mockSplits, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            verify(splitTransactionDao).getSplitsForParent(parentId)
        }

    @Test
    fun `saveSplits executes transactionRunner and delegates to DAOs`() =
        runTest {
            val mockWriteDao: TransactionWriteDao = org.mockito.Mockito.mock(TransactionWriteDao::class.java)
            val runner =
                object : TransactionRunner {
                    override suspend fun <R> run(block: suspend () -> R): R = block()
                }
            val repo = SplitTransactionRepository(splitTransactionDao, mockWriteDao, runner)
            val splits = listOf(SplitTransaction(id = 1, parentTransactionId = 10, amount = 25.0, categoryId = 1, notes = "Snack"))

            repo.saveSplits(10, splits)

            verify(mockWriteDao).markAsSplit(10, true)
            verify(splitTransactionDao).deleteSplitsForParent(10)
            verify(splitTransactionDao).insertAll(splits)
        }

    @Test
    fun `unmarkSplit executes transactionRunner and delegates to DAOs`() =
        runTest {
            val mockWriteDao: TransactionWriteDao = org.mockito.Mockito.mock(TransactionWriteDao::class.java)
            val runner =
                object : TransactionRunner {
                    override suspend fun <R> run(block: suspend () -> R): R = block()
                }
            val repo = SplitTransactionRepository(splitTransactionDao, mockWriteDao, runner)
            `when`(splitTransactionDao.getSplitsForParentSimple(10)).thenReturn(emptyList())

            repo.unmarkSplit(10, "Original Desc")

            verify(splitTransactionDao).deleteSplitsForParent(10)
            verify(mockWriteDao).unmarkAsSplit(10, "Original Desc", null)
        }

    @Test
    fun `insertAll proxies call to DAO`() =
        runTest {
            val splits =
                listOf(
                    SplitTransaction(id = 1, parentTransactionId = 10, amount = 25.0, categoryId = 1, notes = "Snack"),
                )
            repository.insertAll(splits)
            verify(splitTransactionDao).insertAll(splits)
        }
}
