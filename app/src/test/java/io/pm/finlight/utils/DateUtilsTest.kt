package io.pm.finlight.utils

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.TestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class DateUtilsTest : BaseViewModelTest() {

    private val originalTimeZone: TimeZone = TimeZone.getDefault()
    private val testTimeZone: TimeZone = TimeZone.getTimeZone("Asia/Kolkata") // IST

    @Before
    override fun setup() {
        super.setup()
        // Force the timezone to IST for consistency in tests
        TimeZone.setDefault(testTimeZone)
    }

    @After
    override fun tearDown() {
        // Restore the original timezone after the test
        TimeZone.setDefault(originalTimeZone)
        super.tearDown()
    }

    private fun getTestTime(year: Int, month: Int, day: Int): Long {
        return Calendar.getInstance(testTimeZone).apply {
            set(year, month, day, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun `getPreviousMonthDateRange returns correct start and end for middle of month`() {
        // Arrange: Current date is October 15, 2025
        val currentTime = getTestTime(2025, Calendar.OCTOBER, 15)

        // Act
        val (start, end) = DateUtils.getPreviousMonthDateRange(currentTime)

        // Assert: Previous month is September 2025
        val expectedStart = Calendar.getInstance(testTimeZone).apply {
            set(2025, Calendar.SEPTEMBER, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val expectedEnd = Calendar.getInstance(testTimeZone).apply {
            set(2025, Calendar.SEPTEMBER, 30, 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        assertEquals(expectedStart, start)
        assertEquals(expectedEnd, end)
    }

    @Test
    fun `getPreviousMonthDateRange handles year change correctly`() {
        // Arrange: Current date is January 10, 2025
        val currentTime = getTestTime(2025, Calendar.JANUARY, 10)

        // Act
        val (start, end) = DateUtils.getPreviousMonthDateRange(currentTime)

        // Assert: Previous month is December 2024
        val expectedStart = Calendar.getInstance(testTimeZone).apply {
            set(2024, Calendar.DECEMBER, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val expectedEnd = Calendar.getInstance(testTimeZone).apply {
            set(2024, Calendar.DECEMBER, 31, 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        assertEquals(expectedStart, start)
        assertEquals(expectedEnd, end)
    }

    @Test
    fun `getPreviousMonthDateRange handles leap year correctly`() {
        // Arrange: Current date is March 20, 2024 (a leap year)
        val currentTime = getTestTime(2024, Calendar.MARCH, 20)

        // Act
        val (start, end) = DateUtils.getPreviousMonthDateRange(currentTime)

        // Assert: Previous month is February 2024, which has 29 days
        val expectedStart = Calendar.getInstance(testTimeZone).apply {
            set(2024, Calendar.FEBRUARY, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val expectedEnd = Calendar.getInstance(testTimeZone).apply {
            set(2024, Calendar.FEBRUARY, 29, 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        assertEquals(expectedStart, start)
        assertEquals(expectedEnd, end)
    }

    @Test
    fun `getPreviousMonthDateRange handles non-leap year correctly`() {
        // Arrange: Current date is March 10, 2025 (not a leap year)
        val currentTime = getTestTime(2025, Calendar.MARCH, 10)

        // Act
        val (start, end) = DateUtils.getPreviousMonthDateRange(currentTime)

        // Assert: Previous month is February 2025, which has 28 days
        val expectedStart = Calendar.getInstance(testTimeZone).apply {
            set(2025, Calendar.FEBRUARY, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val expectedEnd = Calendar.getInstance(testTimeZone).apply {
            set(2025, Calendar.FEBRUARY, 28, 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        assertEquals(expectedStart, start)
        assertEquals(expectedEnd, end)
    }
}