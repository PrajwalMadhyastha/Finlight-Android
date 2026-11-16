package io.pm.finlight.domain.usecase

import io.pm.finlight.utils.IWorkScheduler

/**
 * UseCase to schedule the recurring transaction background worker.
 *
 * Encapsulates the interaction with [IWorkScheduler] so that
 * ViewModels do not need to hold an [android.app.Application] reference.
 */
fun interface ScheduleRecurringTransactionWorkerUseCase {
    operator fun invoke()
}

/**
 * Default implementation of [ScheduleRecurringTransactionWorkerUseCase] delegating to [IWorkScheduler].
 */
class DefaultScheduleRecurringTransactionWorkerUseCase(
    private val workScheduler: IWorkScheduler,
) : ScheduleRecurringTransactionWorkerUseCase {
    override operator fun invoke() {
        workScheduler.scheduleRecurringTransactionWorker()
    }
}
