package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import io.pm.finlight.ISettingsRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.TestApplication
import io.pm.finlight.di.ServiceLocator
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
class WhatIfViewModelFactoryTest {
    private lateinit var application: Application
    private lateinit var factory: WhatIfViewModelFactory

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        factory = WhatIfViewModelFactory(application)
        ServiceLocator.reset()
    }

    @After
    fun tearDown() {
        ServiceLocator.reset()
    }

    @Test
    fun create_withWhatIfViewModelClass_resolvesRepositoriesFromServiceLocator() {
        val mockSettingsRepo: ISettingsRepository = mockk(relaxed = true)
        val mockTxnRepo: ITransactionRepository = mockk(relaxed = true)

        ServiceLocator.setSettingsRepository(mockSettingsRepo)
        ServiceLocator.setTransactionRepository(mockTxnRepo)

        val viewModel = factory.create(WhatIfViewModel::class.java)

        assertNotNull(viewModel)

        val settingsRepoField =
            WhatIfViewModel::class.java.getDeclaredField("settingsRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockSettingsRepo, settingsRepoField)

        val txnRepoField =
            WhatIfViewModel::class.java.getDeclaredField("transactionRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockTxnRepo, txnRepoField)
    }

    @Test
    fun create_withUnknownViewModelClass_throwsIllegalArgumentException() {
        class UnknownViewModel : ViewModel()

        assertThrows(IllegalArgumentException::class.java) {
            factory.create(UnknownViewModel::class.java)
        }
    }
}
