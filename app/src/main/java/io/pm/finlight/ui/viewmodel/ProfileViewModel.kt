// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ProfileViewModel.kt
// REASON: FIX (Profile Picture Backup) - Changed profile picture storage location
// from 'filesDir/attachments/' (which is excluded from Auto Backup) to
// 'filesDir/profile/' (which is included). Added a one-time migration that
// transparently moves existing profile pictures to the new directory on first
// launch after the update.
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

class ProfileViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {

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

    init {
        // --- MIGRATION: Move profile picture from attachments/ to profile/ on first run ---
        viewModelScope.launch {
            migrateProfilePictureIfNeeded()
        }
    }

    /**
     * One-time migration: if profile picture URI still points into the old
     * 'attachments/' directory, move the file to 'profile/' and update the stored URI.
     */
    private suspend fun migrateProfilePictureIfNeeded() {
        withContext(Dispatchers.IO) {
            val currentUri = settingsRepository.getProfilePictureUri().let {
                // Read the current value once (blocking-style from prefs)
                context.getSharedPreferences("finance_app_settings", android.content.Context.MODE_PRIVATE)
                    .getString("profile_picture_uri", null)
            } ?: return@withContext

            val oldAttachmentsDir = File(context.filesDir, "attachments")
            val oldFile = File(currentUri)

            // Only migrate if the file is inside the old 'attachments' directory
            if (!oldFile.absolutePath.startsWith(oldAttachmentsDir.absolutePath)) return@withContext
            if (!oldFile.exists()) return@withContext

            try {
                val profileDir = File(context.filesDir, "profile")
                if (!profileDir.exists()) profileDir.mkdirs()

                val newFile = File(profileDir, oldFile.name)
                oldFile.copyTo(newFile, overwrite = true)
                oldFile.delete()

                settingsRepository.saveProfilePictureUri(newFile.absolutePath)
                Log.i("ProfileViewModel", "Migrated profile picture to profile/ directory.")
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Failed to migrate profile picture", e)
            }
        }
    }

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
     * Copies a file from a given content URI to the app's private 'profile' directory.
     * This directory IS included in Android Auto Backup (unlike 'attachments/').
     * @param sourceUri The temporary URI of the file to copy (e.g., from the image cropper).
     * @return The absolute path to the newly created file, or null if an error occurred.
     */
    private suspend fun saveImageToInternalStorage(sourceUri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                // --- FIXED: Save to 'profile/' which is included in Auto Backup ---
                val profileDir = File(context.filesDir, "profile")
                if (!profileDir.exists()) {
                    profileDir.mkdirs()
                }

                // Open an input stream from the source URI provided by the cropper
                val inputStream = context.contentResolver.openInputStream(sourceUri)

                // Create a destination file in the app's private 'profile' directory
                val fileName = "profile_${System.currentTimeMillis()}.jpg"
                val file = File(profileDir, fileName)

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