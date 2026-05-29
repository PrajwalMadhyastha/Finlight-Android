package io.pm.finlight.utils

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object FormatUtils {
    private val formatters = ConcurrentHashMap<Pair<String, Locale>, ThreadLocal<SimpleDateFormat>>()

    private class ThreadLocalFormatter(private val pattern: String, private val locale: Locale) : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat(pattern, locale)
    }

    private class ThreadLocalCurrencyFormatter(private val locale: Locale) : ThreadLocal<java.text.NumberFormat>() {
        override fun initialValue() = java.text.NumberFormat.getCurrencyInstance(locale)
    }

    fun getFormatter(
        pattern: String,
        locale: Locale = Locale.getDefault(),
    ): SimpleDateFormat {
        return formatters.getOrPut(pattern to locale) {
            ThreadLocalFormatter(pattern, locale)
        }.get()!!
    }

    private val _defaultDateFormatter = ThreadLocalFormatter("yyyy-MM-dd", Locale.getDefault())
    private val _monthYearFormatter = ThreadLocalFormatter("yyyy-MM", Locale.getDefault())
    private val _shortMonthFormatter = ThreadLocalFormatter("LLL", Locale.getDefault())
    private val _monthYearDisplayFormatter = ThreadLocalFormatter("LLLL yyyy", Locale.getDefault())
    private val _fullDateTimeFormatter = ThreadLocalFormatter("EEE, dd MMMM yy, h:mm a", Locale.getDefault())
    private val _shortYearDateFormatter = ThreadLocalFormatter("dd MMM, yy", Locale.getDefault())
    private val _isoDateFormatter = ThreadLocalFormatter("yyyy-MM-dd", Locale.getDefault())
    private val _dateTimeFormatter = ThreadLocalFormatter("dd MMM yyyy, hh:mm a", Locale.getDefault())
    private val _longDateFormatter = ThreadLocalFormatter("dd MMMM, yyyy", Locale.getDefault())
    private val _dayOfWeekFormatter = ThreadLocalFormatter("EEEE", Locale.getDefault())
    private val _displayDateFormatter = ThreadLocalFormatter("dd MMM, yyyy", Locale.getDefault())
    private val _timestampFormatter = ThreadLocalFormatter("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val _currencyFormatter = ThreadLocalCurrencyFormatter(Locale("en", "IN"))

    val defaultDateFormatter: SimpleDateFormat
        get() = _defaultDateFormatter.get()!!

    val monthYearFormatter: SimpleDateFormat
        get() = _monthYearFormatter.get()!!

    val shortMonthFormatter: SimpleDateFormat
        get() = _shortMonthFormatter.get()!!

    val monthYearDisplayFormatter: SimpleDateFormat
        get() = _monthYearDisplayFormatter.get()!!

    val fullDateTimeFormatter: SimpleDateFormat
        get() = _fullDateTimeFormatter.get()!!

    val shortYearDateFormatter: SimpleDateFormat
        get() = _shortYearDateFormatter.get()!!

    val isoDateFormatter: SimpleDateFormat
        get() = _isoDateFormatter.get()!!

    val dateTimeFormatter: SimpleDateFormat
        get() = _dateTimeFormatter.get()!!

    val longDateFormatter: SimpleDateFormat
        get() = _longDateFormatter.get()!!

    val dayOfWeekFormatter: SimpleDateFormat
        get() = _dayOfWeekFormatter.get()!!

    val displayDateFormatter: SimpleDateFormat
        get() = _displayDateFormatter.get()!!

    val timestampFormatter: SimpleDateFormat
        get() = _timestampFormatter.get()!!

    val currencyFormatter: java.text.NumberFormat
        get() = _currencyFormatter.get()!!
}
