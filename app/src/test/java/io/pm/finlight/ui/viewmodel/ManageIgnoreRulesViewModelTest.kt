// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/ManageIgnoreRulesViewModelTest.kt
// REASON: REFACTOR (Testing) - The test class now extends `BaseViewModelTest`,
// inheriting all common setup logic and removing boilerplate for rules,
// dispatchers, and Mockito initialization.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.IgnoreRule
import io.pm.finlight.IgnoreRuleDao
import io.pm.finlight.ManageIgnoreRulesViewModel
import io.pm.finlight.RuleType
import io.pm.finlight.TestApplication
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Ignore
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
        ignoreRuleCaptor = ArgumentCaptor.forClass(IgnoreRule::class.java)
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
    @Ignore("TODO")
    fun `addIgnoreRule calls dao insert with correct non-default rule`() = runTest {
        // Arrange
        initializeViewModel()
        val pattern = " new rule "
        val type = RuleType.BODY_PHRASE

        // Act
        viewModel.addIgnoreRule(pattern, type)

        // Assert
        verify(ignoreRuleDao).insert(ignoreRuleCaptor.capture())
        val capturedRule = ignoreRuleCaptor.value
        assertEquals(pattern.trim(), capturedRule.pattern)
        assertEquals(type, capturedRule.type)
        assertEquals(false, capturedRule.isDefault)
    }

    @Test
    @Ignore("TODO")
    fun `addIgnoreRule does not insert blank pattern`() = runTest {
        // Arrange
        initializeViewModel()

        // Act
        viewModel.addIgnoreRule("  ", RuleType.SENDER)

        // Assert
        verify(ignoreRuleDao, never()).insert(any(IgnoreRule::class.java))
    }

    @Test
    fun `updateIgnoreRule calls dao update`() = runTest {
        // Arrange
        initializeViewModel()
        val ruleToUpdate = IgnoreRule(1, RuleType.BODY_PHRASE, "pattern", isEnabled = false)

        // Act
        viewModel.updateIgnoreRule(ruleToUpdate)

        // Assert
        verify(ignoreRuleDao).update(ruleToUpdate)
    }

    @Test
    fun `deleteIgnoreRule calls dao delete for non-default rule`() = runTest {
        // Arrange
        initializeViewModel()
        val ruleToDelete = IgnoreRule(1, RuleType.SENDER, "custom", isDefault = false)

        // Act
        viewModel.deleteIgnoreRule(ruleToDelete)

        // Assert
        verify(ignoreRuleDao).delete(ruleToDelete)
    }

    @Test
    fun `deleteIgnoreRule does NOT call dao delete for default rule`() = runTest {
        // Arrange
        initializeViewModel()
        val defaultRule = IgnoreRule(1, RuleType.SENDER, "default", isDefault = true)

        // Act
        viewModel.deleteIgnoreRule(defaultRule)

        // Assert
        verify(ignoreRuleDao, never()).delete(defaultRule)
    }
}