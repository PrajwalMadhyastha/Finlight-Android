package io.pm.finlight.utils

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.TestApplication
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Currency
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class CurrencyHelperTest : BaseViewModelTest() {
    @Test
    fun `getCurrencySymbol returns correct symbol for valid code`() {
        val expectedSymbol = Currency.getInstance("USD").getSymbol(Locale.getDefault())
        assertEquals(expectedSymbol, CurrencyHelper.getCurrencySymbol("USD"))
    }

    @Test
    fun `getCurrencySymbol returns currency code for invalid code`() {
        assertEquals("XYZ", CurrencyHelper.getCurrencySymbol("XYZ"))
    }

    @Test
    fun `getCurrencySymbol returns empty string for null code`() {
        assertEquals("", CurrencyHelper.getCurrencySymbol(null))
    }

    @Test
    fun `getCurrencyInfo returns correct info for valid code`() {
        val currencyInfo = CurrencyHelper.getCurrencyInfo("INR")
        assertNotNull(currencyInfo)
        assertEquals("India", currencyInfo?.countryName)
        assertEquals("INR", currencyInfo?.currencyCode)
        assertEquals("₹", currencyInfo?.currencySymbol)
    }

    @Test
    fun `getCurrencyInfo is case-insensitive`() {
        val currencyInfo = CurrencyHelper.getCurrencyInfo("inr")
        assertNotNull(currencyInfo)
        assertEquals("India", currencyInfo?.countryName)
    }

    @Test
    fun `getCurrencyInfo returns null for invalid code`() {
        assertNull(CurrencyHelper.getCurrencyInfo("XYZ"))
    }

    @Test
    fun `getCurrencyInfo returns null for null code`() {
        assertNull(CurrencyHelper.getCurrencyInfo(null))
    }

    @Test
    fun `commonCurrencies list is not empty and sorted by country name`() {
        val currencies = CurrencyHelper.commonCurrencies
        assert(currencies.isNotEmpty()) { "Common currencies list should not be empty" }

        val countryNames = currencies.map { it.countryName }
        val sortedCountryNames = countryNames.sorted()
        assertEquals("Currencies should be sorted by country name", sortedCountryNames, countryNames)
    }

    @Test
    fun `formatAmountWithSymbol formats positive amount with symbol`() {
        assertEquals("₹240.00", CurrencyHelper.formatAmountWithSymbol(240.0, "₹"))
        assertEquals("₹14.00", CurrencyHelper.formatAmountWithSymbol(14.0, "₹"))
    }

    @Test
    fun `formatAmountWithSymbol formats negative amount with leading minus and symbol`() {
        assertEquals("-₹14.00", CurrencyHelper.formatAmountWithSymbol(-14.0, "₹"))
        assertEquals("-₹100.50", CurrencyHelper.formatAmountWithSymbol(-100.5, "₹"))
    }

    @Test
    fun `formatAmountWithSymbol formats zero without negative sign`() {
        assertEquals("₹0.00", CurrencyHelper.formatAmountWithSymbol(0.0, "₹"))
        assertEquals("₹0.00", CurrencyHelper.formatAmountWithSymbol(-0.0, "₹"))
        assertEquals("₹0.00", CurrencyHelper.formatAmountWithSymbol(-0.001, "₹"))
    }

    @Test
    fun `formatAmountWithSymbol formats foreign currency symbols correctly`() {
        assertEquals("$240.00", CurrencyHelper.formatAmountWithSymbol(240.0, "$"))
        assertEquals("-$14.00", CurrencyHelper.formatAmountWithSymbol(-14.0, "$"))
        assertEquals("€50.25", CurrencyHelper.formatAmountWithSymbol(50.25, "€"))
        assertEquals("-€50.25", CurrencyHelper.formatAmountWithSymbol(-50.25, "€"))
    }
}
