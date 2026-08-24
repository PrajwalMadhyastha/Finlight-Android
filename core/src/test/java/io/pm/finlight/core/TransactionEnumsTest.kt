package io.pm.finlight.core

import io.pm.finlight.DB_STATUS_CONFIRMED
import io.pm.finlight.DB_STATUS_PENDING
import io.pm.finlight.DB_STATUS_SKIPPED
import io.pm.finlight.DB_TYPE_EXPENSE
import io.pm.finlight.DB_TYPE_INCOME
import io.pm.finlight.DB_TYPE_TRANSFER
import io.pm.finlight.TransactionStatus
import io.pm.finlight.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TransactionEnumsTest {
    @Test
    fun transactionType_dbValuesMatchConstants() {
        assertEquals("expense", TransactionType.EXPENSE.dbValue)
        assertEquals("income", TransactionType.INCOME.dbValue)
        assertEquals("transfer", TransactionType.TRANSFER.dbValue)

        assertEquals(DB_TYPE_EXPENSE, TransactionType.DB_EXPENSE)
        assertEquals(DB_TYPE_INCOME, TransactionType.DB_INCOME)
        assertEquals(DB_TYPE_TRANSFER, TransactionType.DB_TRANSFER)
    }

    @Test
    fun transactionType_fromString_caseInsensitive() {
        assertEquals(TransactionType.EXPENSE, TransactionType.fromString("expense"))
        assertEquals(TransactionType.EXPENSE, TransactionType.fromString("EXPENSE"))
        assertEquals(TransactionType.EXPENSE, TransactionType.fromString("Expense"))

        assertEquals(TransactionType.INCOME, TransactionType.fromString("income"))
        assertEquals(TransactionType.INCOME, TransactionType.fromString("INCOME"))
        assertEquals(TransactionType.INCOME, TransactionType.fromString("Income"))

        assertEquals(TransactionType.TRANSFER, TransactionType.fromString("transfer"))
        assertEquals(TransactionType.TRANSFER, TransactionType.fromString("TRANSFER"))
        assertEquals(TransactionType.TRANSFER, TransactionType.fromString("Transfer"))
    }

    @Test
    fun transactionType_fromString_throwsOnInvalid() {
        assertThrows(IllegalArgumentException::class.java) {
            TransactionType.fromString("invalid")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TransactionType.fromString("")
        }
    }

    @Test
    fun transactionType_fromStringOrNull_handlesNullAndInvalidGracefully() {
        assertNull(TransactionType.fromStringOrNull(null))
        assertNull(TransactionType.fromStringOrNull("invalid"))
        assertNull(TransactionType.fromStringOrNull(""))

        assertEquals(TransactionType.EXPENSE, TransactionType.fromStringOrNull("expense"))
        assertEquals(TransactionType.EXPENSE, TransactionType.fromStringOrNull("EXPENSE"))
        assertEquals(TransactionType.INCOME, TransactionType.fromStringOrNull("income"))
        assertEquals(TransactionType.TRANSFER, TransactionType.fromStringOrNull("transfer"))
    }

    @Test
    fun transactionStatus_dbValuesMatchConstants() {
        assertEquals("CONFIRMED", TransactionStatus.CONFIRMED.dbValue)
        assertEquals("PENDING", TransactionStatus.PENDING.dbValue)
        assertEquals("SKIPPED", TransactionStatus.SKIPPED.dbValue)

        assertEquals(DB_STATUS_CONFIRMED, TransactionStatus.DB_CONFIRMED)
        assertEquals(DB_STATUS_PENDING, TransactionStatus.DB_PENDING)
        assertEquals(DB_STATUS_SKIPPED, TransactionStatus.DB_SKIPPED)
    }

    @Test
    fun transactionStatus_fromString_caseInsensitive() {
        assertEquals(TransactionStatus.CONFIRMED, TransactionStatus.fromString("confirmed"))
        assertEquals(TransactionStatus.CONFIRMED, TransactionStatus.fromString("CONFIRMED"))
        assertEquals(TransactionStatus.CONFIRMED, TransactionStatus.fromString("Confirmed"))

        assertEquals(TransactionStatus.PENDING, TransactionStatus.fromString("pending"))
        assertEquals(TransactionStatus.PENDING, TransactionStatus.fromString("PENDING"))
        assertEquals(TransactionStatus.PENDING, TransactionStatus.fromString("Pending"))

        assertEquals(TransactionStatus.SKIPPED, TransactionStatus.fromString("skipped"))
        assertEquals(TransactionStatus.SKIPPED, TransactionStatus.fromString("SKIPPED"))
        assertEquals(TransactionStatus.SKIPPED, TransactionStatus.fromString("Skipped"))
    }

    @Test
    fun transactionStatus_fromString_throwsOnInvalid() {
        assertThrows(IllegalArgumentException::class.java) {
            TransactionStatus.fromString("invalid")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TransactionStatus.fromString("")
        }
    }

    @Test
    fun transactionStatus_fromStringOrNull_handlesNullAndInvalidGracefully() {
        assertNull(TransactionStatus.fromStringOrNull(null))
        assertNull(TransactionStatus.fromStringOrNull("invalid"))
        assertNull(TransactionStatus.fromStringOrNull(""))

        assertEquals(TransactionStatus.CONFIRMED, TransactionStatus.fromStringOrNull("confirmed"))
        assertEquals(TransactionStatus.CONFIRMED, TransactionStatus.fromStringOrNull("CONFIRMED"))
        assertEquals(TransactionStatus.PENDING, TransactionStatus.fromStringOrNull("pending"))
        assertEquals(TransactionStatus.SKIPPED, TransactionStatus.fromStringOrNull("skipped"))
    }
}
