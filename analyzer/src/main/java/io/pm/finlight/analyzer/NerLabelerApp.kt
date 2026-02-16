package io.pm.finlight.analyzer

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.foundation.focusable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import io.pm.finlight.SmsMessage


// --- FILE HELPERS ---
fun getNerProjectFile(name: String): File {
    val safeName = name.replace(Regex("[^a-zA-Z0-9.-]"), "_")
    return File("ner_workspace_$safeName.json")
}

fun getNerProjectFileForNewImport(name: String): File {
    // Add timestamp hash to prevent collisions only for NEW imports
    val safeName = name.replace(Regex("[^a-zA-Z0-9.-]"), "_")
    val baseFile = File("ner_workspace_$safeName.json")
    
    if (!baseFile.exists()) {
        return baseFile
    }
    
    // File exists, add timestamp to create unique name
    val timestamp = System.currentTimeMillis().toString().takeLast(6)
    return File("ner_workspace_${safeName}_$timestamp.json")
}

fun listNerProjects(): List<String> {
    val currentDir = File(".")
    return currentDir.listFiles()
        ?.filter { it.name.startsWith("ner_workspace_") && it.name.endsWith(".json") }
        ?.map { it.name.removePrefix("ner_workspace_").removeSuffix(".json") }
        ?.sorted()
        ?: emptyList()
}

// --- UNDO/REDO HISTORY ---
data class LabelAction(
    val itemId: String,
    val previousLabels: NerLabels?,
    val newLabels: NerLabels?
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
@Preview
fun NerLabelerApp() {
    // --- STATE ---
    var currentProject by remember { mutableStateOf<String?>(null) }
    var projectList by remember { mutableStateOf(emptyList<String>()) }
    var isProjectDropdownOpen by remember { mutableStateOf(false) }
    
    var itemsList by remember { mutableStateOf(emptyList<NerLabelItem>()) }
    var currentIndex by remember { mutableStateOf(0) }
    var statusMessage by remember { mutableStateOf("Ready") }
    
    // Undo/Redo History (per-project)
    val history = remember { mutableStateListOf<LabelAction>() }
    var historyIndex by remember { mutableStateOf(-1) }
    var historyProjectName by remember { mutableStateOf<String?>(null) }
    
    // Pattern Learning
    var patternLibrary by remember { mutableStateOf(PatternLibrary()) }
    val patternLearner = remember { PatternLearner() }
    
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val parser = remember { DesktopSmsParser }
    
    // --- HELPER: Load Parser Suggestions ---
    fun loadSuggestions(item: NerLabelItem) {
        scope.launch {
            val sms = SmsMessage(
                id = 0L,
                body = item.body,
                sender = item.sender,
                date = item.timestamp
            )
            val suggestions = parser.parseAndExtract(sms)
            
            // Update the item with suggestions
            itemsList = itemsList.map {
                if (it.id == item.id) it.copy(parserSuggestions = suggestions) else it
            }
        }
    }
    
    
    // --- HELPER: Save Current Project ---
    fun saveCurrentProject() {
        val proj = currentProject ?: return
        if (itemsList.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            try {
                val file = getNerProjectFile(proj)
                val workspace = NerWorkspace(
                    items = itemsList.map { it.toSerializable() },
                    patternLibrary = patternLibrary
                )
                val jsonString = Json { prettyPrint = true }.encodeToString(workspace)
                file.writeText(jsonString)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    // --- HELPER: Update Item Labels ---
    fun updateItemLabels(itemId: String, newLabels: NerLabels?) {
        val item = itemsList.find { it.id == itemId } ?: return
        val previousLabels = item.labels
        
        // Clear history if switching projects
        if (historyProjectName != currentProject) {
            history.clear()
            historyIndex = -1
            historyProjectName = currentProject
        }
        
        // Add to history (clear redo stack)
        if (historyIndex < history.lastIndex) {
            history.subList(historyIndex + 1, history.size).clear()
        }
        history.add(LabelAction(itemId, previousLabels, newLabels))
        historyIndex = history.lastIndex
        
        // Update item
        itemsList = itemsList.map {
            if (it.id == itemId) it.copy(labels = newLabels) else it
        }
        
        val updatedItem = itemsList.find { it.id == itemId }!!
        
        // NEGATIVE LEARNING: Unlearn from removals first
        patternLibrary = patternLearner.unlearnFromRemovals(
            updatedItem, 
            previousLabels, 
            newLabels, 
            patternLibrary
        )
        
        // POSITIVE LEARNING: Learn from new labels
        if (newLabels != null) {
            patternLibrary = patternLearner.learnFromLabels(updatedItem, patternLibrary)
        }
        
        saveCurrentProject()
    }
    
    // --- HELPER: Undo ---
    fun undo() {
        if (historyIndex < 0) {
            statusMessage = "Nothing to undo"
            return
        }
        
        val action = history[historyIndex]
        itemsList = itemsList.map {
            if (it.id == action.itemId) it.copy(labels = action.previousLabels) else it
        }
        historyIndex--
        statusMessage = "Undid last action"
        saveCurrentProject()
    }
    
    // --- HELPER: Redo ---
    fun redo() {
        if (historyIndex >= history.lastIndex) {
            statusMessage = "Nothing to redo"
            return
        }
        
        historyIndex++
        val action = history[historyIndex]
        itemsList = itemsList.map {
            if (it.id == action.itemId) it.copy(labels = action.newLabels) else it
        }
        statusMessage = "Redid action"
        saveCurrentProject()
    }
    
    // --- HELPER: Fuzzy Match Entity to Tokens ---
    fun findTokenIndices(tokens: List<String>, searchText: String?): List<Int> {
        if (searchText == null) return emptyList()
        
        val searchTokens = searchText.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (searchTokens.isEmpty()) return emptyList()
        
        // Normalize function: remove punctuation for comparison
        fun normalize(s: String) = s.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
        
        // Special handling for currency: Rs. = INR
        fun normalizeCurrency(s: String): String {
            val norm = normalize(s)
            return when {
                norm.startsWith("rs") -> "inr"
                else -> norm
            }
        }
        
        // Normalize numbers: 15000.0 = 15000.00 = 15000
        fun normalizeNumber(s: String): String {
            return try {
                val num = s.toDoubleOrNull()
                if (num != null) {
                    // Convert to int if it's a whole number, otherwise keep as is
                    if (num == num.toLong().toDouble()) {
                        num.toLong().toString()
                    } else {
                        num.toString()
                    }
                } else {
                    s
                }
            } catch (e: Exception) {
                s
            }
        }
        
        // Special case: Handle combined currency+number in token (e.g., "Rs.1742") 
        // matching separated search (e.g., "INR" + "1742.0")
        if (searchTokens.size == 2) {
            val currencySearch = searchTokens[0]
            val numberSearch = searchTokens[1]
            
            for (i in tokens.indices) {
                val token = tokens[i]
                // Check if token matches pattern like "Rs.1742" or "INR15000"
                val match = Regex("^([a-zA-Z₹]+\\.?)([0-9.,]+)$").find(token)
                if (match != null) {
                    val currencyPart = match.groupValues[1]
                    val numberPart = match.groupValues[2]
                    
                    val currencyMatches = normalizeCurrency(currencyPart) == normalizeCurrency(currencySearch)
                    val numberMatches = normalizeNumber(numberPart) == normalizeNumber(numberSearch)
                    
                    if (currencyMatches && numberMatches) {
                        return listOf(i)  // Single token matches both search tokens
                    }
                }
            }
        }
        
        // Find contiguous sequence in tokens that matches searchTokens
        for (i in 0..(tokens.size - searchTokens.size)) {
            var match = true
            for (j in searchTokens.indices) {
                val tokenNorm = normalize(tokens[i + j])
                val searchNorm = normalize(searchTokens[j])
                val tokenCurrency = normalizeCurrency(tokens[i + j])
                val searchCurrency = normalizeCurrency(searchTokens[j])
                val tokenNumber = normalizeNumber(tokens[i + j])
                val searchNumber = normalizeNumber(searchTokens[j])
                
                // Try: exact match, normalized match, currency match, or number match
                if (!tokens[i + j].equals(searchTokens[j], ignoreCase = true) &&
                    tokenNorm != searchNorm &&
                    tokenCurrency != searchCurrency &&
                    tokenNumber != searchNumber) {
                    match = false
                    break
                }
            }
            if (match) {
                return (i until i + searchTokens.size).toList()
            }
        }
        return emptyList()
    }
    
    // --- HELPER: Auto-Apply Parser Suggestions ---
    fun autoApplySuggestions(item: NerLabelItem) {
        // First, try learned patterns
        val (learnedMerchant, learnedAmount, learnedAccount, learnedBalance) = patternLearner.findMatchingPatterns(item.tokens, patternLibrary)
        
        // Then try parser suggestions
        val suggestions = item.parserSuggestions
        val merchantIndices = learnedMerchant ?: if (suggestions != null) findTokenIndices(item.tokens, suggestions.merchantText) else emptyList()
        val amountIndices = learnedAmount ?: if (suggestions != null) findTokenIndices(item.tokens, suggestions.amountText) else emptyList()
        val accountIndices = learnedAccount ?: if (suggestions != null) findTokenIndices(item.tokens, suggestions.accountText) else emptyList()
        val balanceIndices = learnedBalance ?: if (suggestions != null) findTokenIndices(item.tokens, suggestions.balanceText) else emptyList()
        
        println("DEBUG: Auto-apply called")
        println("DEBUG: Learned patterns - M:$learnedMerchant A:$learnedAmount Ac:$learnedAccount B:$learnedBalance")
        println("DEBUG: Parser suggestions - M:${suggestions?.merchantText} A:${suggestions?.amountText} Ac:${suggestions?.accountText} B:${suggestions?.balanceText}")
        println("DEBUG: Final indices - M:$merchantIndices A:$amountIndices Ac:$accountIndices B:$balanceIndices")
        
        if (merchantIndices.isNotEmpty() || amountIndices.isNotEmpty() || accountIndices.isNotEmpty() || balanceIndices.isNotEmpty()) {
            val newLabels = NerLabels(
                merchantTokenIndices = merchantIndices,
                amountTokenIndices = amountIndices,
                accountTokenIndices = accountIndices,
                balanceTokenIndices = balanceIndices
            )
            updateItemLabels(item.id, newLabels)
            
            val parts = mutableListOf<String>()
            if (merchantIndices.isNotEmpty()) parts.add(if (learnedMerchant != null) "Merchant✨" else "Merchant")
            if (amountIndices.isNotEmpty()) parts.add(if (learnedAmount != null) "Amount✨" else "Amount")
            if (accountIndices.isNotEmpty()) parts.add(if (learnedAccount != null) "Account✨" else "Account")
            if (balanceIndices.isNotEmpty()) parts.add(if (learnedBalance != null) "Balance✨" else "Balance")
            statusMessage = "✓ Auto-applied: ${parts.joinToString(", ")} (✨ = learned)"
        } else {
            if (suggestions == null) {
                statusMessage = "⚠️ No parser suggestions loaded yet. Wait a moment and try again."
            } else {
                statusMessage = "⚠️ Could not match suggestions to tokens. Try manual labeling."
            }
        }
    }
    
    // --- INITIAL LOAD ---
    LaunchedEffect(Unit) {
        projectList = listNerProjects()
        if (projectList.isNotEmpty()) {
            val first = projectList.first()
            currentProject = first
            val file = getNerProjectFile(first)
            if (file.exists()) {
                val json = Json { ignoreUnknownKeys = true }
                try {
                    // Try loading new format with pattern library
                    val workspace = json.decodeFromString<NerWorkspace>(file.readText())
                    itemsList = workspace.items.map { it.toDomain() }
                    patternLibrary = workspace.patternLibrary
                    val patternCount = patternLibrary.merchantPatterns.size + 
                                      patternLibrary.amountContextPatterns.size + 
                                      patternLibrary.accountPatterns.size
                    statusMessage = if (patternCount > 0) {
                        "Loaded project '$first' with $patternCount learned patterns ✨"
                    } else {
                        "Loaded project '$first'"
                    }
                } catch (e: Exception) {
                    // Fallback to old format (just items list)
                    try {
                        val loaded = json.decodeFromString<List<SerializableNerLabelItem>>(file.readText())
                        itemsList = loaded.map { it.toDomain() }
                        patternLibrary = PatternLibrary() // Start fresh
                        statusMessage = "Loaded project '$first' (old format, pattern learning will start fresh)"
                    } catch (e2: Exception) {
                        statusMessage = "Failed to load project: ${e2.message}"
                        e2.printStackTrace()
                    }
                }
            }
        }
        focusRequester.requestFocus()
    }
    
    // --- DERIVED STATE ---
    val currentItem = itemsList.getOrNull(currentIndex)
    val labeledCount = itemsList.count { it.labels != null }
    
    // Load parser suggestions when item changes
    LaunchedEffect(currentItem?.id) {
        currentItem?.let { item ->
            if (item.parserSuggestions == null) {
                loadSuggestions(item)
                // Auto-apply after suggestions are loaded
                delay(200)
                val updatedItem = itemsList.find { it.id == item.id }
                if (updatedItem != null && updatedItem.labels == null && updatedItem.parserSuggestions != null) {
                    autoApplySuggestions(updatedItem)
                }
            }
        }
    }
    
    
    MaterialTheme {
        Box(
            modifier = Modifier.fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp) {
                        val item = itemsList.getOrNull(currentIndex)
                        when {
                            // Undo/Redo
                            event.key == Key.Backspace && event.isCtrlPressed -> { undo(); true }
                            event.key == Key.Z && event.isCtrlPressed && event.isShiftPressed -> { redo(); true }
                            event.key == Key.Z && event.isCtrlPressed -> { undo(); true }
                            event.key == Key.Y && event.isCtrlPressed -> { redo(); true }
                            
                            // Auto-apply suggestions (Tab)
                            event.key == Key.Tab && item != null -> {
                                autoApplySuggestions(item)
                                true
                            }
                            
                            // Save & Next (S)
                            event.key == Key.S && item != null -> {
                                if (currentIndex < itemsList.lastIndex) {
                                    currentIndex++
                                    statusMessage = "Moved to next item"
                                }
                                true
                            }
                            
                            // Skip (Space)
                            event.key == Key.Spacebar -> {
                                if (currentIndex < itemsList.lastIndex) {
                                    currentIndex++
                                    statusMessage = "Skipped item"
                                }
                                true
                            }
                            
                            else -> false
                        }
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
                        Text("Finlight NER Labeler", fontSize = 24.sp, fontWeight = FontWeight.Bold)
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
                                                        val file = getNerProjectFile(proj)
                                                        if (file.exists()) file.delete()
                                                        
                                                        // Refresh List
                                                        val newList = projectList.filter { it != proj }
                                                        projectList = newList
                                                        
                                                        // If current was deleted, switch or clear
                                                        if (currentProject == proj) {
                                                            if (newList.isNotEmpty()) {
                                                                val next = newList.first()
                                                                currentProject = next
                                                                val nextFile = getNerProjectFile(next)
                                                                if (nextFile.exists()) {
                                                                    val json = Json { ignoreUnknownKeys = true }
                                                                    try {
                                                                        // Try loading new format with pattern library
                                                                        val workspace = json.decodeFromString<NerWorkspace>(nextFile.readText())
                                                                        itemsList = workspace.items.map { it.toDomain() }
                                                                        patternLibrary = workspace.patternLibrary
                                                                        statusMessage = "Deleted '$proj', switched to '$next'"
                                                                    } catch (e: Exception) {
                                                                        // Fallback to old format
                                                                        try {
                                                                            val loaded = json.decodeFromString<List<SerializableNerLabelItem>>(nextFile.readText())
                                                                            itemsList = loaded.map { it.toDomain() }
                                                                            patternLibrary = PatternLibrary()
                                                                            statusMessage = "Deleted '$proj', switched to '$next' (old format)"
                                                                        } catch (e2: Exception) {
                                                                            statusMessage = "Deleted '$proj', but failed to load '$next': ${e2.message}"
                                                                            e2.printStackTrace()
                                                                        }
                                                                    }
                                                                }
                                                            } else {
                                                                currentProject = null
                                                                itemsList = emptyList()
                                                                statusMessage = "Deleted '$proj'"
                                                            }
                                                        } else {
                                                            statusMessage = "Deleted project '$proj'"
                                                        }
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray)
                                                }
                                            }
                                        },
                                        onClick = {
                                            isProjectDropdownOpen = false
                                            currentProject = proj
                                            val file = getNerProjectFile(proj)
                                            if (file.exists()) {
                                                val json = Json { ignoreUnknownKeys = true }
                                                try {
                                                    // Try loading new format with pattern library
                                                    val workspace = json.decodeFromString<NerWorkspace>(file.readText())
                                                    itemsList = workspace.items.map { it.toDomain() }
                                                    patternLibrary = workspace.patternLibrary
                                                    val patternCount = patternLibrary.merchantPatterns.size + 
                                                                      patternLibrary.amountContextPatterns.size + 
                                                                      patternLibrary.accountPatterns.size +
                                                                      patternLibrary.balanceContextPatterns.size
                                                    statusMessage = if (patternCount > 0) {
                                                        "Switched to '$proj' with $patternCount learned patterns ✨"
                                                    } else {
                                                        "Switched to '$proj'"
                                                    }
                                                } catch (e: Exception) {
                                                    // Fallback to old format (just items list)
                                                    try {
                                                        val loaded = json.decodeFromString<List<SerializableNerLabelItem>>(file.readText())
                                                        itemsList = loaded.map { it.toDomain() }
                                                        patternLibrary = PatternLibrary() // Start fresh
                                                        statusMessage = "Switched to '$proj' (old format, pattern learning will start fresh)"
                                                    } catch (e2: Exception) {
                                                        statusMessage = "Failed to load project: ${e2.message}"
                                                        e2.printStackTrace()
                                                    }
                                                }
                                                currentIndex = 0
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        // Import Button
                        Button(
                            onClick = {
                                val file = chooseFile()
                                if (file != null) {
                                    try {
                                        val text = file.readText()
                                        val json = Json { ignoreUnknownKeys = true }
                                        val loaded = json.decodeFromString<List<VerifiedTransactionItem>>(text)
                                        
                                        // Convert to NerLabelItem and tokenize
                                        val newItems = loaded.map { item ->
                                            NerLabelItem(
                                                id = item.id,
                                                sender = item.sender,
                                                body = item.body,
                                                timestamp = item.timestamp,
                                                tokens = tokenize(item.body)
                                            )
                                        }
                                        
                                        itemsList = newItems
                                        currentProject = file.nameWithoutExtension
                                        currentIndex = 0
                                        statusMessage = "Imported ${newItems.size} items from ${file.name}"
                                        
                                        // Save with collision-safe filename and refresh project list
                                        val projectFile = getNerProjectFileForNewImport(currentProject!!)
                                        currentProject = projectFile.nameWithoutExtension.removePrefix("ner_workspace_")
                                        projectList = listNerProjects() + currentProject!!
                                        scope.launch(Dispatchers.IO) {
                                            saveCurrentProject()
                                        }
                                    } catch (e: Exception) {
                                        statusMessage = "Import failed: ${e.message}"
                                        e.printStackTrace()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                        ) { Text("Import Verified") }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        // Export Button with Filter
                        var exportFilter by remember { mutableStateOf(ExportFilter.ALL) }
                        var isExportDropdownOpen by remember { mutableStateOf(false) }
                        
                        Box {
                            Button(
                                onClick = { isExportDropdownOpen = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006400)),
                                enabled = itemsList.isNotEmpty()
                            ) { Text("Export NER Data ▼") }
                            
                            DropdownMenu(
                                expanded = isExportDropdownOpen,
                                onDismissRequest = { isExportDropdownOpen = false }
                            ) {
                                ExportFilter.values().forEach { filter ->
                                    DropdownMenuItem(
                                        text = { 
                                            Text(when(filter) {
                                                ExportFilter.ALL -> "All Labeled (Any Entity)"
                                                ExportFilter.WITH_AMOUNT -> "With Amount"
                                                ExportFilter.HIGH_QUALITY -> "High Quality (2+ Entities)"
                                                ExportFilter.COMPLETE -> "Complete (All 3 Entities)"
                                            })
                                        },
                                        onClick = {
                                            exportFilter = filter
                                            isExportDropdownOpen = false
                                            
                                            // Perform export
                                            val file = saveFile("ner_training_data.json")
                                            if (file != null) {
                                                try {
                                                    val exportItems = itemsList.filter { item ->
                                                        val labels = item.labels ?: return@filter false
                                                        val hasMerchant = labels.merchantTokenIndices.isNotEmpty()
                                                        val hasAmount = labels.amountTokenIndices.isNotEmpty()
                                                        val hasAccount = labels.accountTokenIndices.isNotEmpty()
                                                        val hasBalance = labels.balanceTokenIndices.isNotEmpty()
                                                        val entityCount = listOf(hasMerchant, hasAmount, hasAccount, hasBalance).count { it }
                                                        
                                                        when (filter) {
                                                            ExportFilter.ALL -> entityCount > 0
                                                            ExportFilter.WITH_AMOUNT -> hasAmount
                                                            ExportFilter.HIGH_QUALITY -> entityCount >= 2
                                                            ExportFilter.COMPLETE -> entityCount >= 3
                                                        }
                                                    }.map { item ->
                                                        val labels = item.labels!!
                                                        val hasMerchant = labels.merchantTokenIndices.isNotEmpty()
                                                        val hasAmount = labels.amountTokenIndices.isNotEmpty()
                                                        val hasAccount = labels.accountTokenIndices.isNotEmpty()
                                                        val hasBalance = labels.balanceTokenIndices.isNotEmpty()
                                                        val entityCount = listOf(hasMerchant, hasAmount, hasAccount, hasBalance).count { it }
                                                        
                                                        val entities = buildList {
                                                            if (hasMerchant) {
                                                                add(NerEntity(
                                                                    type = "MERCHANT",
                                                                    tokenIndices = labels.merchantTokenIndices,
                                                                    text = labels.merchantTokenIndices
                                                                        .filter { it < item.tokens.size }  // Bounds check
                                                                        .joinToString(" ") { item.tokens[it] }
                                                                ))
                                                            }
                                                            if (hasAmount) {
                                                                add(NerEntity(
                                                                    type = "AMOUNT",
                                                                    tokenIndices = labels.amountTokenIndices,
                                                                    text = labels.amountTokenIndices
                                                                        .filter { it < item.tokens.size }  // Bounds check
                                                                        .joinToString(" ") { item.tokens[it] }
                                                                ))
                                                            }
                                                            if (hasAccount) {
                                                                add(NerEntity(
                                                                    type = "ACCOUNT",
                                                                    tokenIndices = labels.accountTokenIndices,
                                                                    text = labels.accountTokenIndices
                                                                        .filter { it < item.tokens.size }  // Bounds check
                                                                        .joinToString(" ") { item.tokens[it] }
                                                                ))
                                                            }
                                                            if (hasBalance) {
                                                                add(NerEntity(
                                                                    type = "BALANCE",
                                                                    tokenIndices = labels.balanceTokenIndices,
                                                                    text = labels.balanceTokenIndices
                                                                        .filter { it < item.tokens.size }  // Bounds check
                                                                        .joinToString(" ") { item.tokens[it] }
                                                                ))
                                                            }
                                                        }
                                                        
                                                        NerTrainingItemWithMetadata(
                                                            id = item.id,
                                                            text = item.body,
                                                            tokens = item.tokens,
                                                            entities = entities,
                                                            completeness = NerCompletenessMetadata(
                                                                hasMerchant = hasMerchant,
                                                                hasAmount = hasAmount,
                                                                hasAccount = hasAccount,
                                                                hasBalance = hasBalance,
                                                                qualityScore = entityCount / 4.0f
                                                            )
                                                        )
                                                    }
                                                    
                                                    // Validation and export
                                                    if (exportItems.isEmpty()) {
                                                        statusMessage = "No labeled items match the selected filter!"
                                                    } else {
                                                        val json = Json { prettyPrint = true; encodeDefaults = true }
                                                        val jsonString = json.encodeToString(exportItems)
                                                        file.writeText(jsonString)
                                                        
                                                        // Show detailed statistics
                                                        val totalLabeled = itemsList.count { it.labels != null }
                                                        statusMessage = "Exported ${exportItems.size} items (of $totalLabeled labeled) to ${file.name}"
                                                    }
                                                } catch (e: Exception) {
                                                    statusMessage = "Export failed: ${e.message}"
                                                    e.printStackTrace()
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Row 2: Stats & Undo/Redo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val progressPercent = if (itemsList.isNotEmpty()) (labeledCount * 100) / itemsList.size else 0
                    Text(
                        "Progress: $labeledCount / ${itemsList.size} labeled ($progressPercent%) | Current: ${currentIndex + 1} / ${itemsList.size}", 
                        fontSize = 12.sp, 
                        color = Color.Gray
                    )
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { undo() },
                            enabled = historyIndex >= 0,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                            modifier = Modifier.padding(end = 8.dp)
                        ) { Text("Undo (Ctrl+Z)") }
                        
                        Button(
                            onClick = { redo() },
                            enabled = historyIndex < history.lastIndex,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                            modifier = Modifier.padding(end = 16.dp)
                        ) { Text("Redo (Ctrl+Y)") }
                        
                        Text(statusMessage, fontSize = 12.sp, color = Color.Blue)
                    }
                }
            }
            
            HorizontalDivider()
            
            // --- MAIN CONTENT ---
            if (itemsList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No data loaded. Click 'Import Verified' to start.")
                }
            } else if (currentItem != null) {
                Column(modifier = Modifier.fillMaxSize().weight(1f)) {
                    // SMS Display Card
                    Card(
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 16.dp),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(currentItem.sender, style = MaterialTheme.typography.titleMedium)
                                Text("Item ${currentIndex + 1} of ${itemsList.size}", fontSize = 12.sp, color = Color.Gray)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // SMS Body with Copy Button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                SelectionContainer {
                                    Text(
                                        currentItem.body, 
                                        style = MaterialTheme.typography.bodyLarge, 
                                        fontSize = 18.sp,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                    )
                                }
                                
                                // Copy Button
                                IconButton(
                                    onClick = {
                                        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                                        val stringSelection = java.awt.datatransfer.StringSelection(currentItem.body)
                                        clipboard.setContents(stringSelection, null)
                                        statusMessage = "📋 SMS text copied to clipboard"
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Text("📋", fontSize = 18.sp)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            // Parser Suggestions Panel
                            if (currentItem.parserSuggestions != null) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("💡 SmsParser Suggestions", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Spacer(modifier = Modifier.height(12.dp))
                                        
                                        currentItem.parserSuggestions?.merchantText?.let {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("🟢 Merchant: ", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                Text(it, fontSize = 14.sp, color = Color(0xFF2E7D32))
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                        }
                                        
                                        currentItem.parserSuggestions?.amountText?.let {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("🔵 Amount: ", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                Text(it, fontSize = 14.sp, color = Color(0xFF1565C0))
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                        }
                                        
                                        currentItem.parserSuggestions?.accountText?.let {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("🟠 Account: ", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                Text(it, fontSize = 14.sp, color = Color(0xFFE65100))
                                            }
                                        }
                                        
                                        currentItem.parserSuggestions?.balanceText?.let {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("🟡 Balance: ", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                Text(it, fontSize = 14.sp, color = Color(0xFFFFA726))
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                        }
                                    }
                                }
                            } else {
                                Text("⏳ Loading parser suggestions...", fontSize = 14.sp, color = Color.Gray, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Token Selection UI
                            Text("Select Tokens & Assign Entity Type", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Selection state
                            val selectedIndices = remember(currentItem.id) { mutableStateListOf<Int>() }
                            
                            // Entity Type Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                
                                Button(
                                    onClick = {
                                        if (selectedIndices.isNotEmpty()) {
                                            val currentLabels = currentItem.labels ?: NerLabels()
                                            val selectedList = selectedIndices.toList()
                                            
                                            // Remove selected tokens from other entity lists to prevent overlap
                                            val newLabels = currentLabels.copy(
                                                merchantTokenIndices = selectedList,
                                                amountTokenIndices = currentLabels.amountTokenIndices.filter { it !in selectedList },
                                                accountTokenIndices = currentLabels.accountTokenIndices.filter { it !in selectedList },
                                                balanceTokenIndices = currentLabels.balanceTokenIndices.filter { it !in selectedList }
                                            )
                                            updateItemLabels(currentItem.id, newLabels)
                                            selectedIndices.clear()
                                            statusMessage = "Merchant label applied"
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                    enabled = selectedIndices.isNotEmpty()
                                ) {
                                    Text("🟢 Merchant (M)")
                                }
                                
                                Button(
                                    onClick = {
                                        if (selectedIndices.isNotEmpty()) {
                                            val currentLabels = currentItem.labels ?: NerLabels()
                                            val selectedList = selectedIndices.toList()
                                            
                                            // Remove selected tokens from other entity lists to prevent overlap
                                            val newLabels = currentLabels.copy(
                                                merchantTokenIndices = currentLabels.merchantTokenIndices.filter { it !in selectedList },
                                                amountTokenIndices = selectedList,
                                                accountTokenIndices = currentLabels.accountTokenIndices.filter { it !in selectedList },
                                                balanceTokenIndices = currentLabels.balanceTokenIndices.filter { it !in selectedList }
                                            )
                                            updateItemLabels(currentItem.id, newLabels)
                                            selectedIndices.clear()
                                            statusMessage = "Amount label applied"
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                                    enabled = selectedIndices.isNotEmpty()
                                ) {
                                    Text("🔵 Amount (A)")
                                }
                                
                                Button(
                                    onClick = {
                                        if (selectedIndices.isNotEmpty()) {
                                            val currentLabels = currentItem.labels ?: NerLabels()
                                            val selectedList = selectedIndices.toList()
                                            
                                            // Remove selected tokens from other entity lists to prevent overlap
                                            val newLabels = currentLabels.copy(
                                                merchantTokenIndices = currentLabels.merchantTokenIndices.filter { it !in selectedList },
                                                amountTokenIndices = currentLabels.amountTokenIndices.filter { it !in selectedList },
                                                accountTokenIndices = selectedList,
                                                balanceTokenIndices = currentLabels.balanceTokenIndices.filter { it !in selectedList }
                                            )
                                            updateItemLabels(currentItem.id, newLabels)
                                            selectedIndices.clear()
                                            statusMessage = "Account label applied"
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                                    enabled = selectedIndices.isNotEmpty()
                                ) {
                                    Text("🟠 Account (C)")
                                }
                                
                                Button(
                                    onClick = {
                                        if (selectedIndices.isNotEmpty()) {
                                            val currentLabels = currentItem.labels ?: NerLabels()
                                            val selectedList = selectedIndices.toList()
                                            
                                            // Remove selected tokens from other entity lists to prevent overlap
                                            val newLabels = currentLabels.copy(
                                                merchantTokenIndices = currentLabels.merchantTokenIndices.filter { it !in selectedList },
                                                amountTokenIndices = currentLabels.amountTokenIndices.filter { it !in selectedList },
                                                accountTokenIndices = currentLabels.accountTokenIndices.filter { it !in selectedList },
                                                balanceTokenIndices = selectedList
                                            )
                                            updateItemLabels(currentItem.id, newLabels)
                                            selectedIndices.clear()
                                            statusMessage = "Balance label applied"
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA726)),
                                    enabled = selectedIndices.isNotEmpty()
                                ) {
                                    Text("🟡 Balance (B)")
                                }
                                
                                Button(
                                    onClick = {
                                        selectedIndices.clear()
                                        statusMessage = "Selection cleared"
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                                    enabled = selectedIndices.isNotEmpty()
                                ) {
                                    Text("Clear")
                                }
                            }
                            
                            // Row 2: Remove Entity Buttons (only show if labels exist)
                            if (currentItem.labels != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            val newLabels = currentItem.labels!!.copy(merchantTokenIndices = emptyList())
                                            updateItemLabels(currentItem.id, newLabels)
                                            statusMessage = "Removed Merchant label"
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32).copy(alpha = 0.5f)),
                                        enabled = currentItem.labels!!.merchantTokenIndices.isNotEmpty()
                                    ) {
                                        Text("✕ Merchant")
                                    }
                                    
                                    Button(
                                        onClick = {
                                            val newLabels = currentItem.labels!!.copy(amountTokenIndices = emptyList())
                                            updateItemLabels(currentItem.id, newLabels)
                                            statusMessage = "Removed Amount label"
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0).copy(alpha = 0.5f)),
                                        enabled = currentItem.labels!!.amountTokenIndices.isNotEmpty()
                                    ) {
                                        Text("✕ Amount")
                                    }
                                    
                                    Button(
                                        onClick = {
                                            val newLabels = currentItem.labels!!.copy(accountTokenIndices = emptyList())
                                            updateItemLabels(currentItem.id, newLabels)
                                            statusMessage = "Removed Account label"
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100).copy(alpha = 0.5f)),
                                        enabled = currentItem.labels!!.accountTokenIndices.isNotEmpty()
                                    ) {
                                        Text("✕ Account")
                                    }
                                    
                                    Button(
                                        onClick = {
                                            val newLabels = currentItem.labels!!.copy(balanceTokenIndices = emptyList())
                                            updateItemLabels(currentItem.id, newLabels)
                                            statusMessage = "Removed Balance label"
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA726).copy(alpha = 0.5f)),
                                        enabled = currentItem.labels!!.balanceTokenIndices.isNotEmpty()
                                    ) {
                                        Text("✕ Balance")
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Clickable Token Chips
                            androidx.compose.foundation.layout.FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                currentItem.tokens.forEachIndexed { index, token ->
                                    val isSelected = selectedIndices.contains(index)
                                    val isMerchant = currentItem.labels?.merchantTokenIndices?.contains(index) == true
                                    val isAmount = currentItem.labels?.amountTokenIndices?.contains(index) == true
                                    val isAccount = currentItem.labels?.accountTokenIndices?.contains(index) == true
                                    val isBalance = currentItem.labels?.balanceTokenIndices?.contains(index) == true
                                    
                                    val backgroundColor = when {
                                        isSelected -> Color(0xFFFFEB3B) // Yellow for selection
                                        isMerchant -> Color(0xFFC8E6C9) // Light green
                                        isAmount -> Color(0xFFBBDEFB) // Light blue
                                        isAccount -> Color(0xFFFFE0B2) // Light orange
                                        isBalance -> Color(0xFFFFE082) // Light gold
                                        else -> Color(0xFFF5F5F5) // Light gray
                                    }
                                    
                                    val textColor = when {
                                        isMerchant -> Color(0xFF2E7D32)
                                        isAmount -> Color(0xFF1565C0)
                                        isAccount -> Color(0xFFE65100)
                                        isBalance -> Color(0xFFFFA726)
                                        else -> Color.Black
                                    }
                                    
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            if (isSelected) {
                                                selectedIndices.remove(index)
                                            } else {
                                                selectedIndices.add(index)
                                            }
                                        },
                                        label = { Text(token, color = textColor, fontSize = 14.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = backgroundColor,
                                            selectedContainerColor = Color(0xFFFFEB3B)
                                        )
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Current Labels Summary
                            if (currentItem.labels != null) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("Current Labels:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        if (currentItem.labels!!.merchantTokenIndices.isNotEmpty()) {
                                            val merchantText = currentItem.labels!!.merchantTokenIndices
                                                .filter { it < currentItem.tokens.size }  // Bounds check
                                                .joinToString(" ") { currentItem.tokens[it] }
                                            if (merchantText.isNotBlank()) {
                                                Text("🟢 Merchant: $merchantText", fontSize = 12.sp, color = Color(0xFF2E7D32))
                                            }
                                        }
                                        
                                        if (currentItem.labels!!.amountTokenIndices.isNotEmpty()) {
                                            val amountText = currentItem.labels!!.amountTokenIndices
                                                .filter { it < currentItem.tokens.size }  // Bounds check
                                                .joinToString(" ") { currentItem.tokens[it] }
                                            if (amountText.isNotBlank()) {
                                                Text("🔵 Amount: $amountText", fontSize = 12.sp, color = Color(0xFF1565C0))
                                            }
                                        }
                                        
                                        if (currentItem.labels!!.accountTokenIndices.isNotEmpty()) {
                                            val accountText = currentItem.labels!!.accountTokenIndices
                                                .filter { it < currentItem.tokens.size }  // Bounds check
                                                .joinToString(" ") { currentItem.tokens[it] }
                                            if (accountText.isNotBlank()) {
                                                Text("🟠 Account: $accountText", fontSize = 12.sp, color = Color(0xFFE65100))
                                            }
                                            
                                            // Balance
                                            if (currentItem.labels!!.balanceTokenIndices.isNotEmpty()) {
                                                val balanceText = currentItem.labels!!.balanceTokenIndices
                                                    .filter { it < currentItem.tokens.size }
                                                    .joinToString(" ") { currentItem.tokens[it] }
                                                Text("🟡 Balance: $balanceText", fontSize = 12.sp, color = Color(0xFFFFA726))
                                            }
                                        }
                                    }
                                }
                            }
                            
                        }
                    }
                    
                    // Navigation Controls
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = {
                                if (currentIndex > 0) {
                                    currentIndex--
                                }
                            },
                            enabled = currentIndex > 0,
                            modifier = Modifier.padding(end = 16.dp)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Previous")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Previous")
                        }
                        
                        Button(
                            onClick = {
                                if (currentIndex < itemsList.lastIndex) {
                                    currentIndex++
                                }
                            },
                            enabled = currentIndex < itemsList.lastIndex
                        ) {
                            Text("Next")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowForward, contentDescription = "Next")
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Button(
                            onClick = {
                                if (currentIndex < itemsList.lastIndex) {
                                    currentIndex++
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                        ) {
                            Text("Skip")
                        }
                    }
                }
            }
        }
        }
    }
}

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Finlight NER Labeler",
        state = rememberWindowState(width = 1280.dp, height = 900.dp)
    ) {
        NerLabelerApp()
    }
}
