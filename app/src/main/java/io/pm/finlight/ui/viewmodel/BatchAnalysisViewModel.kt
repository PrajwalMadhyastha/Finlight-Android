package io.pm.finlight.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.JsonReader
import android.util.JsonWriter
import android.util.Xml
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.pm.finlight.DEFAULT_IGNORE_PHRASES
import io.pm.finlight.RuleType
import io.pm.finlight.ml.MlModelFactory
import io.pm.finlight.ml.SmsClassifier
import io.pm.finlight.ml.SmsEntityExtractor
import io.pm.finlight.utils.DefaultDispatcherProvider
import io.pm.finlight.utils.DispatcherProvider
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.util.regex.Pattern

data class BatchStatus(
    val processed: Int = 0,
    val isRunning: Boolean = false,
    val resultFile: File? = null,
    val errorMessage: String? = null,
)

private data class CompiledIgnoreRule(
    val rawPattern: String,
    val pattern: Pattern,
)

class BatchAnalysisViewModel(
    context: Context,
    val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
    private val nerExtractorProvider: () -> SmsEntityExtractor = {
        MlModelFactory.getNerExtractor(context.applicationContext ?: context)
    },
    private val classifier: SmsClassifier =
        MlModelFactory.getClassifier(context.applicationContext ?: context),
) : ViewModel() {
    private val context: Context = context.applicationContext ?: context

    var status by mutableStateOf(BatchStatus())
        private set

    private val _uiEvent = Channel<String>(Channel.BUFFERED)
    val uiEvent: Flow<String> = _uiEvent.receiveAsFlow()

    private val compiledBodyRules: List<CompiledIgnoreRule> by lazy {
        DEFAULT_IGNORE_PHRASES
            .filter { it.type == RuleType.BODY_PHRASE }
            .map {
                CompiledIgnoreRule(
                    rawPattern = it.pattern,
                    pattern = Pattern.compile(it.pattern, Pattern.CASE_INSENSITIVE),
                )
            }
    }

    private val compiledSenderRules: List<CompiledIgnoreRule> by lazy {
        DEFAULT_IGNORE_PHRASES
            .filter { it.type == RuleType.SENDER }
            .map {
                val regex =
                    it.pattern
                        .replace(".", "\\.")
                        .replace("*", ".*")
                CompiledIgnoreRule(
                    rawPattern = it.pattern,
                    pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE),
                )
            }
    }

    fun processFile(uri: Uri) {
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                updateStatus { it.copy(isRunning = true, processed = 0, resultFile = null, errorMessage = null) }

                // Temp output file to avoid OOM by holding results in memory
                val outputFile = File(context.cacheDir, "temp_classified_sms.json")
                var processedCount = 0

                outputFile.writer().buffered().use { fileWriter ->
                    JsonWriter(fileWriter).use { jsonWriter ->
                        jsonWriter.setIndent("  ")
                        jsonWriter.beginArray()

                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            nerExtractorProvider().use { nerExtractor ->
                                val bufferedIn = BufferedInputStream(inputStream)
                                bufferedIn.mark(10)
                                val firstByte = bufferedIn.read()
                                bufferedIn.reset()

                                if (firstByte == '<'.code) {
                                    processedCount =
                                        processXmlStream(bufferedIn, jsonWriter, nerExtractor) { count ->
                                            // Update UI every 50 items
                                            if (count % 50 == 0) updateStatus { it.copy(processed = count) }
                                        }
                                } else {
                                    processedCount =
                                        processJsonStream(bufferedIn, jsonWriter, nerExtractor) { count ->
                                            if (count % 50 == 0) updateStatus { it.copy(processed = count) }
                                        }
                                }
                            }
                        }

                        jsonWriter.endArray()
                    }
                }

                updateStatus { it.copy(isRunning = false, processed = processedCount, resultFile = outputFile) }
            } catch (e: Exception) {
                e.printStackTrace()
                val errorMsg = "Error: ${e.message}"
                _uiEvent.send(errorMsg)
                updateStatus { it.copy(isRunning = false, errorMessage = errorMsg) }
            }
        }
    }

    // Streaming XML Parser (XmlPullParser)
    private suspend fun processXmlStream(
        input: InputStream,
        writer: JsonWriter,
        nerExtractor: SmsEntityExtractor,
        onProgress: suspend (Int) -> Unit,
    ): Int {
        var count = 0
        val parser = Xml.newPullParser()
        parser.setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input.reader())

        var eventType = parser.eventType
        while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "sms") {
                val address = parser.getAttributeValue(null, "address") ?: ""
                val date = parser.getAttributeValue(null, "date")?.toLongOrNull() ?: 0L
                val body = parser.getAttributeValue(null, "body") ?: ""

                processSingleItem(address, body, date, writer, nerExtractor)
                count++
                onProgress(count)
            }
            eventType = parser.next()
        }
        return count
    }

    // Streaming JSON Parser (JsonReader)
    private suspend fun processJsonStream(
        input: InputStream,
        writer: JsonWriter,
        nerExtractor: SmsEntityExtractor,
        onProgress: suspend (Int) -> Unit,
    ): Int {
        var count = 0
        val reader = JsonReader(input.reader())

        // Check if root is array or object (dump vs flat)
        // Implementation assumption: Input is Array of Objects
        // If it's a legacy dump object {"count": N, "smses": [...]}, this simple reader will fail.
        // But for "Batch Classifier", we mostly consume the flat arrays we produce or simple dumps.
        // Let's assume array for now or try to detect.

        try {
            reader.beginArray()
            while (reader.hasNext()) {
                var address = ""
                var body = ""
                var date = 0L

                reader.beginObject()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    when (name) {
                        "address" -> address = reader.nextString()
                        "body", "message" -> body = reader.nextString()
                        "date" -> date = reader.nextLong() // safely parses string numbers too
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()

                processSingleItem(address, body, date, writer, nerExtractor)
                count++
                onProgress(count)
            }
            reader.endArray()
        } catch (e: Exception) {
            // Fallback for nested object? Or just let it fail for now and advise user.
            throw e
        }
        return count
    }

    private fun processSingleItem(
        address: String,
        body: String,
        date: Long,
        writer: JsonWriter,
        nerExtractor: SmsEntityExtractor,
    ) {
        val score = classifier.classify(body)

        // Default Ignore Rules
        val ignoreBodyRule = compiledBodyRules.find { it.pattern.matcher(body).find() }
        val ignoreSenderRule = compiledSenderRules.find { it.pattern.matcher(address).matches() }

        val isIgnored = ignoreBodyRule != null || ignoreSenderRule != null
        val isTransaction = !isIgnored && score > 0.5f

        // Run NER extraction for transactional-looking messages (score > 0.1 to catch borderline cases)
        val nerEntities =
            if (!isIgnored && score > 0.1f) {
                try {
                    nerExtractor.extract(body)
                } catch (e: Exception) {
                    emptyMap()
                }
            } else {
                emptyMap()
            }

        writer.beginObject()
        writer.name("address").value(address)
        writer.name("body").value(body)
        writer.name("date").value(date)
        writer.name("ml_score").value(score.toDouble())
        writer.name("ml_predicted_is_transaction").value(isTransaction)
        writer.name("ml_rule_ignore").value(isIgnored)
        if (isIgnored) {
            writer.name("ml_ignore_reason").value(ignoreBodyRule?.rawPattern ?: ignoreSenderRule?.rawPattern)
        }
        // Write NER entities as individual fields for easy analysis
        if (nerEntities.isNotEmpty()) {
            writer.name("ner_amount").value(nerEntities["AMOUNT"]?.value)
            writer.name("ner_amount_conf").value(nerEntities["AMOUNT"]?.confidence?.toDouble())
            writer.name("ner_merchant").value(nerEntities["MERCHANT"]?.value)
            writer.name("ner_account").value(nerEntities["ACCOUNT"]?.value)
            nerEntities["BALANCE"]?.let { writer.name("ner_balance").value(it.value) }
        }
        writer.endObject()
    }

    private suspend fun updateStatus(update: (BatchStatus) -> BatchStatus) {
        withContext(dispatcherProvider.main) {
            status = update(status)
        }
    }

    override fun onCleared() {
        super.onCleared()
        classifier.close()
    }
}

class BatchAnalysisViewModelFactory(
    private val context: Context,
    private val nerExtractorProvider: (() -> SmsEntityExtractor)? = null,
    private val classifier: SmsClassifier? = null,
    private val dispatcherProvider: DispatcherProvider? = null,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BatchAnalysisViewModel::class.java)) {
            val appContext = context.applicationContext ?: context
            @Suppress("UNCHECKED_CAST")
            return BatchAnalysisViewModel(
                context = appContext,
                dispatcherProvider = dispatcherProvider ?: DefaultDispatcherProvider(),
                nerExtractorProvider = nerExtractorProvider ?: { MlModelFactory.getNerExtractor(appContext) },
                classifier = classifier ?: MlModelFactory.getClassifier(appContext),
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
