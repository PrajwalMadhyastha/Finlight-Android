package io.pm.finlight

import android.content.ComponentCallbacks2
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil.ImageLoaderFactory
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainApplicationMemoryTest {
    @Test
    fun verifyApplicationImplementsImageLoaderFactoryAndConfiguresBoundedCache() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        assertTrue("MainApplication must implement ImageLoaderFactory", app is ImageLoaderFactory)
        assertTrue("MainApplication must implement ComponentCallbacks2", app is ComponentCallbacks2)

        val imageLoaderFactory = app as ImageLoaderFactory
        val imageLoader = imageLoaderFactory.newImageLoader()
        assertNotNull("ImageLoader should not be null", imageLoader)

        val memoryCache = imageLoader.memoryCache
        assertNotNull("Memory cache should be configured", memoryCache)
        assertTrue("Memory cache max size must be bounded and greater than 0", memoryCache!!.maxSize > 0)

        // Verify onTrimMemory executes cleanly without throwing
        val callbacks = app as ComponentCallbacks2
        callbacks.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
    }
}
