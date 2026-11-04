package io.pm.finlight.data.model

import android.net.Uri
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.Account
import io.pm.finlight.Category
import io.pm.finlight.Tag
import io.pm.finlight.TestApplication
import io.pm.finlight.TransactionType
import io.pm.finlight.TravelModeSettings
import io.pm.finlight.TripType
import io.pm.finlight.ui.viewmodel.AnalysisTransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class TransactionUiModelsTest {
    @Test
    fun `TransactionFilterState default and custom values behave correctly`() {
        val defaultState = TransactionFilterState()
        assertEquals("", defaultState.keyword)
        assertNull(defaultState.account)
        assertNull(defaultState.category)
        assertEquals(AnalysisTransactionType.EXPENSE, defaultState.transactionType)

        val account = Account(id = 1, name = "Savings", type = "BANK")
        val category = Category(id = 2, name = "Dining", iconKey = "food", colorKey = "orange")
        val customState =
            TransactionFilterState(
                keyword = "coffee",
                account = account,
                category = category,
                transactionType = AnalysisTransactionType.ALL,
            )

        assertEquals("coffee", customState.keyword)
        assertEquals(account, customState.account)
        assertEquals(category, customState.category)
        assertEquals(AnalysisTransactionType.ALL, customState.transactionType)

        val copied = defaultState.copy(keyword = "test")
        assertEquals("test", copied.keyword)
        assertNotEquals(defaultState, copied)
        assertEquals(defaultState, TransactionFilterState())
        assertEquals(defaultState.hashCode(), TransactionFilterState().hashCode())
        assertTrue(customState.toString().contains("coffee"))
    }

    @Test
    fun `AnalysisTransactionType toTransactionType maps types correctly`() {
        assertEquals(TransactionType.EXPENSE, AnalysisTransactionType.EXPENSE.toTransactionType())
        assertEquals(TransactionType.INCOME, AnalysisTransactionType.INCOME.toTransactionType())
        assertNull(AnalysisTransactionType.ALL.toTransactionType())
    }

    @Test
    fun `ManualTransactionData toTransaction maps standard entry without travel mode`() {
        val data =
            ManualTransactionData(
                description = "Groceries",
                amountStr = "125.50",
                accountId = 10,
                notes = "Weekly grocery run",
                date = 1000L,
                transactionType = TransactionType.EXPENSE,
                imageUris = listOf(Uri.parse("content://media/1")),
                tags = setOf(Tag(id = 1, name = "Food")),
            )

        val txn = data.toTransaction(categoryId = 5, travelSettings = null)

        assertEquals("Groceries", txn.description)
        assertEquals("Groceries", txn.originalDescription)
        assertEquals(5, txn.categoryId)
        assertEquals(125.50, txn.amount, 0.001)
        assertEquals(1000L, txn.date)
        assertEquals(10, txn.accountId)
        assertEquals("Weekly grocery run", txn.notes)
        assertEquals(TransactionType.EXPENSE, txn.transactionType)
        assertFalse(txn.isExcluded)
        assertNull(txn.sourceSmsId)
        assertNull(txn.sourceSmsHash)
        assertEquals("Manual Entry", txn.source)
        assertEquals(125.50, txn.originalAmount ?: 0.0, 0.001)
        assertNull(txn.currencyCode)
        assertNull(txn.conversionRate)
    }

    @Test
    fun `ManualTransactionData toTransaction handles invalid amountStr defaulting to 0`() {
        val data =
            ManualTransactionData(
                description = "Invalid Amount Test",
                amountStr = "not-a-number",
                accountId = 1,
                notes = null,
                date = 2000L,
                transactionType = TransactionType.INCOME,
                imageUris = emptyList(),
                tags = emptySet(),
            )

        val txn = data.toTransaction(categoryId = null, travelSettings = null)

        assertEquals(0.0, txn.amount, 0.001)
        assertEquals(0.0, txn.originalAmount ?: 0.0, 0.001)
        assertNull(txn.categoryId)
    }

    @Test
    fun `ManualTransactionData toTransaction applies international travel conversion when eligible`() {
        val travelSettings =
            TravelModeSettings(
                isEnabled = true,
                tripName = "US Vacation",
                tripType = TripType.INTERNATIONAL,
                startDate = 1000L,
                endDate = 5000L,
                currencyCode = "USD",
                conversionRate = 83.5f,
            )

        val data =
            ManualTransactionData(
                description = "Coffee in NY",
                amountStr = "5.0",
                accountId = 2,
                notes = "Starbucks",
                date = 2500L,
                transactionType = TransactionType.EXPENSE,
                imageUris = emptyList(),
                tags = emptySet(),
            )

        val txn = data.toTransaction(categoryId = 3, travelSettings = travelSettings)

        // 5.0 * 83.5 = 417.5
        assertEquals(417.5, txn.amount, 0.01)
        assertEquals(5.0, txn.originalAmount ?: 0.0, 0.001)
        assertEquals("USD", txn.currencyCode)
        assertEquals(83.5, txn.conversionRate ?: 0.0, 0.01)
    }

    @Test
    fun `ManualTransactionData toTransaction handles international travel with null conversionRate`() {
        val travelSettings =
            TravelModeSettings(
                isEnabled = true,
                tripName = "Euro Trip",
                tripType = TripType.INTERNATIONAL,
                startDate = 1000L,
                endDate = 5000L,
                currencyCode = "EUR",
                conversionRate = null,
            )

        val data =
            ManualTransactionData(
                description = "Museum Ticket",
                amountStr = "20.0",
                accountId = 2,
                notes = null,
                date = 2000L,
                transactionType = TransactionType.EXPENSE,
                imageUris = emptyList(),
                tags = emptySet(),
            )

        val txn = data.toTransaction(categoryId = 4, travelSettings = travelSettings)

        // Null conversionRate defaults multiplier to 1f -> amount = 20.0 * 1f
        assertEquals(20.0, txn.amount, 0.001)
        assertEquals("EUR", txn.currencyCode)
        assertNull(txn.conversionRate)
    }

    @Test
    fun `ManualTransactionData toTransaction ignores travel conversion when disabled`() {
        val travelSettings =
            TravelModeSettings(
                isEnabled = false,
                tripName = "Disabled Trip",
                tripType = TripType.INTERNATIONAL,
                startDate = 1000L,
                endDate = 5000L,
                currencyCode = "USD",
                conversionRate = 80f,
            )

        val data =
            ManualTransactionData(
                description = "Expense",
                amountStr = "50.0",
                accountId = 1,
                notes = null,
                date = 2000L,
                transactionType = TransactionType.EXPENSE,
                imageUris = emptyList(),
                tags = emptySet(),
            )

        val txn = data.toTransaction(categoryId = 1, travelSettings = travelSettings)

        assertEquals(50.0, txn.amount, 0.001)
        assertNull(txn.currencyCode)
        assertNull(txn.conversionRate)
    }

    @Test
    fun `ManualTransactionData toTransaction ignores travel conversion when tripType is domestic`() {
        val travelSettings =
            TravelModeSettings(
                isEnabled = true,
                tripName = "Domestic Goa Trip",
                tripType = TripType.DOMESTIC,
                startDate = 1000L,
                endDate = 5000L,
                currencyCode = "INR",
                conversionRate = 1f,
            )

        val data =
            ManualTransactionData(
                description = "Domestic Expense",
                amountStr = "50.0",
                accountId = 1,
                notes = null,
                date = 2000L,
                transactionType = TransactionType.EXPENSE,
                imageUris = emptyList(),
                tags = emptySet(),
            )

        val txn = data.toTransaction(categoryId = 1, travelSettings = travelSettings)

        assertEquals(50.0, txn.amount, 0.001)
        assertNull(txn.currencyCode)
        assertNull(txn.conversionRate)
    }

    @Test
    fun `ManualTransactionData toTransaction ignores travel conversion when date is outside trip range`() {
        val travelSettings =
            TravelModeSettings(
                isEnabled = true,
                tripName = "US Trip",
                tripType = TripType.INTERNATIONAL,
                startDate = 2000L,
                endDate = 5000L,
                currencyCode = "USD",
                conversionRate = 80f,
            )

        val beforeData =
            ManualTransactionData(
                description = "Before Trip",
                amountStr = "10.0",
                accountId = 1,
                notes = null,
                date = 1000L,
                transactionType = TransactionType.EXPENSE,
                imageUris = emptyList(),
                tags = emptySet(),
            )
        val afterData =
            ManualTransactionData(
                description = "After Trip",
                amountStr = "10.0",
                accountId = 1,
                notes = null,
                date = 6000L,
                transactionType = TransactionType.EXPENSE,
                imageUris = emptyList(),
                tags = emptySet(),
            )

        val txnBefore = beforeData.toTransaction(categoryId = 1, travelSettings = travelSettings)
        val txnAfter = afterData.toTransaction(categoryId = 1, travelSettings = travelSettings)

        assertEquals(10.0, txnBefore.amount, 0.001)
        assertNull(txnBefore.currencyCode)
        assertEquals(10.0, txnAfter.amount, 0.001)
        assertNull(txnAfter.currencyCode)
    }

    @Test
    fun `ManualTransactionData copy equals and hashCode behave correctly`() {
        val d1 =
            ManualTransactionData(
                description = "Desc",
                amountStr = "10",
                accountId = 1,
                notes = null,
                date = 100L,
                transactionType = TransactionType.EXPENSE,
                imageUris = emptyList(),
                tags = emptySet(),
            )
        val d2 = d1.copy()
        val d3 = d1.copy(description = "Other")

        assertEquals(d1, d2)
        assertEquals(d1.hashCode(), d2.hashCode())
        assertNotEquals(d1, d3)
        assertTrue(d1.toString().contains("Desc"))
    }
}
