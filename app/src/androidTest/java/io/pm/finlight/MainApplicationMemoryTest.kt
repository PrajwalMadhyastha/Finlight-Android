package io.pm.finlight

import android.app.Application
import android.content.ComponentCallbacks2
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil.Coil
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainApplicationMemoryTest {
    @After
    fun tearDown() {
        Coil.reset()
    }

    @Test
    fun verifyApplicationImplementsImageLoaderFactoryAndConfiguresBoundedCache() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        assertTrue("MainApplication must implement ImageLoaderFactory", app is ImageLoaderFactory)
        assertTrue("MainApplication must implement ComponentCallbacks2", app is ComponentCallbacks2)

        val imageLoaderFactory = app as ImageLoaderFactory
        val imageLoader = imageLoaderFactory.newImageLoader()
        assertNotNull("ImageLoader should not be null", imageLoader)
        Coil.setImageLoader(imageLoader)

        val memoryCache = imageLoader.memoryCache
        assertNotNull("Memory cache should be configured", memoryCache)
        assertTrue("Memory cache max size must be bounded and greater than 0", memoryCache!!.maxSize > 0)

        // Populate Coil memory cache with a real bitmap
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        memoryCache.set(MemoryCache.Key("instrumented_test_bitmap"), MemoryCache.Value(bitmap))
        assertTrue("Memory cache size should be greater than 0 after insertion", memoryCache.size > 0)

        // Verify onTrimMemory with TRIM_MEMORY_UI_HIDDEN clears Coil memory cache
        val callbacks = app as ComponentCallbacks2
        callbacks.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        assertEquals("Memory cache size should be 0 after trim", 0, memoryCache.size)

        // Re-populate and verify TRIM_MEMORY_RUNNING_CRITICAL clears cache
        val criticalBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        memoryCache.set(MemoryCache.Key("critical_bitmap"), MemoryCache.Value(criticalBitmap))
        assertTrue("Memory cache size should be greater than 0 after insertion", memoryCache.size > 0)
        callbacks.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        assertEquals("Memory cache size should be 0 after critical trim", 0, memoryCache.size)

        // Re-populate and verify onLowMemory clears cache
        val lowMemoryBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        memoryCache.set(MemoryCache.Key("low_memory_bitmap"), MemoryCache.Value(lowMemoryBitmap))
        assertTrue("Memory cache size should be greater than 0 after insertion", memoryCache.size > 0)
        (app as Application).onLowMemory()
        assertEquals("Memory cache size should be 0 after onLowMemory", 0, memoryCache.size)
    }
}
