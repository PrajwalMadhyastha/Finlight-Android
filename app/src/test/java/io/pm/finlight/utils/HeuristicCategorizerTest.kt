package io.pm.finlight.utils

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.Category
import io.pm.finlight.TestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class HeuristicCategorizerTest : BaseViewModelTest() {

    private val mockCategories = listOf(
        Category(id = 1, name = "Food & Drinks", iconKey = "", colorKey = ""),
        Category(id = 2, name = "Shopping", iconKey = "", colorKey = ""),
        Category(id = 3, name = "Travel", iconKey = "", colorKey = "")
    )

    @Test
    fun `findCategoryForDescription returns correct category for known keyword`() {
        val description = "Dinner at swiggy"
        val expectedCategory = mockCategories.find { it.name == "Food & Drinks" }
        val actualCategory = HeuristicCategorizer.findCategoryForDescription(description, mockCategories)
        assertEquals(expectedCategory, actualCategory)
    }

    @Test
    fun `findCategoryForDescription is case-insensitive`() {
        val description = "Order from AMAZON"
        val expectedCategory = mockCategories.find { it.name == "Shopping" }
        val actualCategory = HeuristicCategorizer.findCategoryForDescription(description, mockCategories)
        assertEquals(expectedCategory, actualCategory)
    }

    @Test
    fun `findCategoryForDescription returns null for unknown keyword`() {
        val description = "Some random purchase"
        val actualCategory = HeuristicCategorizer.findCategoryForDescription(description, mockCategories)
        assertNull(actualCategory)
    }

    @Test
    fun `findCategoryForDescription handles partial matches within words`() {
        val description = "trip to myntra store"
        val expectedCategory = mockCategories.find { it.name == "Shopping" }
        val actualCategory = HeuristicCategorizer.findCategoryForDescription(description, mockCategories)
        assertEquals(expectedCategory, actualCategory)
    }

    @Test
    fun `findCategoryForDescription returns null when categories are empty`() {
        val description = "Dinner at swiggy"
        val actualCategory = HeuristicCategorizer.findCategoryForDescription(description, emptyList())
        assertNull(actualCategory)
    }

    @Test
    fun `findCategoryForDescription returns first matching category`() {
        // "pizza" is in "Food & Drinks", "amazon" is in "Shopping"
        val description = "pizza from amazon"
        // "Food & Drinks" is likely to be checked first based on map iteration order
        val expectedCategory = mockCategories.find { it.name == "Food & Drinks" }
        val actualCategory = HeuristicCategorizer.findCategoryForDescription(description, mockCategories)
        assertEquals(expectedCategory, actualCategory)
    }
}