package io.pm.finlight.ml

import android.content.Context
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class MlModelFactoryTest {
    @Test
    fun `getClassifier returns a valid instance or throws UnsatisfiedLinkError`() {
        val context = mockk<Context>(relaxed = true)
        try {
            val classifier1 = MlModelFactory.getClassifier(context)
            assertNotNull(classifier1)
            val classifier2 = MlModelFactory.getClassifier(context)
            assertSame(classifier1, classifier2, "Should return the same singleton instance")
        } catch (e: UnsatisfiedLinkError) {
            // Expected in pure JVM tests since TFLite native libs aren't available
            assertNotNull(e)
        }
    }

    @Test
    fun `getNerExtractor returns a valid instance or throws UnsatisfiedLinkError`() {
        val context = mockk<Context>(relaxed = true)
        try {
            val extractor1 = MlModelFactory.getNerExtractor(context)
            assertNotNull(extractor1)
            val extractor2 = MlModelFactory.getNerExtractor(context)
            assertSame(extractor1, extractor2, "Should return the same singleton instance")
        } catch (e: UnsatisfiedLinkError) {
            // Expected in pure JVM tests since TFLite native libs aren't available
            assertNotNull(e)
        }
    }
}
