package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SpectrumSnapshot
import com.example.ui.theme.DigitalMonoFontFamily
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.LocalPlinthColors
import com.example.viewmodel.formatDuration

/**
 * Architectural Acoustic Spectrum Deck + Floating Peak-Hold Beads + Digital Time + Scrub Track.
 * Driven by real PCM FFT Bass/Mid/Treble energy from AudioSpectrumProcessor.
 * Tapping the spectrum instrument opens the 5-Band Hardware Equalizer directly.
 */
@Composable
fun LiveAudioVisualizerSection(
    spectrum: SpectrumSnapshot,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
    onOpenEqualizer: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val palette = LocalPlinthColors.current
    val strings = LocalAppStrings.current
    val safeDuration = durationMs.coerceAtLeast(1L)
    val progress = (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableFloatStateOf(progress) }
    val displayProgress = if (isScrubbing) scrubProgress else progress
    val displayPositionMs = if (isScrubbing) (scrubProgress * safeDuration).toLong() else positionMs

    val activeEnvelope by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 420),
        label = "visualizerEnvelope",
    )

    // Interpolate 13 FFT channels into 25 symmetrical hi-fi telemetry columns with peak-hold physics
    val columnCount = 25
    val peaks = remember { mutableStateListOf<Float>().apply { repeat(columnCount) { add(0.12f) } } }

    val targetLevels = remember(spectrum, activeEnvelope) {
        val raw = spectrum.bars
        List(columnCount) { col ->
            val srcPos = (col.toFloat() / (columnCount - 1)) * (raw.size - 1).coerceAtLeast(1)
            val lo = srcPos.toInt().coerceIn(0, raw.lastIndex)
            val hi = (lo + 1).coerceIn(0, raw.lastIndex)
            val frac = srcPos - lo
            val interpolated = raw[lo] + (raw[hi] - raw[lo]) * frac

            val centerIdx = (columnCount - 1) / 2f
            val distNorm = kotlin.math.abs(col - centerIdx) / centerIdx
            val idleProfile = (0.22f - distNorm * 0.13f).coerceAtLeast(0.08f)
            val activeProfile = (0.12f + interpolated * (1.0f - distNorm * 0.22f)).coerceIn(0.08f, 1f)
            idleProfile + (activeProfile - idleProfile) * activeEnvelope
        }
    }

    LaunchedEffect(targetLevels) {
        for (i in 0 until columnCount) {
            val lvl = targetLevels[i]
            peaks[i] = if (lvl >= peaks[i]) {
                lvl
            } else {
                (peaks[i] - 0.022f).coerceAtLeast(lvl)
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
    ) {
        // Recessed Acoustic Instrument Bay (tappable to launch 5-band Equalizer)
        val bayShape = RoundedCornerShape(22.dp)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clip(bayShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            palette.plinthBottom.copy(alpha = if (palette.isDark) 0.55f else 0.72f),
                            palette.capsuleBg.copy(alpha = if (palette.isDark) 0.35f else 0.88f),
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            palette.plinthEdgeShadow.copy(alpha = 0.55f),
                            palette.plinthEdgeLight.copy(alpha = 0.85f),
                        )
                    ),
                    shape = bayShape,
                )
                .clickable { onOpenEqualizer() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            // Subtle Instrument Header: Frequency Telemetry + EQ Trigger
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(if (isPlaying) palette.warmBrass else palette.inkMuted),
                    )
                    Text(
                        text = "${strings.bass} ${(spectrum.bass * 100).toInt()}%  •  ${strings.treble} ${(spectrum.treble * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = DigitalMonoFontFamily,
                            fontSize = 9.5.sp,
                            letterSpacing = 0.6.sp,
                        ),
                        color = palette.inkSecondary,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = strings.equalizer,
                        tint = palette.inkSecondary,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = "EQ",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = DigitalMonoFontFamily,
                            fontSize = 9.5.sp,
                            letterSpacing = 0.8.sp,
                        ),
                        color = palette.inkSecondary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 25-Channel Symmetrical Precision Spectrum Waveform + Floating Peak Beads
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .testTag("live_audio_visualizer"),
            ) {
                val barW = 2.6.dp.toPx()
                val gap = (size.width - columnCount * barW) / (columnCount - 1).coerceAtLeast(1)
                val midY = size.height * 0.52f
                val maxHalfH = size.height * 0.44f

                // 0 dB center datum hairline & micro scale ticks
                drawLine(
                    color = palette.hairline.copy(alpha = 0.65f),
                    start = Offset(0f, midY),
                    end = Offset(size.width, midY),
                    strokeWidth = 0.8.dp.toPx(),
                )

                for (i in 0 until columnCount) {
                    val lvl = targetLevels[i]
                    val peak = peaks[i]
                    val x = i * (barW + gap)
                    val halfH = (maxHalfH * lvl).coerceAtLeast(2.2.dp.toPx())

                    val barBrush = if (lvl > 0.62f && isPlaying) {
                        Brush.verticalGradient(
                            listOf(palette.warmBrass, palette.visualizerActive, palette.warmBrass)
                        )
                    } else if (lvl > 0.35f && isPlaying) {
                        Brush.verticalGradient(
                            listOf(palette.visualizerActive, palette.visualizerBar, palette.visualizerActive)
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(palette.visualizerBar, palette.visualizerBar.copy(alpha = 0.6f))
                        )
                    }

                    // Mirrored harmonic bar
                    drawRoundRect(
                        brush = barBrush,
                        topLeft = Offset(x, midY - halfH),
                        size = Size(barW, halfH * 2f),
                        cornerRadius = CornerRadius(barW / 2f, barW / 2f),
                    )

                    // Floating peak-hold metallic bead above each harmonic spike
                    if (isPlaying && peak > lvl + 0.04f) {
                        val peakY = (midY - maxHalfH * peak - 2.5.dp.toPx()).coerceAtLeast(1.5.dp.toPx())
                        drawCircle(
                            color = if (peak > 0.72f) palette.warmBrass else palette.inkPrimary.copy(alpha = 0.65f),
                            radius = 1.3.dp.toPx(),
                            center = Offset(x + barW / 2f, peakY),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Digital Time Readout + Interactive Precision Rail Scrub Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = formatDuration(displayPositionMs),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = DigitalMonoFontFamily,
                        fontSize = 11.5.sp,
                        letterSpacing = 0.7.sp,
                    ),
                    color = palette.inkPrimary,
                    modifier = Modifier.testTag("digital_time_readout"),
                )

                Spacer(modifier = Modifier.width(10.dp))

                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp)
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

                    // Engraved micro-scale ticks along rail
                    for (t in 0..10) {
                        val tx = (size.width / 10f) * t
                        drawLine(
                            color = palette.hairline.copy(alpha = 0.7f),
                            start = Offset(tx, cy - 5.dp.toPx()),
                            end = Offset(tx, cy - 3.dp.toPx()),
                            strokeWidth = 0.8.dp.toPx(),
                        )
                    }

                    drawRoundRect(
                        color = palette.hairline,
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
                    val thumbR = if (isScrubbing) 6.5.dp.toPx() else 4.5.dp.toPx()
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
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.6.dp.toPx()),
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = formatDuration(durationMs),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = DigitalMonoFontFamily,
                        fontSize = 11.5.sp,
                        letterSpacing = 0.7.sp,
                    ),
                    color = palette.inkSecondary,
                )
            }
        }
    }
}
