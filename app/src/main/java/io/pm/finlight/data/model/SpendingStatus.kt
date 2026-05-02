package io.pm.finlight

/**
 * Represents the spending status for a single day relative to the daily budget.
 * This is used to color the cells in the Spending Consistency Calendar.
 */
enum class SpendingStatus {
    /**
     * No expenses were recorded on this day.
     */
    NO_SPEND,

    /**
     * Expenses were recorded but were within the calculated daily budget.
     */
    WITHIN_LIMIT,

    /**
     * Expenses exceeded the calculated daily budget.
     */
    OVER_LIMIT,

    /**
     * The day is in the future or before the user started tracking, so no data is available.
     */
    NO_DATA,
}
