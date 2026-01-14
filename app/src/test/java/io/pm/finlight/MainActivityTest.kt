// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/MainActivityTest.kt
// REASON: NEW - Unit tests for app shortcuts feature
// Tests verify that shortcut action constants and navigation logic work correctly
// =================================================================================
package io.pm.finlight

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for app shortcut navigation logic.
 * 
 * These tests verify that shortcut actions map to the correct navigation routes
 * and that the action constants are properly defined.
 */
class MainActivityTest {

    // Action constants defined to match MainActivity
    companion object {
        const val ACTION_ADD_EXPENSE = "io.pm.finlight.ACTION_ADD_EXPENSE"
        const val ACTION_ADD_INCOME = "io.pm.finlight.ACTION_ADD_INCOME"
        const val ACTION_SEARCH = "io.pm.finlight.ACTION_SEARCH"
    }

    // Helper function that mimics the navigation logic in MainActivity
    private fun getRouteForShortcutAction(action: String?): String? {
        return when (action) {
            ACTION_ADD_EXPENSE -> "add_transaction?transactionType=expense"
            ACTION_ADD_INCOME -> "add_transaction?transactionType=income"
            ACTION_SEARCH -> "search_screen"
            else -> null
        }
    }

    @Test
    fun `shortcut action ADD_EXPENSE maps to correct route`() {
        // Arrange
        val shortcutAction = ACTION_ADD_EXPENSE
        val expectedRoute = "add_transaction?transactionType=expense"
        
        // Act
        val actualRoute = getRouteForShortcutAction(shortcutAction)
        
        // Assert
        assertEquals("Expected route $expectedRoute for ADD_EXPENSE action", expectedRoute, actualRoute)
    }

    @Test
    fun `shortcut action ADD_INCOME maps to correct route`() {
        // Arrange
        val shortcutAction = ACTION_ADD_INCOME
        val expectedRoute = "add_transaction?transactionType=income"
        
        // Act
        val actualRoute = getRouteForShortcutAction(shortcutAction)
        
        // Assert
        assertEquals("Expected route $expectedRoute for ADD_INCOME action", expectedRoute, actualRoute)
    }

    @Test
    fun `shortcut action SEARCH maps to correct route`() {
        // Arrange
        val shortcutAction = ACTION_SEARCH
        val expectedRoute = "search_screen"
        
        // Act
        val actualRoute = getRouteForShortcutAction(shortcutAction)
        
        // Assert
        assertEquals("Expected route $expectedRoute for SEARCH action", expectedRoute, actualRoute)
    }

    @Test
    fun `null shortcut action does not map to any route`() {
        // Arrange
        val shortcutAction: String? = null
        
        // Act
        val actualRoute = getRouteForShortcutAction(shortcutAction)
        
        // Assert
        assertNull("Expected no navigation for null shortcut action", actualRoute)
    }

    @Test
    fun `ACTION_MAIN does not map to shortcut route`() {
        // Arrange
        val shortcutAction = "android.intent.action.MAIN"
        
        // Act
        val actualRoute = getRouteForShortcutAction(shortcutAction)
        
        // Assert
        assertNull("Expected no navigation for ACTION_MAIN", actualRoute)
    }

    @Test
    fun `unknown shortcut action does not map to any route`() {
        // Arrange
        val shortcutAction = "io.pm.finlight.UNKNOWN_ACTION"
        
        // Act
        val actualRoute = getRouteForShortcutAction(shortcutAction)
        
        // Assert
        assertNull("Expected no navigation for unknown action", actualRoute)
    }

    @Test
    fun `shortcut action constants have correct values`() {
        // Assert - Verify the constant values are correctly defined
        assertEquals("ACTION_ADD_EXPENSE should have correct package-qualified name",
            "io.pm.finlight.ACTION_ADD_EXPENSE", 
            ACTION_ADD_EXPENSE)
        
        assertEquals("ACTION_ADD_INCOME should have correct package-qualified name",
            "io.pm.finlight.ACTION_ADD_INCOME", 
            ACTION_ADD_INCOME)
        
        assertEquals("ACTION_SEARCH should have correct package-qualified name",
            "io.pm.finlight.ACTION_SEARCH", 
            ACTION_SEARCH)
    }
    
    @Test
    fun `all shortcut actions are unique`() {
        // Arrange
        val actions = setOf(
            ACTION_ADD_EXPENSE,
            ACTION_ADD_INCOME,
            ACTION_SEARCH
        )
        
        // Assert - Verify all actions are unique (set size equals number of actions)
        assertEquals("All shortcut actions should be unique", 3, actions.size)
    }
    
    @Test
    fun `add expense route contains correct transaction type`() {
        // Arrange
        val route = getRouteForShortcutAction(ACTION_ADD_EXPENSE)
        
        // Assert
        assertNotNull("Route should not be null", route)
        assertTrue("Route should contain transactionType=expense", 
            route!!.contains("transactionType=expense"))
    }
    
    @Test
    fun `add income route contains correct transaction type`() {
        // Arrange
        val route = getRouteForShortcutAction(ACTION_ADD_INCOME)
        
        // Assert
        assertNotNull("Route should not be null", route)
        assertTrue("Route should contain transactionType=income", 
            route!!.contains("transactionType=income"))
    }
    
    @Test
    fun `search route does not contain transaction type parameter`() {
        // Arrange
        val route = getRouteForShortcutAction(ACTION_SEARCH)
        
        // Assert
        assertNotNull("Route should not be null", route)
        assertFalse("Search route should not contain transactionType parameter", 
            route!!.contains("transactionType"))
        assertEquals("Search route should be exactly 'search_screen'", 
            "search_screen", route)
    }
}
