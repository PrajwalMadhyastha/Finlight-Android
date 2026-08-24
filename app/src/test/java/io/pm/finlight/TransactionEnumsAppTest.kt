package io.pm.finlight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TransactionEnumsAppTest {
    @Test
    fun transactionType_mappingsAndConstants() {
        assertEquals("expense", TransactionType.EXPENSE.dbValue)
        assertEquals("income", TransactionType.INCOME.dbValue)
        assertEquals("transfer", TransactionType.TRANSFER.dbValue)

        assertEquals("expense", TransactionType.DB_EXPENSE)
        assertEquals("income", TransactionType.DB_INCOME)
        assertEquals("transfer", TransactionType.DB_TRANSFER)
    }

    @Test
    fun transactionType_conversions() {
        assertEquals(TransactionType.EXPENSE, TransactionType.fromString("expense"))
        assertEquals(TransactionType.INCOME, TransactionType.fromString("income"))
        assertEquals(TransactionType.TRANSFER, TransactionType.fromString("transfer"))

        assertThrows(IllegalArgumentException::class.java) {
            TransactionType.fromString("UNKNOWN")
        }

        assertNull(TransactionType.fromStringOrNull(null))
        assertNull(TransactionType.fromStringOrNull("UNKNOWN"))
        assertEquals(TransactionType.EXPENSE, TransactionType.fromStringOrNull("expense"))
    }

    @Test
    fun transactionStatus_mappingsAndConstants() {
        assertEquals("CONFIRMED", TransactionStatus.CONFIRMED.dbValue)
        assertEquals("PENDING", TransactionStatus.PENDING.dbValue)
        assertEquals("SKIPPED", TransactionStatus.SKIPPED.dbValue)

        assertEquals("CONFIRMED", TransactionStatus.DB_CONFIRMED)
        assertEquals("PENDING", TransactionStatus.DB_PENDING)
        assertEquals("SKIPPED", TransactionStatus.DB_SKIPPED)
    }

    @Test
    fun transactionStatus_conversions() {
        assertEquals(TransactionStatus.CONFIRMED, TransactionStatus.fromString("confirmed"))
        assertEquals(TransactionStatus.PENDING, TransactionStatus.fromString("pending"))
        assertEquals(TransactionStatus.SKIPPED, TransactionStatus.fromString("skipped"))

        assertThrows(IllegalArgumentException::class.java) {
            TransactionStatus.fromString("UNKNOWN")
        }

        assertNull(TransactionStatus.fromStringOrNull(null))
        assertNull(TransactionStatus.fromStringOrNull("UNKNOWN"))
        assertEquals(TransactionStatus.CONFIRMED, TransactionStatus.fromStringOrNull("confirmed"))
    }
}
