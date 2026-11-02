package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import io.pm.finlight.TestApplication
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.ml.SmsClassifier
import io.pm.finlight.ml.SmsEntityExtractor
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class BatchAnalysisViewModelFactoryTest {
    private lateinit var application: Application
    private lateinit var factory: BatchAnalysisViewModelFactory

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        val mockClassifier: SmsClassifier = mockk(relaxed = true)
        val mockNer: SmsEntityExtractor = mockk(relaxed = true)
        ServiceLocator.reset()
        ServiceLocator.setSmsClassifier(mockClassifier)
        ServiceLocator.setNerExtractorProvider { mockNer }
        factory = BatchAnalysisViewModelFactory(application)
    }

    @After
    fun tearDown() {
        ServiceLocator.reset()
    }

    @Test
    fun create_withBatchAnalysisViewModelClass_resolvesFromServiceLocator() {
        val vm = factory.create(BatchAnalysisViewModel::class.java)
        assertNotNull(vm)
    }

    @Test
    fun create_withUnknownViewModelClass_throwsIllegalArgumentException() {
        class UnknownViewModel : ViewModel()

        assertThrows(IllegalArgumentException::class.java) {
            factory.create(UnknownViewModel::class.java)
        }
    }

    @Test
    fun create_withExtras_returnsBatchAnalysisViewModel() {
        val vm = factory.create(BatchAnalysisViewModel::class.java, CreationExtras.Empty)
        assertNotNull(vm)
    }

    @Test
    fun create_withOverriddenViewModel_returnsMockedInstance() {
        val mockVM: BatchAnalysisViewModel = mockk(relaxed = true)
        ServiceLocator.setBatchAnalysisViewModel(mockVM)

        val resolved = factory.create(BatchAnalysisViewModel::class.java)
        assertSame(mockVM, resolved)
    }

    @Test
    fun create_withExplicitOverrides_usesPassedDependencies() {
        val mockClassifier: SmsClassifier = mockk(relaxed = true)
        val mockNer: SmsEntityExtractor = mockk(relaxed = true)
        val customFactory =
            BatchAnalysisViewModelFactory(
                context = application,
                nerExtractorProvider = { mockNer },
                classifier = mockClassifier,
            )

        val vm = customFactory.create(BatchAnalysisViewModel::class.java)
        assertNotNull(vm)
    }
}
