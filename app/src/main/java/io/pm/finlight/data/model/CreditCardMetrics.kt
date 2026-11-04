package io.pm.finlight

/**
 * Encapsulates contextual metrics for a credit card account, computed from the account's
 * configured credit limit, billing cycle anchor day, and transactions within the active cycle.
 *
 * @property creditLimit The total credit limit assigned to the card.
 * @property billingCycleDay The day of month (1..31) on which the statement cuts.
 * @property currentCycleSpend Total net spend (expenses - refunds/credits) in the active cycle.
 * @property availableLimit Remaining credit headroom (creditLimit - currentCycleSpend).
 * @property utilizationPercentage Credit utilization ratio expressed as a percentage (0..100+).
 * @property cycleStartDate Timestamp in milliseconds for the start of the active billing cycle.
 * @property cycleEndDate Timestamp in milliseconds for the end of the active billing cycle.
 * @property daysRemainingInCycle Number of days remaining until the active billing cycle closes.
 */
data class CreditCardMetrics(
    val creditLimit: Double,
    val billingCycleDay: Int,
    val currentCycleSpend: Double,
    val availableLimit: Double,
    val utilizationPercentage: Float,
    val cycleStartDate: Long,
    val cycleEndDate: Long,
    val daysRemainingInCycle: Int,
)
