package io.pm.finlight.ml

import org.junit.Test

class SmsEntityExtractorTest {
    @Test
    fun testDefaultClose() {
        // Create an anonymous implementation that uses the default close()
        val extractor = object : SmsEntityExtractor {
            override fun extract(text: String): Map<String, String> {
                return emptyMap()
            }
        }
        // This calls the default implementation in the interface: override fun close() {}
        extractor.close()
    }
}
