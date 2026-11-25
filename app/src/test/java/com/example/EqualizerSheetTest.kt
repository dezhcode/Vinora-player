package com.example

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.audio.AudioEqualizerController
import com.example.audio.EqualizerState
import com.example.audio.SpectrumSnapshot
import com.example.ui.i18n.AppLanguage
import com.example.ui.screens.EqualizerBottomSheet
import com.example.ui.theme.PlinthTheme
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EqualizerSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun launchEqualizerSheet(
        widthQualifiers: String,
        fontScale: Float,
        language: AppLanguage,
        initialState: EqualizerState = EqualizerState(
            enabled = true,
            hardwareAttached = true,
            presetIndex = 0,
            bandLevelsDb = listOf(0, 0, 0, 0, 0),
        ),
        onStateObserved: (EqualizerState) -> Unit = {},
        onDismissCalled: () -> Unit = {},
    ) {
        RuntimeEnvironment.setQualifiers(widthQualifiers)
        val spectrumFlow = MutableStateFlow(SpectrumSnapshot())
        composeTestRule.setContent {
            val baseDensity = LocalDensity.current
            var eqState by remember { mutableStateOf(initialState) }
            CompositionLocalProvider(
                LocalDensity provides Density(baseDensity.density, fontScale = fontScale),
            ) {
                PlinthTheme(themeMode = ThemeMode.LIGHT, language = language) {
                    EqualizerBottomSheet(
                        eqState = eqState,
                        spectrumFlow = spectrumFlow,
                        hapticsEnabled = false,
                        onToggleEnabled = { enabled ->
                            eqState = eqState.copy(enabled = enabled)
                            onStateObserved(eqState)
                        },
                        onSelectPreset = { idx ->
                            val preset = AudioEqualizerController.presets.getOrNull(idx)
                            if (preset != null) {
                                eqState = eqState.copy(
                                    enabled = true,
                                    presetIndex = preset.index,
                                    bandLevelsDb = preset.bandsDb,
                                )
                                onStateObserved(eqState)
                            }
                        },
                        onBandChange = { bandIdx, db ->
                            val updated = eqState.bandLevelsDb.toMutableList()
                            updated[bandIdx] = db
                            eqState = eqState.copy(
                                enabled = true,
                                presetIndex = -1,
                                bandLevelsDb = updated,
                            )
                            onStateObserved(eqState)
                        },
                        onDismiss = onDismissCalled,
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun width360_font10_en_showsSingleRowSlidersBeforeChartsAndUpdatesOnDragPresetReset() {
        var latestState = EqualizerState()
        var dismissed = false
        launchEqualizerSheet(
            widthQualifiers = "w360dp-h760dp-mdpi",
            fontScale = 1.0f,
            language = AppLanguage.EN,
            onStateObserved = { latestState = it },
            onDismissCalled = { dismissed = true },
        )

        composeTestRule.onNodeWithTag("equalizer_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("eq_header_row").assertIsDisplayed()
        composeTestRule.onNodeWithTag("eq_enable_switch").assertIsDisplayed()

        // Sliders are in a single row at 360dp / 1.0f
        composeTestRule.onNodeWithTag("eq_sliders_row_0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("eq_sliders_row_1").assertDoesNotExist()

        // Verify vertical order: header -> sliders -> presets -> scope -> telemetry
        val headerBounds = composeTestRule.onNodeWithTag("eq_header_row").getUnclippedBoundsInRoot()
        val slidersBounds = composeTestRule.onNodeWithTag("eq_sliders_panel").getUnclippedBoundsInRoot()
        val scopeBounds = composeTestRule.onNodeWithTag("eq_response_scope").getUnclippedBoundsInRoot()
        val telemetryBounds = composeTestRule.onNodeWithTag("eq_telemetry_row").getUnclippedBoundsInRoot()
        assertTrue(headerBounds.bottom <= slidersBounds.top + 20.dp)
        assertTrue(slidersBounds.bottom <= scopeBounds.top + 80.dp)
        assertTrue(scopeBounds.bottom <= telemetryBounds.top + 20.dp)

        // All 5 bands visible and >= 48dp touch width
        for (i in 0..4) {
            composeTestRule.onNodeWithTag("eq_slider_$i").assertIsDisplayed()
            val b = composeTestRule.onNodeWithTag("eq_slider_$i").getUnclippedBoundsInRoot()
            assertTrue("Band $i touch width ${b.right - b.left} < 48dp", (b.right - b.left) >= 47.9.dp)
        }

        // Preset and Reset buttons wrap to content instead of stretching full width
        val sheetWidth = composeTestRule.onNodeWithTag("equalizer_sheet").getUnclippedBoundsInRoot().let { it.right - it.left }
        val resetBounds = composeTestRule.onNodeWithTag("eq_reset_button").getUnclippedBoundsInRoot()
        val presetBounds = composeTestRule.onNodeWithTag("eq_preset_2").getUnclippedBoundsInRoot()
        assertTrue((resetBounds.right - resetBounds.left) < sheetWidth * 0.6f)
        assertTrue((presetBounds.right - presetBounds.left) < sheetWidth * 0.65f)

        // Drag slider 0 up -> increases dB without dismissing sheet
        composeTestRule.onNodeWithTag("eq_slider_0").performTouchInput {
            swipeUp(startY = centerY + 35f, endY = centerY - 55f)
        }
        composeTestRule.waitForIdle()
        assertTrue("Expected positive dB after swipeUp, got ${latestState.bandLevelsDb}", latestState.bandLevelsDb[0] > 0)
        assertTrue("Sheet should not dismiss while dragging slider", !dismissed)
        composeTestRule.onNodeWithTag("equalizer_sheet").assertIsDisplayed()

        // Select Warm Vinyl preset (index 1 -> [4, 2, 0, -1, 2])
        composeTestRule.onNodeWithTag("eq_preset_1").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf(4, 2, 0, -1, 2), latestState.bandLevelsDb)
        composeTestRule.onNodeWithText("+4").assertIsDisplayed()
        composeTestRule.onNodeWithText("-1").assertIsDisplayed()

        // Reset button restores [0, 0, 0, 0, 0]
        composeTestRule.onNodeWithTag("eq_reset_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf(0, 0, 0, 0, 0), latestState.bandLevelsDb)
    }

    @Test
    fun width320_font10_en_usesTwoBalancedRowsAndWrapsButtons() {
        var latestState = EqualizerState()
        var dismissed = false
        launchEqualizerSheet(
            widthQualifiers = "w320dp-h560dp-mdpi",
            fontScale = 1.0f,
            language = AppLanguage.EN,
            onStateObserved = { latestState = it },
            onDismissCalled = { dismissed = true },
        )

        composeTestRule.onNodeWithTag("equalizer_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("eq_sliders_row_0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("eq_sliders_row_1").assertIsDisplayed()

        for (i in 0..4) {
            composeTestRule.onNodeWithTag("eq_slider_$i").assertIsDisplayed()
            val b = composeTestRule.onNodeWithTag("eq_slider_$i").getUnclippedBoundsInRoot()
            assertTrue("Band $i width < 48dp", (b.right - b.left) >= 47.9.dp)
        }

        // Drag slider 4 downward -> negative dB without dismissing sheet
        composeTestRule.onNodeWithTag("eq_slider_4").performTouchInput {
            swipeDown(startY = centerY - 25f, endY = centerY + 55f)
        }
        composeTestRule.waitForIdle()
        assertTrue("Expected negative dB after swipeDown, got ${latestState.bandLevelsDb}", latestState.bandLevelsDb[4] < 0)
        assertTrue(!dismissed)

        // Reset chip wraps cleanly and works on 320dp
        composeTestRule.onNodeWithTag("eq_reset_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf(0, 0, 0, 0, 0), latestState.bandLevelsDb)
    }

    @Test
    fun width360_font15_faRtl_usesTwoRowsShowsSignsProperlyAndNoClippedButtons() {
        var latestState = EqualizerState()
        var dismissed = false
        launchEqualizerSheet(
            widthQualifiers = "w360dp-h640dp-mdpi",
            fontScale = 1.5f,
            language = AppLanguage.FA,
            initialState = EqualizerState(
                enabled = true,
                hardwareAttached = true,
                presetIndex = 1,
                bandLevelsDb = listOf(4, 2, 0, -1, 2),
            ),
            onStateObserved = { latestState = it },
            onDismissCalled = { dismissed = true },
        )

        composeTestRule.onNodeWithTag("equalizer_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithText("اکولایزر").assertIsDisplayed()

        // At 1.5x font scale on 360dp, sliders split into 2 balanced rows
        composeTestRule.onNodeWithTag("eq_sliders_row_0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("eq_sliders_row_1").assertIsDisplayed()

        // Explicit + and - signs preserved
        composeTestRule.onNodeWithText("+4").assertIsDisplayed()
        composeTestRule.onNodeWithText("-1").assertIsDisplayed()

        // All 5 frequency labels readable
        listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz").forEach { freq ->
            composeTestRule.onNodeWithText(freq).assertIsDisplayed()
        }

        // Preset buttons wrap rather than stretch full width
        val sheetWidth = composeTestRule.onNodeWithTag("equalizer_sheet").getUnclippedBoundsInRoot().let { it.right - it.left }
        val resetBounds = composeTestRule.onNodeWithTag("eq_reset_button").performScrollTo().getUnclippedBoundsInRoot()
        assertTrue((resetBounds.right - resetBounds.left) < sheetWidth * 0.65f)

        // Dragging band 1 in RTL + 1.5x font scale updates band without dismissing sheet
        composeTestRule.onNodeWithTag("eq_slider_1").performScrollTo().performTouchInput {
            swipeUp(startY = centerY + 25f, endY = centerY - 50f)
        }
        composeTestRule.waitForIdle()
        assertTrue("Expected band 1 > 2 after swipeUp, got ${latestState.bandLevelsDb}", latestState.bandLevelsDb[1] > 2)
        assertTrue(!dismissed)

        // Reset works in FA RTL
        composeTestRule.onNodeWithTag("eq_reset_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf(0, 0, 0, 0, 0), latestState.bandLevelsDb)
    }

    @Test
    fun width320_font15_faRtl_shortScreen_scrollsAndSelectsPreset() {
        var latestState = EqualizerState()
        launchEqualizerSheet(
            widthQualifiers = "w320dp-h480dp-mdpi",
            fontScale = 1.5f,
            language = AppLanguage.FA,
            onStateObserved = { latestState = it },
        )

        composeTestRule.onNodeWithTag("equalizer_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("eq_sliders_row_0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("eq_sliders_row_1").performScrollTo().assertIsDisplayed()

        // Scroll to preset 2 ("باس عمیق") and click
        composeTestRule.onNodeWithTag("eq_preset_2").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf(7, 4, 0, 1, 2), latestState.bandLevelsDb)

        // Scroll down to scope and telemetry meters on short screen
        composeTestRule.onNodeWithTag("eq_response_scope").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("eq_telemetry_row").performScrollTo().assertIsDisplayed()
    }
}
