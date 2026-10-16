package io.pm.finlight.data.model

import io.pm.finlight.SearchFilter
import io.pm.finlight.TransactionType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFilterTest {
    @Test
    fun `default SearchFilter isActive is false`() {
        val filter = SearchFilter()
        assertFalse(filter.isActive)
    }

    @Test
    fun `SearchFilter with accountId isActive is true`() {
        val filter = SearchFilter(accountId = 1)
        assertTrue(filter.isActive)
    }

    @Test
    fun `SearchFilter with categoryId isActive is true`() {
        val filter = SearchFilter(categoryId = 2)
        assertTrue(filter.isActive)
    }

    @Test
    fun `SearchFilter with tagId isActive is true`() {
        val filter = SearchFilter(tagId = 3)
        assertTrue(filter.isActive)
    }

    @Test
    fun `SearchFilter with transactionType isActive is true`() {
        val filter = SearchFilter(transactionType = TransactionType.EXPENSE)
        assertTrue(filter.isActive)
    }

    @Test
    fun `SearchFilter with startDate isActive is true`() {
        val filter = SearchFilter(startDate = 100L)
        assertTrue(filter.isActive)
    }

    @Test
    fun `SearchFilter with endDate isActive is true`() {
        val filter = SearchFilter(endDate = 200L)
        assertTrue(filter.isActive)
    }
}
