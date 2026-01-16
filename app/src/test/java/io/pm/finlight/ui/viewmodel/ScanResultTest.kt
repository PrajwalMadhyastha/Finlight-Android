package io.pm.finlight.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanResultTest {

    @Test
    fun `ScanResult subclasses can be instantiated`() {
        // Test Success data class
        val success = ScanResult.Success(10)
        assertEquals(10, success.count)

        // Test Error object
        val error = ScanResult.Error
        assertTrue(error is ScanResult)
        assertTrue(error is ScanResult.Error)
    }
}
