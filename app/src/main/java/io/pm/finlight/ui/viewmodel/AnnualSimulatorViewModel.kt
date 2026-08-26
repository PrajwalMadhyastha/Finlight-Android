package io.pm.finlight.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.SettingsRepository
import io.pm.finlight.TransactionRepository
import io.pm.finlight.utils.TimeProvider
import io.pm.finlight.utils.SystemTimeProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID

data class LifeEvent(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val amount: Double,
    // false for one-time events, true for monthly recurring
    val isRecurring: Boolean = false,
    // 0-11 for Jan-Dec. The event starts applying from this month onwards.
    val startMonthIndex: Int
)

class AnnualSimulatorViewModel(
    private val transactionRepository: TransactionRepository,
    private val settingsRepository: SettingsRepository,
    private val timeProvider: TimeProvider = SystemTimeProvider()
) : ViewModel() {
    private val _lifeEvents = MutableStateFlow<List<LifeEvent>>(emptyList())
    val lifeEvents: StateFlow<List<LifeEvent>> = _lifeEvents.asStateFlow()

    private val _currentYear = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))
    val currentYear = _currentYear.asStateFlow()

    private val _baseAnnualIncome = MutableStateFlow(0.0)
    val baseAnnualIncome = _baseAnnualIncome.asStateFlow()

    private val _baseAnnualBudget = MutableStateFlow(0.0)
    val baseAnnualBudget = _baseAnnualBudget.asStateFlow()

    val privacyModeEnabled =
        settingsRepository.getSimulatorPrivacyModeEnabled()
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), false)

    init {
        val cal = timeProvider.now()
        _currentYear.value = cal.get(Calendar.YEAR)
        loadBaseAnnualMetrics()
    }

    private fun loadBaseAnnualMetrics() {
        viewModelScope.launch {
            val year = _currentYear.value

            // Calculate Base Annual Budget
            val existingBudgets = settingsRepository.getOverallBudgetsForYear(year)
            var totalBudget = 0.0

            // Default budget if not explicitly set for all months
            val latestMonthBudget = existingBudgets.entries.maxByOrNull { it.key }?.value ?: 0f

            for (month in 1..12) {
                totalBudget += existingBudgets[month]?.toDouble() ?: latestMonthBudget.toDouble()
            }
            _baseAnnualBudget.value = totalBudget

            // Estimate Base Annual Income
            val cal = timeProvider.now()
            cal.set(Calendar.YEAR, year)
            cal.set(Calendar.DAY_OF_YEAR, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            val startOfYear = cal.timeInMillis

            cal.set(Calendar.MONTH, Calendar.DECEMBER)
            cal.set(Calendar.DAY_OF_MONTH, 31)
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            val endOfYear = cal.timeInMillis

            // Get YTD income
            val incomeTransactions = transactionRepository.getIncomeTransactionsForRange(startOfYear, endOfYear, "", null, null).firstOrNull() ?: emptyList()
            val ytdIncome = incomeTransactions.sumOf { it.transaction.amount.toDouble() }

            val currentMonthIndex = timeProvider.now().get(Calendar.MONTH) + 1

            val projectedAnnualIncome =
                if (currentMonthIndex > 0 && ytdIncome > 0) {
                    (ytdIncome / currentMonthIndex) * 12.0
                } else {
                    0.0
                }

            _baseAnnualIncome.value = projectedAnnualIncome
        }
    }

    fun addLifeEvent(
        name: String,
        amount: Double,
        isRecurring: Boolean,
        startMonthIndex: Int
    ) {
        val event =
            LifeEvent(
                name = name,
                amount = amount,
                isRecurring = isRecurring,
                startMonthIndex = startMonthIndex
            )
        _lifeEvents.value = _lifeEvents.value + event
    }

    fun removeLifeEvent(id: String) {
        _lifeEvents.value = _lifeEvents.value.filter { it.id != id }
    }

    fun calculateProjectedAnnualImpact(): Double {
        var totalImpact = 0.0
        for (event in _lifeEvents.value) {
            if (event.isRecurring) {
                val monthsActive = 12 - event.startMonthIndex
                totalImpact += event.amount * monthsActive
            } else {
                totalImpact += event.amount
            }
        }
        return totalImpact
    }

    fun togglePrivacyMode() {
        viewModelScope.launch {
            settingsRepository.saveSimulatorPrivacyModeEnabled(!privacyModeEnabled.value)
        }
    }
}
