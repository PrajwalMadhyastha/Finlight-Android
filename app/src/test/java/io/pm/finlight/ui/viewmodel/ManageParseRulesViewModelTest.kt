// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/ManageParseRulesViewModelTest.kt
// REASON: NEW FILE - Unit tests for ManageParseRulesViewModel, covering state
// observation from the DAO and the delete rule action.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.os.Build
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.CustomSmsRule
import io.pm.finlight.CustomSmsRuleDao
import io.pm.finlight.ManageParseRulesViewModel
import io.pm.finlight.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class ManageParseRulesViewModelTest {

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    @Mock
    private lateinit var customSmsRuleDao: CustomSmsRuleDao

    private lateinit var viewModel: ManageParseRulesViewModel

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `allRules flow emits rules from DAO`() = runTest {
        // Arrange
        val rules = listOf(
            CustomSmsRule(1, "trigger1", null, null, null, null, null, null, 10, "")
        )
        `when`(customSmsRuleDao.getAllRules()).thenReturn(flowOf(rules))
        viewModel = ManageParseRulesViewModel(customSmsRuleDao)

        // Assert
        viewModel.allRules.test {
            assertEquals(rules, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteRule calls dao delete`() = runTest {
        // Arrange
        val ruleToDelete = CustomSmsRule(1, "trigger1", null, null, null, null, null, null, 10, "")
        `when`(customSmsRuleDao.getAllRules()).thenReturn(flowOf(emptyList()))
        viewModel = ManageParseRulesViewModel(customSmsRuleDao)

        // Act
        viewModel.deleteRule(ruleToDelete)

        // Assert
        verify(customSmsRuleDao).delete(ruleToDelete)
    }
}
