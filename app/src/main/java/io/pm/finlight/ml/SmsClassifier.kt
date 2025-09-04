// =================================================================================
// FILE: app/src/main/java/io/pm/finlight/ml/SmsClassifier.kt
// REASON: NEW FILE - This service encapsulates all logic for interacting with
// the on-device TFLite text classification model. It handles loading the model
// from assets and provides a simple interface for classifying SMS messages.
// =================================================================================
package io.pm.finlight.ml

import android.content.Context
import android.util.Log
import org.tensorflow.lite.task.text.nlclassifier.NLClassifier

/**
 * A service class to handle on-device SMS classification using a TFLite model.
 *
 * This class is responsible for loading the model and providing a simple method
 * to classify a given SMS body as either transactional or non-transactional.
 *
 * @param context The application context, needed to access model assets.
 */
class SmsClassifier(private val context: Context) {
    private var classifier: NLClassifier? = null

    companion object {
        private const val TAG = "SmsClassifier"
        private const val MODEL_PATH = "sms_classifier.tflite"
    }

    init {
        // Initialize the classifier when the class is instantiated.
        try {
            classifier = NLClassifier.createFromFile(context, MODEL_PATH)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing the TFLite model.", e)
        }
    }

    /**
     * Classifies the given SMS text and returns the confidence score for the "TRANSACTION" class.
     *
     * @param text The body of the SMS message to classify.
     * @return A Float between 0.0 and 1.0 representing the confidence that the message
     * is a transaction. Returns 0.0f if the model fails or is not initialized.
     */
    fun classify(text: String): Float {
        if (classifier == null) {
            Log.w(TAG, "Classifier is not initialized. Returning 0 confidence.")
            return 0.0f
        }

        return try {
            // The model returns a list of categories with their scores.
            val results = classifier?.classify(text)

            // We are interested in the score for the "TRANSACTION" class, which has the label "1".
            val transactionCategory = results?.find { it.label == "1" }

            // Return the score, or 0.0 if not found.
            transactionCategory?.score ?: 0.0f
        } catch (e: Exception) {
            Log.e(TAG, "Error during SMS classification.", e)
            0.0f // Return a neutral score on error
        }
    }

    /**
     * Closes the classifier to release resources.
     */
    fun close() {
        classifier?.close()
        classifier = null
    }
}
