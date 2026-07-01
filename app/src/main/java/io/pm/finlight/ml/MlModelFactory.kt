// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ml/MlModelFactory.kt
// REASON: FEATURE (Testability) - Wraps the creation of native ML models.
// TFLite's Interpreter uses System.loadLibrary which crashes Robolectric tests.
// By using this factory, we can mock the creation of these models in our
// worker unit tests and return pure mocks, avoiding the native library load.
// =================================================================================
package io.pm.finlight.ml

import android.content.Context

object MlModelFactory {
    @Volatile
    private var classifierInstance: SmsClassifier? = null

    @Volatile
    private var nerExtractorInstance: NerExtractor? = null

    fun getClassifier(context: Context): SmsClassifier {
        return classifierInstance ?: synchronized(this) {
            classifierInstance ?: SmsClassifier(context.applicationContext).also { classifierInstance = it }
        }
    }

    fun getNerExtractor(context: Context): NerExtractor {
        return nerExtractorInstance ?: synchronized(this) {
            nerExtractorInstance ?: NerExtractor(context.applicationContext).also { nerExtractorInstance = it }
        }
    }
}
