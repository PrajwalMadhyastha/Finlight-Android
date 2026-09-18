package io.pm.finlight.ml

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.pm.finlight.TestApplication
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.tensorflow.lite.Interpreter
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
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
            assertEquals(classifier1.vocab, classifier2.vocab)
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
        } finally {
            extractor1.close()
            extractor2.close()
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
