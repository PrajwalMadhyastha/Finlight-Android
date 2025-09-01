// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/utils/CategoryIconHelper.kt
// REASON: FEATURE - Added a new `getCategoryIdByName` function. This provides a
// crucial utility for the new heuristic parser to look up a category's ID based
// on its name, which is needed to complete the keyword-to-category mapping.
// =================================================================================
package io.pm.finlight.utils

import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AddCard
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CreditScore
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.House
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import io.pm.finlight.Category
import io.pm.finlight.R
import kotlin.collections.get

/**
 * A helper object to manage category icons and colors, and provide a predefined list of categories.
 */
object CategoryIconHelper {

    private val iconColors = mapOf(
        "green_light" to Color(0xFFA5D6A7),
        "blue_light" to Color(0xFF90CAF9),
        "purple_light" to Color(0xFFCE93D8),
        "orange_light" to Color(0xFFFFCC80),
        "red_light" to Color(0xFFEF9A9A),
        "teal_light" to Color(0xFF80CBC4),
        "pink_light" to Color(0xFFF48FB1),
        "brown_light" to Color(0xFFBCAAA4),
        "cyan_light" to Color(0xFF80DEEA),
        "indigo_light" to Color(0xFF9FA8DA),
        "deep_purple_light" to Color(0xFFB39DDB),
        "yellow_light" to Color(0xFFFFF59D),
        "gray_light" to Color(0xFFE0E0E0),
        // --- NEW: Expanded Palette ---
        "lime_light" to Color(0xFFE6EE9C),
        "amber_light" to Color(0xFFFFE082),
        "deep_orange_light" to Color(0xFFFFAB91),
        "blue_grey_light" to Color(0xFFB0BEC5),
        "light_green_light" to Color(0xFFC5E1A5),
        "light_blue_light" to Color(0xFF81D4FA),
        "pink_accent_light" to Color(0xFFF8BBD0),
        "teal_accent_light" to Color(0xFFA7FFEB),
        "purple_accent_light" to Color(0xFFE1BEE7),
        "orange_accent_light" to Color(0xFFFFD180),
        "yellow_accent_light" to Color(0xFFFFFF8D),
        "cyan_accent_light" to Color(0xFF84FFFF)
    )

    private val categoryBackgrounds = mapOf(
        "receipt_long" to R.drawable.bg_cat_bills,
        "trending_up" to R.drawable.bg_cat_investment,
        "star" to R.drawable.bg_cat_entertainment,
        "restaurant" to R.drawable.bg_cat_food,
        "local_gas_station" to R.drawable.bg_cat_fuel,
        "shopping_cart" to R.drawable.bg_cat_groceries,
        "favorite" to R.drawable.bg_cat_health,
        "business" to R.drawable.bg_cat_investment,
        "shopping_bag" to R.drawable.bg_cat_shopping,
        "swap_horiz" to R.drawable.bg_cat_transfer,
        "travel_explore" to R.drawable.bg_cat_travel,
        "work" to R.drawable.bg_cat_salary,
        "redo" to R.drawable.bg_cat_refund,
        "add_card" to R.drawable.bg_cat_card,
        "more_horiz" to R.drawable.bg_cat_general,
        "schedule" to R.drawable.bg_cat_emi,
        // --- NEW: Mappings for new default categories ---
        "two_wheeler" to R.drawable.bg_cat_bike,
        "directions_car" to R.drawable.bg_cat_car,
        "credit_score" to R.drawable.bg_cat_debt,
        "people" to R.drawable.bg_cat_family,
        "group" to R.drawable.bg_cat_friends,
        "card_giftcard" to R.drawable.bg_cat_gift,
        "fitness_center" to R.drawable.bg_cat_fitness,
        "home" to R.drawable.bg_cat_home,
        "shield" to R.drawable.bg_cat_insurance,
        "school" to R.drawable.bg_cat_learning,
        "house" to R.drawable.bg_cat_rent,
        "help_outline" to R.drawable.bg_cat_general,
        "default" to R.drawable.bg_cat_general
    )

    @DrawableRes
    fun getCategoryBackground(categoryIconKey: String?): Int {
        return categoryBackgrounds[categoryIconKey] ?: R.drawable.bg_cat_general
    }


    fun getIconBackgroundColor(colorKey: String): Color {
        return iconColors[colorKey] ?: Color.Companion.LightGray
    }

    fun getAllIconColors(): Map<String, Color> {
        return iconColors
    }

    fun getNextAvailableColor(usedColorKeys: List<String>): String {
        return iconColors.keys.firstOrNull { it !in usedColorKeys }
            ?: iconColors.keys.firstOrNull()
            ?: "gray_light"
    }

    val predefinedCategories = listOf(
        Category(id = 1, name = "Bills", iconKey = "receipt_long", colorKey = "green_light"),
        Category(id = 2, name = "EMI", iconKey = "schedule", colorKey = "blue_light"),
        Category(id = 3, name = "Entertainment", iconKey = "star", colorKey = "purple_light"),
        Category(id = 4, name = "Food & Drinks", iconKey = "restaurant", colorKey = "orange_light"),
        Category(id = 5, name = "Fuel", iconKey = "local_gas_station", colorKey = "red_light"),
        Category(id = 6, name = "Groceries", iconKey = "shopping_cart", colorKey = "teal_light"),
        Category(id = 7, name = "Health", iconKey = "favorite", colorKey = "pink_light"),
        Category(id = 8, name = "Investment", iconKey = "business", colorKey = "brown_light"),
        Category(id = 9, name = "Shopping", iconKey = "shopping_bag", colorKey = "cyan_light"),
        Category(id = 10, name = "Transfer", iconKey = "swap_horiz", colorKey = "indigo_light"),
        Category(
            id = 11,
            name = "Travel",
            iconKey = "travel_explore",
            colorKey = "deep_purple_light"
        ),
        Category(id = 12, name = "Salary", iconKey = "work", colorKey = "yellow_light"),
        Category(id = 13, name = "Other", iconKey = "more_horiz", colorKey = "gray_light"),
        Category(id = 14, name = "Refund", iconKey = "redo", colorKey = "green_light"),
        Category(id = 15, name = "Credit", iconKey = "add_card", colorKey = "blue_light"),
        // --- NEW: Added new set of default categories ---
        Category(id = 16, name = "Bike", iconKey = "two_wheeler", colorKey = "red_light"),
        Category(id = 17, name = "Car", iconKey = "directions_car", colorKey = "blue_light"),
        Category(id = 18, name = "Debt", iconKey = "credit_score", colorKey = "brown_light"),
        Category(id = 19, name = "Family", iconKey = "people", colorKey = "pink_light"),
        Category(id = 20, name = "Friends", iconKey = "group", colorKey = "cyan_light"),
        Category(id = 21, name = "Gift", iconKey = "card_giftcard", colorKey = "purple_light"),
        Category(id = 22, name = "Fitness", iconKey = "fitness_center", colorKey = "green_light"),
        Category(id = 23, name = "Home Maintenance", iconKey = "home", colorKey = "teal_light"),
        Category(id = 24, name = "Insurance", iconKey = "shield", colorKey = "indigo_light"),
        Category(
            id = 25,
            name = "Learning & Education",
            iconKey = "school",
            colorKey = "orange_light"
        ),
        Category(id = 26, name = "Rent", iconKey = "house", colorKey = "deep_purple_light"),
    )

    // --- NEW: Function to get a category's ID by its name ---
    fun getCategoryIdByName(name: String): Int? {
        return predefinedCategories.find { it.name.equals(name, ignoreCase = true) }?.id
    }

    fun getIcon(iconKey: String): ImageVector {
        return when (iconKey) {
            "receipt_long" -> Icons.AutoMirrored.Filled.ReceiptLong
            "trending_up" -> Icons.AutoMirrored.Filled.TrendingUp
            "star" -> Icons.Default.Star
            "restaurant" -> Icons.Default.Restaurant
            "local_gas_station" -> Icons.Default.LocalGasStation
            "shopping_cart" -> Icons.Default.ShoppingCart
            "favorite" -> Icons.Default.Favorite
            "business" -> Icons.Default.Business
            "shopping_bag" -> Icons.Default.ShoppingBag
            "swap_horiz" -> Icons.Default.SwapHoriz
            "travel_explore" -> Icons.Default.TravelExplore
            "account_balance" -> Icons.Default.AccountBalance
            "more_horiz" -> Icons.Default.MoreHoriz
            "card_giftcard" -> Icons.Default.CardGiftcard
            "school" -> Icons.Default.School
            "pets" -> Icons.Default.Pets
            "fastfood" -> Icons.Default.Fastfood
            "directions_car" -> Icons.Default.DirectionsCar
            "work" -> Icons.Default.Work
            "redo" -> Icons.AutoMirrored.Filled.Redo
            "add_card" -> Icons.Default.AddCard
            "schedule" -> Icons.Default.Schedule
            // --- NEW: Mappings for new category icons ---
            "two_wheeler" -> Icons.Default.TwoWheeler
            "credit_score" -> Icons.Default.CreditScore
            "people" -> Icons.Default.People
            "group" -> Icons.Default.Group
            "fitness_center" -> Icons.Default.FitnessCenter
            "home" -> Icons.Default.Home
            "shield" -> Icons.Default.Shield
            "house" -> Icons.Default.House
            "help_outline" -> Icons.AutoMirrored.Filled.HelpOutline
            else -> Icons.Default.Category
        }
    }

    fun getAllIcons(): Map<String, ImageVector> {
        return mapOf(
            "receipt_long" to Icons.AutoMirrored.Filled.ReceiptLong,
            "trending_up" to Icons.AutoMirrored.Filled.TrendingUp,
            "star" to Icons.Default.Star,
            "restaurant" to Icons.Default.Restaurant,
            "local_gas_station" to Icons.Default.LocalGasStation,
            "shopping_cart" to Icons.Default.ShoppingCart,
            "favorite" to Icons.Default.Favorite,
            "business" to Icons.Default.Business,
            "shopping_bag" to Icons.Default.ShoppingBag,
            "swap_horiz" to Icons.Default.SwapHoriz,
            "travel_explore" to Icons.Default.TravelExplore,
            "account_balance" to Icons.Default.AccountBalance,
            "more_horiz" to Icons.Default.MoreHoriz,
            "card_giftcard" to Icons.Default.CardGiftcard,
            "school" to Icons.Default.School,
            "pets" to Icons.Default.Pets,
            "fastfood" to Icons.Default.Fastfood,
            "directions_car" to Icons.Default.DirectionsCar,
            //"category" to Icons.Default.Category,
            "work" to Icons.Default.Work,
            "redo" to Icons.AutoMirrored.Filled.Redo,
            "add_card" to Icons.Default.AddCard,
            "schedule" to Icons.Default.Schedule,
            // --- NEW: Mappings for new category icons ---
            "two_wheeler" to Icons.Default.TwoWheeler,
            "credit_score" to Icons.Default.CreditScore,
            "people" to Icons.Default.People,
            "group" to Icons.Default.Group,
            "fitness_center" to Icons.Default.FitnessCenter,
            "home" to Icons.Default.Home,
            "shield" to Icons.Default.Shield,
            "house" to Icons.Default.House,
            "help_outline" to Icons.AutoMirrored.Filled.HelpOutline,
        )
    }
}
