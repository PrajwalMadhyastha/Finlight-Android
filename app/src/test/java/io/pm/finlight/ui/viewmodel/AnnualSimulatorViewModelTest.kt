package io.pm.finlight.ui.viewmodel

import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.SettingsRepository
import io.pm.finlight.TransactionRepository
import io.pm.finlight.TransactionType
import io.pm.finlight.utils.TimeProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.anyString
import io.pm.finlight.anyObject
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class AnnualSimulatorViewModelTest : BaseViewModelTest() {
    @Mock
    private lateinit var transactionRepository: TransactionRepository

    @Mock
    private lateinit var settingsRepository: SettingsRepository

    private lateinit var viewModel: AnnualSimulatorViewModel

    private val testCalendar =
        Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.JUNE)
            set(Calendar.DAY_OF_MONTH, 15)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    private val mockTimeProvider =
        object : TimeProvider {
            override fun now(): Calendar = testCalendar.clone() as Calendar
        }

    @Before
    override fun setup() {
        super.setup()

        // Mock SettingsRepository
        val mockBudgets = mapOf(1 to 1000f, 2 to 1000f, 3 to 1000f, 4 to 1000f, 5 to 1000f, 6 to 1000f)
        runTest {
            `when`(settingsRepository.getOverallBudgetsForYear(2026)).thenReturn(mockBudgets)
        }
        `when`(settingsRepository.getSimulatorPrivacyModeEnabled()).thenReturn(flowOf(false))

        // Mock TransactionRepository
        `when`(transactionRepository.getIncomeTransactionsForRange(anyLong(), anyLong(), anyString(), anyObject(), anyObject()))
            .thenReturn(flowOf(emptyList()))
    }

    private fun createViewModel() {
        viewModel =
            AnnualSimulatorViewModel(
                transactionRepository = transactionRepository,
                settingsRepository = settingsRepository,
                timeProvider = mockTimeProvider
            )
    }

    @Test
    fun `initialization loads base annual budget correctly`() =
        runTest {
            createViewModel()

            // existingBudgets has 6 months with 1000f. The max month is 6 with 1000f.
            // It should extrapolate 1000f for the remaining 6 months.
            // Total should be 12000.
            val baseBudget = viewModel.baseAnnualBudget.first()
            assertEquals(12000.0, baseBudget, 0.0)
        }

    @Test
    fun `initialization loads base annual income correctly`() =
        runTest {
            createViewModel()

            // YTD income is empty list -> 0.0
            val baseIncome = viewModel.baseAnnualIncome.first()
            assertEquals(0.0, baseIncome, 0.0)
        }

    @Test
    fun `initialization calculates projected annual income correctly`() =
        runTest {
            val incomeTxn =
                io.pm.finlight.TransactionDetails(
                    transaction = io.pm.finlight.Transaction(id = 1, description = "Salary", amount = 5000.0, transactionType = TransactionType.INCOME, date = 100L, accountId = 1, categoryId = null, notes = null),
                    images = emptyList(),
                    accountName = "Bank",
                    categoryName = "Salary",
                    categoryIconKey = null,
                    categoryColorKey = null,
                    tagNames = null
                )
            `when`(transactionRepository.getIncomeTransactionsForRange(anyLong(), anyLong(), anyString(), anyObject(), anyObject()))
                .thenReturn(flowOf(listOf(incomeTxn)))

            createViewModel()

            // It should calculate (5000 / 6) * 12 = 10000.0 (since current month index is 6)
            val baseIncome = viewModel.baseAnnualIncome.first()
            assertEquals(10000.0, baseIncome, 0.0)
        }

    @Test
    fun `addLifeEvent adds event correctly`() =
        runTest {
            createViewModel()

            viewModel.addLifeEvent("Buy Car", 5000.0, false, 5)

            val events = viewModel.lifeEvents.first()
            assertEquals(1, events.size)
            assertEquals("Buy Car", events[0].name)
            assertEquals(5000.0, events[0].amount, 0.0)
        }

    @Test
    fun `removeLifeEvent removes event correctly`() =
        runTest {
            createViewModel()

            viewModel.addLifeEvent("Buy Car", 5000.0, false, 5)
            val addedEvent = viewModel.lifeEvents.first().first()

            viewModel.removeLifeEvent(addedEvent.id)
            val events = viewModel.lifeEvents.first()
            assertTrue(events.isEmpty())
        }

    @Test
    fun `calculateProjectedAnnualImpact calculates correctly for one-time event`() =
        runTest {
            createViewModel()

            viewModel.addLifeEvent("Vacation", 2000.0, false, 7)
            val impact = viewModel.calculateProjectedAnnualImpact()

            assertEquals(2000.0, impact, 0.0)
        }

    @Test
    fun `calculateProjectedAnnualImpact calculates correctly for recurring event`() =
        runTest {
            createViewModel()

            // Start month 6 (July, 0-indexed). It will be active for 12 - 6 = 6 months
            viewModel.addLifeEvent("New Subscription", 50.0, true, 6)
            val impact = viewModel.calculateProjectedAnnualImpact()

            assertEquals(300.0, impact, 0.0)
        }

    @Test
    fun `togglePrivacyMode toggles simulator privacy mode`() =
        runTest {
            createViewModel()

            viewModel.togglePrivacyMode()

            // Initially false, so toggling should save true
            verify(settingsRepository).saveSimulatorPrivacyModeEnabled(true)
        }
}
