// =================================================================================
// FILE: app/src/main/java/io/pm/finlight/ui/screens/TagManagementScreen.kt
// REASON: FEATURE (Help System - Phase 3) - Added a TopAppBar to the existing
// Scaffold, which now includes a HelpActionIcon to provide users with contextual
// guidance on how to create and manage tags.
// FIX (UI) - Removed the local TopAppBar from this screen's Scaffold. The main
// NavHost now provides a centralized TopAppBar, and this change removes the
// duplicate, resolving a UI bug while preserving the Snackbar.
// =================================================================================
package io.pm.finlight.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.pm.finlight.Tag
import io.pm.finlight.TagViewModel
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.components.HelpActionIcon
import io.pm.finlight.ui.theme.PopupSurfaceDark
import io.pm.finlight.ui.theme.PopupSurfaceLight

// Helper function to determine if a color is 'dark' based on luminance.
private fun Color.isDark() = (red * 0.299 + green * 0.587 + blue * 0.114) < 0.5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagManagementScreen(
    navController: NavController,
    viewModel: TagViewModel = viewModel()
) {
    val tags by viewModel.allTags.collectAsState()
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedTag by remember { mutableStateOf<Tag?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(key1 = viewModel.uiEvent) {
        viewModel.uiEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AddTagInput(onAddTag = viewModel::addTag)

            if (tags.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No tags created yet. Add one above!",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(tags, key = { it.id }) { tag ->
                        GlassPanel {
                            ListItem(
                                headlineContent = { Text(tag.name, color = MaterialTheme.colorScheme.onSurface) },
                                trailingContent = {
                                    Row {
                                        IconButton(onClick = {
                                            selectedTag = tag
                                            showEditDialog = true
                                        }) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Edit Tag",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(onClick = {
                                            selectedTag = tag
                                            showDeleteDialog = true
                                        }) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete Tag",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog && selectedTag != null) {
        EditTagDialog(
            tag = selectedTag!!,
            onDismiss = { showEditDialog = false },
            onConfirm = { updatedTag ->
                viewModel.updateTag(updatedTag)
                showEditDialog = false
            }
        )
    }

    if (showDeleteDialog && selectedTag != null) {
        DeleteTagDialog(
            tag = selectedTag!!,
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                viewModel.deleteTag(it)
                showDeleteDialog = false
            }
        )
    }
}

@Composable
private fun AddTagInput(onAddTag: (String) -> Unit) {
    var newTagName by remember { mutableStateOf("") }
    GlassPanel {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = newTagName,
                onValueChange = { newTagName = it },
                label = { Text("New Tag Name") },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                )
            )
            Button(
                onClick = {
                    onAddTag(newTagName)
                    newTagName = ""
                },
                enabled = newTagName.isNotBlank()
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Tag")
            }
        }
    }
}

@Composable
private fun EditTagDialog(
    tag: Tag,
    onDismiss: () -> Unit,
    onConfirm: (Tag) -> Unit
) {
    var tagName by remember(tag) { mutableStateOf(tag.name) }
    val isThemeDark = MaterialTheme.colorScheme.background.isDark()
    val popupContainerColor = if (isThemeDark) PopupSurfaceDark else PopupSurfaceLight

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Tag") },
        text = {
            OutlinedTextField(
                value = tagName,
                onValueChange = { tagName = it },
                label = { Text("Tag Name") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(tag.copy(name = tagName)) },
                enabled = tagName.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = popupContainerColor
    )
}

@Composable
private fun DeleteTagDialog(
    tag: Tag,
    onDismiss: () -> Unit,
    onConfirm: (Tag) -> Unit
) {
    val isThemeDark = MaterialTheme.colorScheme.background.isDark()
    val popupContainerColor = if (isThemeDark) PopupSurfaceDark else PopupSurfaceLight

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Tag?") },
        text = { Text("Are you sure you want to delete the tag '${tag.name}'?") },
        confirmButton = {
            Button(
                onClick = { onConfirm(tag) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = popupContainerColor
    )
}