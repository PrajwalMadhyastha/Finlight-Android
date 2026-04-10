package io.pm.finlight.ml

import java.io.Closeable

/**
 * Interface for extracting named entities from SMS text.
 *
 * Implementations include:
 * - [NerExtractor]: Android production implementation using LiteRT/TFLite.
 * - DesktopNerExtractor (test-only): JVM desktop implementation using Python bridge.
 */
interface SmsEntityExtractor : Closeable {
    /**
     * Extract named entities from an SMS message.
     *
     * @param text The raw SMS body.
     * @return Map of entity type to extracted text, e.g. {"MERCHANT": "Amazon", "AMOUNT": "Rs 500"}.
     *         Only entities found in the text are included.
     */
    fun extract(text: String): Map<String, String>

    /** Default no-op close for implementations that don't hold resources. */
    override fun close() {}
}
