package io.pm.finlight.data.db

import io.pm.finlight.TransactionStatus
import io.pm.finlight.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ConvertersTest {
    private val converters = Converters()

    @Test
    fun `fromTransactionType converts enum to lowercase string`() {
        assertEquals("expense", converters.fromTransactionType(TransactionType.EXPENSE))
        assertEquals("income", converters.fromTransactionType(TransactionType.INCOME))
        assertEquals("transfer", converters.fromTransactionType(TransactionType.TRANSFER))
    }

    @Test
    fun `toTransactionType converts valid string to TransactionType regardless of casing`() {
        assertEquals(TransactionType.EXPENSE, converters.toTransactionType("expense"))
        assertEquals(TransactionType.EXPENSE, converters.toTransactionType("EXPENSE"))
        assertEquals(TransactionType.EXPENSE, converters.toTransactionType("Expense"))
        assertEquals(TransactionType.INCOME, converters.toTransactionType("income"))
        assertEquals(TransactionType.INCOME, converters.toTransactionType("INCOME"))
        assertEquals(TransactionType.TRANSFER, converters.toTransactionType("transfer"))
        assertEquals(TransactionType.TRANSFER, converters.toTransactionType("TRANSFER"))
    }

    @Test
    fun `fromTransactionStatus converts enum to exact uppercase string`() {
        assertEquals("CONFIRMED", converters.fromTransactionStatus(TransactionStatus.CONFIRMED))
        assertEquals("PENDING", converters.fromTransactionStatus(TransactionStatus.PENDING))
        assertEquals("SKIPPED", converters.fromTransactionStatus(TransactionStatus.SKIPPED))
    }

    @Test
    fun `toTransactionStatus converts valid string to TransactionStatus regardless of casing`() {
        assertEquals(TransactionStatus.CONFIRMED, converters.toTransactionStatus("CONFIRMED"))
        assertEquals(TransactionStatus.CONFIRMED, converters.toTransactionStatus("confirmed"))
        assertEquals(TransactionStatus.CONFIRMED, converters.toTransactionStatus("Confirmed"))
        assertEquals(TransactionStatus.PENDING, converters.toTransactionStatus("PENDING"))
        assertEquals(TransactionStatus.PENDING, converters.toTransactionStatus("pending"))
        assertEquals(TransactionStatus.SKIPPED, converters.toTransactionStatus("SKIPPED"))
        assertEquals(TransactionStatus.SKIPPED, converters.toTransactionStatus("skipped"))
    }

    @Test
    fun `TransactionType companion methods parse valid and invalid strings correctly`() {
        assertEquals(TransactionType.EXPENSE, TransactionType.fromString("expense"))
        assertEquals(TransactionType.INCOME, TransactionType.fromString("INCOME"))
        assertEquals(TransactionType.TRANSFER, TransactionType.fromString("Transfer"))

        assertEquals(TransactionType.EXPENSE, TransactionType.fromStringOrNull("expense"))
        assertEquals(TransactionType.INCOME, TransactionType.fromStringOrNull("income"))
        assertEquals(TransactionType.TRANSFER, TransactionType.fromStringOrNull("transfer"))
        assertNull(TransactionType.fromStringOrNull("invalid"))
        assertNull(TransactionType.fromStringOrNull(null))

        assertThrows(IllegalArgumentException::class.java) {
            TransactionType.fromString("invalid")
        }
    }

    @Test
    fun `TransactionStatus companion methods parse valid and invalid strings correctly`() {
        assertEquals(TransactionStatus.CONFIRMED, TransactionStatus.fromString("CONFIRMED"))
        assertEquals(TransactionStatus.PENDING, TransactionStatus.fromString("pending"))
        assertEquals(TransactionStatus.SKIPPED, TransactionStatus.fromString("Skipped"))

        assertEquals(TransactionStatus.CONFIRMED, TransactionStatus.fromStringOrNull("confirmed"))
        assertEquals(TransactionStatus.PENDING, TransactionStatus.fromStringOrNull("PENDING"))
        assertEquals(TransactionStatus.SKIPPED, TransactionStatus.fromStringOrNull("skipped"))
        assertNull(TransactionStatus.fromStringOrNull("invalid"))
        assertNull(TransactionStatus.fromStringOrNull(null))

        assertThrows(IllegalArgumentException::class.java) {
            TransactionStatus.fromString("invalid")
        }
    }
}
