package io.pm.finlight.ui.screens

import android.content.Context
import android.net.Uri
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
import io.pm.finlight.ml.SmsClassifier
import io.pm.finlight.DEFAULT_IGNORE_PHRASES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

data class BatchStatus(
    val total: Int = 0,
    val processed: Int = 0,
    val isRunning: Boolean = false,
    val resultJson: String? = null
)

class BatchAnalysisViewModel(private val context: Context) : ViewModel() {
    private val classifier = SmsClassifier(context)
    var status by mutableStateOf(BatchStatus())
        private set

    fun processFile(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                updateStatus { it.copy(isRunning = true, total = 0, processed = 0, resultJson = null) }
                
                val content = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).readText()
                } ?: throw Exception("Could not read file")

                val jsonArray = if (content.trim().startsWith("<")) {
                    parseXmlToJson(content)
                } else {
                    JSONArray(content)
                }

                val total = jsonArray.length()
                updateStatus { it.copy(total = total) }

                val results = JSONArray()

                for (i in 0 until total) {
                    val item = jsonArray.getJSONObject(i)
                    // Support both common schema variations
                    val body = item.optString("body", item.optString("message", ""))
                    val address = item.optString("address", "") // Needed for Sender Rules
                    
                    // 1. Run Classifier (Returns Float 0.0 - 1.0)
                    val score = classifier.classify(body)
                    
                    // 2. Run Ignore Rules (Mimic Production App)
                    // Check Body Rules
                    val ignoreBodyRule = DEFAULT_IGNORE_PHRASES
                        .filter { it.type == io.pm.finlight.RuleType.BODY_PHRASE }
                        .find { java.util.regex.Pattern.compile(it.pattern, java.util.regex.Pattern.CASE_INSENSITIVE).matcher(body).find() }
                    
                    // Check Sender Rules
                    val ignoreSenderRule = DEFAULT_IGNORE_PHRASES
                        .filter { it.type == io.pm.finlight.RuleType.SENDER }
                        .find { matchesSender(address, it.pattern) }

                    val isIgnored = ignoreBodyRule != null || ignoreSenderRule != null
                    
                    // logic: If ignored by rule -> Not a transaction. Else -> trust ML (custom threshold)
                    val isTransaction = !isIgnored && score > 0.5f 
                    
                    // Enrich Object with explicit casts
                    item.put("ml_score", score.toDouble())
                    item.put("ml_predicted_is_transaction", isTransaction)
                    item.put("ml_rule_ignore", isIgnored)
                    if (isIgnored) {
                        item.put("ml_ignore_reason", ignoreBodyRule?.pattern ?: ignoreSenderRule?.pattern)
                    }

                    results.put(item)

                    // Update Progress every 10 items to avoid UI spam
                    if (i % 10 == 0) {
                        updateStatus { it.copy(processed = i + 1) }
                    }
                }

                val finalJson = results.toString(2)
                updateStatus { it.copy(isRunning = false, processed = total, resultJson = finalJson) }
                
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
                updateStatus { it.copy(isRunning = false) }
            }
        }
    }

    // Helper for Wildcard Sender Matching (e.g. *Jio* matches VM-JioNet)
    private fun matchesSender(sender: String, pattern: String): Boolean {
        // Convert wildcard to regex
        // Escape special regex chars but allow * as .*
        val regex = pattern
             .replace(".", "\\\\.") // Escape dots first
             .replace("*", ".*")    // Convert wildcard
        
        // Sender match usually contains partial match logic or exact?
        // DefaultIgnoreRules patterns like *HDFCMF imply we want to match ends.
        // Let's use simple find() or matches() depending on intent.
        // ".*HDFCMF" -> matches()
        return java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.CASE_INSENSITIVE).matcher(sender).matches()
    }

    private fun parseXmlToJson(xmlContent: String): JSONArray {
        val entries = JSONArray()
        try {
            val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val inputSource = org.xml.sax.InputSource(java.io.StringReader(xmlContent))
            val doc = builder.parse(inputSource)
            
            val smsNodes = doc.getElementsByTagName("sms")
            
            for (i in 0 until smsNodes.length) {
                val node = smsNodes.item(i)
                if (node.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                    val element = node as org.w3c.dom.Element
                    val address = element.getAttribute("address") ?: ""
                    val date = element.getAttribute("date") ?: "0"
                    val body = element.getAttribute("body") ?: ""
                    
                    val jsonObj = JSONObject()
                    jsonObj.put("address", address)
                    jsonObj.put("date", date.toLongOrNull() ?: 0L)
                    jsonObj.put("body", body)
                    
                    entries.put(jsonObj)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Return empty or partial list on error
        }
        return entries
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
            status.resultJson?.let { json ->
                context.contentResolver.openOutputStream(it)?.use { os ->
                    os.write(json.toByteArray())
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
                "Load a JSON or XML dump to run the TFLite model.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (status.isRunning) {
                LinearProgressIndicator(
                    progress = { if (status.total > 0) status.processed.toFloat() / status.total.toFloat() else 0f },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Processing: ${status.processed} / ${status.total}")
            } else {
                Button(
                    onClick = { filePickerLauncher.launch("*/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.UploadFile, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Load Dump & Run")
                }

                if (status.resultJson != null) {
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
                        "Processed ${status.total} items.",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
