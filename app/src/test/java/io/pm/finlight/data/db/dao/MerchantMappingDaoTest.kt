package io.pm.finlight.data.db.dao

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.MerchantMapping
import io.pm.finlight.MerchantMappingDao
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
import kotlin.test.assertNotNull

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class MerchantMappingDaoTest {

    @get:Rule
    val dbRule = DatabaseTestRule()

    private lateinit var merchantMappingDao: MerchantMappingDao

    @Before
    fun setup() {
        merchantMappingDao = dbRule.db.merchantMappingDao()
    }

    @Test
    fun `getMappingForSender retrieves correct mapping`() = runTest {
        // Arrange
        val mapping = MerchantMapping(smsSender = "AM-HDFCBK", merchantName = "HDFC Bank")
        merchantMappingDao.insert(mapping)

        // Act
        val result = merchantMappingDao.getMappingForSender("AM-HDFCBK")

        // Assert
        assertNotNull(result)
        assertEquals("HDFC Bank", result?.merchantName)
    }
}
