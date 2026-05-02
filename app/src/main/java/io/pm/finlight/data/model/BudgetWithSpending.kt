package io.pm.finlight

import androidx.room.Embedded

data class BudgetWithSpending(
    @Embedded
    val budget: Budget,
    val spent: Double,
    // --- NEW: Add fields for category icon and color ---
    val iconKey: String?,
    val colorKey: String?,
)
