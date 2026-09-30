package io.pm.finlight

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil.Coil
import coil.memory.MemoryCache
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.pm.finlight.ml.MlModelFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = MainApplication::class)
class MainApplicationTest {
    private lateinit var app: MainApplication

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        MlModelFactory.clearVocabCache()
    }

    @After
    fun tearDown() {
        MlModelFactory.clearVocabCache()
        Coil.imageLoader(app).memoryCache?.clear()
        Coil.reset()
    }

    @Test
    fun `newImageLoader configures memory cache with bounded size`() {
        val loader = app.newImageLoader()
        assertNotNull(loader)
        val memoryCache = loader.memoryCache
        assertNotNull("Memory cache should not be null", memoryCache)
        assertTrue("Memory cache size should be greater than zero", memoryCache!!.maxSize > 0)
    }

    @Test
    fun `onTrimMemory with TRIM_MEMORY_UI_HIDDEN clears Coil and ML vocab caches`() {
        val mockContext = mockk<Context>()
        val mockAssets = mockk<AssetManager>()
        every { mockContext.assets } returns mockAssets
        every { mockAssets.open("vocab.txt") } answers {
            ByteArrayInputStream("food\nfuel\ngrocery".toByteArray())
        }

        // Preload vocabulary
        val vocabBefore = MlModelFactory.getClassifierVocab(mockContext)
        assertEquals(3, vocabBefore.size)
        verify(exactly = 1) { mockAssets.open("vocab.txt") }

        // Populate Coil memory cache
        val imageLoader = app.newImageLoader()
        Coil.setImageLoader(imageLoader)
        val bitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        imageLoader.memoryCache?.set(MemoryCache.Key("test_bitmap"), MemoryCache.Value(bitmap))
        assertTrue("Memory cache size should be greater than zero before trim", (imageLoader.memoryCache?.size ?: 0) > 0)

        // Trigger onTrimMemory with TRIM_MEMORY_UI_HIDDEN
        app.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)

        // Verify Coil cache was cleared
        assertEquals(0, imageLoader.memoryCache?.size)

        // Verify MlModelFactory cache was evicted - next call re-opens the asset
        val vocabAfter = MlModelFactory.getClassifierVocab(mockContext)
        assertEquals(3, vocabAfter.size)
        verify(exactly = 2) { mockAssets.open("vocab.txt") }
    }

    @Test
    fun `onTrimMemory with TRIM_MEMORY_RUNNING_CRITICAL clears Coil and ML vocab caches`() {
        val mockContext = mockk<Context>()
        val mockAssets = mockk<AssetManager>()
        every { mockContext.assets } returns mockAssets
        every { mockAssets.open("vocab.txt") } answers {
            ByteArrayInputStream("food\nfuel\ngrocery".toByteArray())
        }

        val vocabBefore = MlModelFactory.getClassifierVocab(mockContext)
        assertEquals(3, vocabBefore.size)
        verify(exactly = 1) { mockAssets.open("vocab.txt") }

        val imageLoader = app.newImageLoader()
        Coil.setImageLoader(imageLoader)
        val bitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        imageLoader.memoryCache?.set(MemoryCache.Key("critical_bitmap"), MemoryCache.Value(bitmap))
        assertTrue("Memory cache size should be greater than zero before trim", (imageLoader.memoryCache?.size ?: 0) > 0)

        // Trigger onTrimMemory with TRIM_MEMORY_RUNNING_CRITICAL (15)
        app.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)

        assertEquals(0, imageLoader.memoryCache?.size)

        val vocabAfter = MlModelFactory.getClassifierVocab(mockContext)
        assertEquals(3, vocabAfter.size)
        verify(exactly = 2) { mockAssets.open("vocab.txt") }
    }

    @Test
    fun `onLowMemory clears Coil and ML vocab caches`() {
        val mockContext = mockk<Context>()
        val mockAssets = mockk<AssetManager>()
        every { mockContext.assets } returns mockAssets
        every { mockAssets.open("vocab.txt") } answers {
            ByteArrayInputStream("food\nfuel\ngrocery".toByteArray())
        }

        val vocabBefore = MlModelFactory.getClassifierVocab(mockContext)
        assertEquals(3, vocabBefore.size)
        verify(exactly = 1) { mockAssets.open("vocab.txt") }

        val imageLoader = app.newImageLoader()
        Coil.setImageLoader(imageLoader)
        val bitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        imageLoader.memoryCache?.set(MemoryCache.Key("low_memory_bitmap"), MemoryCache.Value(bitmap))
        assertTrue("Memory cache size should be greater than zero before low memory", (imageLoader.memoryCache?.size ?: 0) > 0)

        // Trigger onLowMemory
        app.onLowMemory()

        assertEquals(0, imageLoader.memoryCache?.size)

        val vocabAfter = MlModelFactory.getClassifierVocab(mockContext)
        assertEquals(3, vocabAfter.size)
        verify(exactly = 2) { mockAssets.open("vocab.txt") }
    }

    @Test
    fun `onTrimMemory with level greater than TRIM_MEMORY_UI_HIDDEN clears caches`() {
        val mockContext = mockk<Context>()
        val mockAssets = mockk<AssetManager>()
        every { mockContext.assets } returns mockAssets
        every { mockAssets.open("vocab.txt") } answers {
            ByteArrayInputStream("food\nfuel\ngrocery".toByteArray())
        }

        MlModelFactory.getClassifierVocab(mockContext)
        verify(exactly = 1) { mockAssets.open("vocab.txt") }

        val imageLoader = app.newImageLoader()
        Coil.setImageLoader(imageLoader)
        val bitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        imageLoader.memoryCache?.set(MemoryCache.Key("complete_bitmap"), MemoryCache.Value(bitmap))
        assertTrue((imageLoader.memoryCache?.size ?: 0) > 0)

        // Test with TRIM_MEMORY_COMPLETE (80)
        app.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)

        assertEquals(0, imageLoader.memoryCache?.size)
        MlModelFactory.getClassifierVocab(mockContext)
        verify(exactly = 2) { mockAssets.open("vocab.txt") }
    }

    @Test
    fun `onTrimMemory with level less than TRIM_MEMORY_UI_HIDDEN does not clear caches`() {
        val mockContext = mockk<Context>()
        val mockAssets = mockk<AssetManager>()
        every { mockContext.assets } returns mockAssets
        every { mockAssets.open("vocab.txt") } answers {
            ByteArrayInputStream("food\nfuel\ngrocery".toByteArray())
        }

        val vocabBefore = MlModelFactory.getClassifierVocab(mockContext)
        verify(exactly = 1) { mockAssets.open("vocab.txt") }

        val imageLoader = app.newImageLoader()
        Coil.setImageLoader(imageLoader)
        val bitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        imageLoader.memoryCache?.set(MemoryCache.Key("retained_bitmap"), MemoryCache.Value(bitmap))
        assertTrue((imageLoader.memoryCache?.size ?: 0) > 0)

        // Test with TRIM_MEMORY_RUNNING_MODERATE (5 < 20 and != 15)
        app.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE)

        assertTrue("Memory cache should retain bitmap on moderate trim", (imageLoader.memoryCache?.size ?: 0) > 0)

        val vocabAfter = MlModelFactory.getClassifierVocab(mockContext)
        assertSame("Vocabulary map reference should remain cached", vocabBefore, vocabAfter)
        verify(exactly = 1) { mockAssets.open("vocab.txt") }
    }

    @Test
    fun `constants define all expected notification channel IDs`() {
        assertEquals("transaction_channel", MainApplication.TRANSACTION_CHANNEL_ID)
        assertEquals("rich_transaction_channel", MainApplication.RICH_TRANSACTION_CHANNEL_ID)
        assertEquals("daily_report_channel", MainApplication.DAILY_REPORT_CHANNEL_ID)
        assertEquals("summary_channel", MainApplication.SUMMARY_CHANNEL_ID)
        assertEquals("monthly_summary_channel", MainApplication.MONTHLY_SUMMARY_CHANNEL_ID)
        assertEquals("backup_channel", MainApplication.BACKUP_CHANNEL_ID)
        assertEquals("goals_channel", MainApplication.GOALS_CHANNEL_ID)
    }
}
