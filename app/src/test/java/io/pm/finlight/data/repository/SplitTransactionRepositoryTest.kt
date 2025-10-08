package io.pm.finlight.data.repository

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.*
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

    private lateinit var repository: SplitTransactionRepository

    @Before
    override fun setup() {
        super.setup()
        repository = SplitTransactionRepository(splitTransactionDao)
    }

    @Test
    fun `getSplitsForParent flow proxies call to DAO`() = runTest {
        // Arrange
        val parentId = 1
        val mockSplits = listOf(
            SplitTransactionDetails(
                SplitTransaction(id = 1, parentTransactionId = parentId, amount = 50.0, categoryId = 1, notes = "Lunch"),
                "Food", "restaurant", "red"
            )
        )
        `when`(splitTransactionDao.getSplitsForParent(parentId)).thenReturn(flowOf(mockSplits))

        // Act & Assert
        repository.getSplitsForParent(parentId).test {
            assertEquals(mockSplits, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        verify(splitTransactionDao).getSplitsForParent(parentId)
    }
}

