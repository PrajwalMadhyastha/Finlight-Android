package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.repository.ITripRepository
import io.pm.finlight.di.FinlightViewModelFactory
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
    application: Application,
    tripId: Int,
    tagId: Int,
    resolvedDb: AppDatabase? = null,
    tripRepository: ITripRepository? = null,
    transactionRepository: ITransactionRepository? = null,
    dispatcherProvider: DispatcherProvider? = null,
) : ViewModelProvider.Factory by FinlightViewModelFactory.createTripDetailFactory(
        context = application,
        tripId = tripId,
        tagId = tagId,
        resolvedDb = resolvedDb,
        tripRepository = tripRepository,
        transactionRepository = transactionRepository,
        dispatcherProvider = dispatcherProvider,
    )
