package io.pm.finlight.ui.viewmodel

import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.TestApplication
import io.pm.finlight.core.NerEntity
import io.pm.finlight.ml.SmsClassifier
import io.pm.finlight.ml.SmsEntityExtractor
import io.pm.finlight.utils.TestDispatcherProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class BatchAnalysisViewModelTest : BaseViewModelTest() {
    @Mock
    private lateinit var contentResolver: ContentResolver

    @Mock
    private lateinit var classifier: SmsClassifier

    @Mock
    private lateinit var nerExtractor: SmsEntityExtractor

    private lateinit var contextWrapper: Context
    private val testDispatcherProvider by lazy { TestDispatcherProvider(testDispatcher) }

    @Before
    override fun setup() {
        super.setup()
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        contextWrapper =
            object : ContextWrapper(baseContext) {
                override fun getContentResolver(): ContentResolver = this@BatchAnalysisViewModelTest.contentResolver

                override fun getApplicationContext(): Context = this
            }
    }

    @Test
    fun `processFile parses XML stream, extracts NER entities, and closes extractor`() =
        runTest {
            val xmlData =
                """
                <smses>
                    <sms address="VM-HDFCBK" date="1672531199000" body="Spent Rs 500 at Swiggy" />
                </smses>
                """.trimIndent()

            val uri = mock(Uri::class.java)
            `when`(contentResolver.openInputStream(uri)).thenAnswer { xmlData.byteInputStream() }
            `when`(classifier.classify("Spent Rs 500 at Swiggy")).thenReturn(0.95f)
            `when`(nerExtractor.extract("Spent Rs 500 at Swiggy")).thenReturn(
                mapOf(
                    "AMOUNT" to NerEntity("500", 0.95f),
                    "MERCHANT" to NerEntity("Swiggy", 0.9f),
                    "BALANCE" to NerEntity("1000", 0.8f),
                ),
            )

            var providerInvocationCount = 0
            val countingProvider: () -> SmsEntityExtractor = {
                providerInvocationCount++
                nerExtractor
            }

            val viewModel =
                BatchAnalysisViewModel(
                    context = contextWrapper,
                    dispatcherProvider = testDispatcherProvider,
                    nerExtractorProvider = countingProvider,
                    classifier = classifier,
                )

            assertFalse(viewModel.status.isRunning)
            assertEquals(0, viewModel.status.processed)
            assertNull(viewModel.status.resultFile)

            viewModel.processFile(uri)
            advanceUntilIdle()

            val finalStatus = viewModel.status
            assertFalse(finalStatus.isRunning)
            assertEquals(1, finalStatus.processed)
            assertNotNull(finalStatus.resultFile)
            assertTrue(finalStatus.resultFile!!.exists())

            val content = finalStatus.resultFile!!.readText()
            assertTrue(content.contains("\"address\": \"VM-HDFCBK\""))
            assertTrue(content.contains("\"ner_amount\": \"500\""))
            assertTrue(content.contains("\"ner_merchant\": \"Swiggy\""))
            assertTrue(content.contains("\"ner_balance\": \"1000\""))

            assertEquals(1, providerInvocationCount)
            verify(nerExtractor).close()
        }

    @Test
    fun `processFile parses JSON stream, extracts NER entities, and closes extractor`() =
        runTest {
            val jsonData =
                """
                [
                    {
                        "address": "VM-ICICIB",
                        "body": "Rs 1500 debited from A/c XX1234",
                        "date": 1672531200000
                    }
                ]
                """.trimIndent()

            val uri = mock(Uri::class.java)
            `when`(contentResolver.openInputStream(uri)).thenAnswer { jsonData.byteInputStream() }
            `when`(classifier.classify("Rs 1500 debited from A/c XX1234")).thenReturn(0.85f)
            `when`(nerExtractor.extract("Rs 1500 debited from A/c XX1234")).thenReturn(
                mapOf(
                    "AMOUNT" to NerEntity("1500", 0.9f),
                    "ACCOUNT" to NerEntity("XX1234", 0.85f),
                ),
            )

            var providerInvocationCount = 0
            val countingProvider: () -> SmsEntityExtractor = {
                providerInvocationCount++
                nerExtractor
            }

            val viewModel =
                BatchAnalysisViewModel(
                    context = contextWrapper,
                    dispatcherProvider = testDispatcherProvider,
                    nerExtractorProvider = countingProvider,
                    classifier = classifier,
                )

            viewModel.processFile(uri)
            advanceUntilIdle()

            val finalStatus = viewModel.status
            assertFalse(finalStatus.isRunning)
            assertEquals(1, finalStatus.processed)
            assertNotNull(finalStatus.resultFile)

            val content = finalStatus.resultFile!!.readText()
            assertTrue(content.contains("\"address\": \"VM-ICICIB\""))
            assertTrue(content.contains("\"ner_amount\": \"1500\""))
            assertTrue(content.contains("\"ner_account\": \"XX1234\""))

            assertEquals(1, providerInvocationCount)
            verify(nerExtractor).close()
        }

    @Test
    fun `processFile handles ignored messages, low scores, and extractor exceptions`() =
        runTest {
            val jsonData =
                """
                [
                    {
                        "address": "VM-JioNet",
                        "body": "Your Jio plan expires today",
                        "date": 1672531200000
                    },
                    {
                        "address": "VK-HDFCBK",
                        "body": "Hello how are you today",
                        "date": 1672531201000
                    },
                    {
                        "address": "VK-HDFCBK",
                        "body": "Spent Rs 200 at Store",
                        "date": 1672531202000
                    }
                ]
                """.trimIndent()

            val uri = mock(Uri::class.java)
            `when`(contentResolver.openInputStream(uri)).thenAnswer { jsonData.byteInputStream() }
            `when`(classifier.classify("Your Jio plan expires today")).thenReturn(0.05f)
            `when`(classifier.classify("Hello how are you today")).thenReturn(0.02f)
            `when`(classifier.classify("Spent Rs 200 at Store")).thenReturn(0.9f)
            // Test extractor throwing exception
            `when`(nerExtractor.extract("Spent Rs 200 at Store")).thenThrow(RuntimeException("Model inference failed"))

            val viewModel =
                BatchAnalysisViewModel(
                    context = contextWrapper,
                    dispatcherProvider = testDispatcherProvider,
                    nerExtractorProvider = { nerExtractor },
                    classifier = classifier,
                )

            viewModel.processFile(uri)
            advanceUntilIdle()

            val finalStatus = viewModel.status
            assertFalse(finalStatus.isRunning)
            assertEquals(3, finalStatus.processed)
            assertNotNull(finalStatus.resultFile)
            verify(nerExtractor).close()
        }

    @Test
    fun `processFile handles openInputStream failure gracefully and does not allocate extractor`() =
        runTest {
            val uri = mock(Uri::class.java)
            `when`(contentResolver.openInputStream(uri)).thenThrow(java.io.FileNotFoundException("Storage read error"))

            var providerInvocationCount = 0
            val countingProvider: () -> SmsEntityExtractor = {
                providerInvocationCount++
                nerExtractor
            }

            val viewModel =
                BatchAnalysisViewModel(
                    context = contextWrapper,
                    dispatcherProvider = testDispatcherProvider,
                    nerExtractorProvider = countingProvider,
                    classifier = classifier,
                )

            val emittedEvents = mutableListOf<String>()
            val job =
                launch {
                    viewModel.uiEvent.collect { emittedEvents.add(it) }
                }

            viewModel.processFile(uri)
            advanceUntilIdle()

            val finalStatus = viewModel.status
            assertFalse(finalStatus.isRunning)
            assertEquals(0, finalStatus.processed)
            assertNull(finalStatus.resultFile)
            assertEquals("Error: Storage read error", finalStatus.errorMessage)
            assertEquals(listOf("Error: Storage read error"), emittedEvents)
            assertEquals(0, providerInvocationCount)
            verify(nerExtractor, never()).close()

            job.cancel()
        }

    @Test
    fun `processFile handles null openInputStream gracefully and does not allocate extractor`() =
        runTest {
            val uri = mock(Uri::class.java)
            `when`(contentResolver.openInputStream(uri)).thenReturn(null)

            var providerInvocationCount = 0
            val countingProvider: () -> SmsEntityExtractor = {
                providerInvocationCount++
                nerExtractor
            }

            val viewModel =
                BatchAnalysisViewModel(
                    context = contextWrapper,
                    dispatcherProvider = testDispatcherProvider,
                    nerExtractorProvider = countingProvider,
                    classifier = classifier,
                )

            viewModel.processFile(uri)
            advanceUntilIdle()

            val finalStatus = viewModel.status
            assertFalse(finalStatus.isRunning)
            assertEquals(0, finalStatus.processed)
            assertNotNull(finalStatus.resultFile)
            assertNull(finalStatus.errorMessage)
            assertEquals(0, providerInvocationCount)
            verify(nerExtractor, never()).close()
        }

    @Test
    fun `onCleared closes classifier`() {
        val viewModel =
            BatchAnalysisViewModel(
                context = contextWrapper,
                dispatcherProvider = testDispatcherProvider,
                nerExtractorProvider = { nerExtractor },
                classifier = classifier,
            )

        val onClearedMethod =
            BatchAnalysisViewModel::class.java.getDeclaredMethod("onCleared").apply {
                isAccessible = true
            }
        onClearedMethod.invoke(viewModel)

        verify(classifier).close()
        verify(nerExtractor, never()).close()
    }

    @Test
    fun `BatchAnalysisViewModelFactory creates ViewModel and rejects unknown classes`() {
        val factory =
            BatchAnalysisViewModelFactory(
                context = contextWrapper,
                nerExtractorProvider = { nerExtractor },
                classifier = classifier,
                dispatcherProvider = testDispatcherProvider,
            )

        val vm = factory.create(BatchAnalysisViewModel::class.java)
        assertNotNull(vm)

        try {
            factory.create(UnknownViewModel::class.java)
            org.junit.Assert.fail("Expected IllegalArgumentException for unknown ViewModel class")
        } catch (e: IllegalArgumentException) {
            assertEquals("Unknown ViewModel class", e.message)
        }
    }

    private class UnknownViewModel : ViewModel()
}
