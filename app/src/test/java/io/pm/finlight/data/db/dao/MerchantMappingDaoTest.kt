package io.pm.finlight.data.db.dao

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.MerchantMapping
import io.pm.finlight.MerchantMappingDao
import io.pm.finlight.TestApplication
import io.pm.finlight.util.DatabaseTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.DefaultAsserter.assertEquals
import kotlin.test.DefaultAsserter.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun `insert and getMappingForSender retrieves correct mapping`() = runTest {
        // Arrange
        val mapping = MerchantMapping(smsSender = "AM-HDFCBK", merchantName = "HDFC Bank")
        merchantMappingDao.insert(mapping)

        // Act
        val result = merchantMappingDao.getMappingForSender("AM-HDFCBK")

        // Assert
        assertNotNull(result)
        assertEquals("HDFC Bank", result?.merchantName)
    }

    @Test
    fun `getMappingForSender returns null for unknown sender`() = runTest {
        // Arrange
        val mapping = MerchantMapping(smsSender = "AM-HDFCBK", merchantName = "HDFC Bank")
        merchantMappingDao.insert(mapping)

        // Act
        val result = merchantMappingDao.getMappingForSender("VM-ICICI")

        // Assert
        assertNull(result)
    }

    @Test
    fun `insert duplicate sender overwrites existing mapping`() = runTest {
        // Arrange
        val mapping1 = MerchantMapping(smsSender = "AM-HDFCBK", merchantName = "HDFC Bank")
        val mapping2 = MerchantMapping(smsSender = "AM-HDFCBK", merchantName = "HDFC (New)")
        merchantMappingDao.insert(mapping1)
        merchantMappingDao.insert(mapping2) // This should overwrite

        // Act
        val result = merchantMappingDao.getMappingForSender("AM-HDFCBK")

        // Assert
        assertNotNull(result)
        assertEquals("HDFC (New)", result?.merchantName)
        // Also check that there's only one entry
        merchantMappingDao.getAllMappings().test {
            assertEquals(1, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `insertAll inserts multiple mappings correctly`() = runTest {
        // Arrange
        val mapping1 = MerchantMapping(smsSender = "AM-HDFCBK", merchantName = "HDFC Bank")
        val mapping2 = MerchantMapping(smsSender = "VM-ICICI", merchantName = "ICICI Bank")
        val mappings = listOf(mapping1, mapping2)

        // Act
        merchantMappingDao.insertAll(mappings)

        // Assert
        merchantMappingDao.getAllMappings().test {
            val allMappings = awaitItem()
            assertEquals(2, allMappings.size)
            assertTrue("List should contain HDFC mapping", allMappings.any { it.smsSender == "AM-HDFCBK" })
            assertTrue("List should contain ICICI mapping", allMappings.any { it.smsSender == "VM-ICICI" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteAll removes all mappings`() = runTest {
        // Arrange
        val mapping1 = MerchantMapping(smsSender = "AM-HDFCBK", merchantName = "HDFC Bank")
        val mapping2 = MerchantMapping(smsSender = "VM-ICICI", merchantName = "ICICI Bank")
        merchantMappingDao.insertAll(listOf(mapping1, mapping2))

        // Pre-condition check to ensure data exists
        val initialMappings = merchantMappingDao.getAllMappings().first()
        assertEquals("Pre-condition failed: Data was not inserted", 2, initialMappings.size)

        // Act
        merchantMappingDao.deleteAll()

        // Assert
        merchantMappingDao.getAllMappings().test {
            val allMappings = awaitItem()
            assertTrue("Database should be empty after deleteAll", allMappings.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
