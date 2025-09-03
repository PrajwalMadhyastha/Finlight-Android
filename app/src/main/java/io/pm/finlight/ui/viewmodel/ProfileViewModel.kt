// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ProfileViewModel.kt
// REASON: REFACTOR - The logic for saving the profile picture has been updated
// to store the image in a dedicated 'attachments' subdirectory. This aligns
// with the new backup strategy, allowing the directory to be easily excluded
// from Android's Auto Backup to save space.
// =================================================================================
package io.pm.finlight

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val context = application

    val userName: StateFlow<String> = settingsRepository.getUserName()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "User"
        )

    val profilePictureUri: StateFlow<String?> = settingsRepository.getProfilePictureUri()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    /**
     * Saves the cropped image from the source URI to internal storage and persists its path.
     */
    fun saveProfilePictureUri(sourceUri: Uri?) {
        viewModelScope.launch {
            if (sourceUri == null) {
                settingsRepository.saveProfilePictureUri(null)
                return@launch
            }
            // Copy the file to internal storage and get the new path
            val localPath = saveImageToInternalStorage(sourceUri)
            // Save the path to our new local file
            settingsRepository.saveProfilePictureUri(localPath)
        }
    }

    // --- NEW: Function to save the updated user name ---
    fun updateUserName(name: String) {
        viewModelScope.launch {
            if (name.isNotBlank()) {
                settingsRepository.saveUserName(name)
            }
        }
    }

    /**
     * Copies a file from a given content URI to the app's private internal storage.
     * @param sourceUri The temporary URI of the file to copy (e.g., from the image cropper).
     * @return The absolute path to the newly created file, or null if an error occurred.
     */
    private suspend fun saveImageToInternalStorage(sourceUri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                // --- UPDATED: Save images to a dedicated 'attachments' subdirectory ---
                val attachmentsDir = File(context.filesDir, "attachments")
                if (!attachmentsDir.exists()) {
                    attachmentsDir.mkdirs()
                }

                // Open an input stream from the source URI provided by the cropper
                val inputStream = context.contentResolver.openInputStream(sourceUri)

                // Create a destination file in the app's private 'attachments' directory
                val fileName = "profile_${System.currentTimeMillis()}.jpg"
                val file = File(attachmentsDir, fileName)

                // Open an output stream to the destination file
                val outputStream = FileOutputStream(file)

                // Copy the bytes from the input stream to the output stream
                inputStream?.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }

                // Return the path of the file we just created
                file.absolutePath
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error saving image to internal storage", e)
                null
            }
        }
    }
}
