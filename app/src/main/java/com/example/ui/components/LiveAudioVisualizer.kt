package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SpectrumSnapshot
import com.example.ui.theme.DigitalMonoFontFamily
import com.example.ui.theme.LocalPlinthColors
import com.example.viewmodel.formatDuration

/**
 * Minimal architectural live FFT Audio Visualizer + Digital Time Display + Interactive Hairline Scrub Bar.
 * Driven exclusively by real PCM FFT Bass/Mid/Treble energy from AudioSpectrumProcessor.
 */
@Composable
fun LiveAudioVisualizerSection(
    spectrum: SpectrumSnapshot,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPlinthColors.current
    val safeDuration = durationMs.coerceAtLeast(1L)
    val progress = (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableFloatStateOf(progress) }
    val displayProgress = if (isScrubbing) scrubProgress else progress
    val displayPositionMs = if (isScrubbing) (scrubProgress * safeDuration).toLong() else positionMs

    // Smooth decay envelope so bars settle gently to minimal quiet ticks when paused
    val activeEnvelope by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 420),
        label = "visualizerEnvelope",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth(),
    ) {
        // Minimal 13-bar silver-slate frequency visualizer (exact silhouette of reference deck!)
        val barValues = spectrum.bars
        Canvas(
            modifier = Modifier
                .fillMaxWidth(0.56f)
                .height(34.dp)
                .testTag("live_audio_visualizer"),
        ) {
            val count = barValues.size.coerceAtLeast(1)
            val barWidth = 2.6.dp.toPx()
            val gap = (size.width - count * barWidth) / (count - 1).coerceAtLeast(1)
            val centerY = size.height / 2f
            val maxHalfH = size.height * 0.48f

            // Subtle architectural taper envelope so outer treble bars stay airy and sculpted
            val centerIdx = (count - 1) / 2f
            for (i in 0 until count) {
                val distNorm = kotlin.math.abs(i - centerIdx) / centerIdx.coerceAtLeast(1f)
                val idleRatio = (0.28f - distNorm * 0.16f).coerceAtLeast(0.10f)
                val liveEnergy = barValues[i].coerceIn(0f, 1f)
                val weightedLive = (0.14f + liveEnergy * (1.0f - distNorm * 0.25f)).coerceIn(0.10f, 1f)
                val finalRatio = idleRatio + (weightedLive - idleRatio) * activeEnvelope

                val halfH = (maxHalfH * finalRatio).coerceAtLeast(2.5.dp.toPx())
                val x = i * (barWidth + gap)
                val barColor = if (liveEnergy > 0.45f && isPlaying) {
                    palette.visualizerActive
                } else {
                    palette.visualizerBar
                }

                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, centerY - halfH),
                    size = Size(barWidth, halfH * 2f),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Crisp Digital Time Readout ("0:32 / 2:49")
        Text(
            text = "${formatDuration(displayPositionMs)} / ${formatDuration(durationMs)}",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = DigitalMonoFontFamily,
                fontSize = 13.5.sp,
                letterSpacing = 1.1.sp,
            ),
            color = palette.inkPrimary,
            modifier = Modifier.testTag("digital_time_readout"),
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Sleek interactive hairline scrub progress track (generous 28dp touch target)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp)
                .height(28.dp)
                .testTag("playback_scrub_bar")
                .pointerInput(safeDuration) {
                    detectTapGestures { offset ->
                        val frac = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
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
                            onSeekTo((scrubProgress * safeDuration).toLong())
                            isScrubbing = false
                        },
                        onDragCancel = { isScrubbing = false },
                    )
                },
        ) {
            val trackH = 3.dp.toPx()
            val cy = size.height / 2f
            val fillW = size.width * displayProgress

            // Recessed groove track
            drawRoundRect(
                color = palette.hairline,
                topLeft = Offset(0f, cy - trackH / 2f),
                size = Size(size.width, trackH),
                cornerRadius = CornerRadius(trackH / 2f, trackH / 2f),
            )
            // Active brushed metallic fill
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
            // Precision metallic thumb bead
            val thumbR = if (isScrubbing) 6.5.dp.toPx() else 4.5.dp.toPx()
            drawCircle(
                color = palette.capsuleBg,
                radius = thumbR,
                center = Offset(fillW.coerceIn(thumbR, size.width - thumbR), cy),
            )
            drawCircle(
                color = palette.inkPrimary,
                radius = thumbR,
                center = Offset(fillW.coerceIn(thumbR, size.width - thumbR), cy),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.6.dp.toPx()),
            )
        }
    }
}
