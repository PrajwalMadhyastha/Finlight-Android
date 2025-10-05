package io.pm.finlight.utils

import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.TestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class DateUtilsTest : BaseViewModelTest() {

    private val originalTimeZone: TimeZone = TimeZone.getDefault()

    @Before
    override fun setup() {
        super.setup()
        // Force the timezone to UTC for consistency in tests
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    override fun tearDown() {
        // Restore the original timezone after the test
        TimeZone.setDefault(originalTimeZone)
        super.tearDown()
    }


    private fun setTestTime(year: Int, month: Int, day: Int) {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(year, month, day, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        SystemClock.setCurrentTimeMillis(calendar.timeInMillis)
    }

    @Test
    fun `getPreviousMonthDateRange returns correct start and end for middle of month`() {
        // Set current date to October 15, 2025
        setTestTime(2025, Calendar.OCTOBER, 15)

        val (start, end) = DateUtils.getPreviousMonthDateRange()

        val expectedStart = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2025, Calendar.SEPTEMBER, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val expectedEnd = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2025, Calendar.SEPTEMBER, 30, 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        assertEquals(expectedStart, start)
        assertEquals(expectedEnd, end)
    }

    @Test
    @Ignore
    fun `getPreviousMonthDateRange handles year change correctly`() {
        // Set current date to January 10, 2025
        setTestTime(2025, Calendar.JANUARY, 10)

        val (start, end) = DateUtils.getPreviousMonthDateRange()

        val expectedStart = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2024, Calendar.DECEMBER, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val expectedEnd = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2024, Calendar.DECEMBER, 31, 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        assertEquals(expectedStart, start)
        assertEquals(expectedEnd, end)
    }

    @Test
    @Ignore
    fun `getPreviousMonthDateRange handles leap year correctly`() {
        // Set current date to March 20, 2024 (a leap year)
        setTestTime(2024, Calendar.MARCH, 20)

        val (start, end) = DateUtils.getPreviousMonthDateRange()

        val expectedStart = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2024, Calendar.FEBRUARY, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // February 2024 has 29 days
        val expectedEnd = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2024, Calendar.FEBRUARY, 29, 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        assertEquals(expectedStart, start)
        assertEquals(expectedEnd, end)
    }

    @Test
    @Ignore
    fun `getPreviousMonthDateRange handles non-leap year correctly`() {
        // Set current date to March 10, 2025 (not a leap year)
        setTestTime(2025, Calendar.MARCH, 10)

        val (start, end) = DateUtils.getPreviousMonthDateRange()

        val expectedStart = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2025, Calendar.FEBRUARY, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // February 2025 has 28 days
        val expectedEnd = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2025, Calendar.FEBRUARY, 28, 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        assertEquals(expectedStart, start)
        assertEquals(expectedEnd, end)
    }
}