package io.pm.finlight.data.repository

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.MerchantCategoryMapping
import io.pm.finlight.MerchantCategoryMappingDao
import io.pm.finlight.MerchantCategoryMappingRepository
import io.pm.finlight.TestApplication
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class MerchantCategoryMappingRepositoryTest : BaseViewModelTest() {

    @Mock
    private lateinit var merchantCategoryMappingDao: MerchantCategoryMappingDao

    private lateinit var repository: MerchantCategoryMappingRepository

    @Before
    override fun setup() {
        super.setup()
        repository = MerchantCategoryMappingRepository(merchantCategoryMappingDao)
    }

    @Test
    fun `insert calls DAO`() = runTest {
        // Arrange
        val newMapping = MerchantCategoryMapping(parsedName = "Zomato", categoryId = 1)

        // Act
        repository.insert(newMapping)

        // Assert
        verify(merchantCategoryMappingDao).insert(newMapping)
    }
}

