package io.pm.finlight.domain.usecase

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import io.pm.finlight.utils.IWorkScheduler
import org.junit.Before
import org.junit.Test

class ScheduleRecurringTransactionWorkerUseCaseTest {
    private val workScheduler: IWorkScheduler = mockk(relaxed = true)
    private lateinit var useCase: DefaultScheduleRecurringTransactionWorkerUseCase

    @Before
    fun setup() {
        every { workScheduler.scheduleRecurringTransactionWorker() } just runs
        useCase = DefaultScheduleRecurringTransactionWorkerUseCase(workScheduler)
    }

    @Test
    fun `invoke calls workScheduler scheduleRecurringTransactionWorker`() {
        useCase()
        verify(exactly = 1) { workScheduler.scheduleRecurringTransactionWorker() }
    }
}
