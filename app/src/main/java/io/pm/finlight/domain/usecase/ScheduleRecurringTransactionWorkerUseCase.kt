package io.pm.finlight.domain.usecase

import android.content.Context
import io.pm.finlight.utils.ReminderManager

/**
 * UseCase to schedule the recurring transaction background worker.
 *
 * Encapsulates the interaction with [ReminderManager] and Android [Context] so that
 * ViewModels do not need to hold an [android.app.Application] reference.
 */
fun interface ScheduleRecurringTransactionWorkerUseCase {
    operator fun invoke()
}

/**
 * Default implementation of [ScheduleRecurringTransactionWorkerUseCase] delegating to [ReminderManager].
 */
class DefaultScheduleRecurringTransactionWorkerUseCase(
    private val context: Context,
) : ScheduleRecurringTransactionWorkerUseCase {
    override operator fun invoke() {
        ReminderManager.scheduleRecurringTransactionWorker(context)
    }
}
