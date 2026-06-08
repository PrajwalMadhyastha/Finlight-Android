package io.pm.finlight.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class FormatUtilsTest {

    @Test
    fun `getFormatter returns correct SimpleDateFormat`() {
        val formatter = FormatUtils.getFormatter("yyyy-MM-dd", Locale.US)
        assertNotNull(formatter)
        val cal =
            Calendar.getInstance().apply {
                set(2023, Calendar.JANUARY, 15)
            }
        assertEquals("2023-01-15", formatter.format(cal.time))
    }

    @Test
    fun `defaultDateFormatter returns correct format`() {
        val formatter = FormatUtils.defaultDateFormatter
        val cal =
            Calendar.getInstance().apply {
                set(2023, Calendar.JANUARY, 15)
            }
        assertEquals("2023-01-15", formatter.format(cal.time))
    }

    @Test
    fun `monthYearFormatter returns correct format`() {
        val formatter = FormatUtils.monthYearFormatter
        val cal =
            Calendar.getInstance().apply {
                set(2023, Calendar.JANUARY, 15)
            }
        assertEquals("2023-01", formatter.format(cal.time))
    }

    @Test
    fun `shortMonthFormatter returns correct format`() {
        val formatter = FormatUtils.shortMonthFormatter
        val cal =
            Calendar.getInstance().apply {
                set(2023, Calendar.JANUARY, 15)
            }
        assertEquals("Jan", formatter.format(cal.time))
    }

    @Test
    fun `monthYearDisplayFormatter returns correct format`() {
        val formatter = FormatUtils.monthYearDisplayFormatter
        val cal =
            Calendar.getInstance().apply {
                set(2023, Calendar.JANUARY, 15)
            }
        assertEquals("January 2023", formatter.format(cal.time))
    }

    @Test
    fun `fullDateTimeFormatter returns correct format`() {
        val formatter = FormatUtils.fullDateTimeFormatter
        val cal =
            Calendar.getInstance().apply {
                set(2023, Calendar.JANUARY, 15, 14, 30) // Jan 15 2023, Sun, 14:30
            }
        assertEquals("Sun, 15 January 23, 2:30 PM", formatter.format(cal.time))
    }

    @Test
    fun `shortYearDateFormatter returns correct format`() {
        val formatter = FormatUtils.shortYearDateFormatter
        val cal =
            Calendar.getInstance().apply {
                set(2023, Calendar.JANUARY, 15)
            }
        assertEquals("15 Jan, 23", formatter.format(cal.time))
    }

    @Test
    fun `isoDateFormatter returns correct format`() {
        val formatter = FormatUtils.isoDateFormatter
        val cal =
            Calendar.getInstance().apply {
                set(2023, Calendar.JANUARY, 15)
            }
        assertEquals("2023-01-15", formatter.format(cal.time))
    }

    @Test
    fun `dateTimeFormatter returns correct format`() {
        val formatter = FormatUtils.dateTimeFormatter
        val cal =
            Calendar.getInstance().apply {
                set(2023, Calendar.JANUARY, 15, 14, 30)
            }
        assertEquals("15 Jan 2023, 02:30 PM", formatter.format(cal.time))
    }

    @Test
    fun `longDateFormatter returns correct format`() {
        val formatter = FormatUtils.longDateFormatter
        val cal =
            Calendar.getInstance().apply {
                set(2023, Calendar.JANUARY, 15)
            }
        assertEquals("15 January, 2023", formatter.format(cal.time))
    }

    @Test
    fun `dayOfWeekFormatter returns correct format`() {
        val formatter = FormatUtils.dayOfWeekFormatter
        val cal =
            Calendar.getInstance().apply {
                set(2023, Calendar.JANUARY, 15) // Sunday
            }
        assertEquals("Sunday", formatter.format(cal.time))
    }

    @Test
    fun `displayDateFormatter returns correct format`() {
        val formatter = FormatUtils.displayDateFormatter
        val cal =
            Calendar.getInstance().apply {
                set(2023, Calendar.JANUARY, 15)
            }
        assertEquals("15 Jan, 2023", formatter.format(cal.time))
    }

    @Test
    fun `timestampFormatter returns correct format`() {
        val formatter = FormatUtils.timestampFormatter
        val cal =
            Calendar.getInstance().apply {
                set(2023, Calendar.JANUARY, 15, 14, 30, 45)
            }
        assertEquals("2023-01-15 14:30:45", formatter.format(cal.time))
    }

    @Test
    fun `currencyFormatter returns correct format for India locale`() {
        val formatter = FormatUtils.currencyFormatter
        val formatted = formatter.format(1234.56)
        assertNotNull(formatted)
        assertTrue(formatted.contains("1,234.56") || formatted.contains("1234.56") || formatted.contains("1,234.56"))
    }
}
