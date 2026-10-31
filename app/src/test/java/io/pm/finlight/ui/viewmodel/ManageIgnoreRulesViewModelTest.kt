package io.pm.finlight.ui.viewmodel

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.IIgnoreRuleRepository
import io.pm.finlight.IgnoreRule
import io.pm.finlight.RuleType
import io.pm.finlight.TestApplication
import io.pm.finlight.anyObject
import io.pm.finlight.argumentCaptor
import io.pm.finlight.capture
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
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class ManageIgnoreRulesViewModelTest : BaseViewModelTest() {
    @Mock
    private lateinit var ignoreRuleRepository: IIgnoreRuleRepository

    private lateinit var ignoreRuleCaptor: ArgumentCaptor<IgnoreRule>
    private lateinit var testDispatcherProvider: TestDispatcherProvider
    private lateinit var viewModel: ManageIgnoreRulesViewModel

    @Before
    override fun setup() {
        super.setup()
        ignoreRuleCaptor = argumentCaptor()
        testDispatcherProvider = TestDispatcherProvider(testDispatcher)
        initializeViewModel()
    }

    private fun initializeViewModel(initialRules: List<IgnoreRule> = emptyList()) {
        `when`(ignoreRuleRepository.getAllRules()).thenReturn(flowOf(initialRules))
        viewModel = ManageIgnoreRulesViewModel(ignoreRuleRepository, testDispatcherProvider)
    }

    @Test
    fun `allRules flow emits rules from repository`() =
        runTest {
            // Arrange
            val rules = listOf(IgnoreRule(1, RuleType.SENDER, "*spam*", true, true))
            initializeViewModel(rules)

            // Assert
            viewModel.allRules.test {
                assertEquals(rules, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `addIgnoreRule calls repository insert with correct non-default rule`() =
        runTest {
            // Arrange
            val pattern = " new rule "
            val type = RuleType.BODY_PHRASE

            // Act
            viewModel.addIgnoreRule(pattern, type)
            advanceUntilIdle()

            // Assert
            verify(ignoreRuleRepository).insert(capture(ignoreRuleCaptor))
            val capturedRule = ignoreRuleCaptor.value
            assertEquals(pattern.trim(), capturedRule.pattern)
            assertEquals(type, capturedRule.type)
            assertEquals(false, capturedRule.isDefault)
            assertNull(viewModel.errorMessage.value)
        }

    @Test
    fun `addIgnoreRule sets errorMessage when repository throws exception`() =
        runTest {
            // Arrange
            val pattern = "fail pattern"
            doThrow(RuntimeException("Insert error")).`when`(ignoreRuleRepository).insert(anyObject())

            // Act
            viewModel.addIgnoreRule(pattern, RuleType.SENDER)
            advanceUntilIdle()

            // Assert
            assertEquals("Insert error", viewModel.errorMessage.value)
            viewModel.clearError()
            assertNull(viewModel.errorMessage.value)
        }

    @Test
    fun `addIgnoreRule does not insert blank pattern`() =
        runTest {
            // Act
            viewModel.addIgnoreRule("  ", RuleType.SENDER)
            advanceUntilIdle()

            // Assert
            verify(ignoreRuleRepository, never()).insert(anyObject())
        }

    @Test
    fun `updateIgnoreRule calls repository update`() =
        runTest {
            // Arrange
            val ruleToUpdate = IgnoreRule(1, RuleType.BODY_PHRASE, "pattern", isEnabled = false)

            // Act
            viewModel.updateIgnoreRule(ruleToUpdate)
            advanceUntilIdle()

            // Assert
            verify(ignoreRuleRepository).update(ruleToUpdate)
            assertNull(viewModel.errorMessage.value)
        }

    @Test
    fun `updateIgnoreRule sets errorMessage when repository throws exception`() =
        runTest {
            // Arrange
            val ruleToUpdate = IgnoreRule(1, RuleType.BODY_PHRASE, "pattern", isEnabled = false)
            doThrow(RuntimeException("Update error")).`when`(ignoreRuleRepository).update(ruleToUpdate)

            // Act
            viewModel.updateIgnoreRule(ruleToUpdate)
            advanceUntilIdle()

            // Assert
            assertEquals("Update error", viewModel.errorMessage.value)
        }

    @Test
    fun `deleteIgnoreRule calls repository delete for non-default rule`() =
        runTest {
            // Arrange
            val ruleToDelete = IgnoreRule(1, RuleType.SENDER, "custom", isDefault = false)

            // Act
            viewModel.deleteIgnoreRule(ruleToDelete)
            advanceUntilIdle()

            // Assert
            verify(ignoreRuleRepository).delete(ruleToDelete)
            assertNull(viewModel.errorMessage.value)
        }

    @Test
    fun `deleteIgnoreRule sets errorMessage when repository throws exception`() =
        runTest {
            // Arrange
            val ruleToDelete = IgnoreRule(1, RuleType.SENDER, "custom", isDefault = false)
            doThrow(RuntimeException("Delete error")).`when`(ignoreRuleRepository).delete(ruleToDelete)

            // Act
            viewModel.deleteIgnoreRule(ruleToDelete)
            advanceUntilIdle()

            // Assert
            assertEquals("Delete error", viewModel.errorMessage.value)
        }

    @Test
    fun `deleteIgnoreRule does NOT call repository delete for default rule`() =
        runTest {
            // Arrange
            val defaultRule = IgnoreRule(1, RuleType.SENDER, "default", isDefault = true)

            // Act
            viewModel.deleteIgnoreRule(defaultRule)
            advanceUntilIdle()

            // Assert
            verify(ignoreRuleRepository, never()).delete(defaultRule)
        }

    @Test
    fun `addIgnoreRule rethrows CancellationException without setting errorMessage`() =
        runTest {
            doThrow(kotlinx.coroutines.CancellationException("Cancelled"))
                .`when`(ignoreRuleRepository).insert(anyObject())

            viewModel.addIgnoreRule("pattern", RuleType.SENDER)
            advanceUntilIdle()

            assertNull(viewModel.errorMessage.value)
        }

    @Test
    fun `updateIgnoreRule rethrows CancellationException without setting errorMessage`() =
        runTest {
            val rule = IgnoreRule(1, RuleType.BODY_PHRASE, "test", isEnabled = false)
            doThrow(kotlinx.coroutines.CancellationException("Cancelled"))
                .`when`(ignoreRuleRepository).update(rule)

            viewModel.updateIgnoreRule(rule)
            advanceUntilIdle()

            assertNull(viewModel.errorMessage.value)
        }

    @Test
    fun `deleteIgnoreRule rethrows CancellationException without setting errorMessage`() =
        runTest {
            val rule = IgnoreRule(1, RuleType.SENDER, "custom", isDefault = false)
            doThrow(kotlinx.coroutines.CancellationException("Cancelled"))
                .`when`(ignoreRuleRepository).delete(rule)

            viewModel.deleteIgnoreRule(rule)
            advanceUntilIdle()

            assertNull(viewModel.errorMessage.value)
        }
}
