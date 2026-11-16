package io.pm.finlight.workers

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import io.mockk.mockk
import io.pm.finlight.ISettingsRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.ITravelSettingsRepository
import io.pm.finlight.utils.IWorkScheduler

object SummaryWorkerTestFixtures {
    inline fun <reified T : ListenableWorker> createWorker(
        context: Context,
        runAttemptCount: Int = 0,
    ): T =
        TestListenableWorkerBuilder<T>(context)
            .setRunAttemptCount(runAttemptCount)
            .build()

    fun mockTransactionRepository(): ITransactionRepository = mockk(relaxed = true)

    fun mockWorkScheduler(): IWorkScheduler = mockk(relaxed = true)

    fun mockSettingsRepository(): ISettingsRepository = mockk(relaxed = true)

    fun mockTravelSettingsRepository(): ITravelSettingsRepository = mockk(relaxed = true)
}
