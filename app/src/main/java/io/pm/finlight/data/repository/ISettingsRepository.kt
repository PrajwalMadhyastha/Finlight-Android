package io.pm.finlight

import io.pm.finlight.ui.theme.AppTheme
import kotlinx.coroutines.flow.Flow

interface ISettingsRepository {
    // --- Recurring Transaction Settings ---
    suspend fun saveRecurringTransactionsEnabled(isEnabled: Boolean)

    fun getRecurringTransactionsEnabled(): Flow<Boolean>

    // --- Savings Goals Settings ---
    suspend fun saveGoalIncomeThreshold(amount: Int)

    fun getGoalIncomeThreshold(): Flow<Int>

    suspend fun saveGoalNudgesEnabled(isEnabled: Boolean)

    fun getGoalNudgesEnabled(): Flow<Boolean>

    // --- Outlier Month Management Functions ---
    fun getExcludedIncomeMonths(): Flow<Set<String>>

    fun getExcludedExpenseMonths(): Flow<Set<String>>

    suspend fun toggleIncomeMonthExclusion(monthKey: String)

    suspend fun toggleExpenseMonthExclusion(monthKey: String)

    // --- Backup Settings ---
    suspend fun saveBackupEnabled(isEnabled: Boolean)

    fun getBackupEnabled(): Flow<Boolean>

    suspend fun saveAutoBackupEnabled(isEnabled: Boolean)

    fun getAutoBackupEnabled(): Flow<Boolean>

    suspend fun saveAutoBackupNotificationEnabled(isEnabled: Boolean)

    fun getAutoBackupNotificationEnabled(): Flow<Boolean>

    suspend fun saveLastBackupTimestamp(timestamp: Long)

    fun getLastBackupTimestamp(): Flow<Long>

    // --- SMS Rule and Merge Settings ---
    suspend fun saveSmsScanStartDate(date: Long)

    fun getSmsScanStartDate(): Flow<Long>

    suspend fun saveIgnoreRulesChecksum(checksum: Int)

    suspend fun getIgnoreRulesChecksum(): Int

    fun getDismissedMergeSuggestions(): Flow<Set<String>>

    suspend fun addDismissedMergeSuggestion(suggestionKey: String)

    // --- Notification and Report Settings ---
    suspend fun saveDailyReportEnabled(isEnabled: Boolean)

    fun getDailyReportEnabled(): Flow<Boolean>

    suspend fun saveDailyReportTime(
        hour: Int,
        minute: Int
    )

    fun getDailyReportTime(): Flow<Pair<Int, Int>>

    suspend fun saveWeeklySummaryEnabled(isEnabled: Boolean)

    fun getWeeklySummaryEnabled(): Flow<Boolean>

    suspend fun saveWeeklyReportTime(
        dayOfWeek: Int,
        hour: Int,
        minute: Int
    )

    fun getWeeklyReportTime(): Flow<Triple<Int, Int, Int>>

    suspend fun saveMonthlySummaryEnabled(isEnabled: Boolean)

    fun getMonthlySummaryEnabled(): Flow<Boolean>

    suspend fun saveMonthlyReportTime(
        dayOfMonth: Int,
        hour: Int,
        minute: Int
    )

    fun getMonthlyReportTime(): Flow<Triple<Int, Int, Int>>

    suspend fun saveAutoCaptureNotificationEnabled(isEnabled: Boolean)

    fun getAutoCaptureNotificationEnabled(): Flow<Boolean>

    suspend fun saveUnknownTransactionPopupEnabled(isEnabled: Boolean)

    fun getUnknownTransactionPopupEnabled(): Flow<Boolean>

    suspend fun setLastMonthSummaryDismissed()

    fun hasLastMonthSummaryBeenDismissed(): Flow<Boolean>

    // --- App Config (User, Theme, Currency) ---
    suspend fun saveUserName(name: String)

    fun getUserName(): Flow<String>

    suspend fun saveProfilePictureUri(uriString: String?)

    fun getProfilePictureUri(): Flow<String?>

    suspend fun saveSelectedTheme(theme: AppTheme)

    fun getSelectedTheme(): Flow<AppTheme>

    suspend fun saveHomeCurrency(currencyCode: String)

    fun getHomeCurrency(): Flow<String>

    // --- Travel Mode Settings ---
    suspend fun saveTravelModeSettings(settings: TravelModeSettings?)

    fun getTravelModeSettings(): Flow<TravelModeSettings?>

    suspend fun getCurrentTravelModeSettings(): TravelModeSettings?

    // --- Budget Settings ---
    suspend fun saveOverallBudgetForCurrentMonth(amount: Float)

    suspend fun saveOverallBudgetForMonth(
        year: Int,
        month: Int,
        amount: Float
    )

    suspend fun getOverallBudgetsForYear(year: Int): Map<Int, Float>

    fun getOverallBudgetForMonth(
        year: Int,
        month: Int
    ): Flow<Float?>

    // --- Dashboard Settings ---
    suspend fun saveDashboardLayout(
        order: List<DashboardCardType>,
        visible: Set<DashboardCardType>
    )

    fun getDashboardCardOrder(): Flow<List<DashboardCardType>>

    fun getDashboardVisibleCards(): Flow<Set<DashboardCardType>>

    // --- Security Settings ---
    suspend fun saveAppLockEnabled(isEnabled: Boolean)

    fun getAppLockEnabled(): Flow<Boolean>

    suspend fun savePrivacyModeEnabled(isEnabled: Boolean)

    fun getPrivacyModeEnabled(): Flow<Boolean>

    suspend fun saveSimulatorPrivacyModeEnabled(isEnabled: Boolean)

    fun getSimulatorPrivacyModeEnabled(): Flow<Boolean>

    // --- First Launch and Onboarding State ---
    fun getHasSeenOnboarding(): Flow<Boolean>

    suspend fun setHasSeenOnboarding(hasSeen: Boolean)

    fun getIsFirstLaunchComplete(): Flow<Boolean>

    suspend fun setFirstLaunchComplete()
}
