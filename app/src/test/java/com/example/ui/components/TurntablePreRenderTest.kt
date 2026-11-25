package com.example.ui.components

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.GraphicsQuality
import com.example.ui.theme.PlinthTheme
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TurntablePreRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun manifestLoadsFromAssetsAndContainsNormalizedGeometry() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        TurntableAssetRepository.clearCacheForTesting()
        val manifest = TurntableAssetRepository.loadManifestSync(context.assets)
        assertNotNull("turntable-layout.json should exist in assets/turntable/", manifest)
        manifest!!

        assertEquals(4096, manifest.referenceSizePx)
        assertEquals(listOf(512, 1024, 2048), manifest.resolutions)
        assertTrue(manifest.themes.containsAll(listOf("light", "dark")))
        assertTrue(manifest.geometry.vinylCenter.x in 0.35f..0.50f)
        assertTrue(manifest.geometry.tonearmPivot.x in 0.72f..0.90f)
        assertTrue(manifest.geometry.playableOuterRadius > manifest.geometry.playableInnerRadius)
        assertTrue(manifest.geometry.tonearmKeyframeAnglesDeg.size >= 8)
    }

    @Test
    fun tonearmKinematicsTracksOuterToInnerGroovesMonotonically() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manifest = TurntableAssetRepository.loadManifestSync(context.assets)!!
        val geom = manifest.geometry

        val outerDeg = TurntableKinematics.computeGrooveAngleDeg(geom, 0f)
        val midDeg = TurntableKinematics.computeGrooveAngleDeg(geom, 0.5f)
        val innerDeg = TurntableKinematics.computeGrooveAngleDeg(geom, 1f)

        assertTrue(abs(outerDeg - geom.tonearmOuterGrooveAngleDeg) < 0.25f)
        assertTrue(abs(innerDeg - geom.tonearmInnerGrooveAngleDeg) < 0.25f)
        // More negative yaw swings tonearm inward toward spindle
        assertTrue("midDeg ($midDeg) should be between outer ($outerDeg) and inner ($innerDeg)", midDeg < outerDeg && innerDeg < midDeg)

        val selection = TurntableKinematics.selectTonearmKeyframe(
            targetAngleDeg = midDeg,
            keyframeAnglesDeg = geom.tonearmKeyframeAnglesDeg,
        )
        assertTrue(selection.frameIndex in geom.tonearmKeyframeAnglesDeg.indices)
        assertTrue(abs(selection.residualRotationZDeg) <= 2.5f)
    }

    @Test
    fun resolutionTierSelectorHonorsQualityAndBatterySaver() {
        assertEquals(
            512,
            TurntableKinematics.selectResolutionTier(
                graphicsQuality = GraphicsQuality.HIGH,
                batterySaverMode = true,
                containerSizePx = 1440,
            ),
        )
        assertEquals(
            512,
            TurntableKinematics.selectResolutionTier(
                graphicsQuality = GraphicsQuality.LOW,
                batterySaverMode = false,
                containerSizePx = 1440,
            ),
        )
        assertEquals(
            1024,
            TurntableKinematics.selectResolutionTier(
                graphicsQuality = GraphicsQuality.MEDIUM,
                batterySaverMode = false,
                containerSizePx = 1440,
            ),
        )
        assertEquals(
            2048,
            TurntableKinematics.selectResolutionTier(
                graphicsQuality = GraphicsQuality.HIGH,
                batterySaverMode = false,
                containerSizePx = 1200,
            ),
        )
    }

    @Test
    fun preRenderedAssetsDecodeAndRenderLayeredCompositorInLightAndDark() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        TurntableAssetRepository.clearCacheForTesting()

        val lightAssets = runBlocking {
            TurntableAssetRepository.loadAssets(context = context, isDark = false, requestedResolution = 512)
        }
        val darkAssets = runBlocking {
            TurntableAssetRepository.loadAssets(context = context, isDark = true, requestedResolution = 512)
        }

        assertNotNull("Light pre-rendered WebP assets must decode cleanly", lightAssets)
        assertNotNull("Dark pre-rendered WebP assets must decode cleanly", darkAssets)
        assertEquals(lightAssets!!.manifest.geometry.tonearmKeyframeAnglesDeg.size, lightAssets.tonearmArmFrames.size)
        assertEquals(lightAssets.tonearmArmFrames.size, lightAssets.tonearmShadowFrames.size)

        var toggled = 0
        composeRule.setContent {
            PlinthTheme(themeMode = ThemeMode.LIGHT) {
                TurntableDeck(
                    artworkUri = null,
                    isPlaying = false,
                    progressFraction = 0.25f,
                    volume = 0.8f,
                    trackChangeNonce = 0,
                    trackChangeDirection = 1,
                    graphicsQuality = GraphicsQuality.LOW,
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
        composeRule.onNodeWithTag("turntable_prerendered_layer_stack").assertIsDisplayed()
        composeRule.onNodeWithTag("turntable_right_knob").performClick()
        composeRule.waitForIdle()
        assertEquals(1, toggled)
    }
}
