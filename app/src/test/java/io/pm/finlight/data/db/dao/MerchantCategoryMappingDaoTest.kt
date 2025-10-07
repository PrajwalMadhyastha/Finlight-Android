package io.pm.finlight.data.db.dao

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.MerchantCategoryMapping
import io.pm.finlight.MerchantCategoryMappingDao
import io.pm.finlight.TestApplication
import io.pm.finlight.util.DatabaseTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class MerchantCategoryMappingDaoTest {

    @get:Rule
    val dbRule = DatabaseTestRule()

    private lateinit var merchantCategoryMappingDao: MerchantCategoryMappingDao

    @Before
    fun setup() {
        merchantCategoryMappingDao = dbRule.db.merchantCategoryMappingDao()
    }

    @Test
    fun `getCategoryIdForMerchant retrieves correct category ID`() = runTest {
        // Arrange
        val mapping = MerchantCategoryMapping(parsedName = "Zomato", categoryId = 4) // Assuming 4 is Food
        merchantCategoryMappingDao.insert(mapping)

        // Act
        val categoryId = merchantCategoryMappingDao.getCategoryIdForMerchant("Zomato")
        val nullResult = merchantCategoryMappingDao.getCategoryIdForMerchant("Swiggy")

        // Assert
        assertEquals(4, categoryId)
        assertNull(nullResult)
    }
}
