package io.pm.finlight.utils

import io.pm.finlight.MerchantRenameRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantRuleCollisionDetectorTest {
    @Test
    fun `areConflicting returns true when rules share merchant token root with different rename targets`() {
        val rule1 = MerchantRenameRule(originalName = "AMAZON PAY", newName = "Amazon Pay")
        val rule2 = MerchantRenameRule(originalName = "AMAZON INDIA", newName = "Amazon")

        assertTrue(MerchantRuleCollisionDetector.areConflicting(rule1, rule2))
        assertTrue(MerchantRuleCollisionDetector.areConflicting(rule2, rule1))
    }

    @Test
    fun `areConflicting returns false when rules share token root but have identical rename targets`() {
        val rule1 = MerchantRenameRule(originalName = "AMAZON PAY", newName = "Amazon")
        val rule2 = MerchantRenameRule(originalName = "AMAZON INDIA", newName = "Amazon")

        assertFalse(MerchantRuleCollisionDetector.areConflicting(rule1, rule2))
    }

    @Test
    fun `areConflicting returns true for token subset containment with different targets`() {
        val rule1 = MerchantRenameRule(originalName = "UBER", newName = "Uber")
        val rule2 = MerchantRenameRule(originalName = "UBER EATS", newName = "Uber Eats")

        assertTrue(MerchantRuleCollisionDetector.areConflicting(rule1, rule2))
    }

    @Test
    fun `areConflicting returns false for disjoint merchants sharing only generic stopwords`() {
        val rule1 = MerchantRenameRule(originalName = "AIRTEL INDIA PVT LTD", newName = "Airtel")
        val rule2 = MerchantRenameRule(originalName = "RELIANCE INDIA PVT LTD", newName = "Jio")

        assertFalse(MerchantRuleCollisionDetector.areConflicting(rule1, rule2))
    }

    @Test
    fun `areConflicting returns false for completely disjoint merchants`() {
        val rule1 = MerchantRenameRule(originalName = "SWIGGY BANGALORE", newName = "Swiggy")
        val rule2 = MerchantRenameRule(originalName = "NETFLIX MUMBAI", newName = "Netflix")

        assertFalse(MerchantRuleCollisionDetector.areConflicting(rule1, rule2))
    }

    @Test
    fun `areConflicting returns false when compared to itself`() {
        val rule = MerchantRenameRule(originalName = "SWIGGY", newName = "Swiggy")
        assertFalse(MerchantRuleCollisionDetector.areConflicting(rule, rule))
    }

    @Test
    fun `findCollisions returns bidirectional collision mapping for colliding rules`() {
        val rule1 = MerchantRenameRule(originalName = "AMAZON PAY", newName = "Amazon Pay")
        val rule2 = MerchantRenameRule(originalName = "AMAZON INDIA", newName = "Amazon")
        val rule3 = MerchantRenameRule(originalName = "SWIGGY", newName = "Swiggy")

        val collisions = MerchantRuleCollisionDetector.findCollisions(listOf(rule1, rule2, rule3))

        assertEquals(1, collisions["amazon pay"]?.size)
        assertEquals("AMAZON INDIA", collisions["amazon pay"]?.first()?.originalName)

        assertEquals(1, collisions["amazon india"]?.size)
        assertEquals("AMAZON PAY", collisions["amazon india"]?.first()?.originalName)

        assertEquals(null, collisions["swiggy"])
    }
}
