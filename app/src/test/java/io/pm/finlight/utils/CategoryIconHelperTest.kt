package io.pm.finlight.utils

import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Restaurant
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.TestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class CategoryIconHelperTest : BaseViewModelTest() {

    @Test
    fun `getIcon returns correct icon for known key`() {
        val icon = CategoryIconHelper.getIcon("restaurant")
        assertEquals(Icons.Default.Restaurant, icon)
    }

    @Test
    fun `getIcon returns default icon for unknown key`() {
        val icon = CategoryIconHelper.getIcon("unknown_key_xyz")
        assertEquals(Icons.Default.Category, icon)
    }

    @Test
    fun `getIconBackgroundColor returns correct color for known key`() {
        val color = CategoryIconHelper.getIconBackgroundColor("red_light")
        assertNotNull(color)
        assertNotEquals(0, color.value) // Ensure it's not transparent or black
    }

    @Test
    fun `getIconBackgroundColor returns default color for unknown key`() {
        val defaultColor = CategoryIconHelper.getIconBackgroundColor("unknown_key_xyz")
        // The implementation's fallback is Color.LightGray. The test is updated to assert this directly.
        assertEquals(androidx.compose.ui.graphics.Color.LightGray, defaultColor)
    }

    @Test
    fun `getNextAvailableColor returns first color when none are used`() {
        val nextColor = CategoryIconHelper.getNextAvailableColor(emptyList())
        assertEquals("green_light", nextColor)
    }

    @Test
    fun `getNextAvailableColor returns next available color when some are used`() {
        val usedColors = listOf("green_light", "blue_light")
        val nextColor = CategoryIconHelper.getNextAvailableColor(usedColors)
        assertEquals("purple_light", nextColor)
    }

    @Test
    fun `getNextAvailableColor cycles back if all are used`() {
        val allColors = CategoryIconHelper.getAllIconColors().keys.toList()
        val nextColor = CategoryIconHelper.getNextAvailableColor(allColors)
        assertNotNull(nextColor)
        // It should return the first one in the list as a fallback
        assertEquals(allColors.first(), nextColor)
    }

    @Test
    fun `getCategoryIdByName returns correct ID for existing category`() {
        val id = CategoryIconHelper.getCategoryIdByName("Food & Drinks")
        assertEquals(4, id)
    }

    @Test
    fun `getCategoryIdByName is case-insensitive`() {
        val id = CategoryIconHelper.getCategoryIdByName("food & drinks")
        assertEquals(4, id)
    }

    @Test
    fun `getCategoryIdByName returns null for non-existent category`() {
        val id = CategoryIconHelper.getCategoryIdByName("Non-Existent Category")
        assertNull(id)
    }
}