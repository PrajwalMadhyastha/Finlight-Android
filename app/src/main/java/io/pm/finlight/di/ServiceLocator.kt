package io.pm.finlight.di

import android.content.Context
import androidx.annotation.VisibleForTesting
import io.pm.finlight.AppConfigRepository
import io.pm.finlight.BackupSettingsRepository
import io.pm.finlight.BudgetSettingsRepository
import io.pm.finlight.DashboardSettingsRepository
import io.pm.finlight.FeatureSettingsRepository
import io.pm.finlight.FirstLaunchSettingsRepository
import io.pm.finlight.IAppConfigRepository
import io.pm.finlight.IBackupSettingsRepository
import io.pm.finlight.IBudgetSettingsRepository
import io.pm.finlight.IDashboardSettingsRepository
import io.pm.finlight.IFeatureSettingsRepository
import io.pm.finlight.IFirstLaunchSettingsRepository
import io.pm.finlight.INotificationSettingsRepository
import io.pm.finlight.ISecuritySettingsRepository
import io.pm.finlight.ISettingsRepository
import io.pm.finlight.ISmsRuleSettingsRepository
import io.pm.finlight.ITravelSettingsRepository
import io.pm.finlight.NotificationSettingsRepository
import io.pm.finlight.SecuritySettingsRepository
import io.pm.finlight.SettingsRepository
import io.pm.finlight.SmsRuleSettingsRepository
import io.pm.finlight.TravelSettingsRepository
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
    }
}
