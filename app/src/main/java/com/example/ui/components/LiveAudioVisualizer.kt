package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.FeedbackEvent
import com.example.audio.PlinthFeedback
import com.example.audio.SpectrumSnapshot
import com.example.ui.theme.DigitalMonoFontFamily
import com.example.ui.theme.LocalPlinthColors
import com.example.viewmodel.formatDuration
import kotlinx.coroutines.flow.StateFlow

/**
 * Reference-faithful floating Acoustic Waveform Visualizer + Digital Time Readout ("0:32 / 2:49")
 * + Slim Precision Scrub Rail inside the lower convex dome.
 * Collects 45Hz PCM FFT spectrum locally so NowPlayingScreen & TurntableDeck never recompose.
 */
@Composable
fun LiveAudioVisualizerSection(
    spectrumFlow: StateFlow<SpectrumSnapshot>,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
    onOpenEqualizer: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val palette = LocalPlinthColors.current
    val spectrum by spectrumFlow.collectAsStateWithLifecycle()
    val safeDuration = durationMs.coerceAtLeast(1L)
    val progress = (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableFloatStateOf(progress) }
    val displayProgress = if (isScrubbing) scrubProgress else progress
    val displayPositionMs = if (isScrubbing) (scrubProgress * safeDuration).toLong() else positionMs

    val activeEnvelope by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 380),
        label = "visualizerEnvelope",
    )

    // 13 clean symmetrical harmonic bars floating gently inside the convex dome (matches reference silhouette)
    val columnCount = 13
    val peaks = remember { mutableStateListOf<Float>().apply { repeat(columnCount) { add(0.18f) } } }

    val targetLevels = remember(spectrum, activeEnvelope) {
        val raw = spectrum.bars
        val centerIdx = (columnCount - 1) / 2f
        List(columnCount) { col ->
            val srcIdx = col.coerceIn(0, raw.lastIndex)
            val liveEnergy = raw[srcIdx]
            val distNorm = kotlin.math.abs(col - centerIdx) / centerIdx
            // Clean diamond-like resting silhouette matching the reference waveform
            val idleProfile = (0.58f - distNorm * 0.42f).coerceAtLeast(0.14f)
            val activeProfile = (0.16f + liveEnergy * (1.05f - distNorm * 0.28f)).coerceIn(0.12f, 1f)
            idleProfile + (activeProfile - idleProfile) * activeEnvelope
        }
    }

    LaunchedEffect(targetLevels) {
        for (i in 0 until columnCount) {
            val lvl = targetLevels[i]
            peaks[i] = if (lvl >= peaks[i]) {
                lvl
            } else {
                (peaks[i] - 0.025f).coerceAtLeast(lvl)
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp),
    ) {
        // Floating Waveform Bars (tappable to open the 5-band Equalizer sheet)
        Canvas(
            modifier = Modifier
                .width(124.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    PlinthFeedback.trigger(FeedbackEvent.TAP_SUBTLE)
                    onOpenEqualizer()
                }
                .testTag("live_audio_visualizer"),
        ) {
            val barW = 2.8.dp.toPx()
            val gap = (size.width - columnCount * barW) / (columnCount - 1).coerceAtLeast(1)
            val midY = size.height * 0.5f
            val maxHalfH = size.height * 0.46f

            for (i in 0 until columnCount) {
                val lvl = targetLevels[i]
                val x = i * (barW + gap)
                val halfH = (maxHalfH * lvl).coerceAtLeast(2.4.dp.toPx())
                val barColor = when {
                    lvl > 0.72f && isPlaying -> palette.warmBrass
                    lvl > 0.42f && isPlaying -> palette.visualizerActive
                    else -> palette.visualizerBar
                }
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, midY - halfH),
                    size = Size(barW, halfH * 2f),
                    cornerRadius = CornerRadius(barW / 2f, barW / 2f),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Crisp Digital Time Readout: "0:32 / 2:49" (matches reference placement & digital style)
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.testTag("digital_time_readout"),
        ) {
            Text(
                text = formatDuration(displayPositionMs),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = DigitalMonoFontFamily,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp,
                ),
                color = palette.inkPrimary,
            )
            Text(
                text = " / ",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = DigitalMonoFontFamily,
                    fontSize = 13.5.sp,
                ),
                color = palette.inkSecondary,
            )
            Text(
                text = formatDuration(durationMs),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = DigitalMonoFontFamily,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.8.sp,
                ),
                color = palette.inkPrimary.copy(alpha = 0.78f),
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Ultra-slim Interactive Scrub Rail tucked discreetly underneath the digital time
        Canvas(
            modifier = Modifier
                .fillMaxWidth(0.76f)
                .height(20.dp)
                .testTag("playback_scrub_bar")
                .pointerInput(safeDuration) {
                    detectTapGestures { offset ->
                        val frac = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                        PlinthFeedback.trigger(FeedbackEvent.TAP_SUBTLE)
                        onSeekTo((frac * safeDuration).toLong())
                    }
                }
                .pointerInput(safeDuration) {
                    detectHorizontalDragGestures(
                        onDragStart = { start ->
                            isScrubbing = true
                            scrubProgress = (start.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            scrubProgress = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            PlinthFeedback.trigger(FeedbackEvent.TAP_SUBTLE)
                            onSeekTo((scrubProgress * safeDuration).toLong())
                            isScrubbing = false
                        },
                        onDragCancel = { isScrubbing = false },
                    )
                },
        ) {
            val trackH = 2.4.dp.toPx()
            val cy = size.height / 2f
            val fillW = size.width * displayProgress

            drawRoundRect(
                color = palette.hairline.copy(alpha = 0.85f),
                topLeft = Offset(0f, cy - trackH / 2f),
                size = Size(size.width, trackH),
                cornerRadius = CornerRadius(trackH / 2f, trackH / 2f),
            )
            if (fillW > 1f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        listOf(palette.visualizerBar, palette.inkPrimary)
                    ),
                    topLeft = Offset(0f, cy - trackH / 2f),
                    size = Size(fillW, trackH),
                    cornerRadius = CornerRadius(trackH / 2f, trackH / 2f),
                )
            }
            val thumbR = if (isScrubbing) 5.5.dp.toPx() else 3.6.dp.toPx()
            val thumbX = fillW.coerceIn(thumbR, size.width - thumbR)
            drawCircle(
                color = palette.capsuleBg,
                radius = thumbR,
                center = Offset(thumbX, cy),
            )
            drawCircle(
                color = palette.inkPrimary,
                radius = thumbR,
                center = Offset(thumbX, cy),
                style = Stroke(width = 1.4.dp.toPx()),
            )
        }
    }
}
