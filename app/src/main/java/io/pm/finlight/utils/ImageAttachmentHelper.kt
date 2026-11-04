package io.pm.finlight.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ImageAttachmentHelper {
    private const val TAG = "ImageAttachmentHelper"

    suspend fun saveImageToInternalStorage(
        context: Context,
        sourceUri: Uri,
        dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
    ): String? =
        withContext(dispatcherProvider.io) {
            try {
                val filesDir = File(context.filesDir, "attachments").apply { if (!exists()) mkdirs() }
                val file = File(filesDir, "txn_attach_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    FileOutputStream(file).use { output -> input.copyTo(output) }
                }
                file.absolutePath
            } catch (e: Exception) {
                Log.e(TAG, "Error saving image to internal storage", e)
                null
            }
        }
}
