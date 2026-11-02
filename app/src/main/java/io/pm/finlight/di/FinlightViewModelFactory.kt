package io.pm.finlight.di

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.pm.finlight.IAccountRepository
import io.pm.finlight.ICategoryRepository
import io.pm.finlight.IRecurringTransactionRepository
import io.pm.finlight.ISettingsRepository
import io.pm.finlight.ISplitTransactionRepository
import io.pm.finlight.ITagRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.PotentialTransaction
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.dao.TransactionAnalyticsDao
import io.pm.finlight.data.model.TimePeriod
import io.pm.finlight.data.repository.ITripRepository
import io.pm.finlight.domain.usecase.GetMonthlyConsistencyDataUseCase
import io.pm.finlight.ml.SmsClassifier
import io.pm.finlight.ml.SmsEntityExtractor
import io.pm.finlight.ui.viewmodel.AnalysisDetailViewModel
import io.pm.finlight.ui.viewmodel.AnalysisDimension
import io.pm.finlight.ui.viewmodel.BatchAnalysisViewModel
import io.pm.finlight.ui.viewmodel.DrilldownType
import io.pm.finlight.ui.viewmodel.DrilldownViewModel
import io.pm.finlight.ui.viewmodel.LinkRecurringTransactionViewModel
import io.pm.finlight.ui.viewmodel.LinkTransactionViewModel
import io.pm.finlight.ui.viewmodel.SearchViewModel
import io.pm.finlight.ui.viewmodel.SplitTransactionViewModel
import io.pm.finlight.ui.viewmodel.TimePeriodReportViewModel
import io.pm.finlight.ui.viewmodel.TripDetailViewModel
import io.pm.finlight.utils.DispatcherProvider

/**
 * Centralized generic ViewModel factory provider for Finlight.
 *
 * Uses Jetpack ViewModel's [viewModelFactory] DSL to configure initializers
 * backed by centralized [ServiceLocator] dependency resolution.
 *
 * Migration & Deprecation Strategy:
 * Existing legacy ViewModelFactory classes are marked with @Deprecated pointing to
 * [FinlightViewModelFactory.create] or corresponding assisted factory builder functions,
 * and delegated to [ServiceLocator].
 */
class FinlightViewModelFactory(
    private val context: Context? = null,
) : ViewModelProvider.Factory {
    private val delegate: ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                val app = resolveApplication(this, context)
                val handle = runCatching { this.createSavedStateHandle() }.getOrElse { SavedStateHandle() }
                ServiceLocator.provideTransactionViewModel(app, savedStateHandle = handle)
            }
            initializer {
                val app = resolveApplication(this, context)
                ServiceLocator.provideDashboardViewModel(app)
            }
            initializer {
                val app = resolveApplication(this, context)
                val handle = runCatching { this.createSavedStateHandle() }.getOrElse { SavedStateHandle() }
                ServiceLocator.provideRuleCreationViewModel(app, savedStateHandle = handle)
            }
            initializer {
                val app = resolveApplication(this, context)
                ServiceLocator.provideAccountViewModel(app)
            }
            initializer {
                val app = resolveApplication(this, context)
                ServiceLocator.provideBudgetViewModel(app)
            }
            initializer {
                val app = resolveApplication(this, context)
                ServiceLocator.provideCategoryViewModel(app)
            }
            initializer {
                val app = resolveApplication(this, context)
                ServiceLocator.provideCurrencyViewModel(app)
            }
            initializer {
                val app = resolveApplication(this, context)
                ServiceLocator.provideGoalViewModel(app)
            }
            initializer {
                val app = resolveApplication(this, context)
                ServiceLocator.provideHistoricTripsViewModel(app)
            }
            initializer {
                val app = resolveApplication(this, context)
                ServiceLocator.provideIncomeViewModel(app)
            }
            initializer {
                val app = resolveApplication(this, context)
                ServiceLocator.provideOnboardingViewModel(app)
            }
            initializer {
                val app = resolveApplication(this, context)
                ServiceLocator.provideProfileViewModel(app)
            }
            initializer {
                val app = resolveApplication(this, context)
                ServiceLocator.provideTagViewModel(app)
            }
            initializer {
                val app = resolveApplication(this, context)
                ServiceLocator.provideReportsViewModel(app)
            }
            initializer {
                val app = resolveApplication(this, context)
                ServiceLocator.provideAnalysisViewModel(app)
            }
            initializer {
                val app = resolveApplication(this, context)
                ServiceLocator.provideAnnualSimulatorViewModel(app)
            }
            initializer {
                val app = resolveApplication(this, context)
                ServiceLocator.provideWhatIfViewModel(app)
            }
            initializer {
                val app = resolveApplication(this, context)
                ServiceLocator.provideManageMerchantRulesViewModel(app)
            }
            initializer {
                val app = resolveApplication(this, context)
                ServiceLocator.provideSettingsViewModel(app)
            }
            initializer {
                val app = resolveApplication(this, context)
                ServiceLocator.provideRecurringTransactionViewModel(app)
            }
            initializer {
                val app = resolveApplication(this, context)
                ServiceLocator.provideSmsDebugViewModel(app)
            }
            initializer {
                val app = resolveApplication(this, context)
                ServiceLocator.provideManageIgnoreRulesViewModel(app)
            }
            initializer {
                val app = resolveApplication(this, context)
                ServiceLocator.provideManageParseRulesViewModel(app)
            }
            initializer {
                val app = resolveApplication(this, context)
                ServiceLocator.providePendingTransactionsViewModel(app)
            }
            initializer {
                val app = resolveApplication(this, context)
                val transactionId =
                    this[SPLIT_TRANSACTION_ID_KEY]
                        ?: throw IllegalArgumentException("SPLIT_TRANSACTION_ID_KEY required for SplitTransactionViewModel")
                ServiceLocator.provideSplitTransactionViewModel(app, transactionId)
            }
            initializer {
                val app = resolveApplication(this, context)
                val tripId =
                    this[TRIP_ID_KEY]
                        ?: throw IllegalArgumentException("TRIP_ID_KEY required for TripDetailViewModel")
                val tagId =
                    this[TAG_ID_KEY]
                        ?: throw IllegalArgumentException("TAG_ID_KEY required for TripDetailViewModel")
                ServiceLocator.provideTripDetailViewModel(app, tripId, tagId)
            }
            initializer {
                val app = resolveApplication(this, context)
                val potentialTransaction =
                    this[POTENTIAL_TRANSACTION_KEY]
                        ?: throw IllegalArgumentException("POTENTIAL_TRANSACTION_KEY required for LinkTransactionViewModel")
                ServiceLocator.provideLinkTransactionViewModel(app, potentialTransaction)
            }
            initializer {
                val app = resolveApplication(this, context)
                val potentialTransaction =
                    this[POTENTIAL_TRANSACTION_KEY]
                        ?: throw IllegalArgumentException("POTENTIAL_TRANSACTION_KEY required for LinkRecurringTransactionViewModel")
                ServiceLocator.provideLinkRecurringTransactionViewModel(app, potentialTransaction)
            }
            initializer {
                val app = resolveApplication(this, context)
                val drilldownType =
                    this[DRILLDOWN_TYPE_KEY]
                        ?: throw IllegalArgumentException("DRILLDOWN_TYPE_KEY required for DrilldownViewModel")
                val entityName =
                    this[ENTITY_NAME_KEY]
                        ?: throw IllegalArgumentException("ENTITY_NAME_KEY required for DrilldownViewModel")
                val month =
                    this[MONTH_KEY]
                        ?: throw IllegalArgumentException("MONTH_KEY required for DrilldownViewModel")
                val year =
                    this[YEAR_KEY]
                        ?: throw IllegalArgumentException("YEAR_KEY required for DrilldownViewModel")
                ServiceLocator.provideDrilldownViewModel(app, drilldownType, entityName, month, year)
            }
            initializer {
                val app = resolveApplication(this, context)
                val timePeriod =
                    this[TIME_PERIOD_KEY]
                        ?: throw IllegalArgumentException("TIME_PERIOD_KEY required for TimePeriodReportViewModel")
                val initialDateMillis = this[INITIAL_DATE_MILLIS_KEY]
                val showPreviousMonth = this[SHOW_PREVIOUS_MONTH_KEY] ?: false
                ServiceLocator.provideTimePeriodReportViewModel(app, timePeriod, initialDateMillis, showPreviousMonth)
            }
            initializer {
                val app = resolveApplication(this, context)
                val dimension =
                    this[ANALYSIS_DIMENSION_KEY]
                        ?: throw IllegalArgumentException("ANALYSIS_DIMENSION_KEY required for AnalysisDetailViewModel")
                val dimensionId =
                    this[DIMENSION_ID_KEY]
                        ?: throw IllegalArgumentException("DIMENSION_ID_KEY required for AnalysisDetailViewModel")
                val startDate =
                    this[START_DATE_KEY]
                        ?: throw IllegalArgumentException("START_DATE_KEY required for AnalysisDetailViewModel")
                val endDate =
                    this[END_DATE_KEY]
                        ?: throw IllegalArgumentException("END_DATE_KEY required for AnalysisDetailViewModel")
                ServiceLocator.provideAnalysisDetailViewModel(app, dimension, dimensionId, startDate, endDate)
            }
            initializer {
                val app = resolveApplication(this, context)
                ServiceLocator.provideSearchViewModel(
                    context = app,
                    initialCategoryId = this[INITIAL_CATEGORY_ID_KEY],
                    initialDateMillis = this[INITIAL_DATE_MILLIS_KEY],
                    initialQuery = this[INITIAL_QUERY_KEY],
                )
            }
            initializer {
                val app = resolveApplication(this, context)
                ServiceLocator.provideBatchAnalysisViewModel(
                    context = app,
                    nerExtractorProvider = this[BATCH_NER_EXTRACTOR_PROVIDER_KEY],
                    classifier = this[BATCH_CLASSIFIER_KEY],
                    dispatcherProvider = this[DISPATCHER_PROVIDER_KEY],
                )
            }
        }

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return delegate.create(modelClass, CreationExtras.Empty)
    }

    override fun <T : ViewModel> create(
        modelClass: Class<T>,
        extras: CreationExtras,
    ): T {
        return delegate.create(modelClass, extras)
    }

    companion object {
        val SPLIT_TRANSACTION_ID_KEY = object : CreationExtras.Key<Int> {}
        val TRIP_ID_KEY = object : CreationExtras.Key<Int> {}
        val TAG_ID_KEY = object : CreationExtras.Key<Int> {}
        val POTENTIAL_TRANSACTION_KEY = object : CreationExtras.Key<PotentialTransaction> {}
        val DRILLDOWN_TYPE_KEY = object : CreationExtras.Key<DrilldownType> {}
        val ENTITY_NAME_KEY = object : CreationExtras.Key<String> {}
        val MONTH_KEY = object : CreationExtras.Key<Int> {}
        val YEAR_KEY = object : CreationExtras.Key<Int> {}
        val TIME_PERIOD_KEY = object : CreationExtras.Key<TimePeriod> {}
        val INITIAL_DATE_MILLIS_KEY = object : CreationExtras.Key<Long> {}
        val SHOW_PREVIOUS_MONTH_KEY = object : CreationExtras.Key<Boolean> {}
        val ANALYSIS_DIMENSION_KEY = object : CreationExtras.Key<AnalysisDimension> {}
        val DIMENSION_ID_KEY = object : CreationExtras.Key<String> {}
        val START_DATE_KEY = object : CreationExtras.Key<Long> {}
        val END_DATE_KEY = object : CreationExtras.Key<Long> {}
        val INITIAL_CATEGORY_ID_KEY = object : CreationExtras.Key<Int> {}
        val INITIAL_QUERY_KEY = object : CreationExtras.Key<String> {}
        val BATCH_NER_EXTRACTOR_PROVIDER_KEY = object : CreationExtras.Key<() -> SmsEntityExtractor> {}
        val BATCH_CLASSIFIER_KEY = object : CreationExtras.Key<SmsClassifier> {}
        val DISPATCHER_PROVIDER_KEY = object : CreationExtras.Key<DispatcherProvider> {}

        /**
         * Generic assisted provider helper that produces a [ViewModelProvider.Factory]
         * for a specific ViewModel class using an assisted factory lambda.
         */
        inline fun <reified VM : ViewModel> createAssistedFactory(
            context: Context? = null,
            crossinline creator: (Application) -> VM,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras
                ): T {
                    if (modelClass.isAssignableFrom(VM::class.java)) {
                        val app = resolveApplication(extras, context)
                        @Suppress("UNCHECKED_CAST")
                        return creator(app) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }

                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    create(modelClass, CreationExtras.Empty)
            }

        fun createSplitTransactionFactory(
            context: Context? = null,
            transactionId: Int,
            resolvedDb: AppDatabase? = null,
            transactionRepository: ITransactionRepository? = null,
            categoryRepository: ICategoryRepository? = null,
            splitTransactionRepository: ISplitTransactionRepository? = null,
            dispatcherProvider: DispatcherProvider? = null,
        ): ViewModelProvider.Factory =
            createAssistedFactory<SplitTransactionViewModel>(context) { app ->
                ServiceLocator.provideSplitTransactionViewModel(
                    context = app,
                    transactionId = transactionId,
                    resolvedDb = resolvedDb,
                    transactionRepository = transactionRepository,
                    categoryRepository = categoryRepository,
                    splitTransactionRepository = splitTransactionRepository,
                    dispatcherProvider = dispatcherProvider,
                )
            }

        fun createTripDetailFactory(
            context: Context? = null,
            tripId: Int,
            tagId: Int,
            resolvedDb: AppDatabase? = null,
            tripRepository: ITripRepository? = null,
            transactionRepository: ITransactionRepository? = null,
            dispatcherProvider: DispatcherProvider? = null,
        ): ViewModelProvider.Factory =
            createAssistedFactory<TripDetailViewModel>(context) { app ->
                ServiceLocator.provideTripDetailViewModel(
                    context = app,
                    tripId = tripId,
                    tagId = tagId,
                    resolvedDb = resolvedDb,
                    tripRepository = tripRepository,
                    transactionRepository = transactionRepository,
                    dispatcherProvider = dispatcherProvider,
                )
            }

        fun createLinkTransactionFactory(
            context: Context? = null,
            potentialTransaction: PotentialTransaction,
            transactionRepository: ITransactionRepository? = null,
            dispatcherProvider: DispatcherProvider? = null,
        ): ViewModelProvider.Factory =
            createAssistedFactory<LinkTransactionViewModel>(context) { app ->
                ServiceLocator.provideLinkTransactionViewModel(
                    context = app,
                    potentialTransaction = potentialTransaction,
                    transactionRepository = transactionRepository,
                    dispatcherProvider = dispatcherProvider,
                )
            }

        fun createLinkRecurringTransactionFactory(
            context: Context? = null,
            potentialTransaction: PotentialTransaction,
            resolvedDb: AppDatabase? = null,
            transactionRepository: ITransactionRepository? = null,
            recurringTransactionRepository: IRecurringTransactionRepository? = null,
            dispatcherProvider: DispatcherProvider? = null,
        ): ViewModelProvider.Factory =
            createAssistedFactory<LinkRecurringTransactionViewModel>(context) { app ->
                ServiceLocator.provideLinkRecurringTransactionViewModel(
                    context = app,
                    potentialTransaction = potentialTransaction,
                    resolvedDb = resolvedDb,
                    transactionRepository = transactionRepository,
                    recurringTransactionRepository = recurringTransactionRepository,
                    dispatcherProvider = dispatcherProvider,
                )
            }

        fun createDrilldownFactory(
            context: Context? = null,
            drilldownType: DrilldownType,
            entityName: String,
            month: Int,
            year: Int,
            transactionRepository: ITransactionRepository? = null,
            dispatcherProvider: DispatcherProvider? = null,
        ): ViewModelProvider.Factory =
            createAssistedFactory<DrilldownViewModel>(context) { app ->
                ServiceLocator.provideDrilldownViewModel(
                    context = app,
                    drilldownType = drilldownType,
                    entityName = entityName,
                    month = month,
                    year = year,
                    transactionRepository = transactionRepository,
                    dispatcherProvider = dispatcherProvider,
                )
            }

        fun createTimePeriodReportFactory(
            context: Context? = null,
            timePeriod: TimePeriod,
            initialDateMillis: Long?,
            showPreviousMonth: Boolean,
            settingsRepository: ISettingsRepository? = null,
            transactionRepository: ITransactionRepository? = null,
            getMonthlyConsistencyDataUseCase: GetMonthlyConsistencyDataUseCase? = null,
            dispatcherProvider: DispatcherProvider? = null,
        ): ViewModelProvider.Factory =
            createAssistedFactory<TimePeriodReportViewModel>(context) { app ->
                ServiceLocator.provideTimePeriodReportViewModel(
                    context = app,
                    timePeriod = timePeriod,
                    initialDateMillis = initialDateMillis,
                    showPreviousMonth = showPreviousMonth,
                    settingsRepository = settingsRepository,
                    transactionRepository = transactionRepository,
                    getMonthlyConsistencyDataUseCase = getMonthlyConsistencyDataUseCase,
                    dispatcherProvider = dispatcherProvider,
                )
            }

        fun createAnalysisDetailFactory(
            context: Context? = null,
            dimension: AnalysisDimension,
            dimensionId: String,
            startDate: Long,
            endDate: Long,
            resolvedDb: AppDatabase? = null,
            transactionAnalyticsDao: TransactionAnalyticsDao? = null,
            dispatcherProvider: DispatcherProvider? = null,
        ): ViewModelProvider.Factory =
            createAssistedFactory<AnalysisDetailViewModel>(context) { app ->
                ServiceLocator.provideAnalysisDetailViewModel(
                    context = app,
                    dimension = dimension,
                    dimensionId = dimensionId,
                    startDate = startDate,
                    endDate = endDate,
                    resolvedDb = resolvedDb,
                    transactionAnalyticsDao = transactionAnalyticsDao,
                    dispatcherProvider = dispatcherProvider,
                )
            }

        fun createSearchFactory(
            context: Context? = null,
            initialCategoryId: Int? = null,
            initialDateMillis: Long? = null,
            initialQuery: String? = null,
            transactionRepository: ITransactionRepository? = null,
            accountRepository: IAccountRepository? = null,
            categoryRepository: ICategoryRepository? = null,
            tagRepository: ITagRepository? = null,
            dispatcherProvider: DispatcherProvider? = null,
        ): ViewModelProvider.Factory =
            createAssistedFactory<SearchViewModel>(context) { app ->
                ServiceLocator.provideSearchViewModel(
                    context = app,
                    initialCategoryId = initialCategoryId,
                    initialDateMillis = initialDateMillis,
                    initialQuery = initialQuery,
                    transactionRepository = transactionRepository,
                    accountRepository = accountRepository,
                    categoryRepository = categoryRepository,
                    tagRepository = tagRepository,
                    dispatcherProvider = dispatcherProvider,
                )
            }

        fun createBatchAnalysisFactory(
            context: Context? = null,
            nerExtractorProvider: (() -> SmsEntityExtractor)? = null,
            classifier: SmsClassifier? = null,
            dispatcherProvider: DispatcherProvider? = null,
        ): ViewModelProvider.Factory =
            createAssistedFactory<BatchAnalysisViewModel>(context) { app ->
                ServiceLocator.provideBatchAnalysisViewModel(
                    context = context ?: app,
                    nerExtractorProvider = nerExtractorProvider,
                    classifier = classifier,
                    dispatcherProvider = dispatcherProvider,
                )
            }

        /**
         * Creates a [ViewModelProvider.Factory] supporting ViewModels registered in [FinlightViewModelFactory].
         *
         * @param context Optional context fallback if [ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
         * is not provided in [CreationExtras].
         */
        fun create(context: Context? = null): ViewModelProvider.Factory {
            return FinlightViewModelFactory(context)
        }

        fun resolveApplication(
            extras: CreationExtras,
            context: Context?,
        ): Application {
            val fromExtras = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as? Application
            if (fromExtras != null) return fromExtras

            var current = context
            while (current != null) {
                if (current is Application) return current
                val appCtx = current.applicationContext
                if (appCtx is Application) return appCtx
                current = (current as? ContextWrapper)?.baseContext
            }

            throw IllegalArgumentException(
                "Application not found in CreationExtras or FinlightViewModelFactory constructor",
            )
        }
    }
}
