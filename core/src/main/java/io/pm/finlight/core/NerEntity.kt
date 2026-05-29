package io.pm.finlight.core

/**
 * A named entity extracted from SMS text, bundled with the NER model's confidence.
 *
 * @param value      The raw text span identified as this entity (e.g. "Rs 500", "Amazon").
 * @param confidence Softmax probability [0..1] the model assigned to this entity label.
 *                   Higher values mean the model is more certain.
 */
data class NerEntity(val value: String, val confidence: Float)
