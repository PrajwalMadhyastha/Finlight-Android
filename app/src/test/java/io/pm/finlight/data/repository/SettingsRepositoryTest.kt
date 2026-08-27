package io.pm.finlight.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.pm.finlight.DashboardCardType
import io.pm.finlight.IAppConfigRepository
import io.pm.finlight.IBackupSettingsRepository
import io.pm.finlight.IBudgetSettingsRepository
import io.pm.finlight.IDashboardSettingsRepository
import io.pm.finlight.IFeatureSettingsRepository
import io.pm.finlight.IFirstLaunchSettingsRepository
import io.pm.finlight.INotificationSettingsRepository
import io.pm.finlight.ISecuritySettingsRepository
import io.pm.finlight.ISmsRuleSettingsRepository
import io.pm.finlight.ITravelSettingsRepository
import io.pm.finlight.SettingsRepository
import io.pm.finlight.TravelModeSettings
import io.pm.finlight.TripType
import io.pm.finlight.ui.theme.AppTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SettingsRepositoryTest {
    private val appConfigRepository: IAppConfigRepository = mockk(relaxed = true)
    private val dashboardSettingsRepository: IDashboardSettingsRepository = mockk(relaxed = true)
    private val securitySettingsRepository: ISecuritySettingsRepository = mockk(relaxed = true)
    private val budgetSettingsRepository: IBudgetSettingsRepository = mockk(relaxed = true)
    private val backupSettingsRepository: IBackupSettingsRepository = mockk(relaxed = true)
    private val notificationSettingsRepository: INotificationSettingsRepository = mockk(relaxed = true)
    private val smsRuleSettingsRepository: ISmsRuleSettingsRepository = mockk(relaxed = true)
    private val travelSettingsRepository: ITravelSettingsRepository = mockk(relaxed = true)
    private val firstLaunchSettingsRepository: IFirstLaunchSettingsRepository = mockk(relaxed = true)
    private val featureSettingsRepository: IFeatureSettingsRepository = mockk(relaxed = true)

    private lateinit var repository: SettingsRepository

    @Before
    fun setup() {
        repository =
            SettingsRepository(
                appConfigRepository = appConfigRepository,
                dashboardSettingsRepository = dashboardSettingsRepository,
                securitySettingsRepository = securitySettingsRepository,
                budgetSettingsRepository = budgetSettingsRepository,
                backupSettingsRepository = backupSettingsRepository,
                notificationSettingsRepository = notificationSettingsRepository,
                smsRuleSettingsRepository = smsRuleSettingsRepository,
                travelSettingsRepository = travelSettingsRepository,
                firstLaunchSettingsRepository = firstLaunchSettingsRepository,
                featureSettingsRepository = featureSettingsRepository,
            )
    }

    // --- Recurring Transaction Settings ---

    @Test
    fun `saveRecurringTransactionsEnabled delegates to featureSettingsRepository`() =
        runTest {
            repository.saveRecurringTransactionsEnabled(true)
            coVerify(exactly = 1) { featureSettingsRepository.saveRecurringTransactionsEnabled(true) }
        }

    @Test
    fun `getRecurringTransactionsEnabled delegates to featureSettingsRepository`() =
        runTest {
            every { featureSettingsRepository.getRecurringTransactionsEnabled() } returns flowOf(true)
            val result = repository.getRecurringTransactionsEnabled().first()
            assertEquals(true, result)
        }

    // --- Savings Goals Settings ---

    @Test
    fun `saveGoalIncomeThreshold delegates to featureSettingsRepository`() =
        runTest {
            repository.saveGoalIncomeThreshold(5000)
            coVerify(exactly = 1) { featureSettingsRepository.saveGoalIncomeThreshold(5000) }
        }

    @Test
    fun `getGoalIncomeThreshold delegates to featureSettingsRepository`() =
        runTest {
            every { featureSettingsRepository.getGoalIncomeThreshold() } returns flowOf(5000)
            val result = repository.getGoalIncomeThreshold().first()
            assertEquals(5000, result)
        }

    @Test
    fun `saveGoalNudgesEnabled delegates to featureSettingsRepository`() =
        runTest {
            repository.saveGoalNudgesEnabled(true)
            coVerify(exactly = 1) { featureSettingsRepository.saveGoalNudgesEnabled(true) }
        }

    @Test
    fun `getGoalNudgesEnabled delegates to featureSettingsRepository`() =
        runTest {
            every { featureSettingsRepository.getGoalNudgesEnabled() } returns flowOf(true)
            val result = repository.getGoalNudgesEnabled().first()
            assertEquals(true, result)
        }

    // --- Outlier Month Management Functions ---

    @Test
    fun `getExcludedIncomeMonths delegates to featureSettingsRepository`() =
        runTest {
            val months = setOf("2026-01", "2026-02")
            every { featureSettingsRepository.getExcludedIncomeMonths() } returns flowOf(months)
            val result = repository.getExcludedIncomeMonths().first()
            assertEquals(months, result)
        }

    @Test
    fun `getExcludedExpenseMonths delegates to featureSettingsRepository`() =
        runTest {
            val months = setOf("2026-03")
            every { featureSettingsRepository.getExcludedExpenseMonths() } returns flowOf(months)
            val result = repository.getExcludedExpenseMonths().first()
            assertEquals(months, result)
        }

    @Test
    fun `toggleIncomeMonthExclusion delegates to featureSettingsRepository`() =
        runTest {
            repository.toggleIncomeMonthExclusion("2026-01")
            coVerify(exactly = 1) { featureSettingsRepository.toggleIncomeMonthExclusion("2026-01") }
        }

    @Test
    fun `toggleExpenseMonthExclusion delegates to featureSettingsRepository`() =
        runTest {
            repository.toggleExpenseMonthExclusion("2026-02")
            coVerify(exactly = 1) { featureSettingsRepository.toggleExpenseMonthExclusion("2026-02") }
        }

    // --- Backup Settings ---

    @Test
    fun `saveBackupEnabled delegates to backupSettingsRepository`() =
        runTest {
            repository.saveBackupEnabled(true)
            coVerify(exactly = 1) { backupSettingsRepository.saveBackupEnabled(true) }
        }

    @Test
    fun `getBackupEnabled delegates to backupSettingsRepository`() =
        runTest {
            every { backupSettingsRepository.getBackupEnabled() } returns flowOf(true)
            val result = repository.getBackupEnabled().first()
            assertEquals(true, result)
        }

    @Test
    fun `saveAutoBackupEnabled delegates to backupSettingsRepository`() =
        runTest {
            repository.saveAutoBackupEnabled(false)
            coVerify(exactly = 1) { backupSettingsRepository.saveAutoBackupEnabled(false) }
        }

    @Test
    fun `getAutoBackupEnabled delegates to backupSettingsRepository`() =
        runTest {
            every { backupSettingsRepository.getAutoBackupEnabled() } returns flowOf(false)
            val result = repository.getAutoBackupEnabled().first()
            assertEquals(false, result)
        }

    @Test
    fun `saveAutoBackupNotificationEnabled delegates to backupSettingsRepository`() =
        runTest {
            repository.saveAutoBackupNotificationEnabled(true)
            coVerify(exactly = 1) { backupSettingsRepository.saveAutoBackupNotificationEnabled(true) }
        }

    @Test
    fun `getAutoBackupNotificationEnabled delegates to backupSettingsRepository`() =
        runTest {
            every { backupSettingsRepository.getAutoBackupNotificationEnabled() } returns flowOf(true)
            val result = repository.getAutoBackupNotificationEnabled().first()
            assertEquals(true, result)
        }

    @Test
    fun `saveLastBackupTimestamp delegates to backupSettingsRepository`() =
        runTest {
            repository.saveLastBackupTimestamp(123456789L)
            coVerify(exactly = 1) { backupSettingsRepository.saveLastBackupTimestamp(123456789L) }
        }

    @Test
    fun `getLastBackupTimestamp delegates to backupSettingsRepository`() =
        runTest {
            every { backupSettingsRepository.getLastBackupTimestamp() } returns flowOf(123456789L)
            val result = repository.getLastBackupTimestamp().first()
            assertEquals(123456789L, result)
        }

    // --- SMS Rule and Merge Settings ---

    @Test
    fun `saveSmsScanStartDate delegates to smsRuleSettingsRepository`() =
        runTest {
            repository.saveSmsScanStartDate(1000L)
            coVerify(exactly = 1) { smsRuleSettingsRepository.saveSmsScanStartDate(1000L) }
        }

    @Test
    fun `getSmsScanStartDate delegates to smsRuleSettingsRepository`() =
        runTest {
            every { smsRuleSettingsRepository.getSmsScanStartDate() } returns flowOf(1000L)
            val result = repository.getSmsScanStartDate().first()
            assertEquals(1000L, result)
        }

    @Test
    fun `saveIgnoreRulesChecksum delegates to smsRuleSettingsRepository`() =
        runTest {
            repository.saveIgnoreRulesChecksum(42)
            coVerify(exactly = 1) { smsRuleSettingsRepository.saveIgnoreRulesChecksum(42) }
        }

    @Test
    fun `getIgnoreRulesChecksum delegates to smsRuleSettingsRepository`() =
        runTest {
            coEvery { smsRuleSettingsRepository.getIgnoreRulesChecksum() } returns 42
            val result = repository.getIgnoreRulesChecksum()
            assertEquals(42, result)
        }

    @Test
    fun `getDismissedMergeSuggestions delegates to smsRuleSettingsRepository`() =
        runTest {
            val suggestions = setOf("sug1", "sug2")
            every { smsRuleSettingsRepository.getDismissedMergeSuggestions() } returns flowOf(suggestions)
            val result = repository.getDismissedMergeSuggestions().first()
            assertEquals(suggestions, result)
        }

    @Test
    fun `addDismissedMergeSuggestion delegates to smsRuleSettingsRepository`() =
        runTest {
            repository.addDismissedMergeSuggestion("sug1")
            coVerify(exactly = 1) { smsRuleSettingsRepository.addDismissedMergeSuggestion("sug1") }
        }

    // --- Notification and Report Settings ---

    @Test
    fun `saveDailyReportEnabled delegates to notificationSettingsRepository`() =
        runTest {
            repository.saveDailyReportEnabled(true)
            coVerify(exactly = 1) { notificationSettingsRepository.saveDailyReportEnabled(true) }
        }

    @Test
    fun `getDailyReportEnabled delegates to notificationSettingsRepository`() =
        runTest {
            every { notificationSettingsRepository.getDailyReportEnabled() } returns flowOf(true)
            val result = repository.getDailyReportEnabled().first()
            assertEquals(true, result)
        }

    @Test
    fun `saveDailyReportTime delegates to notificationSettingsRepository`() =
        runTest {
            repository.saveDailyReportTime(20, 30)
            coVerify(exactly = 1) { notificationSettingsRepository.saveDailyReportTime(20, 30) }
        }

    @Test
    fun `getDailyReportTime delegates to notificationSettingsRepository`() =
        runTest {
            every { notificationSettingsRepository.getDailyReportTime() } returns flowOf(Pair(20, 30))
            val result = repository.getDailyReportTime().first()
            assertEquals(Pair(20, 30), result)
        }

    @Test
    fun `saveWeeklySummaryEnabled delegates to notificationSettingsRepository`() =
        runTest {
            repository.saveWeeklySummaryEnabled(true)
            coVerify(exactly = 1) { notificationSettingsRepository.saveWeeklySummaryEnabled(true) }
        }

    @Test
    fun `getWeeklySummaryEnabled delegates to notificationSettingsRepository`() =
        runTest {
            every { notificationSettingsRepository.getWeeklySummaryEnabled() } returns flowOf(true)
            val result = repository.getWeeklySummaryEnabled().first()
            assertEquals(true, result)
        }

    @Test
    fun `saveWeeklyReportTime delegates to notificationSettingsRepository`() =
        runTest {
            repository.saveWeeklyReportTime(1, 10, 0)
            coVerify(exactly = 1) { notificationSettingsRepository.saveWeeklyReportTime(1, 10, 0) }
        }

    @Test
    fun `getWeeklyReportTime delegates to notificationSettingsRepository`() =
        runTest {
            every { notificationSettingsRepository.getWeeklyReportTime() } returns flowOf(Triple(1, 10, 0))
            val result = repository.getWeeklyReportTime().first()
            assertEquals(Triple(1, 10, 0), result)
        }

    @Test
    fun `saveMonthlySummaryEnabled delegates to notificationSettingsRepository`() =
        runTest {
            repository.saveMonthlySummaryEnabled(false)
            coVerify(exactly = 1) { notificationSettingsRepository.saveMonthlySummaryEnabled(false) }
        }

    @Test
    fun `getMonthlySummaryEnabled delegates to notificationSettingsRepository`() =
        runTest {
            every { notificationSettingsRepository.getMonthlySummaryEnabled() } returns flowOf(false)
            val result = repository.getMonthlySummaryEnabled().first()
            assertEquals(false, result)
        }

    @Test
    fun `saveMonthlyReportTime delegates to notificationSettingsRepository`() =
        runTest {
            repository.saveMonthlyReportTime(15, 9, 30)
            coVerify(exactly = 1) { notificationSettingsRepository.saveMonthlyReportTime(15, 9, 30) }
        }

    @Test
    fun `getMonthlyReportTime delegates to notificationSettingsRepository`() =
        runTest {
            every { notificationSettingsRepository.getMonthlyReportTime() } returns flowOf(Triple(15, 9, 30))
            val result = repository.getMonthlyReportTime().first()
            assertEquals(Triple(15, 9, 30), result)
        }

    @Test
    fun `saveAutoCaptureNotificationEnabled delegates to notificationSettingsRepository`() =
        runTest {
            repository.saveAutoCaptureNotificationEnabled(false)
            coVerify(exactly = 1) { notificationSettingsRepository.saveAutoCaptureNotificationEnabled(false) }
        }

    @Test
    fun `getAutoCaptureNotificationEnabled delegates to notificationSettingsRepository`() =
        runTest {
            every { notificationSettingsRepository.getAutoCaptureNotificationEnabled() } returns flowOf(false)
            val result = repository.getAutoCaptureNotificationEnabled().first()
            assertEquals(false, result)
        }

    @Test
    fun `saveUnknownTransactionPopupEnabled delegates to notificationSettingsRepository`() =
        runTest {
            repository.saveUnknownTransactionPopupEnabled(true)
            coVerify(exactly = 1) { notificationSettingsRepository.saveUnknownTransactionPopupEnabled(true) }
        }

    @Test
    fun `getUnknownTransactionPopupEnabled delegates to notificationSettingsRepository`() =
        runTest {
            every { notificationSettingsRepository.getUnknownTransactionPopupEnabled() } returns flowOf(true)
            val result = repository.getUnknownTransactionPopupEnabled().first()
            assertEquals(true, result)
        }

    @Test
    fun `setLastMonthSummaryDismissed delegates to notificationSettingsRepository`() =
        runTest {
            repository.setLastMonthSummaryDismissed()
            coVerify(exactly = 1) { notificationSettingsRepository.setLastMonthSummaryDismissed() }
        }

    @Test
    fun `hasLastMonthSummaryBeenDismissed delegates to notificationSettingsRepository`() =
        runTest {
            every { notificationSettingsRepository.hasLastMonthSummaryBeenDismissed() } returns flowOf(true)
            val result = repository.hasLastMonthSummaryBeenDismissed().first()
            assertEquals(true, result)
        }

    // --- App Config (User, Theme, Currency) ---

    @Test
    fun `saveUserName delegates to appConfigRepository`() =
        runTest {
            repository.saveUserName("Alice")
            coVerify(exactly = 1) { appConfigRepository.saveUserName("Alice") }
        }

    @Test
    fun `getUserName delegates to appConfigRepository`() =
        runTest {
            every { appConfigRepository.getUserName() } returns flowOf("Alice")
            val result = repository.getUserName().first()
            assertEquals("Alice", result)
        }

    @Test
    fun `saveProfilePictureUri delegates to appConfigRepository`() =
        runTest {
            repository.saveProfilePictureUri("content://uri")
            coVerify(exactly = 1) { appConfigRepository.saveProfilePictureUri("content://uri") }
        }

    @Test
    fun `getProfilePictureUri delegates to appConfigRepository`() =
        runTest {
            every { appConfigRepository.getProfilePictureUri() } returns flowOf("content://uri")
            val result = repository.getProfilePictureUri().first()
            assertEquals("content://uri", result)
        }

    @Test
    fun `saveSelectedTheme delegates to appConfigRepository`() =
        runTest {
            repository.saveSelectedTheme(AppTheme.SYSTEM_DEFAULT)
            coVerify(exactly = 1) { appConfigRepository.saveSelectedTheme(AppTheme.SYSTEM_DEFAULT) }
        }

    @Test
    fun `getSelectedTheme delegates to appConfigRepository`() =
        runTest {
            every { appConfigRepository.getSelectedTheme() } returns flowOf(AppTheme.SYSTEM_DEFAULT)
            val result = repository.getSelectedTheme().first()
            assertEquals(AppTheme.SYSTEM_DEFAULT, result)
        }

    @Test
    fun `saveHomeCurrency delegates to appConfigRepository`() =
        runTest {
            repository.saveHomeCurrency("EUR")
            coVerify(exactly = 1) { appConfigRepository.saveHomeCurrency("EUR") }
        }

    @Test
    fun `getHomeCurrency delegates to appConfigRepository`() =
        runTest {
            every { appConfigRepository.getHomeCurrency() } returns flowOf("EUR")
            val result = repository.getHomeCurrency().first()
            assertEquals("EUR", result)
        }

    // --- Travel Mode Settings ---

    @Test
    fun `saveTravelModeSettings delegates to travelSettingsRepository`() =
        runTest {
            val settings =
                TravelModeSettings(
                    isEnabled = true,
                    tripName = "Paris Trip",
                    tripType = TripType.INTERNATIONAL,
                    startDate = 1000L,
                    endDate = 2000L,
                    currencyCode = "EUR",
                    conversionRate = 1.1f,
                )
            repository.saveTravelModeSettings(settings)
            coVerify(exactly = 1) { travelSettingsRepository.saveTravelModeSettings(settings) }
        }

    @Test
    fun `getTravelModeSettings delegates to travelSettingsRepository`() =
        runTest {
            val settings =
                TravelModeSettings(
                    isEnabled = true,
                    tripName = "Paris Trip",
                    tripType = TripType.INTERNATIONAL,
                    startDate = 1000L,
                    endDate = 2000L,
                    currencyCode = "EUR",
                    conversionRate = 1.1f,
                )
            every { travelSettingsRepository.getTravelModeSettings() } returns flowOf(settings)
            val result = repository.getTravelModeSettings().first()
            assertEquals(settings, result)
        }

    // --- Budget Settings ---

    @Test
    fun `saveOverallBudgetForCurrentMonth delegates to budgetSettingsRepository`() =
        runTest {
            repository.saveOverallBudgetForCurrentMonth(1500f)
            coVerify(exactly = 1) { budgetSettingsRepository.saveOverallBudgetForCurrentMonth(1500f) }
        }

    @Test
    fun `saveOverallBudgetForMonth delegates to budgetSettingsRepository`() =
        runTest {
            repository.saveOverallBudgetForMonth(2026, 8, 2000f)
            coVerify(exactly = 1) { budgetSettingsRepository.saveOverallBudgetForMonth(2026, 8, 2000f) }
        }

    @Test
    fun `getOverallBudgetsForYear delegates to budgetSettingsRepository`() =
        runTest {
            val yearBudgets = mapOf(1 to 1000f, 2 to 1200f)
            coEvery { budgetSettingsRepository.getOverallBudgetsForYear(2026) } returns yearBudgets
            val result = repository.getOverallBudgetsForYear(2026)
            assertEquals(yearBudgets, result)
        }

    @Test
    fun `getOverallBudgetForMonth delegates to budgetSettingsRepository`() =
        runTest {
            every { budgetSettingsRepository.getOverallBudgetForMonth(2026, 8) } returns flowOf(2000f)
            val result = repository.getOverallBudgetForMonth(2026, 8).first()
            assertEquals(2000f, result)
        }

    // --- Dashboard Settings ---

    @Test
    fun `saveDashboardLayout delegates to dashboardSettingsRepository`() =
        runTest {
            val order = listOf(DashboardCardType.HERO_BUDGET, DashboardCardType.RECENT_TRANSACTIONS)
            val visible = setOf(DashboardCardType.HERO_BUDGET)
            repository.saveDashboardLayout(order, visible)
            coVerify(exactly = 1) { dashboardSettingsRepository.saveDashboardLayout(order, visible) }
        }

    @Test
    fun `getDashboardCardOrder delegates to dashboardSettingsRepository`() =
        runTest {
            val order = listOf(DashboardCardType.HERO_BUDGET, DashboardCardType.RECENT_TRANSACTIONS)
            every { dashboardSettingsRepository.getDashboardCardOrder() } returns flowOf(order)
            val result = repository.getDashboardCardOrder().first()
            assertEquals(order, result)
        }

    @Test
    fun `getDashboardVisibleCards delegates to dashboardSettingsRepository`() =
        runTest {
            val visible = setOf(DashboardCardType.HERO_BUDGET)
            every { dashboardSettingsRepository.getDashboardVisibleCards() } returns flowOf(visible)
            val result = repository.getDashboardVisibleCards().first()
            assertEquals(visible, result)
        }

    // --- Security Settings ---

    @Test
    fun `saveAppLockEnabled delegates to securitySettingsRepository`() =
        runTest {
            repository.saveAppLockEnabled(true)
            coVerify(exactly = 1) { securitySettingsRepository.saveAppLockEnabled(true) }
        }

    @Test
    fun `getAppLockEnabled delegates to securitySettingsRepository`() =
        runTest {
            every { securitySettingsRepository.getAppLockEnabled() } returns flowOf(true)
            val result = repository.getAppLockEnabled().first()
            assertEquals(true, result)
        }

    @Test
    fun `savePrivacyModeEnabled delegates to securitySettingsRepository`() =
        runTest {
            repository.savePrivacyModeEnabled(true)
            coVerify(exactly = 1) { securitySettingsRepository.savePrivacyModeEnabled(true) }
        }

    @Test
    fun `getPrivacyModeEnabled delegates to securitySettingsRepository`() =
        runTest {
            every { securitySettingsRepository.getPrivacyModeEnabled() } returns flowOf(true)
            val result = repository.getPrivacyModeEnabled().first()
            assertEquals(true, result)
        }

    @Test
    fun `saveSimulatorPrivacyModeEnabled delegates to securitySettingsRepository`() =
        runTest {
            repository.saveSimulatorPrivacyModeEnabled(false)
            coVerify(exactly = 1) { securitySettingsRepository.saveSimulatorPrivacyModeEnabled(false) }
        }

    @Test
    fun `getSimulatorPrivacyModeEnabled delegates to securitySettingsRepository`() =
        runTest {
            every { securitySettingsRepository.getSimulatorPrivacyModeEnabled() } returns flowOf(false)
            val result = repository.getSimulatorPrivacyModeEnabled().first()
            assertEquals(false, result)
        }

    // --- First Launch and Onboarding State ---

    @Test
    fun `getHasSeenOnboarding delegates to firstLaunchSettingsRepository`() =
        runTest {
            every { firstLaunchSettingsRepository.getHasSeenOnboarding() } returns flowOf(true)
            val result = repository.getHasSeenOnboarding().first()
            assertEquals(true, result)
        }

    @Test
    fun `setHasSeenOnboarding delegates to firstLaunchSettingsRepository`() =
        runTest {
            repository.setHasSeenOnboarding(true)
            coVerify(exactly = 1) { firstLaunchSettingsRepository.setHasSeenOnboarding(true) }
        }

    @Test
    fun `getIsFirstLaunchComplete delegates to firstLaunchSettingsRepository`() =
        runTest {
            every { firstLaunchSettingsRepository.getIsFirstLaunchComplete() } returns flowOf(true)
            val result = repository.getIsFirstLaunchComplete().first()
            assertEquals(true, result)
        }

    @Test
    fun `setFirstLaunchComplete delegates to firstLaunchSettingsRepository`() =
        runTest {
            repository.setFirstLaunchComplete()
            coVerify(exactly = 1) { firstLaunchSettingsRepository.setFirstLaunchComplete() }
        }
}
