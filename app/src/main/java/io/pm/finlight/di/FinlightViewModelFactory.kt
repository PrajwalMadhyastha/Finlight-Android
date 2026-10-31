package io.pm.finlight.di

import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/**
 * Centralized generic ViewModel factory provider for Finlight.
 *
 * Uses Jetpack ViewModel's [viewModelFactory] DSL to configure initializers
 * backed by centralized [ServiceLocator] dependency resolution.
 *
 * Migration & Deprecation Strategy:
 * Existing legacy ViewModelFactory classes are marked with @Deprecated pointing to
 * [FinlightViewModelFactory.create] and delegated to [ServiceLocator]. Remaining factories
 * can be systematically consolidated here by adding corresponding [initializer] blocks.
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
        }

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return delegate.create(modelClass, CreationExtras.Empty)
    }

    override fun <T : ViewModel> create(
        modelClass: Class<T>,
        extras: CreationExtras
    ): T {
        return delegate.create(modelClass, extras)
    }

    companion object {
        /**
         * Creates a [ViewModelProvider.Factory] supporting ViewModels registered in [FinlightViewModelFactory].
         *
         * @param context Optional context fallback if [ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
         * is not provided in [CreationExtras].
         */
        fun create(context: Context? = null): ViewModelProvider.Factory {
            return FinlightViewModelFactory(context)
        }

        private fun resolveApplication(
            extras: CreationExtras,
            context: Context?
        ): Application {
            return (extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as? Application)
                ?: (context as? Application)
                ?: (context?.applicationContext as? Application)
                ?: throw IllegalArgumentException(
                    "Application not found in CreationExtras or FinlightViewModelFactory constructor"
                )
        }
    }
}
