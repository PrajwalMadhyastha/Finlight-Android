package io.pm.finlight

import io.pm.finlight.ui.theme.AppTheme
import kotlinx.coroutines.flow.Flow

/**
 * An enum to distinguish between domestic and international travel modes.
 */
enum class TripType {
    DOMESTIC,
    INTERNATIONAL,
}

data class TravelModeSettings(
    val isEnabled: Boolean,
    val tripName: String,
    val tripType: TripType,
    val startDate: Long,
    val endDate: Long,
    val currencyCode: String?,
    val conversionRate: Float?,
)

/**
 * Facade repository combining granular domain settings repositories for backward compatibility.
 */
class SettingsRepository(
    private val appConfigRepository: IAppConfigRepository,
    private val dashboardSettingsRepository: IDashboardSettingsRepository,
    private val securitySettingsRepository: ISecuritySettingsRepository,
    private val budgetSettingsRepository: IBudgetSettingsRepository,
    private val backupSettingsRepository: IBackupSettingsRepository,
    private val notificationSettingsRepository: INotificationSettingsRepository,
    private val smsRuleSettingsRepository: ISmsRuleSettingsRepository,
    private val travelSettingsRepository: ITravelSettingsRepository,
    private val firstLaunchSettingsRepository: IFirstLaunchSettingsRepository,
    private val featureSettingsRepository: IFeatureSettingsRepository,
) : ISettingsRepository {
    // --- Recurring Transaction Settings ---

    override suspend fun saveRecurringTransactionsEnabled(isEnabled: Boolean) =
        featureSettingsRepository.saveRecurringTransactionsEnabled(isEnabled)

    override fun getRecurringTransactionsEnabled(): Flow<Boolean> =
        featureSettingsRepository.getRecurringTransactionsEnabled()

    // --- Savings Goals Settings ---

    override suspend fun saveGoalIncomeThreshold(amount: Int) =
        featureSettingsRepository.saveGoalIncomeThreshold(amount)

    override fun getGoalIncomeThreshold(): Flow<Int> =
        featureSettingsRepository.getGoalIncomeThreshold()

    override suspend fun saveGoalNudgesEnabled(isEnabled: Boolean) =
        featureSettingsRepository.saveGoalNudgesEnabled(isEnabled)

    override fun getGoalNudgesEnabled(): Flow<Boolean> =
        featureSettingsRepository.getGoalNudgesEnabled()

    // --- Outlier Month Management Functions ---

    override fun getExcludedIncomeMonths(): Flow<Set<String>> =
        featureSettingsRepository.getExcludedIncomeMonths()

    override fun getExcludedExpenseMonths(): Flow<Set<String>> =
        featureSettingsRepository.getExcludedExpenseMonths()

    override suspend fun toggleIncomeMonthExclusion(monthKey: String) =
        featureSettingsRepository.toggleIncomeMonthExclusion(monthKey)

    override suspend fun toggleExpenseMonthExclusion(monthKey: String) =
        featureSettingsRepository.toggleExpenseMonthExclusion(monthKey)

    // --- Backup Settings ---

    override suspend fun saveBackupEnabled(isEnabled: Boolean) =
        backupSettingsRepository.saveBackupEnabled(isEnabled)

    override fun getBackupEnabled(): Flow<Boolean> =
        backupSettingsRepository.getBackupEnabled()

    override suspend fun saveAutoBackupEnabled(isEnabled: Boolean) =
        backupSettingsRepository.saveAutoBackupEnabled(isEnabled)

    override fun getAutoBackupEnabled(): Flow<Boolean> =
        backupSettingsRepository.getAutoBackupEnabled()

    override suspend fun saveAutoBackupNotificationEnabled(isEnabled: Boolean) =
        backupSettingsRepository.saveAutoBackupNotificationEnabled(isEnabled)

    override fun getAutoBackupNotificationEnabled(): Flow<Boolean> =
        backupSettingsRepository.getAutoBackupNotificationEnabled()

    override suspend fun saveLastBackupTimestamp(timestamp: Long) =
        backupSettingsRepository.saveLastBackupTimestamp(timestamp)

    override fun getLastBackupTimestamp(): Flow<Long> =
        backupSettingsRepository.getLastBackupTimestamp()

    // --- SMS Rule and Merge Settings ---

    override suspend fun saveSmsScanStartDate(date: Long) =
        smsRuleSettingsRepository.saveSmsScanStartDate(date)

    override fun getSmsScanStartDate(): Flow<Long> =
        smsRuleSettingsRepository.getSmsScanStartDate()

    override suspend fun saveIgnoreRulesChecksum(checksum: Int) =
        smsRuleSettingsRepository.saveIgnoreRulesChecksum(checksum)

    override suspend fun getIgnoreRulesChecksum(): Int =
        smsRuleSettingsRepository.getIgnoreRulesChecksum()

    override fun getDismissedMergeSuggestions(): Flow<Set<String>> =
        smsRuleSettingsRepository.getDismissedMergeSuggestions()

    override suspend fun addDismissedMergeSuggestion(suggestionKey: String) =
        smsRuleSettingsRepository.addDismissedMergeSuggestion(suggestionKey)

    // --- Notification and Report Settings ---

    override suspend fun saveDailyReportEnabled(isEnabled: Boolean) =
        notificationSettingsRepository.saveDailyReportEnabled(isEnabled)

    override fun getDailyReportEnabled(): Flow<Boolean> =
        notificationSettingsRepository.getDailyReportEnabled()

    override suspend fun saveDailyReportTime(
        hour: Int,
        minute: Int,
    ) =
        notificationSettingsRepository.saveDailyReportTime(hour, minute)

    override fun getDailyReportTime(): Flow<Pair<Int, Int>> =
        notificationSettingsRepository.getDailyReportTime()

    override suspend fun saveWeeklySummaryEnabled(isEnabled: Boolean) =
        notificationSettingsRepository.saveWeeklySummaryEnabled(isEnabled)

    override fun getWeeklySummaryEnabled(): Flow<Boolean> =
        notificationSettingsRepository.getWeeklySummaryEnabled()

    override suspend fun saveWeeklyReportTime(
        dayOfWeek: Int,
        hour: Int,
        minute: Int,
    ) =
        notificationSettingsRepository.saveWeeklyReportTime(dayOfWeek, hour, minute)

    override fun getWeeklyReportTime(): Flow<Triple<Int, Int, Int>> =
        notificationSettingsRepository.getWeeklyReportTime()

    override suspend fun saveMonthlySummaryEnabled(isEnabled: Boolean) =
        notificationSettingsRepository.saveMonthlySummaryEnabled(isEnabled)

    override fun getMonthlySummaryEnabled(): Flow<Boolean> =
        notificationSettingsRepository.getMonthlySummaryEnabled()

    override suspend fun saveMonthlyReportTime(
        dayOfMonth: Int,
        hour: Int,
        minute: Int,
    ) =
        notificationSettingsRepository.saveMonthlyReportTime(dayOfMonth, hour, minute)

    override fun getMonthlyReportTime(): Flow<Triple<Int, Int, Int>> =
        notificationSettingsRepository.getMonthlyReportTime()

    override suspend fun saveAutoCaptureNotificationEnabled(isEnabled: Boolean) =
        notificationSettingsRepository.saveAutoCaptureNotificationEnabled(isEnabled)

    override fun getAutoCaptureNotificationEnabled(): Flow<Boolean> =
        notificationSettingsRepository.getAutoCaptureNotificationEnabled()

    override suspend fun saveUnknownTransactionPopupEnabled(isEnabled: Boolean) =
        notificationSettingsRepository.saveUnknownTransactionPopupEnabled(isEnabled)

    override fun getUnknownTransactionPopupEnabled(): Flow<Boolean> =
        notificationSettingsRepository.getUnknownTransactionPopupEnabled()

    override suspend fun setLastMonthSummaryDismissed() =
        notificationSettingsRepository.setLastMonthSummaryDismissed()

    override fun hasLastMonthSummaryBeenDismissed(): Flow<Boolean> =
        notificationSettingsRepository.hasLastMonthSummaryBeenDismissed()

    // --- App Config (User, Theme, Currency) ---

    override suspend fun saveUserName(name: String) =
        appConfigRepository.saveUserName(name)

    override fun getUserName(): Flow<String> =
        appConfigRepository.getUserName()

    override suspend fun saveProfilePictureUri(uriString: String?) =
        appConfigRepository.saveProfilePictureUri(uriString)

    override fun getProfilePictureUri(): Flow<String?> =
        appConfigRepository.getProfilePictureUri()

    override suspend fun saveSelectedTheme(theme: AppTheme) =
        appConfigRepository.saveSelectedTheme(theme)

    override fun getSelectedTheme(): Flow<AppTheme> =
        appConfigRepository.getSelectedTheme()

    override suspend fun saveHomeCurrency(currencyCode: String) =
        appConfigRepository.saveHomeCurrency(currencyCode)

    override fun getHomeCurrency(): Flow<String> =
        appConfigRepository.getHomeCurrency()

    // --- Travel Mode Settings ---

    override suspend fun saveTravelModeSettings(settings: TravelModeSettings?) =
        travelSettingsRepository.saveTravelModeSettings(settings)

    override fun getTravelModeSettings(): Flow<TravelModeSettings?> =
        travelSettingsRepository.getTravelModeSettings()

    override suspend fun getCurrentTravelModeSettings(): TravelModeSettings? =
        travelSettingsRepository.getCurrentTravelModeSettings()

    // --- Budget Settings ---

    override suspend fun saveOverallBudgetForCurrentMonth(amount: Float) =
        budgetSettingsRepository.saveOverallBudgetForCurrentMonth(amount)

    override suspend fun saveOverallBudgetForMonth(
        year: Int,
        month: Int,
        amount: Float,
    ) = budgetSettingsRepository.saveOverallBudgetForMonth(year, month, amount)

    override suspend fun getOverallBudgetsForYear(year: Int): Map<Int, Float> =
        budgetSettingsRepository.getOverallBudgetsForYear(year)

    override fun getOverallBudgetForMonth(
        year: Int,
        month: Int,
    ): Flow<Float?> = budgetSettingsRepository.getOverallBudgetForMonth(year, month)

    // --- Dashboard Settings ---

    override suspend fun saveDashboardLayout(
        order: List<DashboardCardType>,
        visible: Set<DashboardCardType>,
    ) = dashboardSettingsRepository.saveDashboardLayout(order, visible)

    override fun getDashboardCardOrder(): Flow<List<DashboardCardType>> =
        dashboardSettingsRepository.getDashboardCardOrder()

    override fun getDashboardVisibleCards(): Flow<Set<DashboardCardType>> =
        dashboardSettingsRepository.getDashboardVisibleCards()

    // --- Security Settings ---

    override suspend fun saveAppLockEnabled(isEnabled: Boolean) =
        securitySettingsRepository.saveAppLockEnabled(isEnabled)

    override fun getAppLockEnabled(): Flow<Boolean> =
        securitySettingsRepository.getAppLockEnabled()

    override suspend fun savePrivacyModeEnabled(isEnabled: Boolean) =
        securitySettingsRepository.savePrivacyModeEnabled(isEnabled)

    override fun getPrivacyModeEnabled(): Flow<Boolean> =
        securitySettingsRepository.getPrivacyModeEnabled()

    override suspend fun saveSimulatorPrivacyModeEnabled(isEnabled: Boolean) =
        securitySettingsRepository.saveSimulatorPrivacyModeEnabled(isEnabled)

    override fun getSimulatorPrivacyModeEnabled(): Flow<Boolean> =
        securitySettingsRepository.getSimulatorPrivacyModeEnabled()

    // --- First Launch and Onboarding State ---

    override fun getHasSeenOnboarding(): Flow<Boolean> =
        firstLaunchSettingsRepository.getHasSeenOnboarding()

    override suspend fun setHasSeenOnboarding(hasSeen: Boolean) =
        firstLaunchSettingsRepository.setHasSeenOnboarding(hasSeen)

    override fun getIsFirstLaunchComplete(): Flow<Boolean> =
        firstLaunchSettingsRepository.getIsFirstLaunchComplete()

    override suspend fun setFirstLaunchComplete() =
        firstLaunchSettingsRepository.setFirstLaunchComplete()
}
