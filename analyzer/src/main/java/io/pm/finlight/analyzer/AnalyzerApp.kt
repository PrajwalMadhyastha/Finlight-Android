package io.pm.finlight.analyzer

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.focusable
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import io.pm.finlight.analyzer.DatasetSmsDump
import io.pm.finlight.SmsMessage
import io.pm.finlight.PotentialTransaction
import io.pm.finlight.PotentialAccount
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

// --- SERIALIZABLE DTOs for State Persistence ---
@Serializable
data class SerializablePotentialAccount(
    val formattedName: String,
    val accountType: String
)

@Serializable
data class SerializablePotentialTransaction(
    val sourceSmsId: Long,
    val smsSender: String,
    val amount: Double,
    val transactionType: String,
    val merchantName: String?,
    val originalMessage: String,
    val potentialAccount: SerializablePotentialAccount? = null,
    val detectedCurrencyCode: String? = null,
    val date: Long
)

@Serializable
data class SerializableReviewItem(
    val id: String,
    val sender: String,
    val body: String,
    val timestamp: Long,
    val isTransaction: Boolean? = null,
    val confidence: Float = 0.0f,
    val silverLabel: SerializablePotentialTransaction? = null,
    val ignoreReason: String? = null // NEW FIELD for Migration
)

// Helper mappers
fun PotentialTransaction.toSerializable() = SerializablePotentialTransaction(
    sourceSmsId = sourceSmsId,
    smsSender = smsSender,
    amount = amount,
    transactionType = transactionType,
    merchantName = merchantName,
    originalMessage = originalMessage,
    potentialAccount = potentialAccount?.let { SerializablePotentialAccount(it.formattedName, it.accountType) },
    detectedCurrencyCode = detectedCurrencyCode,
    date = date
)

fun SerializablePotentialTransaction.toDomain() = PotentialTransaction(
    sourceSmsId = sourceSmsId,
    smsSender = smsSender,
    amount = amount,
    transactionType = transactionType,
    merchantName = merchantName,
    originalMessage = originalMessage,
    potentialAccount = potentialAccount?.let { PotentialAccount(it.formattedName, it.accountType) },
    detectedCurrencyCode = detectedCurrencyCode,
    date = date
)

fun ReviewItem.toSerializable() = SerializableReviewItem(
    id = id,
    sender = sender,
    body = body,
    timestamp = timestamp,
    isTransaction = isTransaction,
    confidence = confidence,
    silverLabel = silverLabel?.toSerializable(),
    ignoreReason = ignoreReason
)

fun SerializableReviewItem.toDomain() = ReviewItem(
    id = id,
    sender = sender,
    body = body,
    timestamp = timestamp,
    isTransaction = isTransaction,
    confidence = confidence,
    silverLabel = silverLabel?.toDomain(),
    ignoreReason = ignoreReason
)

// --- PERSISTENCE UTILS ---
val WORKSPACE_FILE = File(System.getProperty("user.home"), "finlight_labeling_workspace.json")

fun saveWorkspace(items: List<ReviewItem>) {
    try {
        val dtos = items.map { it.toSerializable() }
        val json = Json { prettyPrint = true }
        WORKSPACE_FILE.writeText(json.encodeToString(dtos))
        println("Workspace saved to ${WORKSPACE_FILE.absolutePath}")
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun loadWorkspace(): List<ReviewItem> {
    if (!WORKSPACE_FILE.exists()) return emptyList()
    return try {
        val json = Json { ignoreUnknownKeys = true }
        val dtos = json.decodeFromString<List<SerializableReviewItem>>(WORKSPACE_FILE.readText())
        dtos.map { it.toDomain() }
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}


// Define a simple data model for our UI state
data class ReviewItem(
    val id: String,
    val sender: String,
    val body: String,
    val timestamp: Long,
    var isTransaction: Boolean? = null, // null = undecided, true = yes, false = no
    var confidence: Float = 0.0f,
    var silverLabel: PotentialTransaction? = null, // Regex Result
    var ignoreReason: String? = null // NEW: Stores reason separately from body
)

enum class AppMode {
    REVIEW_QUEUE, // Mid confidence (0.1 - 0.9), prioritizing closest to 0.5
    ALL_TRANSACTIONS // Audit everything classified as Transaction
}

// --- FILE HELPERS ---

fun getProjectFile(name: String): File {
    // Sanitize name
    val safeName = name.replace(Regex("[^a-zA-Z0-9.-]"), "_")
    return File("workspace_$safeName.json")
}

fun listProjects(): List<String> {
    val currentDir = File(".")
    return currentDir.listFiles()
        ?.filter { it.name.startsWith("workspace_") && it.name.endsWith(".json") }
        ?.map { it.name.removePrefix("workspace_").removeSuffix(".json") }
        ?.sorted()
        ?: emptyList()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun App() {
    // --- STATE ---
    var currentProject by remember { mutableStateOf<String?>(null) }
    var projectList by remember { mutableStateOf(emptyList<String>()) }
    var isProjectDropdownOpen by remember { mutableStateOf(false) }

    var smsList by remember { mutableStateOf(emptyList<ReviewItem>()) }
    val history = remember { mutableStateListOf<String>() }
    
    var appMode by remember { mutableStateOf(AppMode.REVIEW_QUEUE) }
    var currentIndex by remember { mutableStateOf(0) }
    var isAnonymizationEnabled by remember { mutableStateOf(true) }
    var statusMessage by remember { mutableStateOf("Ready") }

    val scope = rememberCoroutineScope()
    val classifier = remember { DesktopSmsClassifier() }
    val focusRequester = remember { FocusRequester() }

    // --- HELPER: Save Current Project ---
    fun saveCurrentProject() {
        val proj = currentProject ?: return
        if (smsList.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            try {
                val file = getProjectFile(proj)
                val jsonString = Json { prettyPrint = true }.encodeToString(smsList.map { it.toSerializable() })
                file.writeText(jsonString)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- HELPER: Update Item & Save ---
    fun updateItem(id: String, isTx: Boolean?) {
        // Add to history if this is a Review Action (not undo)
        if (isTx != null) {
            history.add(id)
        }
        smsList = smsList.map {
            if (it.id == id) it.copy(isTransaction = isTx) else it
        }
        saveCurrentProject()
    }
    
    fun undoLast() {
        if (history.isEmpty()) {
            statusMessage = "Nothing to undo"
            return
        }
        val lastId = history.removeLast()
        smsList = smsList.map {
            if (it.id == lastId) it.copy(isTransaction = null) else it
        }
        statusMessage = "Undid last action"
        saveCurrentProject()
    }

    // --- INITIAL LOAD ---
    LaunchedEffect(Unit) {
        // Load list of projects
        projectList = listProjects()
        if (projectList.isNotEmpty()) {
            // Auto-load the first one (or maybe last modified in future)
            val first = projectList.first()
            currentProject = first
            // Load content
            val file = getProjectFile(first)
            if (file.exists()) {
                val json = Json { ignoreUnknownKeys = true }
                val loaded = json.decodeFromString<List<SerializableReviewItem>>(file.readText())
                smsList = loaded.map { it.toDomain() }
                statusMessage = "Loaded project '$first'"
            }
        }
        focusRequester.requestFocus()
    }

    // --- DERIVED STATE ---
    val displayedList by remember(appMode, smsList) {
        derivedStateOf {
            when (appMode) {
                AppMode.REVIEW_QUEUE -> {
                   val undecided = smsList.filter { it.isTransaction == null }
                   // Active Learning: Sort by confidence closest to 0.5 (most confused)
                   undecided.sortedBy { kotlin.math.abs(it.confidence - 0.5f) }
                }
                AppMode.ALL_TRANSACTIONS -> {
                   // Audit Mode: Show EVERYTHING.
                   smsList.sortedByDescending { it.timestamp }
                }
            }
        }
    }

    // Safety check for index
    val validIndex = if (displayedList.isNotEmpty()) currentIndex.coerceIn(0, displayedList.lastIndex) else 0
    val currentItem = displayedList.getOrNull(validIndex)

    MaterialTheme {
        Box(
            modifier = Modifier.fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp) {
                        if (appMode == AppMode.REVIEW_QUEUE && displayedList.isNotEmpty()) {
                             val item = displayedList.getOrNull(currentIndex)
                             if (item != null) {
                                 when (event.key) {
                                     Key.Y -> { updateItem(item.id, true); true }
                                     Key.N -> { updateItem(item.id, false); true }
                                     Key.Backspace -> { undoLast(); true }
                                     else -> false
                                 }
                             } else false
                        } else false
                    } else false
                }
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // --- TOP BAR ---
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {

                // Row 1: Title & Project Selector
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Finlight Labeler", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (currentProject != null) "Project: $currentProject" else "No Project Selected",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Project Dropdown
                        Box {
                            Button(onClick = { isProjectDropdownOpen = true }) {
                                Text(if (currentProject != null) "Switch Project ▼" else "Select Project ▼")
                            }
                            DropdownMenu(
                                expanded = isProjectDropdownOpen,
                                onDismissRequest = { isProjectDropdownOpen = false }
                            ) {
                                projectList.forEach { proj ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(proj)
                                                IconButton(
                                                    onClick = {
                                                        // DELETE PROJECT
                                                        val file = getProjectFile(proj)
                                                        if (file.exists()) file.delete()

                                                        // Refresh List
                                                        val newList = projectList.filter { it != proj }
                                                        projectList = newList

                                                        // If current was deleted, switch or clear
                                                        if (currentProject == proj) {
                                                            if (newList.isNotEmpty()) {
                                                                val next = newList.first()
                                                                currentProject = next
                                                                val nextFile = getProjectFile(next)
                                                                if (nextFile.exists()) {
                                                                    val json = Json { ignoreUnknownKeys = true }
                                                                    val loaded = json.decodeFromString<List<SerializableReviewItem>>(nextFile.readText())
                                                                    smsList = loaded.map { it.toDomain() }
                                                                    statusMessage = "Switched to '$next'"
                                                                }
                                                            } else {
                                                                currentProject = null
                                                                smsList = emptyList()
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(androidx.compose.material.icons.Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray)
                                                }
                                            }
                                        },
                                        onClick = {
                                            isProjectDropdownOpen = false
                                            currentProject = proj
                                            val file = getProjectFile(proj)
                                            if (file.exists()) {
                                                val json = Json { ignoreUnknownKeys = true }
                                                val loaded = json.decodeFromString<List<SerializableReviewItem>>(file.readText())
                                                smsList = loaded.map { it.toDomain() }
                                                statusMessage = "Switched to '$proj'"
                                                currentIndex = 0
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // NEW IMPORT BUTTON
                        Button(
                            onClick = {
                                val file = chooseFile()
                                if (file != null) {
                                    try {
                                        val text = file.readText()
                                        val isXml = text.trim().startsWith("<")
                                        
                                        // FORMAT 1: XML
                                        if (isXml) {
                                            val rawItems = XmlParser.parse(text)
                                            val newProjectName = file.nameWithoutExtension
                                            val newSmsList = rawItems.map { it ->
                                                 val score = classifier.classify(it.body)
                                                 val silver: PotentialTransaction? = if (score > 0.2) {
                                                     runBlocking {
                                                         DesktopSmsParser.parse(SmsMessage(id = 0L, body = it.body, sender = it.address, date = it.date.toLongOrNull() ?: 0L))
                                                     }
                                                 } else null
                                                 
                                                 ReviewItem(
                                                     id = it.date, // Use date as ID for simplicity
                                                     sender = it.address,
                                                     body = it.body,
                                                     timestamp = it.date.toLongOrNull() ?: 0L,
                                                     confidence = score,
                                                     silverLabel = silver,
                                                     // Heuristic: Auto-accept > 0.75, Auto-reject < 0.25
                                                     isTransaction = if (score > 0.75) true else if (score < 0.25) false else null
                                                 )
                                            }
                                            smsList = newSmsList
                                            currentProject = newProjectName
                                            statusMessage = "Created Project: $newProjectName (Legacy XML)"
                                        } 
                                        // FORMAT 2: Android JSON (Flat Array)
                                         else if (text.trim().startsWith("[")) {
                                              val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
                                              // TRY DETECTING: Is this a Workspace Project (ReviewItem list) OR Android Dump (AndroidSmsItem list)?
                                              // Check the first few chars for specific keys to guess
                                              
                                              // MIGRATION / LOAD WORKSPACE LOGIC
                                              if (text.contains("\"id\":") && text.contains("\"isTransaction\":")) {
                                                  // It's likely a saved workspace (SerializableReviewItem)
                                                  val loadedDtos = json.decodeFromString<List<SerializableReviewItem>>(text)
                                                  
                                                  val migratedList = loadedDtos.map { dto ->
                                                      // MIGRATION: Check for polluted body
                                                      var cleanBody = dto.body
                                                      var reason = dto.ignoreReason
                                                      
                                                      // Regex to catch [IGNORED: Reason] Prefix
                                                      val pollutionRegex = Regex("^\\[IGNORED: (.*?)\\] (.*)")
                                                      val match = pollutionRegex.find(cleanBody)
                                                      if (match != null) {
                                                          // We found pollution! Fix it.
                                                          reason = match.groupValues[1] // The reason part
                                                          cleanBody = match.groupValues[2] // The actual SMS part
                                                          println("MIGRATION: Cleaned item ${dto.id}")
                                                      }

                                                      ReviewItem(
                                                          id = dto.id,
                                                          sender = dto.sender,
                                                          body = cleanBody, // Restored Raw Body
                                                          timestamp = dto.timestamp,
                                                          isTransaction = dto.isTransaction,
                                                          confidence = dto.confidence,
                                                          silverLabel = dto.silverLabel?.toDomain(),
                                                          ignoreReason = reason // Moved to proper field
                                                      )
                                                  }
                                                  
                                                  smsList = migratedList
                                                  currentProject = file.nameWithoutExtension
                                                  statusMessage = "Opened Project: $currentProject (with Migration)"
                                              } 
                                              // ANDROID BATCH IMPORT LOGIC
                                              else {
                                                  val rawItems = json.decodeFromString<List<AndroidSmsItem>>(text)
                                                  val newProjectName = file.nameWithoutExtension
    
                                                  val newSmsList = rawItems.map { it ->
                                                      // score is ALREADY computed by Android
                                                      val score = it.ml_score?.toFloat() ?: classifier.classify(it.body)
                                                      val isIgnoredByRule = it.ml_rule_ignore == true
                                                      
                                                      val silver: PotentialTransaction? = if (!isIgnoredByRule && score > 0.2) {
                                                          runBlocking {
                                                              DesktopSmsParser.parse(SmsMessage(id = 0L, body = it.body, sender = it.address, date = it.date))
                                                          }
                                                      } else null
    
                                                      ReviewItem(
                                                          id = it.date.toString(),
                                                          sender = it.address,
                                                          body = it.body, // KEEP RAW
                                                          timestamp = it.date,
                                                          confidence = score,
                                                          silverLabel = silver,
                                                          ignoreReason = if (isIgnoredByRule) it.ml_ignore_reason else null,
                                                          isTransaction = if (isIgnoredByRule) false 
                                                                          else if (score > 0.75) true 
                                                                          else if (score < 0.25) false 
                                                                          else null
                                                      )
                                                  }
                                                  smsList = newSmsList
                                                  currentProject = newProjectName
                                                  statusMessage = "Created Project: $newProjectName (Android Batch)"
                                              }
                                         }
                                        // FORMAT 3: Legacy JSON Dump (Nested)
                                        else {
                                            val json = Json { ignoreUnknownKeys = true; isLenient = true }
                                            val dump = json.decodeFromString<DatasetSmsDump>(text)
                                            val rawItems = dump.smses.sms
                                            
                                            // ... (Same logic as XML basically)
                                            val newProjectName = file.nameWithoutExtension
                                            val newSmsList = rawItems.map { it ->
                                                val score = classifier.classify(it.body)
                                                val silver: PotentialTransaction? = if (score > 0.2) {
                                                     runBlocking {
                                                         DesktopSmsParser.parse(SmsMessage(id = 0L, body = it.body, sender = it.address, date = it.date.toLongOrNull() ?: 0L))
                                                     }
                                                 } else null

                                                 ReviewItem(
                                                     id = it.date, 
                                                     sender = it.address,
                                                     body = it.body,
                                                     timestamp = it.date.toLongOrNull() ?: 0L,
                                                     confidence = score,
                                                     silverLabel = silver,
                                                     isTransaction = if (score > 0.75) true else if (score < 0.25) false else null
                                                 )
                                            }
                                            smsList = newSmsList
                                            currentProject = newProjectName
                                            statusMessage = "Created Project: $newProjectName (Legacy JSON)"
                                        }

                                        // Common Post-Processing
                                        if (smsList.isNotEmpty()) {
                                            currentIndex = 0
                                            // Refresh Project List & Save
                                            projectList = listProjects() + (currentProject ?: "") 
                                            scope.launch(Dispatchers.IO) {
                                                saveCurrentProject() 
                                            }
                                        }

                                    } catch (e: Exception) {
                                        statusMessage = "Error: ${e.message}"
                                        e.printStackTrace()
                                    }
                                }
                            },
                             colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                        ) { Text("Import New File") }
                    }
                }

                // Row 2: Controls and Toggles (Secondary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mode Toggle
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { appMode = AppMode.REVIEW_QUEUE; currentIndex = 0 },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if(appMode == AppMode.REVIEW_QUEUE) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        ) { Text("Review Queue") }

                        Button(
                            onClick = { appMode = AppMode.ALL_TRANSACTIONS; currentIndex = 0 },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if(appMode == AppMode.ALL_TRANSACTIONS) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        ) { Text("Audit All") }
                    }

                    // State Summary
                    // Calculate stats
                    val reviewedCount = smsList.count { it.isTransaction != null }
                    Text("Verified: $reviewedCount / ${smsList.size} | Queue: ${displayedList.size}", fontSize = 12.sp, color = Color.Gray)

                    // Utils
                    Row(verticalAlignment = Alignment.CenterVertically) {

                        // Reset Progress (Keep Data)
                        Button(
                            onClick = {
                                smsList = smsList.map { it.copy(isTransaction = null) }
                                currentIndex = 0
                                saveCurrentProject()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)), // Orange
                            modifier = Modifier.padding(end = 16.dp)
                        ) { Text("Reset Progress") }

                        // EXPORT BUTTON 1: NER Data (Verified Only)
                        Button(
                            onClick = {
                                val verified = smsList.filter { it.isTransaction == true }
                                if (verified.isNotEmpty()) {
                                    val file = saveFile("verified_transactions.json")
                                    if (file != null) {
                                        // Conditionally anonymize based on toggle
                                        val exportList = if (isAnonymizationEnabled) {
                                            verified.map { it.copy(body = Anonymizer.anonymize(it.body)) }
                                        } else {
                                            verified
                                        }
                                        try {
                                            val json = Json { prettyPrint = true; encodeDefaults = true }
                                            val jsonString = json.encodeToString(exportList.map { it.toSerializable() })
                                            file.writeText(jsonString)
                                            statusMessage = "Exported ${exportList.size} items for NER to ${file.name}"
                                        } catch (e: Exception) {
                                            statusMessage = "Export failed: ${e.message}"
                                            e.printStackTrace()
                                        }
                                    }
                                } else {
                                    statusMessage = "Nothing to export! No verified transactions."
                                }
                            },
                             colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006400)), // Dark Green
                             modifier = Modifier.padding(end = 16.dp)
                        ) { Text("Export Verified (NER)") }

                        // EXPORT BUTTON 2: Classifier Training Data (All Labeled)
                        Button(
                            onClick = {
                                val labeled = smsList.filter { it.isTransaction != null }
                                if (labeled.isNotEmpty()) {
                                    val file = saveFile("classifier_training_data.json")
                                    if (file != null) {
                                        // Conditionally anonymize based on toggle
                                        val exportList = if (isAnonymizationEnabled) {
                                            labeled.map { it.copy(body = Anonymizer.anonymize(it.body)) }
                                        } else {
                                            labeled
                                        }
                                        try {
                                            val json = Json { prettyPrint = true; encodeDefaults = true }
                                            val jsonString = json.encodeToString(exportList.map { it.toSerializable() })
                                            file.writeText(jsonString)
                                            statusMessage = "Exported ${exportList.size} items for Classifier to ${file.name}"
                                        } catch (e: Exception) {
                                            statusMessage = "Export failed: ${e.message}"
                                            e.printStackTrace()
                                        }
                                    }
                                } else {
                                    statusMessage = "Nothing to export! No labeled items found."
                                }
                            },
                             colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3F51B5)), // Indigo
                             modifier = Modifier.padding(end = 16.dp)
                        ) { Text("Export Training Data") }

                        Switch(
                            checked = isAnonymizationEnabled,
                            onCheckedChange = { isAnonymizationEnabled = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Anoymize", fontSize = 14.sp)
                    }
                }
            }

            // --- MAIN CONTENT ---
            if (smsList.isEmpty()) {
                 Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No data loaded. Click 'Load Data' to start.")
                }
            } else if (displayedList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Text(if (appMode == AppMode.REVIEW_QUEUE) "Great job! Review Queue is empty." else "No transactions found.")
                }
            } else if (appMode == AppMode.REVIEW_QUEUE) {
                // --- REVIEW MODE (Focus Card) ---
                if (currentItem != null) {
                    val rawBody = currentItem.body
                    val displayBody = if (isAnonymizationEnabled) Anonymizer.anonymize(rawBody) else rawBody
                    val ignoreTag = currentItem.ignoreReason?.let { "[IGNORED: $it]" } // UI Only Tag

                    Card(modifier = Modifier.fillMaxWidth().weight(1f).padding(8.dp), elevation = CardDefaults.cardElevation(4.dp)) {
                        Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(currentItem.sender, style = MaterialTheme.typography.titleMedium)
                                Text("Confidence: ${String.format("%.4f", currentItem.confidence)}",
                                     color = if(currentItem.confidence > 0.5) Color(0xFF006400) else Color(0xFF8B0000))
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Display Ignore Tag if present
                            if (ignoreTag != null) {
                                Text(ignoreTag, color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            
                            Text(displayBody, style = MaterialTheme.typography.bodyLarge, fontSize = 20.sp)

                            // --- SILVER LABEL DISPLAY ---
                            if (currentItem.silverLabel != null) {
                                Spacer(modifier = Modifier.height(24.dp))
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("Regex Found Entities (Silver Label):", fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Merchant: ${currentItem.silverLabel?.merchantName ?: "Unknown"}")
                                        Text("Amount: ${currentItem.silverLabel?.amount ?: "0"} ${currentItem.silverLabel?.detectedCurrencyCode ?: ""}")
                                        Text("Account: ${currentItem.silverLabel?.potentialAccount?.formattedName ?: "None"}")
                                    }
                                }
                            } else if (currentItem.confidence > 0.5) {
                                Spacer(modifier = Modifier.height(24.dp))
                                Text("Regex Parser Failed to Extract Entities", color = Color.Red)
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                        Button(
                            onClick = { undoLast() },
                            colors = ButtonDefaults.buttonColors(Color.Gray),
                            modifier = Modifier.padding(end = 16.dp),
                            enabled = history.isNotEmpty()
                        ) { Text("Undo") }

                        Button(
                            onClick = {
                                updateItem(currentItem.id, false)
                            },
                            colors = ButtonDefaults.buttonColors(Color.Red),
                            modifier = Modifier.padding(end = 16.dp)
                        ) { Text("NOT Transaction (N)") }

                        Button(
                             onClick = {
                                updateItem(currentItem.id, true)
                            },
                            colors = ButtonDefaults.buttonColors(Color.Green)
                        ) { Text(if (currentItem.silverLabel != null) "Verify & Keep (Y)" else "Is Transaction (Manual)") }

                        Button(
                            onClick = {
                                // Skipping moves to next item
                                if (currentIndex < displayedList.lastIndex) {
                                    currentIndex++
                                }
                            },
                            colors = ButtonDefaults.buttonColors(Color.Gray),
                            modifier = Modifier.padding(start = 16.dp)
                        ) { Text("Skip (Space)") }
                    }
                    Text("Pending Reviews: ${displayedList.size} items", modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            } else {
                // --- AUDIT MODE (List) ---
                var auditFilter by remember { mutableStateOf("All") } // All, Valid, Invalid, Auto, Manual

                // Calculate counts for filters
                val allCount = displayedList.size
                val manualCount = displayedList.count { it.isTransaction == true }
                val autoCount = displayedList.count { it.isTransaction == null && it.confidence > 0.5f } 
                val validCount = manualCount + autoCount
                val invalidCount = allCount - validCount

                Column(modifier = Modifier.weight(1f)) {
                    // Confidence Range Filter State
                    var minScoreText by remember { mutableStateOf("") }
                    var maxScoreText by remember { mutableStateOf("") }

                    // Audit Filters with Counts
                    Column(modifier = Modifier.padding(bottom = 8.dp)) {
                         Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = auditFilter == "All",
                                onClick = { auditFilter = "All" },
                                label = { Text("All ($allCount)") },
                                leadingIcon = if (auditFilter == "All") {
                                    { Icon(androidx.compose.material.icons.Icons.Default.Check, contentDescription = null) }
                                } else null
                            )
                             FilterChip(
                                selected = auditFilter == "Valid",
                                onClick = { auditFilter = "Valid" },
                                label = { Text("Valid ($validCount)") },
                                leadingIcon = if (auditFilter == "Valid") {
                                    { Icon(androidx.compose.material.icons.Icons.Default.Check, contentDescription = null) }
                                } else null
                            )
                             FilterChip(
                                selected = auditFilter == "Invalid",
                                onClick = { auditFilter = "Invalid" },
                                label = { Text("Invalid ($invalidCount)") },
                                leadingIcon = if (auditFilter == "Invalid") {
                                    { Icon(androidx.compose.material.icons.Icons.Default.Close, contentDescription = null) }
                                } else null
                            )
                            FilterChip(
                                selected = auditFilter == "Auto",
                                onClick = { auditFilter = "Auto" },
                                label = { Text("Auto ($autoCount)") },
                                leadingIcon = if (auditFilter == "Auto") {
                                    { Icon(androidx.compose.material.icons.Icons.Default.Settings, contentDescription = null) }
                                } else null
                            )
                            FilterChip(
                                selected = auditFilter == "Manual",
                                onClick = { auditFilter = "Manual" },
                                label = { Text("Accepted ($manualCount)") },
                                leadingIcon = if (auditFilter == "Manual") {
                                    { Icon(androidx.compose.material.icons.Icons.Default.Face, contentDescription = null) }
                                } else null
                            )
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                            Text("Confidence Score Range:", fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
                            
                            OutlinedTextField(
                                value = minScoreText,
                                onValueChange = { minScoreText = it },
                                label = { Text("Min (0.0)") },
                                placeholder = { Text("0.0") },
                                modifier = Modifier.width(100.dp),
                                singleLine = true
                            )
                            Text(" - ", modifier = Modifier.padding(horizontal = 4.dp))
                            OutlinedTextField(
                                value = maxScoreText,
                                onValueChange = { maxScoreText = it },
                                label = { Text("Max (1.0)") },
                                placeholder = { Text("1.0") },
                                modifier = Modifier.width(100.dp),
                                singleLine = true
                            )
                        }
                    }

                        // Apply Filters
                        val minVal = minScoreText.toFloatOrNull() ?: 0.0f
                        val maxVal = maxScoreText.toFloatOrNull() ?: 1.0f

                        val filteredAuditList = when(auditFilter) {
                            "Valid" -> displayedList.filter { it.isTransaction == true || (it.isTransaction == null && it.confidence > 0.5f) }
                            "Invalid" -> displayedList.filter { it.isTransaction == false || (it.isTransaction == null && it.confidence <= 0.5f) }
                            "Auto" -> displayedList.filter { it.isTransaction == null && it.confidence > 0.5f }
                            "Manual" -> displayedList.filter { it.isTransaction == true }
                            else -> displayedList
                        }.filter { it.confidence in minVal..maxVal }

                        Box(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                            val state = rememberLazyListState()
                            
                            LazyColumn(state = state, modifier = Modifier.fillMaxSize().padding(end = 12.dp)) {
                                items(filteredAuditList) { item ->
                                val displayBody = if (isAnonymizationEnabled) Anonymizer.anonymize(item.body) else item.body
                                
                                // Logic: 
                                // Manual = isTransaction == true
                                // Auto = isTransaction == null (undecided) BUT high confidence (>0.5)
                                // Spam = isTransaction == false OR (null && confidence < 0.5)
                                
                                val isManual = item.isTransaction == true
                                val isAuto = item.isTransaction == null && item.confidence > 0.5f
                                val isMarkedAsTx = isManual || isAuto

                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isManual) Color(0xFFE8F5E9) else if (isAuto) Color(0xFFE3F2FD) else Color.White
                                    )
                                ) {
                                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        // Indicator Icon
                                        if (isManual) {
                                             Icon(androidx.compose.material.icons.Icons.Default.Face, contentDescription = "Manual", tint = Color(0xFF2E7D32))
                                        } else if (isAuto) {
                                             Icon(androidx.compose.material.icons.Icons.Default.Settings, contentDescription = "Auto", tint = Color(0xFF1565C0))
                                        } else {
                                             Icon(androidx.compose.material.icons.Icons.Default.Close, contentDescription = "Rejected", tint = Color.Gray)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                                Text(item.sender, fontWeight = FontWeight.Bold)
                                                Text(String.format("%.2f", item.confidence), fontSize = 12.sp, color = if(item.confidence > 0.5) Color(0xFF006400) else Color(0xFF8B0000))
                                            }
                                            
                                            // UI: Show Ignore Reason if exists
                                            if (item.ignoreReason != null) {
                                                Text("[IGNORED: ${item.ignoreReason}]", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                            }

                                            // Allow up to 4 lines for body, with ellipsis if longer
                                            Text(displayBody, maxLines = 4, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontSize = 13.sp, lineHeight = 18.sp, color = if (!isMarkedAsTx) Color.Gray else Color.Black)
                                            if (item.silverLabel != null && isMarkedAsTx) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("Merchant: ${item.silverLabel?.merchantName} | Amount: ${item.silverLabel?.amount}", 
                                                     fontSize = 12.sp, color = Color.Blue, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        
                                        Column(horizontalAlignment = Alignment.End) {
                                            if (isMarkedAsTx) {
                                                Text(if (isManual) "VERIFIED" else "AUTO (${String.format("%.2f", item.confidence)})", 
                                                     fontSize = 10.sp, 
                                                     fontWeight = FontWeight.Bold,
                                                     color = if(isManual) Color(0xFF2E7D32) else Color(0xFF1565C0))
                                            } else {
                                                  Text("IGNORED", fontSize = 10.sp, color = Color.Gray)
                                            }
                                            
                                            Checkbox(
                                                checked = isMarkedAsTx,
                                                onCheckedChange = { isChecked ->
                                                    // Functional change:
                                                    // If we Check a "Rejected" item -> Make it Manual True (Recover False Negative)
                                                    // If we Uncheck an "Auto" item -> Make it False (Mark as Spam)
                                                    updateItem(item.id, isChecked)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        VerticalScrollbar(
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                            adapter = rememberScrollbarAdapter(state)
                        )
                    }
                }
            }
            
            Text(statusMessage, modifier = Modifier.padding(top = 8.dp), color = Color.Gray)
        }
    }
    }
}

fun main() = application {
    val state = rememberWindowState(width = 1280.dp, height = 900.dp)
    Window(onCloseRequest = ::exitApplication, title = "Finlight Labeler", state = state) {
        App()
    }
}

fun chooseFile(): File? {
    val chooser = JFileChooser()
    val returnVal = chooser.showOpenDialog(null)
    if (returnVal == JFileChooser.APPROVE_OPTION) {
        return chooser.selectedFile
    }
    return null
}

fun saveFile(defaultName: String = "verified_transactions.json"): File? {
    val chooser = JFileChooser()
    chooser.dialogTitle = "Export Data"
    chooser.selectedFile = File(defaultName)
    val returnVal = chooser.showSaveDialog(null)
    if (returnVal == JFileChooser.APPROVE_OPTION) {
        return chooser.selectedFile
    }
    return null
}
