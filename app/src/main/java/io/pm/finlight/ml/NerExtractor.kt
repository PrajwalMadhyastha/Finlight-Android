package io.pm.finlight.ml

import android.content.Context
import androidx.annotation.VisibleForTesting
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import org.json.JSONObject
import org.tensorflow.lite.Interpreter

/**
 * On-device NER (Named Entity Recognition) extractor using a MobileBERT TFLite model.
 *
 * Extracts structured entities (MERCHANT, AMOUNT, ACCOUNT, BALANCE) from SMS messages.
 *
 * Usage:
 * ```
 * val extractor = NerExtractor(context)
 * val entities = extractor.extract("Your account debited Rs 500 at Amazon")
 * // entities = {MERCHANT=Amazon, AMOUNT=Rs 500, ACCOUNT=...}
 * extractor.close()
 * ```
 */
class NerExtractor private constructor(
    private val context: Context?,
    private val modelName: String,
    private val vocabName: String,
    private val labelMapName: String,
    preloadedTokenizer: WordPieceTokenizer?,
    preloadedInterpreter: Interpreter?,
    preloadedLabelMap: Map<Int, String>?,
) : SmsEntityExtractor {

    companion object {
        private const val DEFAULT_MODEL = "sms_ner.tflite"
        private const val DEFAULT_VOCAB = "ner_vocab.txt"
        private const val DEFAULT_LABEL_MAP = "ner_label_map.json"
    }

    private var interpreter: Interpreter? = preloadedInterpreter
    private var tokenizer: WordPieceTokenizer? = preloadedTokenizer
    private var idToLabel: Map<Int, String> = preloadedLabelMap ?: emptyMap()

    private val interpreterLock = Any()

    /**
     * Primary constructor for production use.
     * Loads the TFLite model, vocabulary, and label map from assets.
     */
    constructor(
        context: Context,
        modelName: String = DEFAULT_MODEL,
        vocabName: String = DEFAULT_VOCAB,
        labelMapName: String = DEFAULT_LABEL_MAP,
    ) : this(context, modelName, vocabName, labelMapName, null, null, null) {
        loadModel()
        loadVocabulary()
        loadLabelMap()
    }

    /**
     * Internal constructor for unit testing.
     */
    @VisibleForTesting
    internal constructor(
        tokenizer: WordPieceTokenizer,
        interpreter: Interpreter?,
        labelMap: Map<Int, String>,
    ) : this(null, "", "", "", tokenizer, interpreter, labelMap)

    private fun loadModel() {
        val ctx = context ?: return
        val fd = ctx.assets.openFd(modelName)
        val fis = FileInputStream(fd.fileDescriptor)
        val channel = fis.channel
        val modelBuffer = channel.map(
            FileChannel.MapMode.READ_ONLY,
            fd.startOffset,
            fd.declaredLength,
        )

        val options = Interpreter.Options()
        options.setNumThreads(4)
        interpreter = Interpreter(modelBuffer, options)
    }

    private fun loadVocabulary() {
        val ctx = context ?: return
        val vocab = mutableMapOf<String, Int>()
        ctx.assets.open(vocabName).bufferedReader().useLines { lines ->
            lines.forEachIndexed { index, line ->
                vocab[line] = index
            }
        }
        tokenizer = WordPieceTokenizer(vocab)
    }

    private fun loadLabelMap() {
        val ctx = context ?: return
        val jsonStr = ctx.assets.open(labelMapName).bufferedReader().readText()
        val json = JSONObject(jsonStr)
        val idToLabelObj = json.getJSONObject("id_to_label")
        val map = mutableMapOf<Int, String>()
        idToLabelObj.keys().forEach { key ->
            map[key.toInt()] = idToLabelObj.getString(key)
        }
        idToLabel = map
    }

    /**
     * Extract named entities from an SMS message.
     *
     * @param text The raw SMS body.
     * @return Map of entity type to extracted text, e.g. {"MERCHANT": "Amazon", "AMOUNT": "Rs 500"}.
     *         Only entities found in the text are included.
     */
    override fun extract(text: String): Map<String, String> {
        val interp = interpreter ?: return emptyMap()
        val tok = tokenizer ?: return emptyMap()

        // Step 1: Tokenize
        val result = tok.tokenize(text)

        // Step 2: Prepare input buffers (int32, 4 bytes each)
        val inputIdsBuffer = ByteBuffer.allocateDirect(
            WordPieceTokenizer.MAX_SEQ_LENGTH * 4
        ).apply {
            order(ByteOrder.nativeOrder())
            for (id in result.inputIds) putInt(id)
            rewind()
        }

        val attentionMaskBuffer = ByteBuffer.allocateDirect(
            WordPieceTokenizer.MAX_SEQ_LENGTH * 4
        ).apply {
            order(ByteOrder.nativeOrder())
            for (mask in result.attentionMask) putInt(mask)
            rewind()
        }

        // Step 3: Prepare output buffer [1, 128, 9] float32
        val numLabels = idToLabel.size
        val outputBuffer = ByteBuffer.allocateDirect(
            1 * WordPieceTokenizer.MAX_SEQ_LENGTH * numLabels * 4
        ).apply {
            order(ByteOrder.nativeOrder())
        }

        // Step 4: Run inference — match inputs by NAME, not index
        val logits = synchronized(interpreterLock) {
            val inputMap = mutableMapOf<Int, Any>()
            val inputCount = interp.inputTensorCount

            for (i in 0 until inputCount) {
                val tensor = interp.getInputTensor(i)
                val name = tensor.name()
                when {
                    name.contains("input_ids") -> inputMap[i] = inputIdsBuffer
                    name.contains("attention_mask") -> inputMap[i] = attentionMaskBuffer
                }
            }

            val outputMap = mapOf(0 to outputBuffer)
            interp.runForMultipleInputsOutputs(
                inputMap.toSortedMap().values.toTypedArray(),
                outputMap,
            )

            // Parse output logits
            outputBuffer.rewind()
            Array(WordPieceTokenizer.MAX_SEQ_LENGTH) { FloatArray(numLabels) }.also { arr ->
                for (pos in 0 until WordPieceTokenizer.MAX_SEQ_LENGTH) {
                    for (label in 0 until numLabels) {
                        arr[pos][label] = outputBuffer.float
                    }
                }
            }
        }

        // Step 5: Argmax → label IDs
        val predictions = IntArray(WordPieceTokenizer.MAX_SEQ_LENGTH) { pos ->
            logits[pos].indices.maxByOrNull { logits[pos][it] } ?: 0
        }

        // Step 6: Post-process BIO tags into entity spans
        return extractEntities(predictions, result)
    }

    /**
     * Post-process BIO predictions + token info into entity text spans.
     *
     * Groups consecutive B-TYPE / I-TYPE tags, reconstructs text from subword
     * tokens (stripping "##" prefixes and joining), and returns a map of
     * entity type → extracted text.
     *
     * For entities appearing multiple times, values are joined with ", ".
     */
    @VisibleForTesting
    internal fun extractEntities(
        predictions: IntArray,
        tokenResult: WordPieceTokenizer.TokenizationResult,
    ): Map<String, String> {
        data class EntitySpan(val type: String, val tokens: MutableList<String>)

        val spans = mutableListOf<EntitySpan>()
        var currentSpan: EntitySpan? = null

        for (i in predictions.indices) {
            // Skip special tokens and padding
            if (tokenResult.wordIds[i] == -1) {
                if (currentSpan != null) {
                    spans.add(currentSpan)
                    currentSpan = null
                }
                continue
            }

            val label = idToLabel[predictions[i]] ?: "O"

            when {
                label.startsWith("B-") -> {
                    // Start a new entity
                    if (currentSpan != null) spans.add(currentSpan)
                    val entityType = label.removePrefix("B-")
                    currentSpan = EntitySpan(entityType, mutableListOf(tokenResult.tokens[i]))
                }
                label.startsWith("I-") && currentSpan != null -> {
                    val entityType = label.removePrefix("I-")
                    if (entityType == currentSpan.type) {
                        // Continue the current entity
                        currentSpan.tokens.add(tokenResult.tokens[i])
                    } else {
                        // Type mismatch — close current and start new
                        spans.add(currentSpan)
                        currentSpan = EntitySpan(entityType, mutableListOf(tokenResult.tokens[i]))
                    }
                }
                else -> {
                    // "O" label or orphan I- tag
                    if (currentSpan != null) {
                        spans.add(currentSpan)
                        currentSpan = null
                    }
                }
            }
        }
        if (currentSpan != null) spans.add(currentSpan)

        // Merge tokens into text, handling WordPiece "##" subword joining
        val entityMap = mutableMapOf<String, MutableList<String>>()
        for (span in spans) {
            val text = mergeSubwordTokens(span.tokens)
            if (text.isNotBlank()) {
                entityMap.getOrPut(span.type) { mutableListOf() }.add(text)
            }
        }

        return entityMap.mapValues { (type, values) -> 
            val uniqueValues = values.distinct()
            var joined = uniqueValues.joinToString(", ")
            
            // Post-process AMOUNT to remove currency prefixes and commas
            if (type == "AMOUNT") {
                joined = joined.replace(Regex("(?i)^(?:rs\\.?|inr\\.?|₹)\\s*"), "")
                               .replace(Regex(",(?=\\d{2,3})"), "") // Remove Indian-style commas
            }
            joined
        }
    }

    /**
     * Merge WordPiece subword tokens back into readable text.
     *
     * "##" prefixed tokens are joined directly to the previous token
     * (no space), while regular tokens are space-separated.
     *
     * After joining, cleans up spacing artifacts from BERT's BasicTokenizer
     * which splits punctuation into separate tokens:
     * - "rs . 267 . 00" → "Rs.267.00"
     * - "a / c" → "A/C"
     * - "1 , 41 , 453" → "1,41,453"
     */
    @VisibleForTesting
    internal fun mergeSubwordTokens(tokens: List<String>): String {
        val sb = StringBuilder()
        for (token in tokens) {
            if (token.startsWith("##")) {
                sb.append(token.removePrefix("##"))
            } else {
                if (sb.isNotEmpty()) sb.append(" ")
                sb.append(token)
            }
        }
        return cleanupPunctuationSpacing(sb.toString())
    }

    /**
     * Remove spurious spaces around punctuation that BERT's tokenizer introduces.
     *
     * Handles: periods, commas, forward slashes, colons, hyphens, and asterisks
     * that appear as isolated tokens with spaces on both sides.
     * Also strips trailing punctuation that BERT includes from sentence boundaries.
     */
    private fun cleanupPunctuationSpacing(text: String): String {
        return text
            // Remove space before punctuation: "Rs . 267" → "Rs. 267"
            .replace(Regex("""\s+([.,/:*\-@;])"""), "$1")
            // Remove space after punctuation: "Rs.267" stays, "Rs. 267" → "Rs.267"
            // But only when followed by alphanumeric (avoid collapsing "Rs. " at end)
            .replace(Regex("""([.,/:*\-@;])\s+(?=\w)"""), "$1")
            // Strip trailing punctuation from sentence boundaries
            .trimEnd('.', ',', ':', ';', '-', '@')
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
    }
}
