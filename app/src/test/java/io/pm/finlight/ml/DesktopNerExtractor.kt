package io.pm.finlight.ml

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Test-only [SmsEntityExtractor] that runs TFLite inference via a Python subprocess.
 *
 * Tokenization and BIO post-processing are done in pure Kotlin (reusing [WordPieceTokenizer]).
 * Only the raw matrix multiply is delegated to Python, which natively supports the
 * model's SELECT_TF_OPS / Flex ops on macOS.
 *
 * @param modelPath Absolute path to the sms_ner.tflite model file.
 * @param vocabPath Absolute path to the ner_vocab.txt file.
 * @param labelMapPath Absolute path to the ner_label_map.json file.
 * @param pythonPath Path to the python3 executable (default: "python3").
 * @param inferenceScriptPath Absolute path to run_tflite_inference.py.
 */
class DesktopNerExtractor(
    private val modelPath: String,
    private val vocabPath: String,
    private val labelMapPath: String,
    private val pythonPath: String = "python3",
    private val inferenceScriptPath: String,
) : SmsEntityExtractor {
    private val tokenizer: WordPieceTokenizer
    private val idToLabel: Map<Int, String>

    init {
        // Load vocabulary
        val vocab = mutableMapOf<String, Int>()
        File(vocabPath).bufferedReader().useLines { lines ->
            lines.forEachIndexed { index, line -> vocab[line] = index }
        }
        tokenizer = WordPieceTokenizer(vocab)

        // Load label map
        val map = mutableMapOf<Int, String>()
        File(labelMapPath).useLines { lines ->
            val regex = Regex("""\s*"(\d+)":\s*"([^"]+)",""")
            lines.forEach { line ->
                val match = regex.find(line) ?: Regex("""\s*"(\d+)":\s*"([^"]+)"\s*""").find(line)
                if (match != null) {
                    map[match.groupValues[1].toInt()] = match.groupValues[2]
                }
            }
        }
        idToLabel = map
    }

    override fun extract(text: String): Map<String, String> {
        // Step 1: Tokenize (pure Kotlin, identical to Android)
        val result = tokenizer.tokenize(text)

        // Step 2: Write inputs to temp .npz file
        val inputFile = File.createTempFile("ner_input_", ".npz")
        val outputFile = File.createTempFile("ner_output_", ".npy")
        try {
            writeNpz(inputFile, result.inputIds, result.attentionMask)

            // Step 3: Run Python inference
            val process =
                ProcessBuilder(
                    pythonPath,
                    inferenceScriptPath,
                    modelPath,
                    inputFile.absolutePath,
                    outputFile.absolutePath,
                ).redirectErrorStream(true).start()

            val exitCode = process.waitFor()
            if (exitCode != 0) {
                val stderr = process.inputStream.bufferedReader().readText()
                System.err.println("Python inference failed (exit=$exitCode): $stderr")
                return emptyMap()
            }

            // Step 4: Read output logits from .npy file
            val logits = readNpy(outputFile)

            // Step 5: Argmax → label IDs
            val numLabels = idToLabel.size
            val predictions =
                IntArray(WordPieceTokenizer.MAX_SEQ_LENGTH) { pos ->
                    var maxIdx = 0
                    var maxVal = logits[pos * numLabels]
                    for (l in 1 until numLabels) {
                        val v = logits[pos * numLabels + l]
                        if (v > maxVal) {
                            maxVal = v
                            maxIdx = l
                        }
                    }
                    maxIdx
                }

            // Step 6: BIO post-processing (identical to NerExtractor.extractEntities)
            return extractEntities(predictions, result)
        } finally {
            inputFile.delete()
            outputFile.delete()
        }
    }

    // ---- BIO Post-Processing (mirrors NerExtractor exactly) ----

    private fun extractEntities(
        predictions: IntArray,
        tokenResult: WordPieceTokenizer.TokenizationResult,
    ): Map<String, String> {
        data class EntitySpan(val type: String, val tokens: MutableList<String>)

        val spans = mutableListOf<EntitySpan>()
        var currentSpan: EntitySpan? = null

        for (i in predictions.indices) {
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
                    if (currentSpan != null) spans.add(currentSpan)
                    currentSpan = EntitySpan(label.removePrefix("B-"), mutableListOf(tokenResult.tokens[i]))
                }
                label.startsWith("I-") && currentSpan != null -> {
                    val entityType = label.removePrefix("I-")
                    if (entityType == currentSpan.type) {
                        currentSpan.tokens.add(tokenResult.tokens[i])
                    } else {
                        spans.add(currentSpan)
                        currentSpan = EntitySpan(entityType, mutableListOf(tokenResult.tokens[i]))
                    }
                }
                else -> {
                    if (currentSpan != null) {
                        spans.add(currentSpan)
                        currentSpan = null
                    }
                }
            }
        }
        if (currentSpan != null) spans.add(currentSpan)

        val entityMap = mutableMapOf<String, MutableList<String>>()
        for (span in spans) {
            val merged = mergeSubwordTokens(span.tokens)
            if (merged.isNotBlank()) {
                entityMap.getOrPut(span.type) { mutableListOf() }.add(merged)
            }
        }
        return entityMap.mapValues { (type, values) ->
            val uniqueValues = values.distinct()
            var joined = uniqueValues.joinToString(", ")

            if (type == "AMOUNT") {
                joined =
                    joined.replace(Regex("(?i)^(?:rs\\.?|inr\\.?|₹)\\s*"), "")
                        .replace(Regex(",(?=\\d{2,3})"), "")
            }
            joined
        }
    }

    private fun mergeSubwordTokens(tokens: List<String>): String {
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

    private fun cleanupPunctuationSpacing(text: String): String {
        return text
            .replace(Regex("""\s+([.,/:*\-@])"""), "$1")
            .replace(Regex("""([.,/:*\-@])\s+(?=\w)"""), "$1")
            .trimEnd('.', ',', ':', ';', '-', '@')
    }

    // ---- NumPy I/O helpers ----

    /**
     * Write a minimal .npz (zip of .npy arrays) containing input_ids and attention_mask.
     * Uses the NumPy v1.0 format: magic + version + header + raw data.
     */
    private fun writeNpz(
        file: File,
        inputIds: IntArray,
        attentionMask: IntArray,
    ) {
        val zipOut = java.util.zip.ZipOutputStream(file.outputStream())

        fun writeNpyEntry(
            name: String,
            data: IntArray,
        ) {
            zipOut.putNextEntry(java.util.zip.ZipEntry("$name.npy"))
            val header = "{'descr': '<i4', 'fortran_order': False, 'shape': (1, ${data.size}), }"
            val padLen = 64 - ((10 + header.length) % 64)
            val paddedHeader = header + " ".repeat(padLen - 1) + "\n"
            // NumPy magic: \x93NUMPY
            zipOut.write(
                byteArrayOf(0x93.toByte(), 'N'.code.toByte(), 'U'.code.toByte(), 'M'.code.toByte(), 'P'.code.toByte(), 'Y'.code.toByte()),
            )
            // Version 1.0
            zipOut.write(byteArrayOf(1, 0))
            // Header length (little-endian uint16)
            val headerLen = paddedHeader.length
            zipOut.write(byteArrayOf((headerLen and 0xFF).toByte(), ((headerLen shr 8) and 0xFF).toByte()))
            zipOut.write(paddedHeader.toByteArray(Charsets.US_ASCII))
            // Raw int32 data (little-endian)
            val buf = ByteBuffer.allocate(data.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            for (v in data) buf.putInt(v)
            zipOut.write(buf.array())
            zipOut.closeEntry()
        }

        writeNpyEntry("input_ids", inputIds)
        writeNpyEntry("attention_mask", attentionMask)
        zipOut.close()
    }

    /**
     * Read a .npy float32 array, returning flattened float values.
     * Supports NumPy format v1.0 with '<f4' (little-endian float32) dtype.
     */
    private fun readNpy(file: File): FloatArray {
        val bytes = file.readBytes()
        // Verify magic: \x93NUMPY
        require(bytes[0] == 0x93.toByte() && bytes[1] == 'N'.code.toByte()) { "Not a valid .npy file" }

        // Version
        val major = bytes[6].toInt()
        val headerLen =
            if (major >= 2) {
                // v2.0: 4-byte little-endian header length at offset 8
                ByteBuffer.wrap(bytes, 8, 4).order(ByteOrder.LITTLE_ENDIAN).int
            } else {
                // v1.0: 2-byte little-endian header length at offset 8
                ByteBuffer.wrap(bytes, 8, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
            }
        val dataOffset = if (major >= 2) 12 + headerLen else 10 + headerLen

        // Parse as float32 little-endian
        val numFloats = (bytes.size - dataOffset) / 4
        val buf = ByteBuffer.wrap(bytes, dataOffset, numFloats * 4).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(numFloats) { buf.float }
    }
}
