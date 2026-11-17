package io.pm.finlight.ui.common

import android.content.res.Configuration
import android.os.Build
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.TestApplication
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class ForceAppScalingTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun forceAppScaling_preservesSystemFontScale_onStandardScreen() {
        val observed =
            renderAndCaptureDensity(
                screenWidthDp = 400,
                initialDensity = 2.0f,
                initialFontScale = 1.5f,
            )

        assertEquals(1.5f, observed.fontScale, 0.001f)
        assertEquals(2.0f, observed.density, 0.001f)
    }

    @Test
    fun forceAppScaling_preservesSystemFontScale_andClampsDensity_onNarrowScreen() {
        val observed =
            renderAndCaptureDensity(
                screenWidthDp = 300,
                initialDensity = 3.0f,
                initialFontScale = 1.5f,
            )

        val expectedDensity = 3.0f * (300f / 375f)
        assertEquals(1.5f, observed.fontScale, 0.001f)
        assertEquals(expectedDensity, observed.density, 0.001f)
    }

    @Test
    fun forceAppScaling_atBoundaryWidth_doesNotScaleDensity() {
        val observed =
            renderAndCaptureDensity(
                screenWidthDp = 375,
                initialDensity = 2.5f,
                initialFontScale = 1.3f,
            )

        assertEquals(1.3f, observed.fontScale, 0.001f)
        assertEquals(2.5f, observed.density, 0.001f)
    }

    @Test
    fun forceAppScaling_whenScreenWidthIsZeroOrNegative_doesNotScaleDensity() {
        val observed =
            renderAndCaptureDensity(
                screenWidthDp = 0,
                initialDensity = 2.0f,
                initialFontScale = 2.0f,
            )

        assertEquals(2.0f, observed.fontScale, 0.001f)
        assertEquals(2.0f, observed.density, 0.001f)
    }

    private fun renderAndCaptureDensity(
        screenWidthDp: Int,
        initialDensity: Float,
        initialFontScale: Float,
    ): Density {
        var capturedDensity: Density? = null
        composeTestRule.setContent {
            val config =
                Configuration().apply {
                    this.screenWidthDp = screenWidthDp
                }
            val density = Density(density = initialDensity, fontScale = initialFontScale)
            CompositionLocalProvider(
                LocalConfiguration provides config,
                LocalDensity provides density,
            ) {
                ForceAppScaling {
                    capturedDensity = LocalDensity.current
                }
            }
        }
        return checkNotNull(capturedDensity) { "Density was not captured" }
    }
}
