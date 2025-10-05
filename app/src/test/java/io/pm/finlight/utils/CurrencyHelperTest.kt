package io.pm.finlight.utils

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.TestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Currency
import java.util.Locale

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
}
