package io.pm.finlight.domain.usecase

import android.content.Context
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import io.mockk.verify
import io.pm.finlight.utils.ReminderManager
import org.junit.After
import org.junit.Before
import org.junit.Test

class ScheduleRecurringTransactionWorkerUseCaseTest {
    private val context: Context = mockk(relaxed = true)
    private lateinit var useCase: DefaultScheduleRecurringTransactionWorkerUseCase

    @Before
    fun setup() {
        mockkObject(ReminderManager)
        every { ReminderManager.scheduleRecurringTransactionWorker(any()) } just runs
        useCase = DefaultScheduleRecurringTransactionWorkerUseCase(context)
    }

    @After
    fun tearDown() {
        unmockkObject(ReminderManager)
    }

    @Test
    fun `invoke calls ReminderManager scheduleRecurringTransactionWorker`() {
        useCase()
        verify(exactly = 1) { ReminderManager.scheduleRecurringTransactionWorker(context) }
    }
}
