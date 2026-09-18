package io.pm.finlight.ml

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.pm.finlight.TestApplication
import org.json.JSONException
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.tensorflow.lite.Interpreter
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class MlModelFactoryTest {
    @Before
    fun setUp() {
        MlModelFactory.clearVocabCache()
    }

    @After
    fun tearDown() {
        MlModelFactory.clearVocabCache()
    }

    @Test
    fun `getClassifierVocab reads assets once and caches map`() {
        val mockContext = mockk<Context>()
        val mockAssets = mockk<android.content.res.AssetManager>()
        every { mockContext.assets } returns mockAssets
        every { mockAssets.open("vocab.txt") } answers {
            ByteArrayInputStream("first\nsecond\nthird".toByteArray())
        }

        val vocab1 = MlModelFactory.getClassifierVocab(mockContext)
        val vocab2 = MlModelFactory.getClassifierVocab(mockContext)

        assertSame(vocab1, vocab2)
        assertEquals(3, vocab1.size)
        assertEquals(0, vocab1["first"])
        assertEquals(1, vocab1["second"])
        assertEquals(2, vocab1["third"])
        verify(exactly = 1) { mockAssets.open("vocab.txt") }
    }

    @Test
    fun `getNerVocab reads assets once and caches map`() {
        val mockContext = mockk<Context>()
        val mockAssets = mockk<android.content.res.AssetManager>()
        every { mockContext.assets } returns mockAssets
        every { mockAssets.open("ner_vocab.txt") } answers {
            ByteArrayInputStream("spent\namount\nrs".toByteArray())
        }

        val vocab1 = MlModelFactory.getNerVocab(mockContext)
        val vocab2 = MlModelFactory.getNerVocab(mockContext)

        assertSame(vocab1, vocab2)
        assertEquals(3, vocab1.size)
        assertEquals(0, vocab1["spent"])
        assertEquals(1, vocab1["amount"])
        assertEquals(2, vocab1["rs"])
        verify(exactly = 1) { mockAssets.open("ner_vocab.txt") }
    }

    @Test
    fun `getNerLabelMap reads assets once and caches map`() {
        val mockContext = mockk<Context>()
        val mockAssets = mockk<android.content.res.AssetManager>()
        every { mockContext.assets } returns mockAssets
        val json =
            """
            {
                "id_to_label": {
                    "0": "O",
                    "1": "B-MERCHANT",
                    "2": "B-AMOUNT"
                }
            }
            """.trimIndent()
        every { mockAssets.open("ner_label_map.json") } answers {
            ByteArrayInputStream(json.toByteArray())
        }

        val map1 = MlModelFactory.getNerLabelMap(mockContext)
        val map2 = MlModelFactory.getNerLabelMap(mockContext)

        assertSame(map1, map2)
        assertEquals(3, map1.size)
        assertEquals("O", map1[0])
        assertEquals("B-MERCHANT", map1[1])
        assertEquals("B-AMOUNT", map1[2])
        verify(exactly = 1) { mockAssets.open("ner_label_map.json") }
    }

    @Test
    fun `getNerLabelMap handles blank JSON safely`() {
        val mockContext = mockk<Context>()
        val mockAssets = mockk<android.content.res.AssetManager>()
        every { mockContext.assets } returns mockAssets
        every { mockAssets.open("ner_label_map.json") } answers {
            ByteArrayInputStream("   ".toByteArray())
        }

        val map = MlModelFactory.getNerLabelMap(mockContext)
        assertTrue(map.isEmpty())
    }

    @Test
    fun `clearVocabCache evicts all cached maps`() {
        val mockContext = mockk<Context>()
        val mockAssets = mockk<android.content.res.AssetManager>()
        every { mockContext.assets } returns mockAssets
        every { mockAssets.open("vocab.txt") } answers {
            ByteArrayInputStream("a\nb".toByteArray())
        }
        every { mockAssets.open("ner_vocab.txt") } answers {
            ByteArrayInputStream("x\ny".toByteArray())
        }
        every { mockAssets.open("ner_label_map.json") } answers {
            ByteArrayInputStream("""{"id_to_label": {"0": "O"}}""".toByteArray())
        }

        val v1 = MlModelFactory.getClassifierVocab(mockContext)
        val nv1 = MlModelFactory.getNerVocab(mockContext)
        val lm1 = MlModelFactory.getNerLabelMap(mockContext)

        MlModelFactory.clearVocabCache()

        val v2 = MlModelFactory.getClassifierVocab(mockContext)
        val nv2 = MlModelFactory.getNerVocab(mockContext)
        val lm2 = MlModelFactory.getNerLabelMap(mockContext)

        assertNotSame(v1, v2)
        assertNotSame(nv1, nv2)
        assertNotSame(lm1, lm2)

        verify(exactly = 2) { mockAssets.open("vocab.txt") }
        verify(exactly = 2) { mockAssets.open("ner_vocab.txt") }
        verify(exactly = 2) { mockAssets.open("ner_label_map.json") }
    }

    @Test
    fun `concurrent access is thread-safe and assets are opened once`() {
        val mockContext = mockk<Context>()
        val mockAssets = mockk<android.content.res.AssetManager>()
        every { mockContext.assets } returns mockAssets
        every { mockAssets.open("vocab.txt") } answers {
            ByteArrayInputStream("token1\ntoken2".toByteArray())
        }
        every { mockAssets.open("ner_vocab.txt") } answers {
            ByteArrayInputStream("tokenA\ntokenB".toByteArray())
        }
        every { mockAssets.open("ner_label_map.json") } answers {
            ByteArrayInputStream("""{"id_to_label": {"0": "O"}}""".toByteArray())
        }

        val threadCount = 16
        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)

        for (i in 0 until threadCount) {
            executor.submit {
                startLatch.await()
                MlModelFactory.getClassifierVocab(mockContext)
                MlModelFactory.getNerVocab(mockContext)
                MlModelFactory.getNerLabelMap(mockContext)
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS))
        executor.shutdown()

        verify(exactly = 1) { mockAssets.open("vocab.txt") }
        verify(exactly = 1) { mockAssets.open("ner_vocab.txt") }
        verify(exactly = 1) { mockAssets.open("ner_label_map.json") }
    }

    @Test
    fun `getClassifier creates a new instance on every call while reusing cached vocab`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mockInterpreter = mockk<Interpreter>(relaxed = true)

        val classifier1 = MlModelFactory.getClassifier(context, interpreterFactory = { _, _ -> mockInterpreter })
        val classifier2 = MlModelFactory.getClassifier(context, interpreterFactory = { _, _ -> mockInterpreter })

        try {
            assertNotNull(classifier1)
            assertNotNull(classifier2)
            assertNotSame(classifier1, classifier2)
            assertSame(MlModelFactory.getClassifierVocab(context), classifier1.vocab)
            assertSame(classifier1.vocab, classifier2.vocab)
            assertTrue(classifier1.vocab.isNotEmpty())
        } finally {
            classifier1.close()
            classifier2.close()
        }
    }

    @Test
    fun `getNerExtractor creates a new instance on every call while reusing cached vocab and label map`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mockInterpreter = mockk<Interpreter>(relaxed = true)

        val extractor1 = MlModelFactory.getNerExtractor(context, interpreterFactory = { _, _ -> mockInterpreter })
        val extractor2 = MlModelFactory.getNerExtractor(context, interpreterFactory = { _, _ -> mockInterpreter })

        try {
            assertNotNull(extractor1)
            assertNotNull(extractor2)
            assertNotSame(extractor1, extractor2)
            assertSame(MlModelFactory.getNerVocab(context), extractor1.tokenizerInstance?.vocabMap)
            assertSame(extractor1.tokenizerInstance?.vocabMap, extractor2.tokenizerInstance?.vocabMap)
            assertSame(MlModelFactory.getNerLabelMap(context), extractor1.idToLabelMap)
            assertSame(extractor1.idToLabelMap, extractor2.idToLabelMap)
        } finally {
            extractor1.close()
            extractor2.close()
        }
    }

    @Test
    fun `getNerLabelMap returns empty map when id_to_label key is missing`() {
        val mockContext = mockk<Context>()
        val mockAssets = mockk<android.content.res.AssetManager>()
        every { mockContext.assets } returns mockAssets
        every { mockAssets.open("ner_label_map.json") } answers {
            ByteArrayInputStream("{}".toByteArray())
        }

        val map = MlModelFactory.getNerLabelMap(mockContext)
        assertTrue(map.isEmpty())
    }

    @Test
    fun `inner double-checked lock branch reuses cached classifier vocab under contention`() {
        val mockContext = mockk<Context>()
        val mockAssets = mockk<android.content.res.AssetManager>()
        every { mockContext.assets } returns mockAssets

        val thread1InLock = CountDownLatch(1)
        val thread1Release = CountDownLatch(1)

        every { mockAssets.open("vocab.txt") } answers {
            thread1InLock.countDown()
            thread1Release.await(5, TimeUnit.SECONDS)
            ByteArrayInputStream("token1\ntoken2".toByteArray())
        }

        val executor = Executors.newFixedThreadPool(2)
        val future1 =
            executor.submit<Map<String, Int>> {
                MlModelFactory.getClassifierVocab(mockContext)
            }

        assertTrue(thread1InLock.await(5, TimeUnit.SECONDS))

        val thread2Done = CountDownLatch(1)
        var vocab2: Map<String, Int>? = null
        executor.submit {
            vocab2 = MlModelFactory.getClassifierVocab(mockContext)
            thread2Done.countDown()
        }

        Thread.sleep(100)
        thread1Release.countDown()

        val vocab1 = future1.get(5, TimeUnit.SECONDS)
        assertTrue(thread2Done.await(5, TimeUnit.SECONDS))
        executor.shutdown()

        assertSame(vocab1, vocab2)
        verify(exactly = 1) { mockAssets.open("vocab.txt") }
    }

    @Test
    fun `inner double-checked lock branch reuses cached ner vocab under contention`() {
        val mockContext = mockk<Context>()
        val mockAssets = mockk<android.content.res.AssetManager>()
        every { mockContext.assets } returns mockAssets

        val thread1InLock = CountDownLatch(1)
        val thread1Release = CountDownLatch(1)

        every { mockAssets.open("ner_vocab.txt") } answers {
            thread1InLock.countDown()
            thread1Release.await(5, TimeUnit.SECONDS)
            ByteArrayInputStream("tokenA\ntokenB".toByteArray())
        }

        val executor = Executors.newFixedThreadPool(2)
        val future1 =
            executor.submit<Map<String, Int>> {
                MlModelFactory.getNerVocab(mockContext)
            }

        assertTrue(thread1InLock.await(5, TimeUnit.SECONDS))

        val thread2Done = CountDownLatch(1)
        var vocab2: Map<String, Int>? = null
        executor.submit {
            vocab2 = MlModelFactory.getNerVocab(mockContext)
            thread2Done.countDown()
        }

        Thread.sleep(100)
        thread1Release.countDown()

        val vocab1 = future1.get(5, TimeUnit.SECONDS)
        assertTrue(thread2Done.await(5, TimeUnit.SECONDS))
        executor.shutdown()

        assertSame(vocab1, vocab2)
        verify(exactly = 1) { mockAssets.open("ner_vocab.txt") }
    }

    @Test
    fun `inner double-checked lock branch reuses cached ner label map under contention`() {
        val mockContext = mockk<Context>()
        val mockAssets = mockk<android.content.res.AssetManager>()
        every { mockContext.assets } returns mockAssets

        val thread1InLock = CountDownLatch(1)
        val thread1Release = CountDownLatch(1)

        every { mockAssets.open("ner_label_map.json") } answers {
            thread1InLock.countDown()
            thread1Release.await(5, TimeUnit.SECONDS)
            ByteArrayInputStream("""{"id_to_label": {"0": "O"}}""".toByteArray())
        }

        val executor = Executors.newFixedThreadPool(2)
        val future1 =
            executor.submit<Map<Int, String>> {
                MlModelFactory.getNerLabelMap(mockContext)
            }

        assertTrue(thread1InLock.await(5, TimeUnit.SECONDS))

        val thread2Done = CountDownLatch(1)
        var map2: Map<Int, String>? = null
        executor.submit {
            map2 = MlModelFactory.getNerLabelMap(mockContext)
            thread2Done.countDown()
        }

        Thread.sleep(100)
        thread1Release.countDown()

        val map1 = future1.get(5, TimeUnit.SECONDS)
        assertTrue(thread2Done.await(5, TimeUnit.SECONDS))
        executor.shutdown()

        assertSame(map1, map2)
        verify(exactly = 1) { mockAssets.open("ner_label_map.json") }
    }

    @Test
    fun `getNerLabelMap throws JSONException on malformed JSON and does not poison cache`() {
        val mockContext = mockk<Context>()
        val mockAssets = mockk<android.content.res.AssetManager>()
        every { mockContext.assets } returns mockAssets
        every { mockAssets.open("ner_label_map.json") } answers {
            ByteArrayInputStream("{malformed".toByteArray())
        }

        assertFailsWith<JSONException> {
            MlModelFactory.getNerLabelMap(mockContext)
        }

        every { mockAssets.open("ner_label_map.json") } answers {
            ByteArrayInputStream("""{"id_to_label": {"0": "O"}}""".toByteArray())
        }
        val map = MlModelFactory.getNerLabelMap(mockContext)
        assertEquals("O", map[0])
    }

    @Test
    fun `getClassifierVocab throws IOException when asset is missing and does not poison cache`() {
        val mockContext = mockk<Context>()
        val mockAssets = mockk<android.content.res.AssetManager>()
        every { mockContext.assets } returns mockAssets
        every { mockAssets.open("vocab.txt") } throws IOException("File not found")

        assertFailsWith<IOException> {
            MlModelFactory.getClassifierVocab(mockContext)
        }

        every { mockAssets.open("vocab.txt") } answers {
            ByteArrayInputStream("token1\ntoken2".toByteArray())
        }
        val vocab = MlModelFactory.getClassifierVocab(mockContext)
        assertEquals(2, vocab.size)
    }

    @Test
    fun `getNerVocab throws IOException when asset is missing and does not poison cache`() {
        val mockContext = mockk<Context>()
        val mockAssets = mockk<android.content.res.AssetManager>()
        every { mockContext.assets } returns mockAssets
        every { mockAssets.open("ner_vocab.txt") } throws IOException("File not found")

        assertFailsWith<IOException> {
            MlModelFactory.getNerVocab(mockContext)
        }

        every { mockAssets.open("ner_vocab.txt") } answers {
            ByteArrayInputStream("tokenA\ntokenB".toByteArray())
        }
        val vocab = MlModelFactory.getNerVocab(mockContext)
        assertEquals(2, vocab.size)
    }

    @Test
    fun `getNerLabelMap throws IOException when asset is missing and does not poison cache`() {
        val mockContext = mockk<Context>()
        val mockAssets = mockk<android.content.res.AssetManager>()
        every { mockContext.assets } returns mockAssets
        every { mockAssets.open("ner_label_map.json") } throws IOException("File not found")

        assertFailsWith<IOException> {
            MlModelFactory.getNerLabelMap(mockContext)
        }

        every { mockAssets.open("ner_label_map.json") } answers {
            ByteArrayInputStream("""{"id_to_label": {"0": "O"}}""".toByteArray())
        }
        val map = MlModelFactory.getNerLabelMap(mockContext)
        assertEquals("O", map[0])
    }

    @Test
    fun `getClassifier and getNerExtractor exercise default interpreterFactory parameter`() {
        val mockContext = mockk<Context>()
        val mockAssets = mockk<android.content.res.AssetManager>()
        every { mockContext.assets } returns mockAssets
        every { mockAssets.open("vocab.txt") } answers {
            ByteArrayInputStream("token1".toByteArray())
        }
        every { mockAssets.open("ner_vocab.txt") } answers {
            ByteArrayInputStream("tokenA".toByteArray())
        }
        every { mockAssets.open("ner_label_map.json") } answers {
            ByteArrayInputStream("""{"id_to_label": {"0": "O"}}""".toByteArray())
        }
        every { mockAssets.openFd(any()) } throws FileNotFoundException("Test stub")

        runCatching {
            MlModelFactory.getClassifier(mockContext)
        }
        runCatching {
            MlModelFactory.getNerExtractor(mockContext)
        }
    }

    @Test
    fun `real assets load correctly from ApplicationContext`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val classifierVocab = MlModelFactory.getClassifierVocab(context)
        val nerVocab = MlModelFactory.getNerVocab(context)
        val nerLabelMap = MlModelFactory.getNerLabelMap(context)

        assertTrue(classifierVocab.isNotEmpty(), "Classifier vocab should be loaded from real assets")
        assertTrue(nerVocab.isNotEmpty(), "NER vocab should be loaded from real assets")
        assertTrue(nerLabelMap.isNotEmpty(), "NER label map should be loaded from real assets")
        assertEquals("O", nerLabelMap[0])
        assertEquals("B-MERCHANT", nerLabelMap[1])
    }
}
