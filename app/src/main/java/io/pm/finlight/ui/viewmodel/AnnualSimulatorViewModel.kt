package io.pm.finlight.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.ISettingsRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.utils.SystemTimeProvider
import io.pm.finlight.utils.TimeProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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
    private val transactionRepository: ITransactionRepository,
    private val settingsRepository: ISettingsRepository,
    private val timeProvider: TimeProvider = SystemTimeProvider(),
    simulationYear: Int? = null
) : ViewModel() {
    private val baseCalendarYear = timeProvider.now().get(Calendar.YEAR)
    val minYear: Int = minOf(baseCalendarYear - 5, simulationYear ?: (baseCalendarYear - 5))
    val maxYear: Int = maxOf(baseCalendarYear + 5, simulationYear ?: (baseCalendarYear + 5))

    private val _lifeEventsByYear = MutableStateFlow<Map<Int, List<LifeEvent>>>(emptyMap())
    private val _lifeEvents = MutableStateFlow<List<LifeEvent>>(emptyList())
    val lifeEvents: StateFlow<List<LifeEvent>> = _lifeEvents.asStateFlow()

    private val _currentYear =
        MutableStateFlow((simulationYear ?: baseCalendarYear).coerceIn(minYear, maxYear))
    val currentYear = _currentYear.asStateFlow()

    private val _baseAnnualIncome = MutableStateFlow(0.0)
    val baseAnnualIncome = _baseAnnualIncome.asStateFlow()

    private val _baseAnnualBudget = MutableStateFlow(0.0)
    val baseAnnualBudget = _baseAnnualBudget.asStateFlow()

    private val _validationError = MutableStateFlow<String?>(null)
    val validationError: StateFlow<String?> = _validationError.asStateFlow()

    val privacyModeEnabled =
        settingsRepository.getSimulatorPrivacyModeEnabled()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val projectedImpact: StateFlow<Double> =
        _lifeEvents.map { events ->
            calculateProjectedAnnualImpact(events)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val projectedDeficitSurplus: StateFlow<Double> =
        combine(_baseAnnualIncome, _baseAnnualBudget, projectedImpact) { income, budget, impact ->
            income - budget - impact
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val projectedBalance: StateFlow<Double> = projectedDeficitSurplus

    private var loadMetricsJob: Job? = null

    init {
        loadBaseAnnualMetrics()
    }

    fun setYear(year: Int) {
        val clampedYear = year.coerceIn(minYear, maxYear)
        if (_currentYear.value != clampedYear) {
            _currentYear.value = clampedYear
            _lifeEvents.value = _lifeEventsByYear.value[clampedYear] ?: emptyList()
            _validationError.value = null
            loadBaseAnnualMetrics()
        }
    }

    private fun loadBaseAnnualMetrics() {
        loadMetricsJob?.cancel()
        loadMetricsJob =
            viewModelScope.launch {
                val year = _currentYear.value

                // Calculate Base Annual Budget
                val existingBudgets = settingsRepository.getOverallBudgetsForYear(year) ?: emptyMap()
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
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val startOfYear = cal.timeInMillis

                cal.set(Calendar.MONTH, Calendar.DECEMBER)
                cal.set(Calendar.DAY_OF_MONTH, 31)
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                val endOfYear = cal.timeInMillis

                val today = timeProvider.now()
                val currentCalendarYear = today.get(Calendar.YEAR)
                val currentMonthIndex = today.get(Calendar.MONTH) + 1

                val queryEndTime =
                    if (year == currentCalendarYear) {
                        minOf(endOfYear, today.timeInMillis)
                    } else {
                        endOfYear
                    }

                // Observe income reactively
                transactionRepository.getIncomeTransactionsForRange(
                    startOfYear,
                    queryEndTime,
                    "",
                    null,
                    null
                ).collect { incomeTransactions ->
                    val ytdIncome = incomeTransactions.sumOf { it.transaction.amount.toDouble() }

                    val projectedAnnualIncome =
                        when {
                            year < currentCalendarYear -> {
                                // Past year: full-year income without monthly extrapolation
                                ytdIncome
                            }
                            year == currentCalendarYear -> {
                                // Current year: extrapolate based on elapsed months
                                if (currentMonthIndex > 0 && ytdIncome > 0) {
                                    (ytdIncome / currentMonthIndex) * 12.0
                                } else {
                                    0.0
                                }
                            }
                            else -> {
                                // Future year: actual transactions entered so far without extrapolation
                                ytdIncome
                            }
                        }

                    _baseAnnualIncome.value = projectedAnnualIncome
                }
            }
    }

    fun addLifeEvent(
        name: String,
        amount: Double,
        isRecurring: Boolean,
        startMonthIndex: Int
    ): Result<Unit> {
        if (name.isBlank()) {
            val error = "Event name cannot be blank"
            _validationError.value = error
            return Result.failure(IllegalArgumentException(error))
        }
        if (amount <= 0.0 || amount.isNaN() || amount.isInfinite()) {
            val error = "Amount must be greater than 0"
            _validationError.value = error
            return Result.failure(IllegalArgumentException(error))
        }
        if (startMonthIndex !in 0..11) {
            val error = "Start month index must be between 0 and 11"
            _validationError.value = error
            return Result.failure(IllegalArgumentException(error))
        }

        _validationError.value = null
        val event =
            LifeEvent(
                name = name.trim(),
                amount = amount,
                isRecurring = isRecurring,
                startMonthIndex = startMonthIndex.coerceIn(0, 11)
            )
        val year = _currentYear.value
        val updatedEvents = (_lifeEventsByYear.value[year] ?: emptyList()) + event
        _lifeEventsByYear.value = _lifeEventsByYear.value + (year to updatedEvents)
        _lifeEvents.value = updatedEvents
        return Result.success(Unit)
    }

    fun clearValidationError() {
        _validationError.value = null
    }

    fun removeLifeEvent(id: String) {
        val year = _currentYear.value
        val updatedEvents = (_lifeEventsByYear.value[year] ?: emptyList()).filter { it.id != id }
        _lifeEventsByYear.value = _lifeEventsByYear.value + (year to updatedEvents)
        _lifeEvents.value = updatedEvents
    }

    fun calculateProjectedAnnualImpact(events: List<LifeEvent> = _lifeEvents.value): Double {
        var totalImpact = 0.0
        for (event in events) {
            if (event.isRecurring) {
                val monthsActive = (12 - event.startMonthIndex).coerceAtLeast(0)
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
