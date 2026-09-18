// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ml/MlModelFactory.kt
// REASON: FEATURE (Testability) - Wraps the creation of native ML models.
// TFLite's Interpreter uses System.loadLibrary which crashes Robolectric tests.
// By using this factory, we can mock the creation of these models in our
// worker unit tests and return pure mocks, avoiding the native library load.
// PERF (Issue #306): Caches static ML vocabularies in memory using thread-safe
// double-checked locking to eliminate per-SMS asset I/O. Interpreters remain
// ephemeral and are not cached.
// =================================================================================
package io.pm.finlight.ml

import android.content.Context
import androidx.annotation.VisibleForTesting
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer

object MlModelFactory {
    @Volatile
    private var classifierVocab: Map<String, Int>? = null

    @Volatile
    private var nerVocab: Map<String, Int>? = null

    @Volatile
    private var nerLabelMap: Map<Int, String>? = null

    fun getClassifierVocab(context: Context): Map<String, Int> =
        classifierVocab ?: synchronized(this) {
            classifierVocab ?: loadClassifierVocab(context).also { classifierVocab = it }
        }

    fun getNerVocab(context: Context): Map<String, Int> =
        nerVocab ?: synchronized(this) {
            nerVocab ?: loadNerVocab(context).also { nerVocab = it }
        }

    fun getNerLabelMap(context: Context): Map<Int, String> =
        nerLabelMap ?: synchronized(this) {
            nerLabelMap ?: loadNerLabelMap(context).also { nerLabelMap = it }
        }

    fun getClassifier(
        context: Context,
        interpreterFactory: ((ByteBuffer, Interpreter.Options) -> Interpreter)? = null,
    ): SmsClassifier =
        SmsClassifier(
            context = context,
            vocab = getClassifierVocab(context),
            interpreterFactory = interpreterFactory,
        )

    fun getNerExtractor(
        context: Context,
        interpreterFactory: ((ByteBuffer, Interpreter.Options) -> Interpreter)? = null,
    ): NerExtractor =
        NerExtractor(
            context = context,
            vocab = getNerVocab(context),
            labelMap = getNerLabelMap(context),
            interpreterFactory = interpreterFactory,
        )

    @VisibleForTesting
    fun clearVocabCache() {
        synchronized(this) {
            classifierVocab = null
            nerVocab = null
            nerLabelMap = null
        }
    }

    private fun loadClassifierVocab(
        context: Context,
        vocabName: String = "vocab.txt",
    ): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        context.assets.open(vocabName).bufferedReader().useLines { lines ->
            lines.forEachIndexed { index, line ->
                map[line] = index
            }
        }
        return map
    }

    private fun loadNerVocab(
        context: Context,
        vocabName: String = "ner_vocab.txt",
    ): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        context.assets.open(vocabName).bufferedReader().useLines { lines ->
            lines.forEachIndexed { index, line ->
                map[line] = index
            }
        }
        return map
    }

    private fun loadNerLabelMap(
        context: Context,
        labelMapName: String = "ner_label_map.json",
    ): Map<Int, String> {
        val jsonStr = context.assets.open(labelMapName).bufferedReader().readText()
        if (jsonStr.isBlank()) {
            return emptyMap()
        }
        val json = JSONObject(jsonStr)
        val idToLabelObj = json.optJSONObject("id_to_label") ?: return emptyMap()
        val map = mutableMapOf<Int, String>()
        idToLabelObj.keys().forEach { key ->
            map[key.toInt()] = idToLabelObj.getString(key)
        }
        return map
    }
}
