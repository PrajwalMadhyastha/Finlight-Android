package io.pm.finlight.ui.viewmodel

import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.SettingsRepository
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionDetails
import io.pm.finlight.TransactionRepository
import io.pm.finlight.TransactionType
import io.pm.finlight.anyObject
import io.pm.finlight.utils.TimeProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.anyString
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
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
            `when`(settingsRepository.getOverallBudgetsForYear(2025)).thenReturn(mapOf(1 to 500f))
            `when`(settingsRepository.getOverallBudgetsForYear(2027)).thenReturn(mapOf(1 to 1200f))
        }
        `when`(settingsRepository.getSimulatorPrivacyModeEnabled()).thenReturn(flowOf(false))

        // Mock TransactionRepository
        `when`(transactionRepository.getIncomeTransactionsForRange(anyLong(), anyLong(), anyString(), anyObject(), anyObject()))
            .thenReturn(flowOf(emptyList()))
    }

    private fun createViewModel(simulationYear: Int? = null) {
        viewModel =
            AnnualSimulatorViewModel(
                transactionRepository = transactionRepository,
                settingsRepository = settingsRepository,
                timeProvider = mockTimeProvider,
                simulationYear = simulationYear
            )
    }

    private fun createIncomeTxn(amount: Double): TransactionDetails =
        TransactionDetails(
            transaction =
                Transaction(
                    id = 1,
                    description = "Salary",
                    amount = amount,
                    transactionType = TransactionType.INCOME,
                    date = 100L,
                    accountId = 1,
                    categoryId = null,
                    notes = null
                ),
            images = emptyList(),
            accountName = "Bank",
            categoryName = "Salary",
            categoryIconKey = null,
            categoryColorKey = null,
            tagNames = null
        )

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
    fun `initialization calculates projected annual income correctly for current year`() =
        runTest {
            `when`(transactionRepository.getIncomeTransactionsForRange(anyLong(), anyLong(), anyString(), anyObject(), anyObject()))
                .thenReturn(flowOf(listOf(createIncomeTxn(5000.0))))

            createViewModel()

            // Current calendar month is June (index 6).
            // It should calculate (5000 / 6) * 12 = 10000.0
            val baseIncome = viewModel.baseAnnualIncome.first()
            assertEquals(10000.0, baseIncome, 0.0)
        }

    @Test
    fun `simulating past years does not extrapolate using the current calendar month`() =
        runTest {
            `when`(transactionRepository.getIncomeTransactionsForRange(anyLong(), anyLong(), anyString(), anyObject(), anyObject()))
                .thenReturn(flowOf(listOf(createIncomeTxn(5000.0))))

            // Simulate year 2025 when current calendar year is 2026
            createViewModel(simulationYear = 2025)

            // Past year should use full actual ytdIncome (5000.0) without monthly extrapolation
            assertEquals(2025, viewModel.currentYear.first())
            val baseIncome = viewModel.baseAnnualIncome.first()
            assertEquals(5000.0, baseIncome, 0.0)
        }

    @Test
    fun `simulating past years with zero income returns zero`() =
        runTest {
            createViewModel(simulationYear = 2025)

            assertEquals(0.0, viewModel.baseAnnualIncome.first(), 0.0)
        }

    @Test
    fun `simulating future years uses actual income without extrapolation`() =
        runTest {
            `when`(transactionRepository.getIncomeTransactionsForRange(anyLong(), anyLong(), anyString(), anyObject(), anyObject()))
                .thenReturn(flowOf(listOf(createIncomeTxn(3000.0))))

            // Simulate year 2027 when current calendar year is 2026
            createViewModel(simulationYear = 2027)

            assertEquals(2027, viewModel.currentYear.first())
            val baseIncome = viewModel.baseAnnualIncome.first()
            assertEquals(3000.0, baseIncome, 0.0)
        }

    @Test
    fun `setYear updates year and reloads base annual metrics`() =
        runTest {
            `when`(transactionRepository.getIncomeTransactionsForRange(anyLong(), anyLong(), anyString(), anyObject(), anyObject()))
                .thenReturn(flowOf(listOf(createIncomeTxn(6000.0))))

            createViewModel(simulationYear = 2026)
            assertEquals(12000.0, viewModel.baseAnnualIncome.first(), 0.0)

            // Switch to past year 2025
            viewModel.setYear(2025)
            assertEquals(2025, viewModel.currentYear.first())
            assertEquals(6000.0, viewModel.baseAnnualIncome.first(), 0.0)

            // Calling with same year should be a no-op
            viewModel.setYear(2025)
            assertEquals(2025, viewModel.currentYear.first())
        }

    @Test
    fun `setYear clears life events and validation errors`() =
        runTest {
            createViewModel(simulationYear = 2026)

            viewModel.addLifeEvent("Buy Car", 5000.0, false, 5)
            assertEquals(1, viewModel.lifeEvents.first().size)

            viewModel.addLifeEvent("", 1000.0, false, 0)
            assertEquals("Event name cannot be blank", viewModel.validationError.first())

            viewModel.setYear(2025)
            assertTrue(viewModel.lifeEvents.first().isEmpty())
            assertNull(viewModel.validationError.first())
        }

    @Test
    fun `addLifeEvent with valid parameters adds event and returns success`() =
        runTest {
            createViewModel()

            val result = viewModel.addLifeEvent("Buy Car", 5000.0, false, 5)

            assertTrue(result.isSuccess)
            assertNull(viewModel.validationError.first())
            val events = viewModel.lifeEvents.first()
            assertEquals(1, events.size)
            assertEquals("Buy Car", events[0].name)
            assertEquals(5000.0, events[0].amount, 0.0)
            assertEquals(5, events[0].startMonthIndex)
        }

    @Test
    fun `addLifeEvent with blank name returns failure and updates validation error`() =
        runTest {
            createViewModel()

            val resultEmpty = viewModel.addLifeEvent("", 5000.0, false, 5)
            assertTrue(resultEmpty.isFailure)
            assertEquals("Event name cannot be blank", viewModel.validationError.first())
            assertTrue(viewModel.lifeEvents.first().isEmpty())

            val resultWhitespace = viewModel.addLifeEvent("   ", 5000.0, false, 5)
            assertTrue(resultWhitespace.isFailure)
            assertEquals("Event name cannot be blank", viewModel.validationError.first())
            assertTrue(viewModel.lifeEvents.first().isEmpty())
        }

    @Test
    fun `addLifeEvent with non-positive amount returns failure and updates validation error`() =
        runTest {
            createViewModel()

            val resultZero = viewModel.addLifeEvent("Event", 0.0, false, 0)
            assertTrue(resultZero.isFailure)
            assertEquals("Amount must be greater than 0", viewModel.validationError.first())

            val resultNegative = viewModel.addLifeEvent("Event", -100.0, false, 0)
            assertTrue(resultNegative.isFailure)
            assertEquals("Amount must be greater than 0", viewModel.validationError.first())

            val resultNan = viewModel.addLifeEvent("Event", Double.NaN, false, 0)
            assertTrue(resultNan.isFailure)
            assertEquals("Amount must be greater than 0", viewModel.validationError.first())

            val resultInfinity = viewModel.addLifeEvent("Event", Double.POSITIVE_INFINITY, false, 0)
            assertTrue(resultInfinity.isFailure)
            assertEquals("Amount must be greater than 0", viewModel.validationError.first())

            assertTrue(viewModel.lifeEvents.first().isEmpty())
        }

    @Test
    fun `addLifeEvent with invalid month index returns failure and updates validation error`() =
        runTest {
            createViewModel()

            val resultUnder = viewModel.addLifeEvent("Event", 100.0, false, -1)
            assertTrue(resultUnder.isFailure)
            assertEquals("Start month index must be between 0 and 11", viewModel.validationError.first())

            val resultOver = viewModel.addLifeEvent("Event", 100.0, false, 12)
            assertTrue(resultOver.isFailure)
            assertEquals("Start month index must be between 0 and 11", viewModel.validationError.first())

            assertTrue(viewModel.lifeEvents.first().isEmpty())
        }

    @Test
    fun `subsequent valid addLifeEvent clears previous validation error`() =
        runTest {
            createViewModel()

            // Trigger error
            viewModel.addLifeEvent("", 100.0, false, 0)
            assertEquals("Event name cannot be blank", viewModel.validationError.first())

            // Follow with valid event
            val result = viewModel.addLifeEvent("Valid Event", 100.0, false, 0)
            assertTrue(result.isSuccess)
            assertNull(viewModel.validationError.first())
            assertEquals(1, viewModel.lifeEvents.first().size)
        }

    @Test
    fun `clearValidationError clears error state`() =
        runTest {
            createViewModel()

            viewModel.addLifeEvent("", 100.0, false, 0)
            assertEquals("Event name cannot be blank", viewModel.validationError.first())

            viewModel.clearValidationError()
            assertNull(viewModel.validationError.first())
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
    fun `calculateProjectedAnnualImpact calculates correctly for recurring event across boundary months`() =
        runTest {
            createViewModel()

            // Start month 0 (Jan): Active for 12 months -> 100 * 12 = 1200
            viewModel.addLifeEvent("Gym Jan", 100.0, true, 0)
            assertEquals(1200.0, viewModel.calculateProjectedAnnualImpact(), 0.0)

            // Start month 11 (Dec): Active for 1 month -> 50 * 1 = 50
            viewModel.addLifeEvent("Gym Dec", 50.0, true, 11)
            assertEquals(1250.0, viewModel.calculateProjectedAnnualImpact(), 0.0)

            // Start month 6 (July): Active for 6 months -> 200 * 6 = 1200
            viewModel.addLifeEvent("Gym Jul", 200.0, true, 6)
            assertEquals(2450.0, viewModel.calculateProjectedAnnualImpact(), 0.0)
        }

    @Test
    fun `togglePrivacyMode toggles simulator privacy mode`() =
        runTest {
            createViewModel()

            viewModel.togglePrivacyMode()

            verify(settingsRepository).saveSimulatorPrivacyModeEnabled(true)
        }
}
