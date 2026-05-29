package io.pm.finlight.utils

import java.text.SimpleDateFormat
import java.util.Locale

object FormatUtils {
    
    private val _defaultDateFormatter = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat {
            return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        }
    }
    
    private val _monthYearFormatter = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat {
            return SimpleDateFormat("yyyy-MM", Locale.getDefault())
        }
    }

    private val _displayDateFormatter = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat {
            return SimpleDateFormat("dd MMM, yyyy", Locale.getDefault())
        }
    }

    private val _timestampFormatter = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat {
            return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        }
    }

    private val _currencyFormatter = object : ThreadLocal<java.text.NumberFormat>() {
        override fun initialValue(): java.text.NumberFormat {
            return java.text.NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        }
    }
    
    val defaultDateFormatter: SimpleDateFormat
        get() = _defaultDateFormatter.get()!!
        
    val monthYearFormatter: SimpleDateFormat
        get() = _monthYearFormatter.get()!!

    val displayDateFormatter: SimpleDateFormat
        get() = _displayDateFormatter.get()!!

    val timestampFormatter: SimpleDateFormat
        get() = _timestampFormatter.get()!!

    val currencyFormatter: java.text.NumberFormat
        get() = _currencyFormatter.get()!!
}
