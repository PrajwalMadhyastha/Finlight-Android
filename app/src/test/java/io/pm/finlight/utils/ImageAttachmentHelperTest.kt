package io.pm.finlight.utils

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.TestApplication
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class ImageAttachmentHelperTest {
    private lateinit var context: Context
    private val testDispatcher = StandardTestDispatcher()
    private val dispatcherProvider = TestDispatcherProvider(testDispatcher)

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `saveImageToInternalStorage successfully saves image file and returns absolute path`() =
        runTest(testDispatcher) {
            val sourceFile =
                File(context.cacheDir, "sample_input.jpg").apply {
                    writeText("sample image content")
                }
            val sourceUri = Uri.fromFile(sourceFile)

            val savedPath = ImageAttachmentHelper.saveImageToInternalStorage(context, sourceUri, dispatcherProvider)

            assertNotNull(savedPath)
            val savedFile = File(savedPath!!)
            assertTrue(savedFile.exists())
            assertEquals("sample image content", savedFile.readText())
        }

    @Test
    fun `saveImageToInternalStorage creates attachments directory if it does not exist`() =
        runTest(testDispatcher) {
            val attachmentsDir = File(context.filesDir, "attachments")
            if (attachmentsDir.exists()) {
                attachmentsDir.deleteRecursively()
            }

            val sourceFile =
                File(context.cacheDir, "sample_input2.jpg").apply {
                    writeText("directory creation test")
                }
            val sourceUri = Uri.fromFile(sourceFile)

            val savedPath = ImageAttachmentHelper.saveImageToInternalStorage(context, sourceUri, dispatcherProvider)

            assertNotNull(savedPath)
            assertTrue(attachmentsDir.exists())
            assertTrue(File(savedPath!!).exists())
        }

    @Test
    fun `saveImageToInternalStorage works when attachments directory already exists`() =
        runTest(testDispatcher) {
            val attachmentsDir = File(context.filesDir, "attachments").apply { mkdirs() }
            assertTrue(attachmentsDir.exists())

            val sourceFile =
                File(context.cacheDir, "sample_input3.jpg").apply {
                    writeText("existing dir test")
                }
            val sourceUri = Uri.fromFile(sourceFile)

            val savedPath = ImageAttachmentHelper.saveImageToInternalStorage(context, sourceUri, dispatcherProvider)

            assertNotNull(savedPath)
            assertTrue(File(savedPath!!).exists())
        }

    @Test
    fun `saveImageToInternalStorage uses default dispatcher when not specified`() =
        runTest {
            val sourceFile =
                File(context.cacheDir, "sample_input4.jpg").apply {
                    writeText("default dispatcher test")
                }
            val sourceUri = Uri.fromFile(sourceFile)

            val savedPath = ImageAttachmentHelper.saveImageToInternalStorage(context, sourceUri)

            assertNotNull(savedPath)
            assertTrue(File(savedPath!!).exists())
        }

    @Test
    fun `saveImageToInternalStorage returns null when exception occurs`() =
        runTest(testDispatcher) {
            // An invalid URI that will fail to open or copy
            val invalidUri = Uri.parse("content://nonexistent.authority/image.jpg")

            val result = ImageAttachmentHelper.saveImageToInternalStorage(context, invalidUri, dispatcherProvider)

            assertNull(result)
        }
}
