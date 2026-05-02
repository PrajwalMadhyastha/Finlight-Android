// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/DashboardCardType.kt
// REASON: REFACTOR - Renamed RECENT_ACTIVITY to RECENT_TRANSACTIONS to better
// reflect its content. Removed the unused NET_WORTH card.
// =================================================================================
package io.pm.finlight

/**
 * Represents the different types of cards that can be displayed on the dashboard.
 * Used for managing customizable dashboard layouts.
 */
enum class DashboardCardType {
    HERO_BUDGET,
    QUICK_ACTIONS,
    RECENT_TRANSACTIONS,
    ACCOUNTS_CAROUSEL,
    BUDGET_WATCH,
    SPENDING_CONSISTENCY,
}
