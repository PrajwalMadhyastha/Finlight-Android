package io.pm.finlight

import android.content.Context
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
    private val appConfigRepository: AppConfigRepository,
    private val dashboardSettingsRepository: DashboardSettingsRepository,
    private val securitySettingsRepository: SecuritySettingsRepository,
    private val budgetSettingsRepository: BudgetSettingsRepository,
    private val backupSettingsRepository: BackupSettingsRepository,
    private val notificationSettingsRepository: NotificationSettingsRepository,
    private val smsRuleSettingsRepository: SmsRuleSettingsRepository,
    private val travelSettingsRepository: TravelSettingsRepository,
    private val firstLaunchSettingsRepository: FirstLaunchSettingsRepository,
    private val featureSettingsRepository: FeatureSettingsRepository,
) {
    constructor(context: Context) : this(
        appConfigRepository = AppConfigRepository(context),
        dashboardSettingsRepository = DashboardSettingsRepository(context),
        securitySettingsRepository = SecuritySettingsRepository(context),
        budgetSettingsRepository = BudgetSettingsRepository(context),
        backupSettingsRepository = BackupSettingsRepository(context),
        notificationSettingsRepository = NotificationSettingsRepository(context),
        smsRuleSettingsRepository = SmsRuleSettingsRepository(context),
        travelSettingsRepository = TravelSettingsRepository(context),
        firstLaunchSettingsRepository = FirstLaunchSettingsRepository(context),
        featureSettingsRepository = FeatureSettingsRepository(context),
    )

    // --- Recurring Transaction Settings ---

    fun saveRecurringTransactionsEnabled(isEnabled: Boolean) =
        featureSettingsRepository.saveRecurringTransactionsEnabled(isEnabled)

    fun getRecurringTransactionsEnabled(): Flow<Boolean> =
        featureSettingsRepository.getRecurringTransactionsEnabled()

    // --- Savings Goals Settings ---

    fun saveGoalIncomeThreshold(amount: Int) =
        featureSettingsRepository.saveGoalIncomeThreshold(amount)

    fun getGoalIncomeThreshold(): Flow<Int> =
        featureSettingsRepository.getGoalIncomeThreshold()

    fun saveGoalNudgesEnabled(isEnabled: Boolean) =
        featureSettingsRepository.saveGoalNudgesEnabled(isEnabled)

    fun getGoalNudgesEnabled(): Flow<Boolean> =
        featureSettingsRepository.getGoalNudgesEnabled()

    fun isGoalNudgesEnabledBlocking(): Boolean =
        featureSettingsRepository.isGoalNudgesEnabledBlocking()

    // --- Outlier Month Management Functions ---

    fun getExcludedIncomeMonths(): Flow<Set<String>> =
        featureSettingsRepository.getExcludedIncomeMonths()

    fun getExcludedExpenseMonths(): Flow<Set<String>> =
        featureSettingsRepository.getExcludedExpenseMonths()

    fun toggleIncomeMonthExclusion(monthKey: String) =
        featureSettingsRepository.toggleIncomeMonthExclusion(monthKey)

    fun toggleExpenseMonthExclusion(monthKey: String) =
        featureSettingsRepository.toggleExpenseMonthExclusion(monthKey)

    // --- Backup Settings ---

    fun saveBackupEnabled(isEnabled: Boolean) =
        backupSettingsRepository.saveBackupEnabled(isEnabled)

    fun getBackupEnabled(): Flow<Boolean> =
        backupSettingsRepository.getBackupEnabled()

    fun saveAutoBackupEnabled(isEnabled: Boolean) =
        backupSettingsRepository.saveAutoBackupEnabled(isEnabled)

    fun getAutoBackupEnabled(): Flow<Boolean> =
        backupSettingsRepository.getAutoBackupEnabled()

    fun saveAutoBackupNotificationEnabled(isEnabled: Boolean) =
        backupSettingsRepository.saveAutoBackupNotificationEnabled(isEnabled)

    fun getAutoBackupNotificationEnabled(): Flow<Boolean> =
        backupSettingsRepository.getAutoBackupNotificationEnabled()

    fun isAutoBackupNotificationEnabledBlocking(): Boolean =
        backupSettingsRepository.isAutoBackupNotificationEnabledBlocking()

    fun saveLastBackupTimestamp(timestamp: Long) =
        backupSettingsRepository.saveLastBackupTimestamp(timestamp)

    fun getLastBackupTimestamp(): Flow<Long> =
        backupSettingsRepository.getLastBackupTimestamp()

    // --- SMS Rule and Merge Settings ---

    fun saveSmsScanStartDate(date: Long) =
        smsRuleSettingsRepository.saveSmsScanStartDate(date)

    fun getSmsScanStartDate(): Flow<Long> =
        smsRuleSettingsRepository.getSmsScanStartDate()

    fun saveIgnoreRulesChecksum(checksum: Int) =
        smsRuleSettingsRepository.saveIgnoreRulesChecksum(checksum)

    fun getIgnoreRulesChecksum(): Int =
        smsRuleSettingsRepository.getIgnoreRulesChecksum()

    fun getDismissedMergeSuggestions(): Flow<Set<String>> =
        smsRuleSettingsRepository.getDismissedMergeSuggestions()

    fun addDismissedMergeSuggestion(suggestionKey: String) =
        smsRuleSettingsRepository.addDismissedMergeSuggestion(suggestionKey)

    // --- Notification and Report Settings ---

    fun saveDailyReportEnabled(isEnabled: Boolean) =
        notificationSettingsRepository.saveDailyReportEnabled(isEnabled)

    fun getDailyReportEnabled(): Flow<Boolean> =
        notificationSettingsRepository.getDailyReportEnabled()

    fun saveDailyReportTime(
        hour: Int,
        minute: Int
    ) =
        notificationSettingsRepository.saveDailyReportTime(hour, minute)

    fun getDailyReportTime(): Flow<Pair<Int, Int>> =
        notificationSettingsRepository.getDailyReportTime()

    fun saveWeeklySummaryEnabled(isEnabled: Boolean) =
        notificationSettingsRepository.saveWeeklySummaryEnabled(isEnabled)

    fun getWeeklySummaryEnabled(): Flow<Boolean> =
        notificationSettingsRepository.getWeeklySummaryEnabled()

    fun saveWeeklyReportTime(
        dayOfWeek: Int,
        hour: Int,
        minute: Int
    ) =
        notificationSettingsRepository.saveWeeklyReportTime(dayOfWeek, hour, minute)

    fun getWeeklyReportTime(): Flow<Triple<Int, Int, Int>> =
        notificationSettingsRepository.getWeeklyReportTime()

    fun saveMonthlySummaryEnabled(isEnabled: Boolean) =
        notificationSettingsRepository.saveMonthlySummaryEnabled(isEnabled)

    fun getMonthlySummaryEnabled(): Flow<Boolean> =
        notificationSettingsRepository.getMonthlySummaryEnabled()

    fun saveMonthlyReportTime(
        dayOfMonth: Int,
        hour: Int,
        minute: Int
    ) =
        notificationSettingsRepository.saveMonthlyReportTime(dayOfMonth, hour, minute)

    fun getMonthlyReportTime(): Flow<Triple<Int, Int, Int>> =
        notificationSettingsRepository.getMonthlyReportTime()

    fun saveAutoCaptureNotificationEnabled(isEnabled: Boolean) =
        notificationSettingsRepository.saveAutoCaptureNotificationEnabled(isEnabled)

    fun getAutoCaptureNotificationEnabled(): Flow<Boolean> =
        notificationSettingsRepository.getAutoCaptureNotificationEnabled()

    fun isAutoCaptureNotificationEnabledBlocking(): Boolean =
        notificationSettingsRepository.isAutoCaptureNotificationEnabledBlocking()

    fun saveUnknownTransactionPopupEnabled(isEnabled: Boolean) =
        notificationSettingsRepository.saveUnknownTransactionPopupEnabled(isEnabled)

    fun getUnknownTransactionPopupEnabled(): Flow<Boolean> =
        notificationSettingsRepository.getUnknownTransactionPopupEnabled()

    fun isUnknownTransactionPopupEnabledBlocking(): Boolean =
        notificationSettingsRepository.isUnknownTransactionPopupEnabledBlocking()

    fun setLastMonthSummaryDismissed() =
        notificationSettingsRepository.setLastMonthSummaryDismissed()

    fun hasLastMonthSummaryBeenDismissed(): Boolean =
        notificationSettingsRepository.hasLastMonthSummaryBeenDismissed()

    // --- App Config (User, Theme, Currency) ---

    fun saveUserName(name: String) =
        appConfigRepository.saveUserName(name)

    fun getUserName(): Flow<String> =
        appConfigRepository.getUserName()

    fun saveProfilePictureUri(uriString: String?) =
        appConfigRepository.saveProfilePictureUri(uriString)

    fun getProfilePictureUri(): Flow<String?> =
        appConfigRepository.getProfilePictureUri()

    fun saveSelectedTheme(theme: AppTheme) =
        appConfigRepository.saveSelectedTheme(theme)

    fun getSelectedTheme(): Flow<AppTheme> =
        appConfigRepository.getSelectedTheme()

    fun saveHomeCurrency(currencyCode: String) =
        appConfigRepository.saveHomeCurrency(currencyCode)

    fun getHomeCurrency(): Flow<String> =
        appConfigRepository.getHomeCurrency()

    // --- Travel Mode Settings ---

    fun saveTravelModeSettings(settings: TravelModeSettings?) =
        travelSettingsRepository.saveTravelModeSettings(settings)

    fun getTravelModeSettings(): Flow<TravelModeSettings?> =
        travelSettingsRepository.getTravelModeSettings()

    // --- Budget Settings ---

    fun saveOverallBudgetForCurrentMonth(amount: Float) =
        budgetSettingsRepository.saveOverallBudgetForCurrentMonth(amount)

    fun saveOverallBudgetForMonth(
        year: Int,
        month: Int,
        amount: Float,
    ) = budgetSettingsRepository.saveOverallBudgetForMonth(year, month, amount)

    fun getOverallBudgetsForYear(year: Int): Map<Int, Float> =
        budgetSettingsRepository.getOverallBudgetsForYear(year)

    fun getOverallBudgetForMonthBlocking(
        year: Int,
        month: Int,
    ): Float? = budgetSettingsRepository.getOverallBudgetForMonthBlocking(year, month)

    fun getOverallBudgetForMonth(
        year: Int,
        month: Int,
    ): Flow<Float?> = budgetSettingsRepository.getOverallBudgetForMonth(year, month)

    // --- Dashboard Settings ---

    fun saveDashboardLayout(
        order: List<DashboardCardType>,
        visible: Set<DashboardCardType>,
    ) = dashboardSettingsRepository.saveDashboardLayout(order, visible)

    fun getDashboardCardOrder(): Flow<List<DashboardCardType>> =
        dashboardSettingsRepository.getDashboardCardOrder()

    fun getDashboardVisibleCards(): Flow<Set<DashboardCardType>> =
        dashboardSettingsRepository.getDashboardVisibleCards()

    // --- Security Settings ---

    fun saveAppLockEnabled(isEnabled: Boolean) =
        securitySettingsRepository.saveAppLockEnabled(isEnabled)

    fun getAppLockEnabled(): Flow<Boolean> =
        securitySettingsRepository.getAppLockEnabled()

    fun isAppLockEnabledBlocking(): Boolean =
        securitySettingsRepository.isAppLockEnabledBlocking()

    fun savePrivacyModeEnabled(isEnabled: Boolean) =
        securitySettingsRepository.savePrivacyModeEnabled(isEnabled)

    fun getPrivacyModeEnabled(): Flow<Boolean> =
        securitySettingsRepository.getPrivacyModeEnabled()

    fun saveSimulatorPrivacyModeEnabled(isEnabled: Boolean) =
        securitySettingsRepository.saveSimulatorPrivacyModeEnabled(isEnabled)

    fun getSimulatorPrivacyModeEnabled(): Flow<Boolean> =
        securitySettingsRepository.getSimulatorPrivacyModeEnabled()

    // --- First Launch and Onboarding State ---

    fun hasSeenOnboarding(): Boolean =
        firstLaunchSettingsRepository.hasSeenOnboarding()

    fun setHasSeenOnboarding(hasSeen: Boolean) =
        firstLaunchSettingsRepository.setHasSeenOnboarding(hasSeen)

    fun isFirstLaunchCompleteBlocking(): Boolean =
        firstLaunchSettingsRepository.isFirstLaunchCompleteBlocking()

    fun setFirstLaunchComplete() =
        firstLaunchSettingsRepository.setFirstLaunchComplete()
}
