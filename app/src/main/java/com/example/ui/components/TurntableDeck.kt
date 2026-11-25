package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.R
import com.example.data.GraphicsQuality
import com.example.ui.theme.LocalPlinthColors
import kotlin.math.roundToInt

/**
 * Pre-Rendered 3D Layered Turntable Deck.
 * Loads Three.js PBR WebP layers + normalized JSON layout/pivot manifest from Android assets
 * and drives vinyl rotation, multi-angle tonearm groove tracking, synchronized cast shadows,
 * and tactile knob press states purely on GPU-accelerated Compose layers (zero runtime Three.js/WebGL).
 */
@Composable
fun TurntableDeck(
    artworkUri: String?,
    isPlaying: Boolean,
    progressFraction: Float,
    volume: Float,
    trackChangeNonce: Int,
    trackChangeDirection: Int,
    graphicsQuality: GraphicsQuality,
    batterySaverMode: Boolean,
    reducedMotion: Boolean,
    onTogglePlayPause: () -> Unit,
    onScrubDeltaMs: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPlinthColors.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val cdTurntableText = stringResource(R.string.cd_turntable)

    var discAngleDeg by remember { mutableFloatStateOf(0f) }
    var isScrubbingVinyl by remember { mutableStateOf(false) }
    var swapSweepBoost by remember { mutableFloatStateOf(0f) }
    var lastHandledNonce by remember { mutableIntStateOf(trackChangeNonce) }

    // Subtle tonearm lift cue when skipping tracks
    LaunchedEffect(trackChangeNonce) {
        if (trackChangeNonce != lastHandledNonce) {
            lastHandledNonce = trackChangeNonce
            swapSweepBoost = 1f
            kotlinx.coroutines.delay(240L)
            swapSweepBoost = 0f
        }
    }

    // 33⅓ RPM = 200 degrees/second. Pauses automatically when lifecycle leaves RESUMED while audio continues.
    LaunchedEffect(isPlaying, isScrubbingVinyl, batterySaverMode, reducedMotion, lifecycleOwner) {
        if (!isPlaying || isScrubbingVinyl) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var lastNs = 0L
            while (true) {
                withFrameNanos { now ->
                    if (lastNs != 0L) {
                        val dt = ((now - lastNs) / 1_000_000_000f).coerceIn(0f, 0.05f)
                        val degPerSec = when {
                            reducedMotion -> 36f
                            batterySaverMode -> 140f
                            else -> 200f
                        }
                        discAngleDeg = (discAngleDeg + degPerSec * dt) % 360f
                    }
                    lastNs = now
                }
            }
        }
    }

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .testTag("turntable_deck")
            .semantics { contentDescription = cdTurntableText },
    ) {
        val containerSizePx = with(density) {
            minOf(maxWidth, maxHeight).toPx().roundToInt().coerceAtLeast(256)
        }
        val targetResolution = remember(graphicsQuality, batterySaverMode, containerSizePx) {
            TurntableKinematics.selectResolutionTier(
                graphicsQuality = graphicsQuality,
                batterySaverMode = batterySaverMode,
                containerSizePx = containerSizePx,
            )
        }

        val initialCached = remember(palette.isDark, targetResolution) {
            TurntableAssetRepository.getCachedAssets(
                assets = context.assets,
                isDark = palette.isDark,
                requestedResolution = targetResolution,
            )
        }

        var loadFinished by remember(palette.isDark, targetResolution) {
            mutableStateOf(initialCached != null)
        }

        val preRenderedAssets by produceState(
            initialValue = initialCached,
            key1 = palette.isDark,
            key2 = targetResolution,
        ) {
            if (value == null) {
                value = TurntableAssetRepository.loadAssets(
                    context = context,
                    isDark = palette.isDark,
                    requestedResolution = targetResolution,
                )
            }
            loadFinished = true
        }

        val targetArmAngleDeg = remember(preRenderedAssets, isPlaying, isScrubbingVinyl, progressFraction, swapSweepBoost) {
            val geom = preRenderedAssets?.manifest?.geometry
            if (geom != null) {
                when {
                    swapSweepBoost > 0.5f -> geom.tonearmRestAngleDeg + 1.5f
                    isPlaying || isScrubbingVinyl -> TurntableKinematics.computeGrooveAngleDeg(geom, progressFraction)
                    progressFraction <= 0.004f || progressFraction >= 0.996f -> geom.tonearmRestAngleDeg
                    else -> TurntableKinematics.computeGrooveAngleDeg(geom, progressFraction)
                }
            } else {
                when {
                    swapSweepBoost > 0.5f -> -12.0f
                    isPlaying -> -19.0f - progressFraction.coerceIn(0f, 1f) * 20.8f
                    else -> -13.5f
                }
            }
        }

        val armSweepDeg by animateFloatAsState(
            targetValue = targetArmAngleDeg,
            animationSpec = if (reducedMotion) {
                tween(durationMillis = 160)
            } else {
                spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)
            },
            label = "tonearmSweep",
        )

        val isTonearmLifted = swapSweepBoost > 0.5f ||
            (!isPlaying && !isScrubbingVinyl && progressFraction > 0.004f && progressFraction < 0.996f)

        val loaded = preRenderedAssets
        if (loaded != null) {
            PreRenderedTurntableCompositor(
                assets = loaded,
                artworkUri = artworkUri,
                isPlaying = isPlaying,
                isTonearmLifted = isTonearmLifted,
                discAngleDeg = discAngleDeg,
                armSweepDeg = armSweepDeg,
                volume = volume,
                onTogglePlayPause = onTogglePlayPause,
                onScrubStart = { isScrubbingVinyl = true },
                onScrubDeltaDeg = { delta -> discAngleDeg = (discAngleDeg + delta) % 360f },
                onScrubDeltaMs = onScrubDeltaMs,
                onScrubEnd = { isScrubbingVinyl = false },
                onVolumeChange = onVolumeChange,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (loadFinished) {
            TurntableFallbackCanvasDeck(
                isPlaying = isPlaying,
                discAngleDeg = discAngleDeg,
                armSweepDeg = armSweepDeg,
                graphicsQuality = graphicsQuality,
                batterySaverMode = batterySaverMode,
                onTogglePlayPause = onTogglePlayPause,
                onScrubStart = { isScrubbingVinyl = true },
                onScrubDeltaDeg = { delta -> discAngleDeg = (discAngleDeg + delta) % 360f },
                onScrubDeltaMs = onScrubDeltaMs,
                onScrubEnd = { isScrubbingVinyl = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
