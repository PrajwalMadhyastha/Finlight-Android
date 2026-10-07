package io.pm.finlight.di

import android.content.Context
import androidx.annotation.VisibleForTesting
import io.pm.finlight.AccountRepository
import io.pm.finlight.AppConfigRepository
import io.pm.finlight.BackupSettingsRepository
import io.pm.finlight.BudgetRepository
import io.pm.finlight.BudgetSettingsRepository
import io.pm.finlight.CategoryRepository
import io.pm.finlight.DashboardSettingsRepository
import io.pm.finlight.FeatureSettingsRepository
import io.pm.finlight.FirstLaunchSettingsRepository
import io.pm.finlight.IAccountRepository
import io.pm.finlight.IAppConfigRepository
import io.pm.finlight.IBackupSettingsRepository
import io.pm.finlight.IBudgetRepository
import io.pm.finlight.IBudgetSettingsRepository
import io.pm.finlight.ICategoryRepository
import io.pm.finlight.IDashboardSettingsRepository
import io.pm.finlight.IFeatureSettingsRepository
import io.pm.finlight.IFirstLaunchSettingsRepository
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
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.domain.usecase.AutoSaveSmsTransactionUseCase
import io.pm.finlight.domain.usecase.ManageReimbursementUseCase
import io.pm.finlight.domain.usecase.MergeAccountsUseCase
import io.pm.finlight.domain.usecase.MergeTransactionsUseCase
import io.pm.finlight.domain.usecase.ResolveTravelModeTagUseCase
import io.pm.finlight.utils.DefaultDispatcherProvider
import io.pm.finlight.utils.DispatcherProvider

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

    fun provideDispatcherProvider(context: Context? = null): DispatcherProvider {
        return dispatcherProvider ?: synchronized(this) {
            dispatcherProvider ?: DefaultDispatcherProvider().also {
                dispatcherProvider = it
            }
        }
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
                val db = resolvedDb ?: AppDatabase.getInstance(context.applicationContext)
                val tagRepository = provideTagRepository(context)
                AutoSaveSmsTransactionUseCase(
                    transactionRepository = provideTransactionRepository(context),
                    accountRepository = provideAccountRepository(context),
                    smsRepository = provideSmsRepository(context),
                    merchantMappingRepository = provideMerchantMappingRepository(context, db),
                    db = db,
                    deletedSmsHashDao = db.deletedSmsHashDao(),
                    resolveTravelModeTagUseCase = ResolveTravelModeTagUseCase(tagRepository),
                    dispatcherProvider = provideDispatcherProvider(context),
                ).also {
                    autoSaveSmsTransactionUseCase = it
                }
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
        smsRepository = null
        mergeAccountsUseCase = null
        manageReimbursementUseCase = null
        mergeTransactionsUseCase = null
        autoSaveSmsTransactionUseCase = null
        merchantMappingRepository = null
        budgetRepository = null
        recurringTransactionRepository = null
        splitTransactionRepository = null
        merchantRenameRuleRepository = null
        merchantCategoryMappingRepository = null
    }
}
