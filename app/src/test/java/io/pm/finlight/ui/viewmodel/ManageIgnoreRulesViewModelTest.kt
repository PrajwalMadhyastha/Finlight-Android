// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/ManageIgnoreRulesViewModelTest.kt
// REASON: REFACTOR (Testing) - The test class now extends `BaseViewModelTest`,
// inheriting all common setup logic and removing boilerplate for rules,
// dispatchers, and Mockito initialization.
// FIX (Testing) - Replaced a potentially problematic `any()` call with the
// null-safe `anyObject()` helper and re-enabled previously ignored tests.
// FIX (Testing) - Replaced ArgumentCaptor with argThat to resolve a
// "capture() must not be null" NullPointerException when verifying mocks with
// non-nullable Kotlin parameters.
// FIX (Testing) - Replaced `argThat` with `ArgumentCaptor` and a custom
// `capture()` helper function. This is the definitive fix for the `NullPointerException`
// when verifying suspend functions with non-nullable parameters.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.*
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class ManageIgnoreRulesViewModelTest : BaseViewModelTest() {

    @Mock
    private lateinit var ignoreRuleDao: IgnoreRuleDao

    private lateinit var ignoreRuleCaptor: ArgumentCaptor<IgnoreRule>

    private lateinit var viewModel: ManageIgnoreRulesViewModel

    @Before
    override fun setup() {
        super.setup()
        ignoreRuleCaptor = argumentCaptor()
        initializeViewModel()
    }

    private fun initializeViewModel(initialRules: List<IgnoreRule> = emptyList()) {
        `when`(ignoreRuleDao.getAll()).thenReturn(flowOf(initialRules))
        viewModel = ManageIgnoreRulesViewModel(ignoreRuleDao)
    }

    @Test
    fun `allRules flow emits rules from DAO`() = runTest {
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
    fun `addIgnoreRule calls dao insert with correct non-default rule`() = runTest {
        // Arrange
        val pattern = " new rule "
        val type = RuleType.BODY_PHRASE

        // Act
        viewModel.addIgnoreRule(pattern, type)
        advanceUntilIdle()

        // Assert
        verify(ignoreRuleDao).insert(capture(ignoreRuleCaptor))
        val capturedRule = ignoreRuleCaptor.value
        assertEquals(pattern.trim(), capturedRule.pattern)
        assertEquals(type, capturedRule.type)
        assertEquals(false, capturedRule.isDefault)
    }

    @Test
    fun `addIgnoreRule does not insert blank pattern`() = runTest {
        // Act
        viewModel.addIgnoreRule("  ", RuleType.SENDER)
        advanceUntilIdle()

        // Assert
        verify(ignoreRuleDao, never()).insert(anyObject())
    }

    @Test
    fun `updateIgnoreRule calls dao update`() = runTest {
        // Arrange
        val ruleToUpdate = IgnoreRule(1, RuleType.BODY_PHRASE, "pattern", isEnabled = false)

        // Act
        viewModel.updateIgnoreRule(ruleToUpdate)
        advanceUntilIdle()


        // Assert
        verify(ignoreRuleDao).update(ruleToUpdate)
    }

    @Test
    fun `deleteIgnoreRule calls dao delete for non-default rule`() = runTest {
        // Arrange
        val ruleToDelete = IgnoreRule(1, RuleType.SENDER, "custom", isDefault = false)

        // Act
        viewModel.deleteIgnoreRule(ruleToDelete)
        advanceUntilIdle()

        // Assert
        verify(ignoreRuleDao).delete(ruleToDelete)
    }

    @Test
    fun `deleteIgnoreRule does NOT call dao delete for default rule`() = runTest {
        // Arrange
        val defaultRule = IgnoreRule(1, RuleType.SENDER, "default", isDefault = true)

        // Act
        viewModel.deleteIgnoreRule(defaultRule)
        advanceUntilIdle()

        // Assert
        verify(ignoreRuleDao, never()).delete(defaultRule)
    }
}

