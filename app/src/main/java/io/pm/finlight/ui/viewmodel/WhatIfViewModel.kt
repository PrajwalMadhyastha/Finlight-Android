package io.pm.finlight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.utils.DateUtils
import io.pm.finlight.utils.FormatUtils
import io.pm.finlight.utils.TimeProvider
import kotlinx.coroutines.flow.*
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToLong

data class HypotheticalExpense(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val amount: Long
)

class WhatIfViewModel(
    private val transactionRepository: TransactionRepository,
    private val settingsRepository: SettingsRepository,
    private val timeProvider: TimeProvider
) : ViewModel() {
    private val _hypotheticalExpenses = MutableStateFlow<List<HypotheticalExpense>>(emptyList())
    val hypotheticalExpenses: StateFlow<List<HypotheticalExpense>> = _hypotheticalExpenses.asStateFlow()

    val actualMonthlyIncome: StateFlow<Long>
    val actualMonthlyExpenses: StateFlow<Long>
    val overallMonthlyBudget: StateFlow<Long>

    val simulatedExpenses: StateFlow<Long>
    val simulatedAmountRemaining: StateFlow<Long>
    val simulatedSafeToSpendPerDay: StateFlow<Long>

    val privacyModeEnabled: StateFlow<Boolean>
    val monthYear: String

    init {
        val calendar = timeProvider.now()
        monthYear = FormatUtils.getFormatter("MMMM", Locale.getDefault()).format(calendar.time)

        privacyModeEnabled =
            settingsRepository.getSimulatorPrivacyModeEnabled()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

        val (monthStart, monthEnd) = DateUtils.getCurrentMonthDateRange(calendar)

        val financialSummaryFlow =
            transactionRepository.getFinancialSummaryForRangeFlow(monthStart, monthEnd)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

        actualMonthlyIncome =
            financialSummaryFlow.map { (it?.totalIncome ?: 0.0).roundToLong() }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

        actualMonthlyExpenses =
            financialSummaryFlow.map { (it?.totalExpenses ?: 0.0).roundToLong() }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH) + 1

        overallMonthlyBudget =
            settingsRepository.getOverallBudgetForMonth(currentYear, currentMonth)
                .map { (it ?: 0f).roundToLong() }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

        simulatedExpenses =
            combine(actualMonthlyExpenses, _hypotheticalExpenses) { actual, hypothetical ->
                actual + hypothetical.sumOf { it.amount }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

        simulatedAmountRemaining =
            combine(overallMonthlyBudget, simulatedExpenses) { budget, expenses ->
                budget - expenses
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

        simulatedSafeToSpendPerDay =
            simulatedAmountRemaining.map { remaining ->
                val today = timeProvider.now()
                val lastDayOfMonth = today.getActualMaximum(Calendar.DAY_OF_MONTH)
                val remainingDays = (lastDayOfMonth - today.get(Calendar.DAY_OF_MONTH) + 1).coerceAtLeast(1)

                if (remaining > 0) (remaining.toDouble() / remainingDays).roundToLong() else 0L
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    }

    fun addHypotheticalExpense(
        name: String,
        amount: Long
    ) {
        _hypotheticalExpenses.update { it + HypotheticalExpense(name = name, amount = amount) }
    }

    fun removeHypotheticalExpense(id: String) {
        _hypotheticalExpenses.update { list -> list.filterNot { it.id == id } }
    }

    fun togglePrivacyMode() {
        settingsRepository.saveSimulatorPrivacyModeEnabled(!privacyModeEnabled.value)
    }
}
