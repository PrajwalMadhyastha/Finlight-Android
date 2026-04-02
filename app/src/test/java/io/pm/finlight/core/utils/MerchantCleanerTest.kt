package io.pm.finlight.core.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class MerchantCleanerTest {

    @Test
    fun `test returns null for null input`() {
        assertNull(MerchantCleaner.clean(null))
    }

    @Test
    fun `test returns original for blank input`() {
        assertEquals("   ", MerchantCleaner.clean("   "))
        assertEquals("", MerchantCleaner.clean(""))
    }

    @Test
    fun `test strips payment gateway prefixes`() {
        assertEquals("Swiggy Food", MerchantCleaner.clean("PYU* Swiggy Food"))
        assertEquals("Zomato", MerchantCleaner.clean("RAZ-Zomato"))
        assertEquals("Uber", MerchantCleaner.clean("PAYTM* Uber"))
        assertEquals("IRCTC", MerchantCleaner.clean("CCA* IRCTC"))
        assertEquals("BillDesk", MerchantCleaner.clean("BIL*BillDesk"))
        assertEquals("Amazon", MerchantCleaner.clean("AMZ* Amazon"))
    }

    @Test
    fun `test strips upi suffixes and domains`() {
        assertEquals("zomato", MerchantCleaner.clean("zomatoupi@hdfc"))
        assertEquals("swiggy", MerchantCleaner.clean("swiggy@icici"))
        assertEquals("randomperson", MerchantCleaner.clean("randomperson@okhdfcbank"))
        assertEquals("upiservice", MerchantCleaner.clean("upiserviceupi@upi"))
    }

    @Test
    fun `test strips legal boilerplates from end`() {
        assertEquals("Amazon", MerchantCleaner.clean("Amazon Retail Pvt Ltd"))
        assertEquals("Amazon", MerchantCleaner.clean("Amazon Private Limited"))
        assertEquals("Zensar", MerchantCleaner.clean("Zensar LLP"))
        assertEquals("MyGov", MerchantCleaner.clean("MyGov Ltd"))
        assertEquals("Apple", MerchantCleaner.clean("Apple Inc."))
    }

    @Test
    fun `test trims hanging special characters`() {
        assertEquals("Swiggy", MerchantCleaner.clean("PYU* Swiggy - Pvt Ltd -"))
        assertEquals("Zomato", MerchantCleaner.clean("RAZ * Zomato Pvt Ltd."))
        assertEquals("Uber", MerchantCleaner.clean(" Uber *"))
    }

    @Test
    fun `test preserves valid merchants without matching patterns`() {
        assertEquals("Pinnacle Stock Broker", MerchantCleaner.clean("Pinnacle Stock Broker"))
        assertEquals("Starbucks", MerchantCleaner.clean("Starbucks"))
        assertEquals("My Company Limited Edition", MerchantCleaner.clean("My Company Limited Edition")) // Limited shouldn't be stripped if it's not the last word (wait, regex requires it to be at the end, so "Edition" is at the end, "Limited" is not - safe!)
    }
    
    @Test
    fun `test returns raw if cleaning strips everything`() {
        // Edge case: what if the merchant name literally was just "PYU*"? It should strip it and return empty, triggering the ifBlank fallback.
        assertEquals("PYU*", MerchantCleaner.clean("PYU*"))
        assertEquals("Pvt Ltd", MerchantCleaner.clean("Pvt Ltd"))
    }
}
