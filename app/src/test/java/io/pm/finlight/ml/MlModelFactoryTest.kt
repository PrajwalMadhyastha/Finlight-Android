package io.pm.finlight.ml

import android.content.Context
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertNotNull

class MlModelFactoryTest {
    @Test
    fun `getClassifier returns a valid instance or throws UnsatisfiedLinkError`() {
        val context = mockk<Context>(relaxed = true)
        try {
            val classifier = MlModelFactory.getClassifier(context)
            assertNotNull(classifier)
        } catch (e: UnsatisfiedLinkError) {
            // Expected in pure JVM tests since TFLite native libs aren't available
            assertNotNull(e)
        }
    }

    @Test
    fun `getNerExtractor returns a valid instance or throws UnsatisfiedLinkError`() {
        val context = mockk<Context>(relaxed = true)
        try {
            val extractor = MlModelFactory.getNerExtractor(context)
            assertNotNull(extractor)
        } catch (e: UnsatisfiedLinkError) {
            // Expected in pure JVM tests since TFLite native libs aren't available
            assertNotNull(e)
        }
    }
}
