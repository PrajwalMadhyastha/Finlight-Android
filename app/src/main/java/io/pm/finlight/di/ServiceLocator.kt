package io.pm.finlight.di

import android.app.Application
import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.AccountRepository
import io.pm.finlight.AppConfigRepository
import io.pm.finlight.BackupSettingsRepository
import io.pm.finlight.BudgetRepository
import io.pm.finlight.BudgetSettingsRepository
import io.pm.finlight.CategoryRepository
import io.pm.finlight.DashboardSettingsRepository
import io.pm.finlight.DashboardViewModel
import io.pm.finlight.FeatureSettingsRepository
import io.pm.finlight.FirstLaunchSettingsRepository
import io.pm.finlight.GoalRepository
import io.pm.finlight.IAccountRepository
import io.pm.finlight.IAppConfigRepository
import io.pm.finlight.IBackupSettingsRepository
import io.pm.finlight.IBudgetRepository
import io.pm.finlight.IBudgetSettingsRepository
import io.pm.finlight.CustomSmsRuleRepository
import io.pm.finlight.ICategoryRepository
import io.pm.finlight.ICustomSmsRuleRepository
import io.pm.finlight.IDashboardSettingsRepository
import io.pm.finlight.IFeatureSettingsRepository
import io.pm.finlight.IFirstLaunchSettingsRepository
import io.pm.finlight.IGoalRepository
import io.pm.finlight.IIgnoreRuleRepository
import io.pm.finlight.IMerchantCategoryMappingRepository
import io.pm.finlight.IMerchantMappingRepository
import io.pm.finlight.IMerchantRenameRuleRepository
import io.pm.finlight.INotificationSettingsRepository
import io.pm.finlight.IRecurringTransactionRepository
import io.pm.finlight.ISecuritySettingsRepository
import io.pm.finlight.ISettingsRepository
import io.pm.finlight.ISmsRepository
import io.pm.finlight.ISmsParseTemplateRepository
import io.pm.finlight.ISmsRuleSettingsRepository
import io.pm.finlight.ISplitTransactionRepository
import io.pm.finlight.ITagRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.ITravelSettingsRepository
import io.pm.finlight.IgnoreRuleRepository
import io.pm.finlight.MerchantCategoryMappingRepository
import io.pm.finlight.MerchantMappingRepository
import io.pm.finlight.MerchantRenameRuleRepository
import io.pm.finlight.NotificationSettingsRepository
import io.pm.finlight.RecurringTransactionRepository
import io.pm.finlight.RecurringTransactionViewModel
import io.pm.finlight.SecuritySettingsRepository
import io.pm.finlight.SettingsRepository
import io.pm.finlight.SmsDebugViewModel
import io.pm.finlight.SmsParseTemplateRepository
import io.pm.finlight.SmsRepository
import io.pm.finlight.SmsRuleSettingsRepository
import io.pm.finlight.SplitTransactionRepository
import io.pm.finlight.TagRepository
import io.pm.finlight.TransactionRepository
import io.pm.finlight.TransactionViewModel
import io.pm.finlight.TravelSettingsRepository
import io.pm.finlight.data.RoomTransactionRunner
import io.pm.finlight.data.TransactionRunner
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.health.DatabaseHealthChecker
import io.pm.finlight.data.health.IDatabaseHealthChecker
import io.pm.finlight.data.repository.ITripRepository
import io.pm.finlight.data.repository.TripRepository
import io.pm.finlight.domain.usecase.AutoSaveSmsTransactionUseCase
import io.pm.finlight.domain.usecase.DefaultScheduleRecurringTransactionWorkerUseCase
import io.pm.finlight.domain.usecase.GetMonthlyConsistencyDataUseCase
import io.pm.finlight.domain.usecase.ManageReimbursementUseCase
import io.pm.finlight.domain.usecase.MergeAccountsUseCase
import io.pm.finlight.domain.usecase.MergeTransactionsUseCase
import io.pm.finlight.domain.usecase.ResolveTravelModeTagUseCase
import io.pm.finlight.domain.usecase.ScheduleRecurringTransactionWorkerUseCase
import io.pm.finlight.ml.MlModelFactory
import io.pm.finlight.ml.SmsClassifier
import io.pm.finlight.ml.SmsEntityExtractor
import io.pm.finlight.ui.viewmodel.AccountViewModel
import io.pm.finlight.ui.viewmodel.AnalysisViewModel
import io.pm.finlight.ui.viewmodel.AnnualSimulatorViewModel
import io.pm.finlight.ui.viewmodel.BudgetViewModel
import io.pm.finlight.ui.viewmodel.CategoryViewModel
import io.pm.finlight.ui.viewmodel.CurrencyViewModel
import io.pm.finlight.ui.viewmodel.GoalViewModel
import io.pm.finlight.ui.viewmodel.HistoricTripsViewModel
import io.pm.finlight.ui.viewmodel.IncomeViewModel
import io.pm.finlight.ui.viewmodel.ManageIgnoreRulesViewModel
import io.pm.finlight.ui.viewmodel.ManageMerchantRulesViewModel
import io.pm.finlight.ui.viewmodel.ManageParseRulesViewModel
import io.pm.finlight.ui.viewmodel.OnboardingViewModel
import io.pm.finlight.ui.viewmodel.PendingTransactionsViewModel
import io.pm.finlight.ui.viewmodel.ProfileViewModel
import io.pm.finlight.ui.viewmodel.ReportsViewModel
import io.pm.finlight.ui.viewmodel.RuleCreationViewModel
import io.pm.finlight.ui.viewmodel.SettingsViewModel
import io.pm.finlight.ui.viewmodel.TagViewModel
import io.pm.finlight.ui.viewmodel.WhatIfViewModel
import io.pm.finlight.utils.DefaultDispatcherProvider
import io.pm.finlight.utils.DispatcherProvider
import io.pm.finlight.utils.SmsProviderHelper
import io.pm.finlight.utils.SystemTimeProvider
import io.pm.finlight.utils.TimeProvider

/**
 * ServiceLocator provides centralized, decoupled dependency resolution for repositories
 * across the application (ViewModel factories, background workers, receivers).
 *
 * Supports dependency overriding for testing.
 */
object ServiceLocator {
    @Volatile
    private var dispatcherProvider: DispatcherProvider? = null

    @Volatile
    private var settingsRepository: ISettingsRepository? = null

    @Volatile
    private var appConfigRepository: IAppConfigRepository? = null

    @Volatile
    private var dashboardSettingsRepository: IDashboardSettingsRepository? = null

    @Volatile
    private var securitySettingsRepository: ISecuritySettingsRepository? = null

    @Volatile
    private var budgetSettingsRepository: IBudgetSettingsRepository? = null

    @Volatile
    private var backupSettingsRepository: IBackupSettingsRepository? = null

    @Volatile
    private var notificationSettingsRepository: INotificationSettingsRepository? = null

    @Volatile
    private var smsRuleSettingsRepository: ISmsRuleSettingsRepository? = null

    @Volatile
    private var travelSettingsRepository: ITravelSettingsRepository? = null

    @Volatile
    private var firstLaunchSettingsRepository: IFirstLaunchSettingsRepository? = null

    @Volatile
    private var featureSettingsRepository: IFeatureSettingsRepository? = null

    @Volatile
    private var transactionRepository: ITransactionRepository? = null

    @Volatile
    private var accountRepository: IAccountRepository? = null

    @Volatile
    private var categoryRepository: ICategoryRepository? = null

    @Volatile
    private var tagRepository: ITagRepository? = null

    @Volatile
    private var smsRepository: ISmsRepository? = null

    @Volatile
    private var mergeAccountsUseCase: MergeAccountsUseCase? = null

    @Volatile
    private var manageReimbursementUseCase: ManageReimbursementUseCase? = null

    @Volatile
    private var mergeTransactionsUseCase: MergeTransactionsUseCase? = null

    @Volatile
    private var autoSaveSmsTransactionUseCase: AutoSaveSmsTransactionUseCase? = null

    @Volatile
    private var getMonthlyConsistencyDataUseCase: GetMonthlyConsistencyDataUseCase? = null

    @Volatile
    private var scheduleRecurringTransactionWorkerUseCase: ScheduleRecurringTransactionWorkerUseCase? = null

    @Volatile
    private var merchantMappingRepository: IMerchantMappingRepository? = null

    @Volatile
    private var budgetRepository: IBudgetRepository? = null

    @Volatile
    private var recurringTransactionRepository: IRecurringTransactionRepository? = null

    @Volatile
    private var splitTransactionRepository: ISplitTransactionRepository? = null

    @Volatile
    private var merchantRenameRuleRepository: IMerchantRenameRuleRepository? = null

    @Volatile
    private var merchantCategoryMappingRepository: IMerchantCategoryMappingRepository? = null

    @Volatile
    private var customSmsRuleRepository: ICustomSmsRuleRepository? = null

    @Volatile
    private var ignoreRuleRepository: IIgnoreRuleRepository? = null

    @Volatile
    private var smsParseTemplateRepository: ISmsParseTemplateRepository? = null

    @Volatile
    private var transactionRunner: TransactionRunner? = null

    @Volatile
    private var transactionViewModel: TransactionViewModel? = null

    @Volatile
    private var dashboardViewModel: DashboardViewModel? = null

    @Volatile
    private var ruleCreationViewModel: RuleCreationViewModel? = null

    @Volatile
    private var tripRepository: ITripRepository? = null

    @Volatile
    private var goalRepository: IGoalRepository? = null

    @Volatile
    private var accountViewModel: AccountViewModel? = null

    @Volatile
    private var budgetViewModel: BudgetViewModel? = null

    @Volatile
    private var categoryViewModel: CategoryViewModel? = null

    @Volatile
    private var currencyViewModel: CurrencyViewModel? = null

    @Volatile
    private var goalViewModel: GoalViewModel? = null

    @Volatile
    private var historicTripsViewModel: HistoricTripsViewModel? = null

    @Volatile
    private var incomeViewModel: IncomeViewModel? = null

    @Volatile
    private var onboardingViewModel: OnboardingViewModel? = null

    @Volatile
    private var profileViewModel: ProfileViewModel? = null

    @Volatile
    private var tagViewModel: TagViewModel? = null

    @Volatile
    private var reportsViewModel: ReportsViewModel? = null

    @Volatile
    private var analysisViewModel: AnalysisViewModel? = null

    @Volatile
    private var annualSimulatorViewModel: AnnualSimulatorViewModel? = null

    @Volatile
    private var whatIfViewModel: WhatIfViewModel? = null

    @Volatile
    private var manageMerchantRulesViewModel: ManageMerchantRulesViewModel? = null

    @Volatile
    private var settingsViewModel: SettingsViewModel? = null

    @Volatile
    private var recurringTransactionViewModel: RecurringTransactionViewModel? = null

    @Volatile
    private var smsDebugViewModel: SmsDebugViewModel? = null

    @Volatile
    private var manageIgnoreRulesViewModel: ManageIgnoreRulesViewModel? = null

    @Volatile
    private var manageParseRulesViewModel: ManageParseRulesViewModel? = null

    @Volatile
    private var pendingTransactionsViewModel: PendingTransactionsViewModel? = null

    @Volatile
    private var smsClassifier: SmsClassifier? = null

    @Volatile
    private var nerExtractorProvider: (() -> SmsEntityExtractor)? = null

    fun provideSmsClassifier(context: Context): SmsClassifier {
        return smsClassifier ?: synchronized(this) {
            smsClassifier ?: MlModelFactory.getClassifier(context.applicationContext).also {
                smsClassifier = it
            }
        }
    }

    fun provideDispatcherProvider(context: Context? = null): DispatcherProvider {
        return dispatcherProvider ?: synchronized(this) {
            dispatcherProvider ?: DefaultDispatcherProvider().also {
                dispatcherProvider = it
            }
        }
    }

    fun provideDatabaseHealthChecker(): IDatabaseHealthChecker {
        return DatabaseHealthChecker
    }

    fun provideAppConfigRepository(context: Context): IAppConfigRepository {
        return appConfigRepository ?: synchronized(this) {
            appConfigRepository ?: AppConfigRepository(context.applicationContext).also {
                appConfigRepository = it
            }
        }
    }

    fun provideDashboardSettingsRepository(context: Context): IDashboardSettingsRepository {
        return dashboardSettingsRepository ?: synchronized(this) {
            dashboardSettingsRepository ?: DashboardSettingsRepository(context.applicationContext).also {
                dashboardSettingsRepository = it
            }
        }
    }

    fun provideSecuritySettingsRepository(context: Context): ISecuritySettingsRepository {
        return securitySettingsRepository ?: synchronized(this) {
            securitySettingsRepository ?: SecuritySettingsRepository(context.applicationContext).also {
                securitySettingsRepository = it
            }
        }
    }

    fun provideBudgetSettingsRepository(context: Context): IBudgetSettingsRepository {
        return budgetSettingsRepository ?: synchronized(this) {
            budgetSettingsRepository ?: BudgetSettingsRepository(context.applicationContext).also {
                budgetSettingsRepository = it
            }
        }
    }

    fun provideBackupSettingsRepository(context: Context): IBackupSettingsRepository {
        return backupSettingsRepository ?: synchronized(this) {
            backupSettingsRepository ?: BackupSettingsRepository(context.applicationContext).also {
                backupSettingsRepository = it
            }
        }
    }

    fun provideNotificationSettingsRepository(context: Context): INotificationSettingsRepository {
        return notificationSettingsRepository ?: synchronized(this) {
            notificationSettingsRepository ?: NotificationSettingsRepository(context.applicationContext).also {
                notificationSettingsRepository = it
            }
        }
    }

    fun provideSmsRuleSettingsRepository(context: Context): ISmsRuleSettingsRepository {
        return smsRuleSettingsRepository ?: synchronized(this) {
            smsRuleSettingsRepository ?: SmsRuleSettingsRepository(context.applicationContext).also {
                smsRuleSettingsRepository = it
            }
        }
    }

    fun provideTravelSettingsRepository(context: Context): ITravelSettingsRepository {
        return travelSettingsRepository ?: synchronized(this) {
            travelSettingsRepository ?: TravelSettingsRepository(context.applicationContext).also {
                travelSettingsRepository = it
            }
        }
    }

    fun provideFirstLaunchSettingsRepository(context: Context): IFirstLaunchSettingsRepository {
        return firstLaunchSettingsRepository ?: synchronized(this) {
            firstLaunchSettingsRepository ?: FirstLaunchSettingsRepository(context.applicationContext).also {
                firstLaunchSettingsRepository = it
            }
        }
    }

    fun provideFeatureSettingsRepository(context: Context): IFeatureSettingsRepository {
        return featureSettingsRepository ?: synchronized(this) {
            featureSettingsRepository ?: FeatureSettingsRepository(context.applicationContext).also {
                featureSettingsRepository = it
            }
        }
    }

    fun provideSettingsRepository(context: Context): ISettingsRepository {
        return settingsRepository ?: synchronized(this) {
            settingsRepository ?: SettingsRepository(
                appConfigRepository = provideAppConfigRepository(context),
                dashboardSettingsRepository = provideDashboardSettingsRepository(context),
                securitySettingsRepository = provideSecuritySettingsRepository(context),
                budgetSettingsRepository = provideBudgetSettingsRepository(context),
                backupSettingsRepository = provideBackupSettingsRepository(context),
                notificationSettingsRepository = provideNotificationSettingsRepository(context),
                smsRuleSettingsRepository = provideSmsRuleSettingsRepository(context),
                travelSettingsRepository = provideTravelSettingsRepository(context),
                firstLaunchSettingsRepository = provideFirstLaunchSettingsRepository(context),
                featureSettingsRepository = provideFeatureSettingsRepository(context),
            ).also {
                settingsRepository = it
            }
        }
    }

    fun provideTransactionRepository(context: Context): ITransactionRepository {
        return transactionRepository ?: synchronized(this) {
            transactionRepository ?: run {
                // Obtain db once and pass it into the use-case provider to avoid a
                // redundant AppDatabase.getInstance() call inside that chain (R4).
                val db = AppDatabase.getInstance(context.applicationContext)
                val dispatcherProvider = provideDispatcherProvider(context)
                val manageReimbursement = provideManageReimbursementUseCase(context, db)
                TransactionRepository(
                    transactionWriteDao = db.transactionWriteDao(),
                    transactionQueryDao = db.transactionQueryDao(),
                    transactionAnalyticsDao = db.transactionAnalyticsDao(),
                    transactionReimbursementDao = db.transactionReimbursementDao(),
                    db = db,
                    dispatcherProvider = dispatcherProvider,
                    manageReimbursementUseCase = manageReimbursement,
                ).also {
                    transactionRepository = it
                }
            }
        }
    }

    /**
     * @param resolvedDb Optional pre-resolved [AppDatabase] instance. Pass this from a caller
     *   that already holds a [AppDatabase] reference to avoid a redundant [AppDatabase.getInstance]
     *   call. Defaults to null, in which case the instance is resolved internally.
     */
    fun provideManageReimbursementUseCase(
        context: Context,
        resolvedDb: AppDatabase? = null,
    ): ManageReimbursementUseCase {
        return manageReimbursementUseCase ?: synchronized(this) {
            manageReimbursementUseCase ?: run {
                val db = resolvedDb ?: AppDatabase.getInstance(context.applicationContext)
                val dispatcherProvider = provideDispatcherProvider(context)
                ManageReimbursementUseCase(
                    transactionQueryDao = db.transactionQueryDao(),
                    transactionWriteDao = db.transactionWriteDao(),
                    transactionReimbursementDao = db.transactionReimbursementDao(),
                    db = db,
                    dispatcherProvider = dispatcherProvider,
                ).also {
                    manageReimbursementUseCase = it
                }
            }
        }
    }

    /**
     * @param resolvedDb Optional pre-resolved [AppDatabase] instance. Pass this from a caller
     *   that already holds a [AppDatabase] reference to avoid a redundant [AppDatabase.getInstance]
     *   call. Defaults to null, in which case the instance is resolved internally.
     */
    fun provideMergeAccountsUseCase(
        context: Context,
        resolvedDb: AppDatabase? = null,
    ): MergeAccountsUseCase {
        return mergeAccountsUseCase ?: synchronized(this) {
            mergeAccountsUseCase ?: run {
                val db = resolvedDb ?: AppDatabase.getInstance(context.applicationContext)
                MergeAccountsUseCase(
                    accountDao = db.accountDao(),
                    accountAliasDao = db.accountAliasDao(),
                    recurringTransactionDao = db.recurringTransactionDao(),
                    goalDao = db.goalDao(),
                    transactionWriteDao = db.transactionWriteDao(),
                    mergeRecordDao = db.mergeRecordDao(),
                    recurringPatternDao = db.recurringPatternDao(),
                    db = db,
                ).also {
                    mergeAccountsUseCase = it
                }
            }
        }
    }

    fun provideAccountRepository(context: Context): IAccountRepository {
        return accountRepository ?: synchronized(this) {
            accountRepository ?: run {
                // Obtain db once and pass it into the use-case provider to avoid a
                // redundant AppDatabase.getInstance() call inside that chain (R4).
                val db = AppDatabase.getInstance(context.applicationContext)
                val mergeAccounts = provideMergeAccountsUseCase(context, db)
                AccountRepository(
                    accountDao = db.accountDao(),
                    accountAliasDao = db.accountAliasDao(),
                    db = db,
                    mergeAccountsUseCase = mergeAccounts,
                ).also {
                    accountRepository = it
                }
            }
        }
    }

    fun provideCategoryRepository(context: Context): ICategoryRepository {
        return categoryRepository ?: synchronized(this) {
            categoryRepository ?: run {
                val db = AppDatabase.getInstance(context.applicationContext)
                CategoryRepository(db.categoryDao()).also {
                    categoryRepository = it
                }
            }
        }
    }

    fun provideTagRepository(context: Context): ITagRepository {
        return tagRepository ?: synchronized(this) {
            tagRepository ?: run {
                val db = AppDatabase.getInstance(context.applicationContext)
                TagRepository(db.tagDao(), db.transactionQueryDao()).also {
                    tagRepository = it
                }
            }
        }
    }

    fun provideSmsRepository(context: Context): ISmsRepository {
        return smsRepository ?: synchronized(this) {
            smsRepository ?: run {
                val dispatcherProvider = provideDispatcherProvider(context)
                SmsRepository(context.applicationContext, dispatcherProvider).also {
                    smsRepository = it
                }
            }
        }
    }

    /**
     * @param resolvedDb Optional pre-resolved [AppDatabase] instance. Pass this from a caller
     *   that already holds a [AppDatabase] reference to avoid a redundant [AppDatabase.getInstance]
     *   call. Defaults to null, in which case the instance is resolved internally.
     */
    fun provideMergeTransactionsUseCase(
        context: Context,
        resolvedDb: AppDatabase? = null,
    ): MergeTransactionsUseCase {
        return mergeTransactionsUseCase ?: synchronized(this) {
            mergeTransactionsUseCase ?: run {
                val db = resolvedDb ?: AppDatabase.getInstance(context.applicationContext)
                MergeTransactionsUseCase(
                    transactionQueryDao = db.transactionQueryDao(),
                    transactionWriteDao = db.transactionWriteDao(),
                    transactionReimbursementDao = db.transactionReimbursementDao(),
                    mergeRecordDao = db.mergeRecordDao(),
                    deletedSmsHashDao = db.deletedSmsHashDao(),
                    db = db,
                ).also {
                    mergeTransactionsUseCase = it
                }
            }
        }
    }

    /**
     * Resolves the singleton [AutoSaveSmsTransactionUseCase] instance.
     *
     * Note: Once initialized, the cached singleton instance is returned on subsequent calls
     * and [resolvedDb] is not re-evaluated.
     *
     * @param resolvedDb Optional pre-resolved [AppDatabase] instance. Pass this from a caller
     *   that already holds a [AppDatabase] reference to avoid a redundant [AppDatabase.getInstance]
     *   call. Defaults to null, in which case the instance is resolved internally.
     */
    fun provideAutoSaveSmsTransactionUseCase(
        context: Context,
        resolvedDb: AppDatabase? = null,
    ): AutoSaveSmsTransactionUseCase {
        return autoSaveSmsTransactionUseCase ?: synchronized(this) {
            autoSaveSmsTransactionUseCase ?: run {
                val tagRepository = provideTagRepository(context)
                AutoSaveSmsTransactionUseCase(
                    transactionRepository = provideTransactionRepository(context),
                    accountRepository = provideAccountRepository(context),
                    merchantMappingRepository = provideMerchantMappingRepository(context, resolvedDb),
                    travelSettingsRepository = provideTravelSettingsRepository(context),
                    resolveTravelModeTagUseCase = ResolveTravelModeTagUseCase(tagRepository),
                    dispatcherProvider = provideDispatcherProvider(context),
                ).also {
                    autoSaveSmsTransactionUseCase = it
                }
            }
        }
    }

    /**
     * Resolves the singleton [GetMonthlyConsistencyDataUseCase] instance.
     */
    fun provideGetMonthlyConsistencyDataUseCase(context: Context): GetMonthlyConsistencyDataUseCase {
        return getMonthlyConsistencyDataUseCase ?: synchronized(this) {
            getMonthlyConsistencyDataUseCase ?: run {
                GetMonthlyConsistencyDataUseCase(
                    settingsRepository = provideSettingsRepository(context),
                    transactionRepository = provideTransactionRepository(context),
                    dispatcherProvider = provideDispatcherProvider(context),
                ).also {
                    getMonthlyConsistencyDataUseCase = it
                }
            }
        }
    }

    /**
     * Resolves the singleton [ScheduleRecurringTransactionWorkerUseCase] instance.
     */
    fun provideScheduleRecurringTransactionWorkerUseCase(
        context: Context,
    ): ScheduleRecurringTransactionWorkerUseCase {
        return scheduleRecurringTransactionWorkerUseCase ?: synchronized(this) {
            scheduleRecurringTransactionWorkerUseCase ?: DefaultScheduleRecurringTransactionWorkerUseCase(
                context.applicationContext,
            ).also {
                scheduleRecurringTransactionWorkerUseCase = it
            }
        }
    }

    /**
     * Resolves the singleton [IMerchantMappingRepository] instance.
     *
     * Note: Once initialized, the cached singleton instance is returned on subsequent calls
     * and [resolvedDb] is not re-evaluated.
     *
     * @param resolvedDb Optional pre-resolved [AppDatabase] instance. Pass this from a caller
     *   that already holds a [AppDatabase] reference to avoid a redundant [AppDatabase.getInstance]
     *   call. Defaults to null, in which case the instance is resolved internally.
     */
    fun provideMerchantMappingRepository(
        context: Context,
        resolvedDb: AppDatabase? = null,
    ): IMerchantMappingRepository {
        return merchantMappingRepository ?: synchronized(this) {
            merchantMappingRepository ?: run {
                val db = resolvedDb ?: AppDatabase.getInstance(context.applicationContext)
                MerchantMappingRepository(
                    merchantMappingDao = db.merchantMappingDao(),
                ).also {
                    merchantMappingRepository = it
                }
            }
        }
    }

    /**
     * Resolves the singleton [IBudgetRepository] instance.
     *
     * Note: Once initialized, the cached singleton instance is returned on subsequent calls
     * and [resolvedDb] is not re-evaluated.
     *
     * @param resolvedDb Optional pre-resolved [AppDatabase] instance. Pass this from a caller
     *   that already holds a [AppDatabase] reference to avoid a redundant [AppDatabase.getInstance]
     *   call. Defaults to null, in which case the instance is resolved internally.
     */
    fun provideBudgetRepository(
        context: Context,
        resolvedDb: AppDatabase? = null,
    ): IBudgetRepository {
        return budgetRepository ?: synchronized(this) {
            budgetRepository ?: run {
                val db = resolvedDb ?: AppDatabase.getInstance(context.applicationContext)
                BudgetRepository(
                    budgetDao = db.budgetDao(),
                ).also {
                    budgetRepository = it
                }
            }
        }
    }

    /**
     * Resolves the singleton [IRecurringTransactionRepository] instance.
     *
     * Note: Once initialized, the cached singleton instance is returned on subsequent calls
     * and [resolvedDb] is not re-evaluated.
     *
     * @param resolvedDb Optional pre-resolved [AppDatabase] instance. Pass this from a caller
     *   that already holds a [AppDatabase] reference to avoid a redundant [AppDatabase.getInstance]
     *   call. Defaults to null, in which case the instance is resolved internally.
     */
    fun provideRecurringTransactionRepository(
        context: Context,
        resolvedDb: AppDatabase? = null,
    ): IRecurringTransactionRepository {
        return recurringTransactionRepository ?: synchronized(this) {
            recurringTransactionRepository ?: run {
                val db = resolvedDb ?: AppDatabase.getInstance(context.applicationContext)
                RecurringTransactionRepository(
                    recurringTransactionDao = db.recurringTransactionDao(),
                    recurringPatternDao = db.recurringPatternDao(),
                ).also {
                    recurringTransactionRepository = it
                }
            }
        }
    }

    /**
     * Resolves the singleton [ISplitTransactionRepository] instance.
     *
     * Note: Once initialized, the cached singleton instance is returned on subsequent calls
     * and [resolvedDb] is not re-evaluated.
     *
     * @param resolvedDb Optional pre-resolved [AppDatabase] instance. Pass this from a caller
     *   that already holds a [AppDatabase] reference to avoid a redundant [AppDatabase.getInstance]
     *   call. Defaults to null, in which case the instance is resolved internally.
     */
    fun provideSplitTransactionRepository(
        context: Context,
        resolvedDb: AppDatabase? = null,
    ): ISplitTransactionRepository {
        return splitTransactionRepository ?: synchronized(this) {
            splitTransactionRepository ?: run {
                val db = resolvedDb ?: AppDatabase.getInstance(context.applicationContext)
                SplitTransactionRepository(
                    splitTransactionDao = db.splitTransactionDao(),
                    transactionWriteDao = db.transactionWriteDao(),
                    transactionRunner = provideTransactionRunner(context, db),
                ).also {
                    splitTransactionRepository = it
                }
            }
        }
    }

    /**
     * Resolves the singleton [IMerchantRenameRuleRepository] instance.
     *
     * Note: Once initialized, the cached singleton instance is returned on subsequent calls
     * and [resolvedDb] is not re-evaluated.
     *
     * @param resolvedDb Optional pre-resolved [AppDatabase] instance. Pass this from a caller
     *   that already holds a [AppDatabase] reference to avoid a redundant [AppDatabase.getInstance]
     *   call. Defaults to null, in which case the instance is resolved internally.
     */
    fun provideMerchantRenameRuleRepository(
        context: Context,
        resolvedDb: AppDatabase? = null,
    ): IMerchantRenameRuleRepository {
        return merchantRenameRuleRepository ?: synchronized(this) {
            merchantRenameRuleRepository ?: run {
                val db = resolvedDb ?: AppDatabase.getInstance(context.applicationContext)
                MerchantRenameRuleRepository(
                    dao = db.merchantRenameRuleDao(),
                ).also {
                    merchantRenameRuleRepository = it
                }
            }
        }
    }

    /**
     * Resolves the singleton [IMerchantCategoryMappingRepository] instance.
     *
     * Note: Once initialized, the cached singleton instance is returned on subsequent calls
     * and [resolvedDb] is not re-evaluated.
     *
     * @param resolvedDb Optional pre-resolved [AppDatabase] instance. Pass this from a caller
     *   that already holds a [AppDatabase] reference to avoid a redundant [AppDatabase.getInstance]
     *   call. Defaults to null, in which case the instance is resolved internally.
     */
    fun provideMerchantCategoryMappingRepository(
        context: Context,
        resolvedDb: AppDatabase? = null,
    ): IMerchantCategoryMappingRepository {
        return merchantCategoryMappingRepository ?: synchronized(this) {
            merchantCategoryMappingRepository ?: run {
                val db = resolvedDb ?: AppDatabase.getInstance(context.applicationContext)
                MerchantCategoryMappingRepository(
                    dao = db.merchantCategoryMappingDao(),
                ).also {
                    merchantCategoryMappingRepository = it
                }
            }
        }
    }

    /**
     * Resolves the singleton [ICustomSmsRuleRepository] instance.
     *
     * Note: Once initialized, the cached singleton instance is returned on subsequent calls
     * and [resolvedDb] is not re-evaluated.
     *
     * @param resolvedDb Optional pre-resolved [AppDatabase] instance. Pass this from a caller
     *   that already holds a [AppDatabase] reference to avoid a redundant [AppDatabase.getInstance]
     *   call. Defaults to null, in which case the instance is resolved internally.
     */
    fun provideCustomSmsRuleRepository(
        context: Context,
        resolvedDb: AppDatabase? = null,
    ): ICustomSmsRuleRepository {
        return customSmsRuleRepository ?: synchronized(this) {
            customSmsRuleRepository ?: run {
                val db = resolvedDb ?: AppDatabase.getInstance(context.applicationContext)
                CustomSmsRuleRepository(
                    dao = db.customSmsRuleDao(),
                ).also {
                    customSmsRuleRepository = it
                }
            }
        }
    }

    /**
     * Resolves the singleton [IIgnoreRuleRepository] instance.
     *
     * Note: Once initialized, the cached singleton instance is returned on subsequent calls
     * and [resolvedDb] is not re-evaluated.
     *
     * @param resolvedDb Optional pre-resolved [AppDatabase] instance. Pass this from a caller
     *   that already holds a [AppDatabase] reference to avoid a redundant [AppDatabase.getInstance]
     *   call. Defaults to null, in which case the instance is resolved internally.
     */
    fun provideIgnoreRuleRepository(
        context: Context,
        resolvedDb: AppDatabase? = null,
    ): IIgnoreRuleRepository {
        return ignoreRuleRepository ?: synchronized(this) {
            ignoreRuleRepository ?: run {
                val db = resolvedDb ?: AppDatabase.getInstance(context.applicationContext)
                IgnoreRuleRepository(
                    dao = db.ignoreRuleDao(),
                ).also {
                    ignoreRuleRepository = it
                }
            }
        }
    }

    /**
     * Resolves the singleton [ISmsParseTemplateRepository] instance.
     *
     * @param resolvedDb Optional pre-resolved [AppDatabase] instance. Pass this from a caller
     *   that already holds a [AppDatabase] reference to avoid a redundant [AppDatabase.getInstance]
     *   call. Defaults to null, in which case the instance is resolved internally.
     */
    fun provideSmsParseTemplateRepository(
        context: Context,
        resolvedDb: AppDatabase? = null,
    ): ISmsParseTemplateRepository {
        return smsParseTemplateRepository ?: synchronized(this) {
            smsParseTemplateRepository ?: run {
                val db = resolvedDb ?: AppDatabase.getInstance(context.applicationContext)
                SmsParseTemplateRepository(
                    smsParseTemplateDao = db.smsParseTemplateDao(),
                ).also {
                    smsParseTemplateRepository = it
                }
            }
        }
    }

    /**
     * Resolves the singleton [ITripRepository] instance.
     *
     * @param context Application or activity context.
     * @param resolvedDb Optional pre-resolved [AppDatabase] instance.
     */
    fun provideTripRepository(
        context: Context,
        resolvedDb: AppDatabase? = null,
    ): ITripRepository {
        return tripRepository ?: synchronized(this) {
            tripRepository ?: run {
                val db = resolvedDb ?: AppDatabase.getInstance(context.applicationContext)
                TripRepository(db.tripDao()).also {
                    tripRepository = it
                }
            }
        }
    }

    /**
     * Resolves the singleton [IGoalRepository] instance.
     *
     * @param context Application or activity context.
     * @param resolvedDb Optional pre-resolved [AppDatabase] instance.
     */
    fun provideGoalRepository(
        context: Context,
        resolvedDb: AppDatabase? = null,
    ): IGoalRepository {
        return goalRepository ?: synchronized(this) {
            goalRepository ?: run {
                val db = resolvedDb ?: AppDatabase.getInstance(context.applicationContext)
                GoalRepository(
                    goalDao = db.goalDao(),
                    linkDao = db.goalTransactionLinkDao(),
                    transactionQueryDao = db.transactionQueryDao(),
                    contributionDao = db.goalContributionDao(),
                ).also {
                    goalRepository = it
                }
            }
        }
    }

    /**
     * Resolves the singleton [TransactionRunner] instance.
     *
     * Note: Once initialized, the cached singleton instance is returned on subsequent calls
     * and [resolvedDb] is not re-evaluated.
     *
     * @param resolvedDb Optional pre-resolved [AppDatabase] instance. Pass this from a caller
     *   that already holds a [AppDatabase] reference to avoid a redundant [AppDatabase.getInstance]
     *   call. Defaults to null, in which case the instance is resolved internally.
     */
    fun provideTransactionRunner(
        context: Context,
        resolvedDb: AppDatabase? = null,
    ): TransactionRunner {
        return transactionRunner ?: synchronized(this) {
            transactionRunner ?: run {
                val db = resolvedDb ?: AppDatabase.getInstance(context.applicationContext)
                RoomTransactionRunner(db).also {
                    transactionRunner = it
                }
            }
        }
    }

    /**
     * Resolves a [TransactionViewModel] instance.
     *
     * @param context Application or activity context.
     * @param resolvedDb Optional pre-resolved [AppDatabase] instance.
     * @param savedStateHandle Optional [SavedStateHandle] for state restoration.
     */
    fun provideTransactionViewModel(
        context: Context,
        resolvedDb: AppDatabase? = null,
        savedStateHandle: SavedStateHandle? = null,
    ): TransactionViewModel {
        return transactionViewModel ?: run {
            val app =
                (context as? Application) ?: (context.applicationContext as? Application)
                    ?: throw IllegalArgumentException("Context must be an Application or provide an Application context")
            val db = resolvedDb ?: AppDatabase.getInstance(app)
            val tagRepo = provideTagRepository(app)
            val resolveTravelModeTagUseCase = ResolveTravelModeTagUseCase(tagRepo)
            val mergeTransactionsUseCase = provideMergeTransactionsUseCase(app, db)
            val dispatcherProvider = provideDispatcherProvider(app)
            val manageReimbursementUseCase = provideManageReimbursementUseCase(app)
            val autoSaveSmsTransactionUseCase = provideAutoSaveSmsTransactionUseCase(app, db)
            val transactionRepository = provideTransactionRepository(app)
            val accountRepository = provideAccountRepository(app)
            val categoryRepository = provideCategoryRepository(app)
            val settingsRepository = provideSettingsRepository(app)
            val smsRepository = provideSmsRepository(app)
            val merchantRenameRuleRepository = provideMerchantRenameRuleRepository(app, db)
            val merchantCategoryMappingRepository = provideMerchantCategoryMappingRepository(app, db)
            val merchantMappingRepository = provideMerchantMappingRepository(app, db)
            val splitTransactionRepository = provideSplitTransactionRepository(app, db)
            val customSmsRuleRepository = provideCustomSmsRuleRepository(app, db)
            val ignoreRuleRepository = provideIgnoreRuleRepository(app, db)
            val smsParseTemplateRepository = provideSmsParseTemplateRepository(app, db)

            TransactionViewModel(
                application = app,
                transactionRepository = transactionRepository,
                accountRepository = accountRepository,
                categoryRepository = categoryRepository,
                tagRepository = tagRepo,
                settingsRepository = settingsRepository,
                smsRepository = smsRepository,
                merchantRenameRuleRepository = merchantRenameRuleRepository,
                merchantCategoryMappingRepository = merchantCategoryMappingRepository,
                merchantMappingRepository = merchantMappingRepository,
                splitTransactionRepository = splitTransactionRepository,
                resolveTravelModeTagUseCase = resolveTravelModeTagUseCase,
                mergeTransactionsUseCase = mergeTransactionsUseCase,
                dispatcherProvider = dispatcherProvider,
                manageReimbursementUseCase = manageReimbursementUseCase,
                autoSaveSmsTransactionUseCase = autoSaveSmsTransactionUseCase,
                customSmsRuleRepository = customSmsRuleRepository,
                ignoreRuleRepository = ignoreRuleRepository,
                smsParseTemplateRepository = smsParseTemplateRepository,
                savedStateHandle = savedStateHandle ?: SavedStateHandle(),
            )
        }
    }

    /**
     * Resolves a [DashboardViewModel] instance.
     *
     * @param context Application or activity context.
     */
    fun provideDashboardViewModel(context: Context): DashboardViewModel {
        return dashboardViewModel ?: run {
            val app = context.applicationContext
            val settingsRepository = provideSettingsRepository(app)
            val dispatcherProvider = provideDispatcherProvider(app)
            val transactionRepository = provideTransactionRepository(app)
            val getMonthlyConsistencyDataUseCase = provideGetMonthlyConsistencyDataUseCase(app)
            val accountRepository = provideAccountRepository(app)
            val mergeTransactionsUseCase = provideMergeTransactionsUseCase(app)
            val budgetRepository = provideBudgetRepository(app)
            val recurringTransactionRepository = provideRecurringTransactionRepository(app)

            DashboardViewModel(
                transactionRepository = transactionRepository,
                accountRepository = accountRepository,
                budgetRepository = budgetRepository,
                settingsRepository = settingsRepository,
                timeProvider = SystemTimeProvider(),
                recurringTransactionRepository = recurringTransactionRepository,
                getMonthlyConsistencyDataUseCase = getMonthlyConsistencyDataUseCase,
                mergeTransactionsUseCase = mergeTransactionsUseCase,
                dispatcherProvider = dispatcherProvider,
            )
        }
    }

    /**
     * Resolves a [RuleCreationViewModel] instance.
     *
     * @param context Application or activity context.
     * @param resolvedDb Optional pre-resolved [AppDatabase] instance.
     * @param savedStateHandle Optional [SavedStateHandle] for state restoration.
     * @param customSmsRuleRepository Optional [ICustomSmsRuleRepository] override.
     * @param dispatcherProvider Optional [DispatcherProvider] override.
     */
    fun provideRuleCreationViewModel(
        context: Context,
        resolvedDb: AppDatabase? = null,
        savedStateHandle: SavedStateHandle? = null,
        customSmsRuleRepository: ICustomSmsRuleRepository? = null,
        dispatcherProvider: DispatcherProvider? = null,
    ): RuleCreationViewModel {
        return ruleCreationViewModel ?: run {
            val app =
                (context as? Application) ?: (context.applicationContext as? Application)
                    ?: throw IllegalArgumentException("Context must be an Application or provide an Application context")
            val repository = customSmsRuleRepository ?: provideCustomSmsRuleRepository(app, resolvedDb)
            val dispatchers = dispatcherProvider ?: provideDispatcherProvider(app)
            RuleCreationViewModel(
                customSmsRuleRepository = repository,
                dispatcherProvider = dispatchers,
                savedStateHandle = savedStateHandle ?: SavedStateHandle(),
            )
        }
    }

    /**
     * Resolves an [AccountViewModel] instance.
     *
     * @param context Application or activity context.
     */
    fun provideAccountViewModel(context: Context): AccountViewModel {
        return accountViewModel ?: run {
            val app =
                (context as? Application) ?: (context.applicationContext as? Application)
                    ?: throw IllegalArgumentException("Context must be an Application or provide an Application context")
            val accountRepository = provideAccountRepository(app)
            val transactionRepository = provideTransactionRepository(app)
            val settingsRepository = provideSettingsRepository(app)
            val mergeAccountsUseCase = provideMergeAccountsUseCase(app)
            val dispatcherProvider = provideDispatcherProvider(app)

            AccountViewModel(
                repository = accountRepository,
                transactionRepository = transactionRepository,
                settingsRepository = settingsRepository,
                mergeAccountsUseCase = mergeAccountsUseCase,
                dispatcherProvider = dispatcherProvider,
            )
        }
    }

    /**
     * Resolves a [BudgetViewModel] instance.
     *
     * @param context Application or activity context.
     */
    fun provideBudgetViewModel(context: Context): BudgetViewModel {
        return budgetViewModel ?: run {
            val app =
                (context as? Application) ?: (context.applicationContext as? Application)
                    ?: throw IllegalArgumentException("Context must be an Application or provide an Application context")
            val budgetRepository = provideBudgetRepository(app)
            val settingsRepository = provideSettingsRepository(app)
            val categoryRepository = provideCategoryRepository(app)
            val transactionRepository = provideTransactionRepository(app)
            val dispatcherProvider = provideDispatcherProvider(app)

            BudgetViewModel(
                budgetRepository = budgetRepository,
                settingsRepository = settingsRepository,
                categoryRepository = categoryRepository,
                transactionRepository = transactionRepository,
                dispatcherProvider = dispatcherProvider,
            )
        }
    }

    /**
     * Resolves a [CategoryViewModel] instance.
     *
     * @param context Application or activity context.
     */
    fun provideCategoryViewModel(context: Context): CategoryViewModel {
        return categoryViewModel ?: run {
            val app =
                (context as? Application) ?: (context.applicationContext as? Application)
                    ?: throw IllegalArgumentException("Context must be an Application or provide an Application context")
            val categoryRepository = provideCategoryRepository(app)
            val transactionRepository = provideTransactionRepository(app)
            val dispatcherProvider = provideDispatcherProvider(app)

            CategoryViewModel(
                categoryRepository = categoryRepository,
                transactionRepository = transactionRepository,
                dispatcherProvider = dispatcherProvider,
            )
        }
    }

    /**
     * Resolves a [CurrencyViewModel] instance.
     *
     * @param context Application or activity context.
     * @param resolvedDb Optional pre-resolved [AppDatabase] instance.
     */
    fun provideCurrencyViewModel(
        context: Context,
        resolvedDb: AppDatabase? = null,
    ): CurrencyViewModel {
        return currencyViewModel ?: run {
            val app =
                (context as? Application) ?: (context.applicationContext as? Application)
                    ?: throw IllegalArgumentException("Context must be an Application or provide an Application context")
            val settingsRepository = provideSettingsRepository(app)
            val tagRepository = provideTagRepository(app)
            val transactionRepository = provideTransactionRepository(app)
            val tripRepository = provideTripRepository(app, resolvedDb)
            val dispatcherProvider = provideDispatcherProvider(app)

            CurrencyViewModel(
                settingsRepository = settingsRepository,
                tripRepository = tripRepository,
                transactionRepository = transactionRepository,
                tagRepository = tagRepository,
                dispatcherProvider = dispatcherProvider,
            )
        }
    }

    /**
     * Resolves a [GoalViewModel] instance.
     *
     * @param context Application or activity context.
     * @param resolvedDb Optional pre-resolved [AppDatabase] instance.
     */
    fun provideGoalViewModel(
        context: Context,
        resolvedDb: AppDatabase? = null,
    ): GoalViewModel {
        return goalViewModel ?: run {
            val app =
                (context as? Application) ?: (context.applicationContext as? Application)
                    ?: throw IllegalArgumentException("Context must be an Application or provide an Application context")
            val goalRepository = provideGoalRepository(app, resolvedDb)

            GoalViewModel(goalRepository = goalRepository)
        }
    }

    /**
     * Resolves a [HistoricTripsViewModel] instance.
     *
     * @param context Application or activity context.
     * @param resolvedDb Optional pre-resolved [AppDatabase] instance.
     */
    fun provideHistoricTripsViewModel(
        context: Context,
        resolvedDb: AppDatabase? = null,
    ): HistoricTripsViewModel {
        return historicTripsViewModel ?: run {
            val app =
                (context as? Application) ?: (context.applicationContext as? Application)
                    ?: throw IllegalArgumentException("Context must be an Application or provide an Application context")
            val tripRepository = provideTripRepository(app, resolvedDb)
            val transactionRepository = provideTransactionRepository(app)
            val dispatcherProvider = provideDispatcherProvider(app)

            HistoricTripsViewModel(
                tripRepository = tripRepository,
                transactionRepository = transactionRepository,
                dispatcherProvider = dispatcherProvider,
            )
        }
    }

    /**
     * Resolves an [IncomeViewModel] instance.
     *
     * @param context Application or activity context.
     */
    fun provideIncomeViewModel(context: Context): IncomeViewModel {
        return incomeViewModel ?: run {
            val app =
                (context as? Application) ?: (context.applicationContext as? Application)
                    ?: throw IllegalArgumentException("Context must be an Application or provide an Application context")
            val transactionRepository = provideTransactionRepository(app)
            val accountRepository = provideAccountRepository(app)
            val categoryRepository = provideCategoryRepository(app)
            val settingsRepository = provideSettingsRepository(app)
            val dispatcherProvider = provideDispatcherProvider(app)

            IncomeViewModel(
                transactionRepository = transactionRepository,
                accountRepository = accountRepository,
                categoryRepository = categoryRepository,
                settingsRepository = settingsRepository,
                dispatcherProvider = dispatcherProvider,
            )
        }
    }

    /**
     * Resolves an [OnboardingViewModel] instance.
     *
     * @param context Application or activity context.
     */
    fun provideOnboardingViewModel(context: Context): OnboardingViewModel {
        return onboardingViewModel ?: run {
            val app =
                (context as? Application) ?: (context.applicationContext as? Application)
                    ?: throw IllegalArgumentException("Context must be an Application or provide an Application context")
            val categoryRepository = provideCategoryRepository(app)
            val settingsRepository = provideSettingsRepository(app)
            val dispatcherProvider = provideDispatcherProvider(app)

            OnboardingViewModel(
                application = app,
                categoryRepository = categoryRepository,
                settingsRepository = settingsRepository,
                dispatcherProvider = dispatcherProvider,
            )
        }
    }

    /**
     * Resolves a [ProfileViewModel] instance.
     *
     * @param context Application or activity context.
     */
    fun provideProfileViewModel(context: Context): ProfileViewModel {
        return profileViewModel ?: run {
            val app =
                (context as? Application) ?: (context.applicationContext as? Application)
                    ?: throw IllegalArgumentException("Context must be an Application or provide an Application context")
            val settingsRepository = provideSettingsRepository(app)
            val dispatcherProvider = provideDispatcherProvider(app)

            ProfileViewModel(
                application = app,
                settingsRepository = settingsRepository,
                dispatcherProvider = dispatcherProvider,
            )
        }
    }

    /**
     * Resolves a [TagViewModel] instance.
     *
     * @param context Application or activity context.
     * @param resolvedDb Optional pre-resolved [AppDatabase] instance.
     */
    fun provideTagViewModel(
        context: Context,
        resolvedDb: AppDatabase? = null,
    ): TagViewModel {
        return tagViewModel ?: run {
            val app =
                (context as? Application) ?: (context.applicationContext as? Application)
                    ?: throw IllegalArgumentException("Context must be an Application or provide an Application context")
            val tagRepository = provideTagRepository(app)
            val tripRepository = provideTripRepository(app, resolvedDb)
            val dispatcherProvider = provideDispatcherProvider(app)

            TagViewModel(
                tagRepository = tagRepository,
                tripRepository = tripRepository,
                dispatcherProvider = dispatcherProvider,
            )
        }
    }

    /**
     * Resolves a [ReportsViewModel] instance.
     *
     * @param context Application or activity context.
     */
    fun provideReportsViewModel(context: Context): ReportsViewModel {
        return reportsViewModel ?: run {
            val app =
                (context as? Application) ?: (context.applicationContext as? Application)
                    ?: throw IllegalArgumentException("Context must be an Application or provide an Application context")
            val transactionRepository = provideTransactionRepository(app)
            val categoryRepository = provideCategoryRepository(app)
            val getMonthlyConsistencyDataUseCase = provideGetMonthlyConsistencyDataUseCase(app)
            val dispatcherProvider = provideDispatcherProvider(app)

            ReportsViewModel(
                transactionRepository = transactionRepository,
                categoryRepository = categoryRepository,
                getMonthlyConsistencyDataUseCase = getMonthlyConsistencyDataUseCase,
                dispatcherProvider = dispatcherProvider,
            )
        }
    }

    /**
     * Resolves an [AnalysisViewModel] instance.
     *
     * @param context Application or activity context.
     * @param resolvedDb Optional pre-resolved [AppDatabase] instance.
     */
    fun provideAnalysisViewModel(
        context: Context,
        resolvedDb: AppDatabase? = null,
    ): AnalysisViewModel {
        return analysisViewModel ?: run {
            val app =
                (context as? Application) ?: (context.applicationContext as? Application)
                    ?: throw IllegalArgumentException("Context must be an Application or provide an Application context")
            val db = resolvedDb ?: AppDatabase.getInstance(app)
            val dispatcherProvider = provideDispatcherProvider(app)

            AnalysisViewModel(
                transactionAnalyticsDao = db.transactionAnalyticsDao(),
                categoryDao = db.categoryDao(),
                tagDao = db.tagDao(),
                dispatcherProvider = dispatcherProvider,
            )
        }
    }

    /**
     * Resolves an [AnnualSimulatorViewModel] instance.
     *
     * @param context Application or activity context.
     * @param simulationYear Optional year override.
     * @param timeProvider Optional [TimeProvider] instance.
     */
    fun provideAnnualSimulatorViewModel(
        context: Context,
        simulationYear: Int? = null,
        timeProvider: TimeProvider = SystemTimeProvider(),
    ): AnnualSimulatorViewModel {
        return annualSimulatorViewModel ?: run {
            val app =
                (context as? Application) ?: (context.applicationContext as? Application)
                    ?: throw IllegalArgumentException("Context must be an Application or provide an Application context")
            val settingsRepository = provideSettingsRepository(app)
            val transactionRepository = provideTransactionRepository(app)

            AnnualSimulatorViewModel(
                transactionRepository = transactionRepository,
                settingsRepository = settingsRepository,
                timeProvider = timeProvider,
                simulationYear = simulationYear,
            )
        }
    }

    /**
     * Resolves a [WhatIfViewModel] instance.
     *
     * @param context Application or activity context.
     * @param timeProvider Optional [TimeProvider] instance.
     */
    fun provideWhatIfViewModel(
        context: Context,
        timeProvider: TimeProvider = SystemTimeProvider(),
    ): WhatIfViewModel {
        return whatIfViewModel ?: run {
            val app =
                (context as? Application) ?: (context.applicationContext as? Application)
                    ?: throw IllegalArgumentException("Context must be an Application or provide an Application context")
            val settingsRepository = provideSettingsRepository(app)
            val transactionRepository = provideTransactionRepository(app)

            WhatIfViewModel(
                transactionRepository = transactionRepository,
                settingsRepository = settingsRepository,
                timeProvider = timeProvider,
            )
        }
    }

    /**
     * Resolves a [ManageMerchantRulesViewModel] instance.
     *
     * @param context Application or activity context.
     * @param resolvedDb Optional pre-resolved [AppDatabase] instance.
     */
    fun provideManageMerchantRulesViewModel(
        context: Context,
        resolvedDb: AppDatabase? = null,
    ): ManageMerchantRulesViewModel {
        return manageMerchantRulesViewModel ?: run {
            val app =
                (context as? Application) ?: (context.applicationContext as? Application)
                    ?: throw IllegalArgumentException("Context must be an Application or provide an Application context")
            val db = resolvedDb ?: AppDatabase.getInstance(app)
            val merchantRenameRuleRepository = provideMerchantRenameRuleRepository(app, db)
            val transactionRepository = provideTransactionRepository(app)
            val categoryMappingRepository = provideMerchantCategoryMappingRepository(app, db)
            val categoryRepository = provideCategoryRepository(app)
            val dispatcherProvider = provideDispatcherProvider(app)

            ManageMerchantRulesViewModel(
                merchantRenameRuleRepository = merchantRenameRuleRepository,
                transactionRepository = transactionRepository,
                categoryMappingRepository = categoryMappingRepository,
                categoryRepository = categoryRepository,
                dispatcherProvider = dispatcherProvider,
            )
        }
    }

    /**
     * Resolves a [SettingsViewModel] instance.
     *
     * @param context Application or activity context.
     * @param resolvedDb Optional pre-resolved [AppDatabase] instance.
     * @param autoSaveSmsTransactionUseCase Optional [AutoSaveSmsTransactionUseCase] override.
     * @param smsClassifier Optional [SmsClassifier] override.
     * @param nerExtractorProvider Optional NER extractor provider lambda.
     */
    fun provideSettingsViewModel(
        context: Context,
        resolvedDb: AppDatabase? = null,
        autoSaveSmsTransactionUseCase: AutoSaveSmsTransactionUseCase? = null,
        smsClassifier: SmsClassifier? = null,
        nerExtractorProvider: (() -> SmsEntityExtractor)? = null,
    ): SettingsViewModel {
        return settingsViewModel ?: run {
            val app =
                (context as? Application) ?: (context.applicationContext as? Application)
                    ?: throw IllegalArgumentException("Context must be an Application or provide an Application context")
            val db = resolvedDb ?: AppDatabase.getInstance(app)
            val settingsRepository = provideSettingsRepository(app)
            val transactionRepository = provideTransactionRepository(app)
            val merchantMappingRepository = provideMerchantMappingRepository(app, db)
            val accountRepository = provideAccountRepository(app)
            val categoryRepository = provideCategoryRepository(app)
            val smsRepository = provideSmsRepository(app)
            val autoSaveUseCase =
                autoSaveSmsTransactionUseCase
                    ?: provideAutoSaveSmsTransactionUseCase(app, db)
            val classifier = smsClassifier ?: provideSmsClassifier(app)
            val tagRepository = provideTagRepository(app)
            val splitTransactionRepository = provideSplitTransactionRepository(app, db)
            val merchantRenameRuleRepository = provideMerchantRenameRuleRepository(app, db)
            val merchantCategoryMappingRepository = provideMerchantCategoryMappingRepository(app, db)
            val customSmsRuleRepository = provideCustomSmsRuleRepository(app, db)
            val ignoreRuleRepository = provideIgnoreRuleRepository(app, db)
            val smsParseTemplateProvider = SmsProviderHelper.getSmsParseTemplateProvider(db)
            val transactionRunner = provideTransactionRunner(app, db)
            val databaseHealthChecker = provideDatabaseHealthChecker()
            val dispatcherProvider = provideDispatcherProvider(app)

            SettingsViewModel(
                application = app,
                settingsRepository = settingsRepository,
                transactionRepository = transactionRepository,
                merchantMappingRepository = merchantMappingRepository,
                accountRepository = accountRepository,
                categoryRepository = categoryRepository,
                smsRepository = smsRepository,
                autoSaveSmsTransactionUseCase = autoSaveUseCase,
                smsClassifier = classifier,
                tagRepository = tagRepository,
                splitTransactionRepository = splitTransactionRepository,
                merchantRenameRuleRepository = merchantRenameRuleRepository,
                merchantCategoryMappingRepository = merchantCategoryMappingRepository,
                customSmsRuleRepository = customSmsRuleRepository,
                ignoreRuleRepository = ignoreRuleRepository,
                smsParseTemplateProvider = smsParseTemplateProvider,
                nerExtractorProvider = nerExtractorProvider ?: this.nerExtractorProvider ?: { MlModelFactory.getNerExtractor(app) },
                transactionRunner = transactionRunner,
                databaseHealthChecker = databaseHealthChecker,
                dispatchers = dispatcherProvider,
            )
        }
    }

    /**
     * Resolves a [RecurringTransactionViewModel] instance.
     *
     * @param context Application or activity context.
     * @param resolvedDb Optional pre-resolved [AppDatabase] instance.
     */
    fun provideRecurringTransactionViewModel(
        context: Context,
        resolvedDb: AppDatabase? = null,
    ): RecurringTransactionViewModel {
        return recurringTransactionViewModel ?: run {
            val app =
                (context as? Application) ?: (context.applicationContext as? Application)
                    ?: throw IllegalArgumentException("Context must be an Application or provide an Application context")
            val repository = provideRecurringTransactionRepository(app, resolvedDb)
            val scheduleWorkerUseCase = provideScheduleRecurringTransactionWorkerUseCase(app)

            RecurringTransactionViewModel(
                repository = repository,
                scheduleWorkerUseCase = scheduleWorkerUseCase,
            )
        }
    }

    /**
     * Resolves a [SmsDebugViewModel] instance.
     *
     * @param context Application or activity context.
     * @param resolvedDb Optional pre-resolved [AppDatabase] instance.
     * @param autoSaveSmsTransactionUseCase Optional [AutoSaveSmsTransactionUseCase] override.
     * @param smsClassifier Optional [SmsClassifier] override.
     * @param nerExtractorProvider Optional NER extractor provider lambda.
     */
    fun provideSmsDebugViewModel(
        context: Context,
        resolvedDb: AppDatabase? = null,
        autoSaveSmsTransactionUseCase: AutoSaveSmsTransactionUseCase? = null,
        smsClassifier: SmsClassifier? = null,
        nerExtractorProvider: (() -> SmsEntityExtractor)? = null,
    ): SmsDebugViewModel {
        return smsDebugViewModel ?: run {
            val app =
                (context as? Application) ?: (context.applicationContext as? Application)
                    ?: throw IllegalArgumentException("Context must be an Application or provide an Application context")
            val db = resolvedDb ?: AppDatabase.getInstance(app)
            val smsRepository = provideSmsRepository(app)
            val transactionRepository = provideTransactionRepository(app)
            val customSmsRuleRepository = provideCustomSmsRuleRepository(app, db)
            val merchantRenameRuleRepository = provideMerchantRenameRuleRepository(app, db)
            val ignoreRuleRepository = provideIgnoreRuleRepository(app, db)
            val merchantCategoryMappingRepository = provideMerchantCategoryMappingRepository(app, db)
            val smsParseTemplateProvider = SmsProviderHelper.getSmsParseTemplateProvider(db)
            val classifier = smsClassifier ?: provideSmsClassifier(app)
            val autoSaveUseCase =
                autoSaveSmsTransactionUseCase
                    ?: provideAutoSaveSmsTransactionUseCase(app, db)
            val dispatcherProvider = provideDispatcherProvider(app)

            SmsDebugViewModel(
                application = app,
                smsRepository = smsRepository,
                transactionRepository = transactionRepository,
                customSmsRuleRepository = customSmsRuleRepository,
                merchantRenameRuleRepository = merchantRenameRuleRepository,
                ignoreRuleRepository = ignoreRuleRepository,
                merchantCategoryMappingRepository = merchantCategoryMappingRepository,
                smsParseTemplateProvider = smsParseTemplateProvider,
                smsClassifier = classifier,
                nerExtractorProvider = nerExtractorProvider ?: this.nerExtractorProvider ?: { MlModelFactory.getNerExtractor(app) },
                autoSaveSmsTransactionUseCase = autoSaveUseCase,
                dispatchers = dispatcherProvider,
            )
        }
    }

    /**
     * Resolves a [ManageIgnoreRulesViewModel] instance.
     *
     * @param context Application or activity context.
     * @param resolvedDb Optional pre-resolved [AppDatabase] instance.
     * @param ignoreRuleRepository Optional [IIgnoreRuleRepository] override.
     * @param dispatcherProvider Optional [DispatcherProvider] override.
     */
    fun provideManageIgnoreRulesViewModel(
        context: Context,
        resolvedDb: AppDatabase? = null,
        ignoreRuleRepository: IIgnoreRuleRepository? = null,
        dispatcherProvider: DispatcherProvider? = null,
    ): ManageIgnoreRulesViewModel {
        return manageIgnoreRulesViewModel ?: run {
            val app =
                (context as? Application) ?: (context.applicationContext as? Application)
                    ?: throw IllegalArgumentException("Context must be an Application or provide an Application context")
            val repository = ignoreRuleRepository ?: provideIgnoreRuleRepository(app, resolvedDb)
            val dispatchers = dispatcherProvider ?: provideDispatcherProvider(app)
            ManageIgnoreRulesViewModel(
                ignoreRuleRepository = repository,
                dispatcherProvider = dispatchers,
            )
        }
    }

    /**
     * Resolves a [ManageParseRulesViewModel] instance.
     *
     * @param context Application or activity context.
     * @param resolvedDb Optional pre-resolved [AppDatabase] instance.
     * @param customSmsRuleRepository Optional [ICustomSmsRuleRepository] override.
     * @param dispatcherProvider Optional [DispatcherProvider] override.
     */
    fun provideManageParseRulesViewModel(
        context: Context,
        resolvedDb: AppDatabase? = null,
        customSmsRuleRepository: ICustomSmsRuleRepository? = null,
        dispatcherProvider: DispatcherProvider? = null,
    ): ManageParseRulesViewModel {
        return manageParseRulesViewModel ?: run {
            val app =
                (context as? Application) ?: (context.applicationContext as? Application)
                    ?: throw IllegalArgumentException("Context must be an Application or provide an Application context")
            val repository = customSmsRuleRepository ?: provideCustomSmsRuleRepository(app, resolvedDb)
            val dispatchers = dispatcherProvider ?: provideDispatcherProvider(app)
            ManageParseRulesViewModel(
                customSmsRuleRepository = repository,
                dispatcherProvider = dispatchers,
            )
        }
    }

    /**
     * Resolves a [PendingTransactionsViewModel] instance.
     *
     * @param context Application or activity context.
     * @param resolvedDb Optional pre-resolved [AppDatabase] instance.
     * @param transactionRepository Optional [ITransactionRepository] override.
     * @param recurringTransactionRepository Optional [IRecurringTransactionRepository] override.
     * @param dispatcherProvider Optional [DispatcherProvider] override.
     */
    fun providePendingTransactionsViewModel(
        context: Context,
        resolvedDb: AppDatabase? = null,
        transactionRepository: ITransactionRepository? = null,
        recurringTransactionRepository: IRecurringTransactionRepository? = null,
        dispatcherProvider: DispatcherProvider? = null,
    ): PendingTransactionsViewModel {
        return pendingTransactionsViewModel ?: run {
            val app =
                (context as? Application) ?: (context.applicationContext as? Application)
                    ?: throw IllegalArgumentException("Context must be an Application or provide an Application context")
            val txnRepo = transactionRepository ?: provideTransactionRepository(app)
            val recurringRepo =
                recurringTransactionRepository ?: provideRecurringTransactionRepository(app, resolvedDb)
            val dispatchers = dispatcherProvider ?: provideDispatcherProvider(app)
            PendingTransactionsViewModel(
                transactionRepository = txnRepo,
                recurringTransactionRepository = recurringRepo,
                dispatcherProvider = dispatchers,
            )
        }
    }

    /**
     * Resolves the centralized [ViewModelProvider.Factory].
     *
     * @param context Optional context fallback.
     */
    fun provideViewModelFactory(context: Context? = null): ViewModelProvider.Factory {
        return FinlightViewModelFactory.create(context)
    }

    @VisibleForTesting
    fun setSettingsRepository(repository: ISettingsRepository?) {
        settingsRepository = repository
    }

    @VisibleForTesting
    fun setAppConfigRepository(repository: IAppConfigRepository?) {
        appConfigRepository = repository
    }

    @VisibleForTesting
    fun setDashboardSettingsRepository(repository: IDashboardSettingsRepository?) {
        dashboardSettingsRepository = repository
    }

    @VisibleForTesting
    fun setSecuritySettingsRepository(repository: ISecuritySettingsRepository?) {
        securitySettingsRepository = repository
    }

    @VisibleForTesting
    fun setBudgetSettingsRepository(repository: IBudgetSettingsRepository?) {
        budgetSettingsRepository = repository
    }

    @VisibleForTesting
    fun setBackupSettingsRepository(repository: IBackupSettingsRepository?) {
        backupSettingsRepository = repository
    }

    @VisibleForTesting
    fun setNotificationSettingsRepository(repository: INotificationSettingsRepository?) {
        notificationSettingsRepository = repository
    }

    @VisibleForTesting
    fun setSmsRuleSettingsRepository(repository: ISmsRuleSettingsRepository?) {
        smsRuleSettingsRepository = repository
    }

    @VisibleForTesting
    fun setTravelSettingsRepository(repository: ITravelSettingsRepository?) {
        travelSettingsRepository = repository
    }

    @VisibleForTesting
    fun setFirstLaunchSettingsRepository(repository: IFirstLaunchSettingsRepository?) {
        firstLaunchSettingsRepository = repository
    }

    @VisibleForTesting
    fun setFeatureSettingsRepository(repository: IFeatureSettingsRepository?) {
        featureSettingsRepository = repository
    }

    @VisibleForTesting
    fun setDispatcherProvider(provider: DispatcherProvider?) {
        dispatcherProvider = provider
    }

    @VisibleForTesting
    fun setTransactionRepository(repository: ITransactionRepository?) {
        transactionRepository = repository
    }

    @VisibleForTesting
    fun setAccountRepository(repository: IAccountRepository?) {
        accountRepository = repository
    }

    @VisibleForTesting
    fun setCategoryRepository(repository: ICategoryRepository?) {
        categoryRepository = repository
    }

    @VisibleForTesting
    fun setTagRepository(repository: ITagRepository?) {
        tagRepository = repository
    }

    @VisibleForTesting
    fun setSmsRepository(repository: ISmsRepository?) {
        smsRepository = repository
    }

    @VisibleForTesting
    fun setMergeAccountsUseCase(useCase: MergeAccountsUseCase?) {
        mergeAccountsUseCase = useCase
    }

    @VisibleForTesting
    fun setManageReimbursementUseCase(useCase: ManageReimbursementUseCase?) {
        manageReimbursementUseCase = useCase
    }

    @VisibleForTesting
    fun setMergeTransactionsUseCase(useCase: MergeTransactionsUseCase?) {
        mergeTransactionsUseCase = useCase
    }

    @VisibleForTesting
    fun setAutoSaveSmsTransactionUseCase(useCase: AutoSaveSmsTransactionUseCase?) {
        autoSaveSmsTransactionUseCase = useCase
    }

    @VisibleForTesting
    fun setGetMonthlyConsistencyDataUseCase(useCase: GetMonthlyConsistencyDataUseCase?) {
        getMonthlyConsistencyDataUseCase = useCase
    }

    @VisibleForTesting
    fun setScheduleRecurringTransactionWorkerUseCase(useCase: ScheduleRecurringTransactionWorkerUseCase?) {
        scheduleRecurringTransactionWorkerUseCase = useCase
    }

    @VisibleForTesting
    fun setMerchantMappingRepository(repository: IMerchantMappingRepository?) {
        merchantMappingRepository = repository
    }

    @VisibleForTesting
    fun setBudgetRepository(repository: IBudgetRepository?) {
        budgetRepository = repository
    }

    @VisibleForTesting
    fun setRecurringTransactionRepository(repository: IRecurringTransactionRepository?) {
        recurringTransactionRepository = repository
    }

    @VisibleForTesting
    fun setSplitTransactionRepository(repository: ISplitTransactionRepository?) {
        splitTransactionRepository = repository
    }

    @VisibleForTesting
    fun setMerchantRenameRuleRepository(repository: IMerchantRenameRuleRepository?) {
        merchantRenameRuleRepository = repository
    }

    @VisibleForTesting
    fun setMerchantCategoryMappingRepository(repository: IMerchantCategoryMappingRepository?) {
        merchantCategoryMappingRepository = repository
    }

    @VisibleForTesting
    fun setCustomSmsRuleRepository(repository: ICustomSmsRuleRepository?) {
        customSmsRuleRepository = repository
    }

    @VisibleForTesting
    fun setIgnoreRuleRepository(repository: IIgnoreRuleRepository?) {
        ignoreRuleRepository = repository
    }

    @VisibleForTesting
    fun setSmsParseTemplateRepository(repository: ISmsParseTemplateRepository?) {
        smsParseTemplateRepository = repository
    }

    @VisibleForTesting
    fun setTransactionRunner(runner: TransactionRunner?) {
        transactionRunner = runner
    }

    @VisibleForTesting
    fun setTransactionViewModel(viewModel: TransactionViewModel?) {
        transactionViewModel = viewModel
    }

    @VisibleForTesting
    fun setDashboardViewModel(viewModel: DashboardViewModel?) {
        dashboardViewModel = viewModel
    }

    @VisibleForTesting
    fun setTripRepository(repository: ITripRepository?) {
        tripRepository = repository
    }

    @VisibleForTesting
    fun setGoalRepository(repository: IGoalRepository?) {
        goalRepository = repository
    }

    @VisibleForTesting
    fun setRuleCreationViewModel(viewModel: RuleCreationViewModel?) {
        ruleCreationViewModel = viewModel
    }

    @VisibleForTesting
    fun setAccountViewModel(viewModel: AccountViewModel?) {
        accountViewModel = viewModel
    }

    @VisibleForTesting
    fun setBudgetViewModel(viewModel: BudgetViewModel?) {
        budgetViewModel = viewModel
    }

    @VisibleForTesting
    fun setCategoryViewModel(viewModel: CategoryViewModel?) {
        categoryViewModel = viewModel
    }

    @VisibleForTesting
    fun setCurrencyViewModel(viewModel: CurrencyViewModel?) {
        currencyViewModel = viewModel
    }

    @VisibleForTesting
    fun setGoalViewModel(viewModel: GoalViewModel?) {
        goalViewModel = viewModel
    }

    @VisibleForTesting
    fun setHistoricTripsViewModel(viewModel: HistoricTripsViewModel?) {
        historicTripsViewModel = viewModel
    }

    @VisibleForTesting
    fun setIncomeViewModel(viewModel: IncomeViewModel?) {
        incomeViewModel = viewModel
    }

    @VisibleForTesting
    fun setOnboardingViewModel(viewModel: OnboardingViewModel?) {
        onboardingViewModel = viewModel
    }

    @VisibleForTesting
    fun setProfileViewModel(viewModel: ProfileViewModel?) {
        profileViewModel = viewModel
    }

    @VisibleForTesting
    fun setTagViewModel(viewModel: TagViewModel?) {
        tagViewModel = viewModel
    }

    @VisibleForTesting
    fun setReportsViewModel(viewModel: ReportsViewModel?) {
        reportsViewModel = viewModel
    }

    @VisibleForTesting
    fun setAnalysisViewModel(viewModel: AnalysisViewModel?) {
        analysisViewModel = viewModel
    }

    @VisibleForTesting
    fun setAnnualSimulatorViewModel(viewModel: AnnualSimulatorViewModel?) {
        annualSimulatorViewModel = viewModel
    }

    @VisibleForTesting
    fun setWhatIfViewModel(viewModel: WhatIfViewModel?) {
        whatIfViewModel = viewModel
    }

    @VisibleForTesting
    fun setManageMerchantRulesViewModel(viewModel: ManageMerchantRulesViewModel?) {
        manageMerchantRulesViewModel = viewModel
    }

    @VisibleForTesting
    fun setSettingsViewModel(viewModel: SettingsViewModel?) {
        settingsViewModel = viewModel
    }

    @VisibleForTesting
    fun setRecurringTransactionViewModel(viewModel: RecurringTransactionViewModel?) {
        recurringTransactionViewModel = viewModel
    }

    @VisibleForTesting
    fun setSmsDebugViewModel(viewModel: SmsDebugViewModel?) {
        smsDebugViewModel = viewModel
    }

    @VisibleForTesting
    fun setManageIgnoreRulesViewModel(viewModel: ManageIgnoreRulesViewModel?) {
        manageIgnoreRulesViewModel = viewModel
    }

    @VisibleForTesting
    fun setManageParseRulesViewModel(viewModel: ManageParseRulesViewModel?) {
        manageParseRulesViewModel = viewModel
    }

    @VisibleForTesting
    fun setPendingTransactionsViewModel(viewModel: PendingTransactionsViewModel?) {
        pendingTransactionsViewModel = viewModel
    }

    @VisibleForTesting
    fun setSmsClassifier(classifier: SmsClassifier?) {
        smsClassifier = classifier
    }

    @VisibleForTesting
    fun setNerExtractorProvider(provider: (() -> SmsEntityExtractor)?) {
        nerExtractorProvider = provider
    }

    @VisibleForTesting
    fun reset() {
        dispatcherProvider = null
        settingsRepository = null
        appConfigRepository = null
        dashboardSettingsRepository = null
        securitySettingsRepository = null
        budgetSettingsRepository = null
        backupSettingsRepository = null
        notificationSettingsRepository = null
        smsRuleSettingsRepository = null
        travelSettingsRepository = null
        firstLaunchSettingsRepository = null
        featureSettingsRepository = null
        transactionRepository = null
        accountRepository = null
        categoryRepository = null
        tagRepository = null
        tripRepository = null
        goalRepository = null
        smsRepository = null
        mergeAccountsUseCase = null
        manageReimbursementUseCase = null
        mergeTransactionsUseCase = null
        autoSaveSmsTransactionUseCase = null
        getMonthlyConsistencyDataUseCase = null
        scheduleRecurringTransactionWorkerUseCase = null
        merchantMappingRepository = null
        budgetRepository = null
        recurringTransactionRepository = null
        splitTransactionRepository = null
        merchantRenameRuleRepository = null
        merchantCategoryMappingRepository = null
        customSmsRuleRepository = null
        ignoreRuleRepository = null
        smsParseTemplateRepository = null
        transactionRunner = null
        transactionViewModel = null
        dashboardViewModel = null
        ruleCreationViewModel = null
        accountViewModel = null
        budgetViewModel = null
        categoryViewModel = null
        currencyViewModel = null
        goalViewModel = null
        historicTripsViewModel = null
        incomeViewModel = null
        onboardingViewModel = null
        profileViewModel = null
        tagViewModel = null
        reportsViewModel = null
        analysisViewModel = null
        annualSimulatorViewModel = null
        whatIfViewModel = null
        manageMerchantRulesViewModel = null
        settingsViewModel = null
        recurringTransactionViewModel = null
        smsDebugViewModel = null
        manageIgnoreRulesViewModel = null
        manageParseRulesViewModel = null
        pendingTransactionsViewModel = null
        smsClassifier = null
        nerExtractorProvider = null
    }
}
