package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.repository.ITripRepository
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.utils.DispatcherProvider

@Deprecated(
    message = "Migrate to FinlightViewModelFactory or ServiceLocator. Individual factories will be removed in a future release.",
    replaceWith =
        ReplaceWith(
            "FinlightViewModelFactory.createTripDetailFactory(application, tripId, tagId)",
            "io.pm.finlight.di.FinlightViewModelFactory",
        ),
)
class TripDetailViewModelFactory(
    private val application: Application,
    private val tripId: Int,
    private val tagId: Int,
    private val resolvedDb: AppDatabase? = null,
    private val tripRepository: ITripRepository? = null,
    private val transactionRepository: ITransactionRepository? = null,
    private val dispatcherProvider: DispatcherProvider? = null,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return create(modelClass, CreationExtras.Empty)
    }

    override fun <T : ViewModel> create(
        modelClass: Class<T>,
        extras: CreationExtras,
    ): T {
        if (modelClass.isAssignableFrom(TripDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ServiceLocator.provideTripDetailViewModel(
                context = application,
                tripId = tripId,
                tagId = tagId,
                resolvedDb = resolvedDb,
                tripRepository = tripRepository,
                transactionRepository = transactionRepository,
                dispatcherProvider = dispatcherProvider,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
