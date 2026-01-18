package io.pm.finlight.ui.screens

import android.content.Context
import android.net.Uri
import android.util.JsonReader
import android.util.JsonWriter
import android.util.Xml
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.pm.finlight.DEFAULT_IGNORE_PHRASES
import io.pm.finlight.ml.SmsClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream

data class BatchStatus(
    val processed: Int = 0,
    val isRunning: Boolean = false,
    val resultFile: File? = null
)

class BatchAnalysisViewModel(private val context: Context) : ViewModel() {
    private val classifier = SmsClassifier(context)
    var status by mutableStateOf(BatchStatus())
        private set

    fun processFile(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                updateStatus { it.copy(isRunning = true, processed = 0, resultFile = null) }
                
                // Temp output file to avoid OOM by holding results in memory
                val outputFile = File(context.cacheDir, "temp_classified_sms.json")
                val jsonWriter = JsonWriter(outputFile.writer())
                jsonWriter.setIndent("  ")
                jsonWriter.beginArray()

                var processedCount = 0

                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bufferedIn = BufferedInputStream(inputStream)
                    bufferedIn.mark(10)
                    val firstByte = bufferedIn.read()
                    bufferedIn.reset()

                    if (firstByte == '<'.code) {
                        processedCount = processXmlStream(bufferedIn, jsonWriter) { count ->
                            // Update UI every 50 items
                            if (count % 50 == 0) updateStatus { it.copy(processed = count) }
                        }
                    } else {
                         processedCount = processJsonStream(bufferedIn, jsonWriter) { count ->
                            if (count % 50 == 0) updateStatus { it.copy(processed = count) }
                        }
                    }
                }
                
                jsonWriter.endArray()
                jsonWriter.close()

                updateStatus { it.copy(isRunning = false, processed = processedCount, resultFile = outputFile) }
                
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
                updateStatus { it.copy(isRunning = false) }
            }
        }
    }

    // Streaming XML Parser (XmlPullParser)
    private fun processXmlStream(input: InputStream, writer: JsonWriter, onProgress: suspend (Int) -> Unit): Int {
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
                
                processSingleItem(address, body, date, writer)
                count++
                runBlocking { onProgress(count) }
            }
            eventType = parser.next()
        }
        return count
    }

    // Streaming JSON Parser (JsonReader)
    private fun processJsonStream(input: InputStream, writer: JsonWriter, onProgress: suspend (Int) -> Unit): Int {
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

                processSingleItem(address, body, date, writer)
                count++
                runBlocking { onProgress(count) }
            }
            reader.endArray()
        } catch (e: Exception) {
            // Fallback for nested object? Or just let it fail for now and advise user.
            throw e
        }
        return count
    }

    private fun processSingleItem(address: String, body: String, date: Long, writer: JsonWriter) {
        val score = classifier.classify(body)
        
        // Default Ignore Rules
        val ignoreBodyRule = DEFAULT_IGNORE_PHRASES
            .filter { it.type == io.pm.finlight.RuleType.BODY_PHRASE }
            .find { java.util.regex.Pattern.compile(it.pattern, java.util.regex.Pattern.CASE_INSENSITIVE).matcher(body).find() }
        
        val ignoreSenderRule = DEFAULT_IGNORE_PHRASES
            .filter { it.type == io.pm.finlight.RuleType.SENDER }
            .find { matchesSender(address, it.pattern) }

        val isIgnored = ignoreBodyRule != null || ignoreSenderRule != null
        val isTransaction = !isIgnored && score > 0.5f

        writer.beginObject()
        writer.name("address").value(address)
        writer.name("body").value(body)
        writer.name("date").value(date)
        writer.name("ml_score").value(score.toDouble())
        writer.name("ml_predicted_is_transaction").value(isTransaction)
        writer.name("ml_rule_ignore").value(isIgnored)
        if (isIgnored) {
             writer.name("ml_ignore_reason").value(ignoreBodyRule?.pattern ?: ignoreSenderRule?.pattern)
        }
        writer.endObject()
    }

    // Helper for Wildcard Sender Matching (e.g. *Jio* matches VM-JioNet)
    private fun matchesSender(sender: String, pattern: String): Boolean {
        // Convert wildcard to regex
        // Escape special regex chars but allow * as .*
        val regex = pattern
             .replace(".", "\\\\.") // Escape dots first
             .replace("*", ".*")    // Convert wildcard
        return java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.CASE_INSENSITIVE).matcher(sender).matches()
    }

    private suspend fun updateStatus(update: (BatchStatus) -> BatchStatus) {
        withContext(Dispatchers.Main) {
            status = update(status)
        }
    }
}

class BatchAnalysisViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BatchAnalysisViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BatchAnalysisViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchAnalysisScreen(
    navController: androidx.navigation.NavController
) {
    val context = LocalContext.current
    val viewModel: BatchAnalysisViewModel = viewModel(factory = BatchAnalysisViewModelFactory(context))
    val status = viewModel.status

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.processFile(it) }
    }

    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            status.resultFile?.let { tempFile ->
                context.contentResolver.openOutputStream(it)?.use { os ->
                    tempFile.inputStream().use { input ->
                        input.copyTo(os)
                    }
                }
                Toast.makeText(context, "Saved!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dev Tools: Batch Classifier") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.BugReport,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                "SMS Batch Classifier",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                "Streaming processing for large datasets.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (status.isRunning) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Processed: ${status.processed}")
            } else {
                Button(
                    onClick = { filePickerLauncher.launch("*/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.UploadFile, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Load Dump & Run")
                }

                if (status.resultFile != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { saveFileLauncher.launch("classified_sms.json") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Check, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save Result JSON")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Processed ${status.processed} items.",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
