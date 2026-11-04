package io.pm.finlight.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class UtilizationColorTest {
    private val testColorScheme =
        darkColorScheme(
            primary = Color.Green,
            tertiary = Color.Yellow,
            error = Color.Red,
        )

    @Test
    fun getUtilizationColor_healthyUtilization_returnsPrimaryColor() {
        assertEquals(testColorScheme.primary, testColorScheme.getUtilizationColor(0f))
        assertEquals(testColorScheme.primary, testColorScheme.getUtilizationColor(15f))
        assertEquals(testColorScheme.primary, testColorScheme.getUtilizationColor(30f))
        assertEquals(testColorScheme.primary, testColorScheme.getUtilizationColor(-5f))
    }

    @Test
    fun getUtilizationColor_moderateUtilization_returnsTertiaryColor() {
        assertEquals(testColorScheme.tertiary, testColorScheme.getUtilizationColor(30.01f))
        assertEquals(testColorScheme.tertiary, testColorScheme.getUtilizationColor(40f))
        assertEquals(testColorScheme.tertiary, testColorScheme.getUtilizationColor(50f))
    }

    @Test
    fun getUtilizationColor_highUtilization_returnsErrorColor() {
        assertEquals(testColorScheme.error, testColorScheme.getUtilizationColor(50.01f))
        assertEquals(testColorScheme.error, testColorScheme.getUtilizationColor(75f))
        assertEquals(testColorScheme.error, testColorScheme.getUtilizationColor(100f))
        assertEquals(testColorScheme.error, testColorScheme.getUtilizationColor(150f))
    }
}
