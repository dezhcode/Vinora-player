package com.example.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.data.GraphicsQuality
import com.example.ui.theme.PlinthTheme
import com.example.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TurntableDeckTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tonearmKinematicsTracksOuterToInnerGroovesWithExactRadius() {
        val outerDeg = TurntableGeometrySpec.computeGrooveAngleDeg(0f)
        val midDeg = TurntableGeometrySpec.computeGrooveAngleDeg(0.5f)
        val innerDeg = TurntableGeometrySpec.computeGrooveAngleDeg(1f)

        assertTrue("Tonearm angle should sweep monotonically inward", outerDeg < midDeg && midDeg < innerDeg)

        fun stylusDistanceFromPlatter(armAngleDeg: Float): Float {
            val rayAngleDeg = armAngleDeg - TurntableGeometrySpec.STYLUS_OFFSET_COMPENSATION_DEG
            val rad = rayAngleDeg * PI / 180.0
            val sx = TurntableGeometrySpec.PIVOT_CX - (sin(rad) * TurntableGeometrySpec.EFFECTIVE_LENGTH_RATIO).toFloat()
            val sy = TurntableGeometrySpec.PIVOT_CY + (cos(rad) * TurntableGeometrySpec.EFFECTIVE_LENGTH_RATIO).toFloat()
            return hypot(sx - TurntableGeometrySpec.PLATTER_CX, sy - TurntableGeometrySpec.PLATTER_CY)
        }

        val outerDist = stylusDistanceFromPlatter(outerDeg)
        val midDist = stylusDistanceFromPlatter(midDeg)
        val innerDist = stylusDistanceFromPlatter(innerDeg)

        assertTrue("Outer stylus radius must sit cleanly inside outer vinyl groove", outerDist in 0.23f..0.26f)
        assertTrue("Mid stylus radius must sit between outer and inner", midDist in 0.18f..0.21f)
        assertTrue("Inner stylus radius must sit near lead-out groove", innerDist in 0.125f..0.145f)
    }

    @Test
    fun turntableDeckRendersInLightModeAndKnobsTogglePlayback() {
        var toggled = 0
        composeRule.setContent {
            PlinthTheme(themeMode = ThemeMode.LIGHT) {
                TurntableDeck(
                    artworkUri = null,
                    isPlaying = false,
                    progressFraction = 0.35f,
                    volume = 0.75f,
                    trackChangeNonce = 0,
                    trackChangeDirection = 1,
                    graphicsQuality = GraphicsQuality.HIGH,
                    batterySaverMode = false,
                    reducedMotion = false,
                    onTogglePlayPause = { toggled++ },
                    onScrubDeltaMs = {},
                    onVolumeChange = {},
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("turntable_deck").assertIsDisplayed()
        composeRule.onNodeWithTag("turntable_right_knob").performClick()
        composeRule.onNodeWithTag("turntable_left_knob").performClick()
        composeRule.waitForIdle()
        assertEquals(2, toggled)
    }

    @Test
    fun turntableDeckSpinsCleanlyWhenPlayingAndRendersInDarkMode() {
        var toggled = 0
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PlinthTheme(themeMode = ThemeMode.DARK) {
                TurntableDeck(
                    artworkUri = null,
                    isPlaying = true,
                    progressFraction = 0.4f,
                    volume = 0.5f,
                    trackChangeNonce = 1,
                    trackChangeDirection = -1,
                    graphicsQuality = GraphicsQuality.LOW,
                    batterySaverMode = true,
                    reducedMotion = true,
                    onTogglePlayPause = { toggled++ },
                    onScrubDeltaMs = {},
                    onVolumeChange = {},
                )
            }
        }

        composeRule.mainClock.advanceTimeBy(120)
        composeRule.onNodeWithTag("turntable_deck").assertIsDisplayed()
        composeRule.onNodeWithTag("turntable_left_knob").assertIsDisplayed()
        composeRule.onNodeWithTag("turntable_right_knob").performClick()
        composeRule.mainClock.advanceTimeBy(48)
        assertEquals(1, toggled)
    }
}
