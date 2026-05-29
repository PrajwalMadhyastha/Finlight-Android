package io.pm.finlight.utils

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object FormatUtils {
    private val formatters = ConcurrentHashMap<Pair<String, Locale>, ThreadLocal<SimpleDateFormat>>()

    fun getFormatter(
        pattern: String,
        locale: Locale = Locale.getDefault(),
    ): SimpleDateFormat {
        return formatters.getOrPut(pattern to locale) {
            object : ThreadLocal<SimpleDateFormat>() {
                override fun initialValue(): SimpleDateFormat {
                    return SimpleDateFormat(pattern, locale)
                }
            }
        }.get()!!
    }

    private val _defaultDateFormatter =
        object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat {
                return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            }
        }

    private val _monthYearFormatter =
        object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat {
                return SimpleDateFormat("yyyy-MM", Locale.getDefault())
            }
        }

    private val _shortMonthFormatter =
        object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat {
                return SimpleDateFormat("LLL", Locale.getDefault())
            }
        }

    private val _monthYearDisplayFormatter =
        object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat {
                return SimpleDateFormat("LLLL yyyy", Locale.getDefault())
            }
        }

    private val _fullDateTimeFormatter =
        object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat {
                return SimpleDateFormat("EEE, dd MMMM yy, h:mm a", Locale.getDefault())
            }
        }

    private val _shortYearDateFormatter =
        object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat {
                return SimpleDateFormat("dd MMM, yy", Locale.getDefault())
            }
        }

    private val _isoDateFormatter =
        object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat {
                return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            }
        }

    private val _dateTimeFormatter =
        object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat {
                return SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            }
        }

    private val _longDateFormatter =
        object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat {
                return SimpleDateFormat("dd MMMM, yyyy", Locale.getDefault())
            }
        }

    private val _dayOfWeekFormatter =
        object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat {
                return SimpleDateFormat("EEEE", Locale.getDefault())
            }
        }

    private val _displayDateFormatter =
        object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat {
                return SimpleDateFormat("dd MMM, yyyy", Locale.getDefault())
            }
        }

    private val _timestampFormatter =
        object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat {
                return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            }
        }

    private val _currencyFormatter =
        object : ThreadLocal<java.text.NumberFormat>() {
            override fun initialValue(): java.text.NumberFormat {
                return java.text.NumberFormat.getCurrencyInstance(Locale("en", "IN"))
            }
        }

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
