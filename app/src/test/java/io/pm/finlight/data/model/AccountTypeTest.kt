package io.pm.finlight.data.model

import io.pm.finlight.AccountType
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountTypeTest {
    @Test
    fun `typeName matches expected database strings`() {
        assertEquals("Bank Account", AccountType.BANK.typeName)
        assertEquals("Credit Card", AccountType.CREDIT_CARD.typeName)
        assertEquals("Card", AccountType.CARD.typeName)
        assertEquals("Prepaid Card", AccountType.PREPAID_CARD.typeName)
        assertEquals("Meal Card", AccountType.MEAL_CARD.typeName)
        assertEquals("General", AccountType.GENERAL.typeName)
        assertEquals("Other", AccountType.OTHER.typeName)
    }

    @Test
    fun `fromString resolves by enum name case-insensitively`() {
        assertEquals(AccountType.BANK, AccountType.fromString("BANK"))
        assertEquals(AccountType.BANK, AccountType.fromString("bank"))
        assertEquals(AccountType.CREDIT_CARD, AccountType.fromString("credit_card"))
        assertEquals(AccountType.CREDIT_CARD, AccountType.fromString("CREDIT_CARD"))
        assertEquals(AccountType.CARD, AccountType.fromString("CARD"))
        assertEquals(AccountType.CARD, AccountType.fromString("card"))
        assertEquals(AccountType.PREPAID_CARD, AccountType.fromString("PREPAID_CARD"))
        assertEquals(AccountType.MEAL_CARD, AccountType.fromString("MEAL_CARD"))
        assertEquals(AccountType.GENERAL, AccountType.fromString("GENERAL"))
        assertEquals(AccountType.OTHER, AccountType.fromString("OTHER"))
    }

    @Test
    fun `fromString resolves by typeName case-insensitively`() {
        assertEquals(AccountType.BANK, AccountType.fromString("Bank Account"))
        assertEquals(AccountType.BANK, AccountType.fromString("bank account"))
        assertEquals(AccountType.CREDIT_CARD, AccountType.fromString("Credit Card"))
        assertEquals(AccountType.CARD, AccountType.fromString("Card"))
        assertEquals(AccountType.PREPAID_CARD, AccountType.fromString("Prepaid Card"))
        assertEquals(AccountType.MEAL_CARD, AccountType.fromString("Meal Card"))
        assertEquals(AccountType.GENERAL, AccountType.fromString("General"))
        assertEquals(AccountType.OTHER, AccountType.fromString("Other"))
    }

    @Test
    fun `fromString returns OTHER for null blank or unknown values`() {
        assertEquals(AccountType.OTHER, AccountType.fromString(null))
        assertEquals(AccountType.OTHER, AccountType.fromString(""))
        assertEquals(AccountType.OTHER, AccountType.fromString("   "))
        assertEquals(AccountType.OTHER, AccountType.fromString("Cryptocurrency"))
        assertEquals(AccountType.OTHER, AccountType.fromString("unknown_type"))
    }
}
