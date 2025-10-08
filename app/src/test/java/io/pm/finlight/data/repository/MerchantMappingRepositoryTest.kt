package io.pm.finlight.data.repository

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.MerchantMapping
import io.pm.finlight.MerchantMappingDao
import io.pm.finlight.MerchantMappingRepository
import io.pm.finlight.TestApplication
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class MerchantMappingRepositoryTest : BaseViewModelTest() {

    @Mock
    private lateinit var merchantMappingDao: MerchantMappingDao

    @Test
    fun `allMappings flow proxies call to DAO`() = runTest {
        // Arrange
        val mockMappings = listOf(MerchantMapping(smsSender = "AM-HDFCBK", merchantName = "HDFC Bank"))
        `when`(merchantMappingDao.getAllMappings()).thenReturn(flowOf(mockMappings))
        val repository = MerchantMappingRepository(merchantMappingDao)


        // Act & Assert
        repository.allMappings.test {
            assertEquals(mockMappings, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        verify(merchantMappingDao).getAllMappings()
    }

    @Test
    fun `insert calls DAO`() = runTest {
        // Arrange
        val repository = MerchantMappingRepository(merchantMappingDao)
        val newMapping = MerchantMapping(smsSender = "VM-ICICIB", merchantName = "ICICI Bank")

        // Act
        repository.insert(newMapping)

        // Assert
        verify(merchantMappingDao).insert(newMapping)
    }
}

