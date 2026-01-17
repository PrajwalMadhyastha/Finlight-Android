// =================================================================================
// FILE: app/src/main/java/io/pm/finlight/ml/SmsClassifier.kt
// REASON: REFACTOR - The classifier has been completely rewritten to use the
// core TFLite libraries instead of the high-level Task library. This resolves
// a dependency conflict with the Flex delegate and provides more direct control
// over the text preprocessing and model inference, fixing the instrumented test.
// FIX - The input buffer now correctly uses integers (Int) instead of floats
// to match the model's expected input data type, resolving a runtime crash.
// FIX - The classifier now correctly passes the raw input string to the model,
// allowing the model's internal TextVectorization layer to handle tokenization.
// This resolves the final "IllegalArgumentException" during inference.
// REFACTOR: The classifier no longer relies on the model's internal TextVectorization
// layer. It now manually loads the vocabulary, cleans the text, and tokenizes it
// into an integer array. This removes the dependency on the TFLite Flex delegate
// for string processing and makes the entire pipeline more explicit and robust,
// fixing the persistent test failures.
// FIX: The data type for tokenization and buffer creation has been changed from
// Int (32-bit) to Long (64-bit). This resolves a ByteBuffer size mismatch crash
// by perfectly aligning the on-device input data type with the model's expected
// `tf.int64` input tensor.
// FIX: The text cleaning regex has been improved to whitelist allowed characters
// (letters, numbers, spaces) rather than blacklisting punctuation. This is a more
// robust method to ensure the on-device preprocessing perfectly matches the
// Python training script, resolving the final assertion error in the test suite.
// FIX: The text cleaning regex has been made an exact 1:1 match of Python's
// `string.punctuation`. This is the definitive fix to align on-device preprocessing
// with the training script, resolving the final low-confidence assertion error.
// FIX: The text cleaning logic now explicitly collapses multiple whitespace
// characters into a single space after removing punctuation. This is the definitive
// fix to perfectly replicate the Python TextVectorization layer's behavior and
// resolve the instrumented test assertion failure.
//
// BUG FIX (Concurrency): Added a synchronized block around the interpreter.run()
// call. The TFLite Interpreter is not thread-safe, and the SettingsViewModel was
// calling classify() from multiple coroutines concurrently during a bulk scan,
// leading to a race condition and native memory corruption. This lock ensures
// that only one thread can access the interpreter at a time, fixing the
// "gather index out of bounds" crash.
//
// REFACTOR (Testability): Added an internal constructor for testing that accepts
// pre-loaded vocabulary and interpreter, enabling unit tests to cover this class
// without requiring TFLite model loading in Robolectric.
// =================================================================================
package io.pm.finlight.ml

import android.content.Context
import androidx.annotation.VisibleForTesting
import java.io.Closeable
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * A service class for classifying SMS messages using a TFLite model.
 *
 * This class handles loading the model and vocabulary, preprocessing the input text,
 * running inference, and returning the classification score.
 *
 * @param context The application context, used to access the assets folder.
 */
class SmsClassifier private constructor(
    private val context: Context?,
    private val modelName: String,
    private val vocabName: String,
    preloadedVocab: Map<String, Int>?,
    preloadedInterpreter: Interpreter?,
    private val interpreterFactory: ((ByteBuffer, Interpreter.Options) -> Interpreter)? = null
) : Closeable {

    companion object {
        // Must match the Python script's configuration
        @VisibleForTesting
        internal const val MAX_SEQUENCE_LENGTH = 250
        @VisibleForTesting
        internal const val UNKNOWN_TOKEN = 1L // '[UNK]' is typically the second item in the vocab
    }

    private var interpreter: Interpreter? = preloadedInterpreter
    @VisibleForTesting
    internal val vocab = preloadedVocab?.toMutableMap() ?: mutableMapOf()

    private val interpreterLock = Any()

    /**
     * Primary constructor for production use.
     * Loads the TFLite model and vocabulary from assets.
     */
    constructor(
        context: Context,
        modelName: String = "sms_classifier.tflite",
        vocabName: String = "vocab.txt"
    ) : this(context, modelName, vocabName, null, null) {
        loadModel()
        loadVocabulary()
    }

    /**
     * Internal constructor for unit testing.
     * Allows injecting pre-loaded vocabulary and interpreter.
     */
    @VisibleForTesting
    internal constructor(
        vocab: Map<String, Int>,
        interpreter: Interpreter?
    ) : this(null, "", "", vocab, interpreter)

    /**
     * Internal constructor for unit testing with Context and mocked Interpreter.
     */
    @VisibleForTesting
    internal constructor(
        context: Context,
        factory: (ByteBuffer, Interpreter.Options) -> Interpreter
    ) : this(context, "sms_classifier.tflite", "vocab.txt", null, null, factory) {
        loadVocabulary()
        loadModel()
    }

    /**
     * Loads the TFLite model from the assets folder into an Interpreter.
     */
    private fun loadModel() {
        val ctx = context ?: return
        val modelFileDescriptor = ctx.assets.openFd(modelName)
        val fileInputStream = FileInputStream(modelFileDescriptor.fileDescriptor)
        val fileChannel = fileInputStream.channel
        val startOffset = modelFileDescriptor.startOffset
        val declaredLength = modelFileDescriptor.declaredLength
        val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

        val options = Interpreter.Options()
        options.setNumThreads(4)
        
        // Use the injected factory or default to creating a real Interpreter
        if (interpreterFactory != null) {
            interpreter = interpreterFactory.invoke(modelBuffer, options)
        } else {
            interpreter = Interpreter(modelBuffer, options)
        }
    }

    /**
     * Loads the vocabulary file from assets into a map for tokenization.
     */
    private fun loadVocabulary() {
        val ctx = context ?: return
        ctx.assets.open(vocabName).bufferedReader().useLines { lines ->
            lines.forEachIndexed { index, line ->
                vocab[line] = index
            }
        }
    }

    /**
     * Cleans and tokenizes the input text into a sequence of integers.
     *
     * @param text The raw SMS body.
     * @return A padded long array representing the tokenized text.
     */
    @VisibleForTesting
    internal fun tokenize(text: String): LongArray {
        // 1. Preprocess the text to exactly match Python's default TextVectorization.
        val punctuationRegex = Regex("[!\"#\$%&'()*+,-./:;<=>?@\\[\\\\\\]^_`{|}~]")
        val cleanedText = text.lowercase()
            .replace(punctuationRegex, "")       // Remove punctuation
            .replace(Regex("\\s+"), " ") // Collapse multiple spaces into one
            .trim()                               // Remove leading/trailing spaces
        val words = if (cleanedText.isEmpty()) emptyList() else cleanedText.split(" ")

        // 2. Convert words to integer tokens using the vocabulary.
        val tokens = words.map { vocab.getOrDefault(it, UNKNOWN_TOKEN.toInt()).toLong() }.toMutableList()

        // 3. Pad or truncate the sequence to the required length.
        while (tokens.size < MAX_SEQUENCE_LENGTH) {
            tokens.add(0L) // 0 is the padding token
        }
        return tokens.take(MAX_SEQUENCE_LENGTH).toLongArray()
    }


    /**
     * Preprocesses and classifies a given SMS message.
     *
     * @param text The raw SMS body.
     * @return A confidence score (0.0 to 1.0) for the "TRANSACTION" class.
     */
    fun classify(text: String): Float {
        if (interpreter == null) {
            return 0.0f
        }

        // 1. Tokenize the input text into a padded long array.
        val tokens = tokenize(text)

        // 2. Prepare the input ByteBuffer for the TFLite model.
        // A Long is 8 bytes, so we allocate MAX_SEQUENCE_LENGTH * 8 bytes.
        val inputBuffer = ByteBuffer.allocateDirect(MAX_SEQUENCE_LENGTH * 8).apply {
            order(ByteOrder.nativeOrder())
            for (token in tokens) {
                putLong(token)
            }
            rewind()
        }

        // 3. Prepare the output buffer. A Float is 4 bytes.
        val outputBuffer = ByteBuffer.allocateDirect(1 * 4).apply {
            order(ByteOrder.nativeOrder())
        }

        val result = synchronized(interpreterLock) {
            interpreter?.run(inputBuffer, outputBuffer)

            outputBuffer.rewind()
            outputBuffer.float
        }

        return result
    }


    /**
     * Closes the interpreter to release resources.
     */
    override fun close() {
        interpreter?.close()
        interpreter = null
    }
}
