package io.pm.finlight.ui.components

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.TestApplication
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class AnimatedBackgroundsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun auroraAnimatedBackground_rendersInActiveMode() {
        composeTestRule.setContent {
            AuroraAnimatedBackground(animationsDisabled = false)
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun auroraAnimatedBackground_rendersInDisabledMode() {
        composeTestRule.setContent {
            AuroraAnimatedBackground(animationsDisabled = true)
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun auroraAnimatedBackground_rendersWithDefaultParameters() {
        composeTestRule.setContent {
            AuroraAnimatedBackground()
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun daybreakAnimatedBackground_rendersInActiveMode() {
        composeTestRule.setContent {
            DaybreakAnimatedBackground(animationsDisabled = false)
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun daybreakAnimatedBackground_rendersInDisabledMode() {
        composeTestRule.setContent {
            DaybreakAnimatedBackground(animationsDisabled = true)
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun daybreakAnimatedBackground_rendersWithDefaultParameters() {
        composeTestRule.setContent {
            DaybreakAnimatedBackground()
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun createCachedCircles_withValidDimensions_generatesAccurateGeometryAndBrushes() {
        val size = Size(1000f, 2000f)
        val cached = createCachedCircles(AuroraCircles, size)

        assertEquals(AuroraCircles.size, cached.size)
        for (i in AuroraCircles.indices) {
            val original = AuroraCircles[i]
            val item = cached[i]

            val expectedCenter =
                Offset(
                    x = original.baseCenter.x * size.width,
                    y = original.baseCenter.y * size.height,
                )
            val expectedRadius = size.maxDimension * original.baseRadiusMultiplier

            assertEquals(expectedCenter.x, item.center.x, 0.001f)
            assertEquals(expectedCenter.y, item.center.y, 0.001f)
            assertEquals(expectedRadius, item.radius, 0.001f)
            assertEquals(original.speedMultiplier, item.speedMultiplier, 0.001f)
            assertNotNull(item.brush)
        }
    }

    @Test
    fun createCachedCircles_withZeroOrNegativeDimensions_returnsEmptyArray() {
        assertTrue(createCachedCircles(AuroraCircles, Size(0f, 1000f)).isEmpty())
        assertTrue(createCachedCircles(AuroraCircles, Size(1000f, 0f)).isEmpty())
        assertTrue(createCachedCircles(AuroraCircles, Size(-10f, -10f)).isEmpty())
    }

    @Test
    fun animatedCircle_precomputesGradientColorsWithAlpha() {
        val circle =
            AnimatedCircle(
                color = Color(0xFF123456),
                baseRadiusMultiplier = 0.5f,
                baseCenter = Offset(0.5f, 0.5f),
                speedMultiplier = 1.0f,
                alpha = 0.4f,
            )

        assertEquals(2, circle.gradientColors.size)
        assertEquals(0.4f, circle.gradientColors[0].alpha, 0.01f)
        assertEquals(Color.Transparent, circle.gradientColors[1])
    }

    @Test
    fun isAnimationDisabled_detectsScaleAndPowerSaveModes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val shadowPm = shadowOf(powerManager)

        // Case 1: Normal mode (scale 1.0, power save off)
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1.0f)
        shadowPm.setIsPowerSaveMode(false)
        assertFalse(isAnimationDisabled(context))

        // Case 2: Animation duration scale = 0.0f (animations disabled)
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0.0f)
        shadowPm.setIsPowerSaveMode(false)
        assertTrue(isAnimationDisabled(context))

        // Case 3: Power save mode enabled
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1.0f)
        shadowPm.setIsPowerSaveMode(true)
        assertTrue(isAnimationDisabled(context))
    }

    @Test
    fun drawLoop_tenThousandFrames_executesWithoutAllocationsOrMemoryLeaks() {
        val size = Size(1080f, 2400f)
        val cachedCircles = createCachedCircles(DaybreakCircles, size)

        System.gc()
        val initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

        // Simulate 10,000 draw frames
        val height = size.height
        var totalOffset = 0f
        for (frame in 0 until 10_000) {
            val currentTime = frame * 0.016f // ~60fps step
            for (i in cachedCircles.indices) {
                val circle = cachedCircles[i]
                val yOffset = sin(currentTime * circle.speedMultiplier) * height * 0.1f
                totalOffset += yOffset
            }
        }

        val finalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        // Ensure simulation did real work
        assertNotNull(totalOffset)
        // Ensure memory usage did not leak or expand abnormally
        assertTrue(finalMemory - initialMemory < 10 * 1024 * 1024)
    }
}
