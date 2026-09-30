package io.pm.finlight.ui.screens

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
import androidx.lifecycle.viewmodel.compose.viewModel
import io.pm.finlight.ui.viewmodel.BatchAnalysisViewModel
import io.pm.finlight.ui.viewmodel.BatchAnalysisViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchAnalysisScreen(navController: androidx.navigation.NavController) {
    val context = LocalContext.current
    val viewModel: BatchAnalysisViewModel = viewModel(factory = BatchAnalysisViewModelFactory(context))
    val status = viewModel.status

    val filePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
        ) { uri ->
            uri?.let { viewModel.processFile(it) }
        }

    val saveFileLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json"),
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
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Default.BugReport,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "SMS Batch Classifier",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                "Streaming processing for large datasets.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (status.isRunning) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Processed: ${status.processed}")
            } else {
                Button(
                    onClick = { filePickerLauncher.launch("*/*") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.UploadFile, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Load Dump & Run")
                }

                if (status.resultFile != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { saveFileLauncher.launch("classified_sms.json") },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Check, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save Result JSON")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Processed ${status.processed} items.",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
