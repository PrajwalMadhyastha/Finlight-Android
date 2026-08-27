package io.pm.finlight.data.repository

import io.mockk.mockk
import io.pm.finlight.AccountRepository
import io.pm.finlight.AppConfigRepository
import io.pm.finlight.BackupSettingsRepository
import io.pm.finlight.BudgetRepository
import io.pm.finlight.BudgetSettingsRepository
import io.pm.finlight.CategoryRepository
import io.pm.finlight.DashboardSettingsRepository
import io.pm.finlight.FeatureSettingsRepository
import io.pm.finlight.FirstLaunchSettingsRepository
import io.pm.finlight.GoalRepository
import io.pm.finlight.IAccountRepository
import io.pm.finlight.IAppConfigRepository
import io.pm.finlight.IBackupSettingsRepository
import io.pm.finlight.IBudgetRepository
import io.pm.finlight.IBudgetSettingsRepository
import io.pm.finlight.ICategoryRepository
import io.pm.finlight.IDashboardSettingsRepository
import io.pm.finlight.IFeatureSettingsRepository
import io.pm.finlight.IFirstLaunchSettingsRepository
import io.pm.finlight.IGoalRepository
import io.pm.finlight.IMerchantCategoryMappingRepository
import io.pm.finlight.IMerchantMappingRepository
import io.pm.finlight.IMerchantRenameRuleRepository
import io.pm.finlight.INotificationSettingsRepository
import io.pm.finlight.IRecurringTransactionRepository
import io.pm.finlight.ISecuritySettingsRepository
import io.pm.finlight.ISettingsRepository
import io.pm.finlight.ISmsRepository
import io.pm.finlight.ISmsRuleSettingsRepository
import io.pm.finlight.ISplitTransactionRepository
import io.pm.finlight.ITagRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.ITravelSettingsRepository
import io.pm.finlight.MerchantCategoryMappingRepository
import io.pm.finlight.MerchantMappingRepository
import io.pm.finlight.MerchantRenameRuleRepository
import io.pm.finlight.NotificationSettingsRepository
import io.pm.finlight.RecurringTransactionRepository
import io.pm.finlight.SecuritySettingsRepository
import io.pm.finlight.SettingsRepository
import io.pm.finlight.SmsRepository
import io.pm.finlight.SmsRuleSettingsRepository
import io.pm.finlight.SplitTransactionRepository
import io.pm.finlight.TagRepository
import io.pm.finlight.TransactionRepository
import io.pm.finlight.TravelSettingsRepository
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verification test ensuring all 24 repositories implement their respective interfaces
 * and can be substituted with interface mocks/fakes.
 */
class RepositoryInterfaceTest {
    @Test
    fun verifyAllRepositoriesImplementInterfaces() {
        val mockAccountRepo = mockk<AccountRepository>(relaxed = true)
        assertTrue(mockAccountRepo is IAccountRepository)

        val mockAppConfigRepo = mockk<AppConfigRepository>(relaxed = true)
        assertTrue(mockAppConfigRepo is IAppConfigRepository)

        val mockBackupRepo = mockk<BackupSettingsRepository>(relaxed = true)
        assertTrue(mockBackupRepo is IBackupSettingsRepository)

        val mockBudgetRepo = mockk<BudgetRepository>(relaxed = true)
        assertTrue(mockBudgetRepo is IBudgetRepository)

        val mockBudgetSettingsRepo = mockk<BudgetSettingsRepository>(relaxed = true)
        assertTrue(mockBudgetSettingsRepo is IBudgetSettingsRepository)

        val mockCategoryRepo = mockk<CategoryRepository>(relaxed = true)
        assertTrue(mockCategoryRepo is ICategoryRepository)

        val mockDashboardSettingsRepo = mockk<DashboardSettingsRepository>(relaxed = true)
        assertTrue(mockDashboardSettingsRepo is IDashboardSettingsRepository)

        val mockFeatureSettingsRepo = mockk<FeatureSettingsRepository>(relaxed = true)
        assertTrue(mockFeatureSettingsRepo is IFeatureSettingsRepository)

        val mockFirstLaunchRepo = mockk<FirstLaunchSettingsRepository>(relaxed = true)
        assertTrue(mockFirstLaunchRepo is IFirstLaunchSettingsRepository)

        val mockGoalRepo = mockk<GoalRepository>(relaxed = true)
        assertTrue(mockGoalRepo is IGoalRepository)

        val mockMerchantCategoryRepo = mockk<MerchantCategoryMappingRepository>(relaxed = true)
        assertTrue(mockMerchantCategoryRepo is IMerchantCategoryMappingRepository)

        val mockMerchantMappingRepo = mockk<MerchantMappingRepository>(relaxed = true)
        assertTrue(mockMerchantMappingRepo is IMerchantMappingRepository)

        val mockMerchantRenameRepo = mockk<MerchantRenameRuleRepository>(relaxed = true)
        assertTrue(mockMerchantRenameRepo is IMerchantRenameRuleRepository)

        val mockNotificationSettingsRepo = mockk<NotificationSettingsRepository>(relaxed = true)
        assertTrue(mockNotificationSettingsRepo is INotificationSettingsRepository)

        val mockRecurringTxnRepo = mockk<RecurringTransactionRepository>(relaxed = true)
        assertTrue(mockRecurringTxnRepo is IRecurringTransactionRepository)

        val mockSecuritySettingsRepo = mockk<SecuritySettingsRepository>(relaxed = true)
        assertTrue(mockSecuritySettingsRepo is ISecuritySettingsRepository)

        val mockSettingsRepo = mockk<SettingsRepository>(relaxed = true)
        assertTrue(mockSettingsRepo is ISettingsRepository)

        val mockSmsRepo = mockk<SmsRepository>(relaxed = true)
        assertTrue(mockSmsRepo is ISmsRepository)

        val mockSmsRuleSettingsRepo = mockk<SmsRuleSettingsRepository>(relaxed = true)
        assertTrue(mockSmsRuleSettingsRepo is ISmsRuleSettingsRepository)

        val mockSplitTxnRepo = mockk<SplitTransactionRepository>(relaxed = true)
        assertTrue(mockSplitTxnRepo is ISplitTransactionRepository)

        val mockTagRepo = mockk<TagRepository>(relaxed = true)
        assertTrue(mockTagRepo is ITagRepository)

        val mockTxnRepo = mockk<TransactionRepository>(relaxed = true)
        assertTrue(mockTxnRepo is ITransactionRepository)

        val mockTravelSettingsRepo = mockk<TravelSettingsRepository>(relaxed = true)
        assertTrue(mockTravelSettingsRepo is ITravelSettingsRepository)

        val mockTripRepo = mockk<TripRepository>(relaxed = true)
        assertTrue(mockTripRepo is ITripRepository)
    }

    @Test
    fun verifySettingsRepositoryFacadeAcceptsInterfaces() {
        val appConfig: IAppConfigRepository = mockk(relaxed = true)
        val dashboard: IDashboardSettingsRepository = mockk(relaxed = true)
        val security: ISecuritySettingsRepository = mockk(relaxed = true)
        val budget: IBudgetSettingsRepository = mockk(relaxed = true)
        val backup: IBackupSettingsRepository = mockk(relaxed = true)
        val notification: INotificationSettingsRepository = mockk(relaxed = true)
        val smsRule: ISmsRuleSettingsRepository = mockk(relaxed = true)
        val travel: ITravelSettingsRepository = mockk(relaxed = true)
        val firstLaunch: IFirstLaunchSettingsRepository = mockk(relaxed = true)
        val feature: IFeatureSettingsRepository = mockk(relaxed = true)

        val settingsRepository: ISettingsRepository =
            SettingsRepository(
                appConfigRepository = appConfig,
                dashboardSettingsRepository = dashboard,
                securitySettingsRepository = security,
                budgetSettingsRepository = budget,
                backupSettingsRepository = backup,
                notificationSettingsRepository = notification,
                smsRuleSettingsRepository = smsRule,
                travelSettingsRepository = travel,
                firstLaunchSettingsRepository = firstLaunch,
                featureSettingsRepository = feature,
            )

        assertTrue(settingsRepository is ISettingsRepository)
    }
}
