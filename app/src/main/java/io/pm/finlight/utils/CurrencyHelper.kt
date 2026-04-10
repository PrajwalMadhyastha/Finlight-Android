package io.pm.finlight.utils

import java.util.Currency
import java.util.Locale

data class CurrencyInfo(
    val countryName: String,
    val currencyCode: String, // e.g., USD
    val currencySymbol: String, // e.g., $
)

object CurrencyHelper {
    // A curated list of common currencies. A full list can be very long.
    val commonCurrencies: List<CurrencyInfo> by lazy {
        listOf(
            "United States" to "USD",
            "Eurozone" to "EUR",
            "Japan" to "JPY",
            "United Kingdom" to "GBP",
            "Australia" to "AUD",
            "Canada" to "CAD",
            "Switzerland" to "CHF",
            "China" to "CNY",
            "Sweden" to "SEK",
            "New Zealand" to "NZD",
            "Singapore" to "SGD",
            "Hong Kong" to "HKD",
            "Norway" to "NOK",
            "South Korea" to "KRW",
            "Turkey" to "TRY",
            "Russia" to "RUB",
            "India" to "INR",
            "Brazil" to "BRL",
            "South Africa" to "ZAR",
            "United Arab Emirates" to "AED",
            "Thailand" to "THB",
            "Malaysia" to "MYR",
            "Indonesia" to "IDR",
            "Vietnam" to "VND",
            "Philippines" to "PHP",
            "Mexico" to "MXN",
            "Saudi Arabia" to "SAR",
            "Qatar" to "QAR",
            "Oman" to "OMR",
            "Kuwait" to "KWD",
            "Bahrain" to "BHD",
        ).mapNotNull { (country, code) ->
            try {
                val currency = Currency.getInstance(code)
                CurrencyInfo(country, currency.currencyCode, currency.getSymbol(Locale.getDefault()))
            } catch (e: Exception) {
                null // Ignore if currency code is not supported on the device
            }
        }.sortedBy { it.countryName }
    }

    fun getCurrencySymbol(currencyCode: String?): String {
        if (currencyCode == null) return ""
        return try {
            Currency.getInstance(currencyCode).getSymbol(Locale.getDefault())
        } catch (e: Exception) {
            currencyCode
        }
    }

    fun getCurrencyInfo(currencyCode: String?): CurrencyInfo? {
        return commonCurrencies.find { it.currencyCode.equals(currencyCode, ignoreCase = true) }
    }
}
