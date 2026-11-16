package io.pm.finlight.ui.theme

import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.TestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class FinlightThemeTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun darkFinlightColors_hasExpectedTokens() {
        assertEquals(IncomeGreenDark, DarkFinlightColors.income)
        assertEquals(ExpenseRedDark, DarkFinlightColors.expense)
        assertEquals(Color(0xFFFF8C00), DarkFinlightColors.warning)
        assertEquals(Color(0xFF34C759), DarkFinlightColors.success)
        assertEquals(Color.White.copy(alpha = 0.08f), DarkFinlightColors.glassSurface)
        assertEquals(GlassPanelBorder, DarkFinlightColors.glassBorder)
        assertEquals(AuroraNumpadHighlight, DarkFinlightColors.glassHighlight)
    }

    @Test
    fun lightFinlightColors_hasExpectedTokens() {
        assertEquals(IncomeGreenLight, LightFinlightColors.income)
        assertEquals(ExpenseRedLight, LightFinlightColors.expense)
        assertEquals(Color(0xFFFF8C00), LightFinlightColors.warning)
        assertEquals(Color(0xFF34C759), LightFinlightColors.success)
        assertEquals(Color.Black.copy(alpha = 0.04f), LightFinlightColors.glassSurface)
        assertEquals(Color(0x26000000), LightFinlightColors.glassBorder)
        assertEquals(Color.Black.copy(alpha = 0.08f), LightFinlightColors.glassHighlight)
    }

    @Test
    fun resolveFinlightColors_auroraTheme_alwaysResolvesDarkTokens() {
        assertResolvesToExpected(AppTheme.AURORA, isSystemDark = false, expected = DarkFinlightColors)
        assertResolvesToExpected(AppTheme.AURORA, isSystemDark = true, expected = DarkFinlightColors)
    }

    @Test
    fun resolveFinlightColors_midnightTheme_alwaysResolvesDarkTokens() {
        assertResolvesToExpected(AppTheme.MIDNIGHT, isSystemDark = false, expected = DarkFinlightColors)
        assertResolvesToExpected(AppTheme.MIDNIGHT, isSystemDark = true, expected = DarkFinlightColors)
    }

    @Test
    fun resolveFinlightColors_daybreakTheme_alwaysResolvesLightTokens() {
        assertResolvesToExpected(AppTheme.DAYBREAK, isSystemDark = false, expected = LightFinlightColors)
        assertResolvesToExpected(AppTheme.DAYBREAK, isSystemDark = true, expected = LightFinlightColors)
    }

    @Test
    fun resolveFinlightColors_paperTheme_alwaysResolvesLightTokens() {
        assertResolvesToExpected(AppTheme.PAPER, isSystemDark = false, expected = LightFinlightColors)
        assertResolvesToExpected(AppTheme.PAPER, isSystemDark = true, expected = LightFinlightColors)
    }

    @Test
    fun resolveFinlightColors_systemDefault_followsSystemSetting() {
        assertResolvesToExpected(AppTheme.SYSTEM_DEFAULT, isSystemDark = false, expected = LightFinlightColors)
        assertResolvesToExpected(AppTheme.SYSTEM_DEFAULT, isSystemDark = true, expected = DarkFinlightColors)
    }

    @Test
    fun resolveFinlightColors_dynamicColor_resolvesBasedOnSystemDarkState() {
        AppTheme.entries.forEach { theme ->
            assertEquals(
                DarkFinlightColors,
                resolveFinlightColors(selectedTheme = theme, isSystemDark = true, dynamicColor = true),
            )
            assertEquals(
                LightFinlightColors,
                resolveFinlightColors(selectedTheme = theme, isSystemDark = false, dynamicColor = true),
            )
        }
    }

    @Test
    fun finlightColors_copyAndEqualityWorkAsExpected() {
        val customColors = DarkFinlightColors.copy(income = Color.Cyan)
        assertNotEquals(DarkFinlightColors, customColors)
        assertEquals(Color.Cyan, customColors.income)
        assertEquals(DarkFinlightColors.expense, customColors.expense)
    }

    @Test
    fun personalFinanceAppTheme_providesFinlightColorsInComposition() {
        var observedColors: FinlightColors? = null
        composeTestRule.setContent {
            PersonalFinanceAppTheme(selectedTheme = AppTheme.AURORA) {
                observedColors = FinlightTheme.colors
            }
        }
        assertEquals(DarkFinlightColors, observedColors)
    }

    @Test
    fun localFinlightColors_unprovided_defaultsToDarkFinlightColors() {
        var observedColors: FinlightColors? = null
        composeTestRule.setContent {
            observedColors = FinlightTheme.colors
        }
        assertEquals(DarkFinlightColors, observedColors)
    }

    @Test
    fun personalFinanceAppTheme_overridesDefaultWithSelectedTheme() {
        var observedColors: FinlightColors? = null
        composeTestRule.setContent {
            PersonalFinanceAppTheme(selectedTheme = AppTheme.DAYBREAK) {
                observedColors = FinlightTheme.colors
            }
        }
        assertEquals(LightFinlightColors, observedColors)
    }

    private fun assertResolvesToExpected(
        theme: AppTheme,
        isSystemDark: Boolean,
        expected: FinlightColors,
    ) {
        val actual = resolveFinlightColors(selectedTheme = theme, isSystemDark = isSystemDark)
        assertEquals(expected, actual)
    }
}
