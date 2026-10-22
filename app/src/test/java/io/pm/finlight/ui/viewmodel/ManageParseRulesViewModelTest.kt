package io.pm.finlight.ui.viewmodel

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.CustomSmsRule
import io.pm.finlight.ICustomSmsRuleRepository
import io.pm.finlight.TestApplication
import io.pm.finlight.utils.TestDispatcherProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class ManageParseRulesViewModelTest : BaseViewModelTest() {
    @Mock
    private lateinit var customSmsRuleRepository: ICustomSmsRuleRepository

    private lateinit var viewModel: ManageParseRulesViewModel
    private lateinit var testDispatcherProvider: TestDispatcherProvider

    @Before
    override fun setup() {
        super.setup()
        testDispatcherProvider = TestDispatcherProvider(testDispatcher)
    }

    @Test
    fun `allRules flow emits rules from repository`() =
        runTest {
            // Arrange
            val rules =
                listOf(
                    CustomSmsRule(1, "trigger1", null, null, null, null, null, null, 10, ""),
                )
            `when`(customSmsRuleRepository.getAllRules()).thenReturn(flowOf(rules))
            viewModel = ManageParseRulesViewModel(customSmsRuleRepository, testDispatcherProvider)

            // Assert
            viewModel.allRules.test {
                assertEquals(rules, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `deleteRule calls repository delete`() =
        runTest {
            // Arrange
            val ruleToDelete = CustomSmsRule(1, "trigger1", null, null, null, null, null, null, 10, "")
            `when`(customSmsRuleRepository.getAllRules()).thenReturn(flowOf(emptyList()))
            viewModel = ManageParseRulesViewModel(customSmsRuleRepository, testDispatcherProvider)

            // Act
            viewModel.deleteRule(ruleToDelete)
            advanceUntilIdle()

            // Assert
            verify(customSmsRuleRepository).delete(ruleToDelete)
            assertNull(viewModel.errorMessage.value)
        }

    @Test
    fun `deleteRule sets errorMessage on exception and clearError resets it`() =
        runTest {
            // Arrange
            val ruleToDelete = CustomSmsRule(1, "trigger1", null, null, null, null, null, null, 10, "")
            `when`(customSmsRuleRepository.getAllRules()).thenReturn(flowOf(emptyList()))
            doThrow(RuntimeException("Disk error")).`when`(customSmsRuleRepository).delete(ruleToDelete)
            viewModel = ManageParseRulesViewModel(customSmsRuleRepository, testDispatcherProvider)

            // Act
            viewModel.deleteRule(ruleToDelete)
            advanceUntilIdle()

            // Assert
            assertEquals("Disk error", viewModel.errorMessage.value)

            // Clear error
            viewModel.clearError()
            assertNull(viewModel.errorMessage.value)
        }
}
