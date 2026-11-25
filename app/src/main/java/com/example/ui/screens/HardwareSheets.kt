package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.EqualizerState
import com.example.audio.SpectrumSnapshot
import com.example.data.PlaylistSummary
import com.example.data.Track
import com.example.data.UserPreferences
import com.example.ui.components.TactileCapsuleButton
import com.example.ui.i18n.AppLanguage
import com.example.ui.theme.DigitalMonoFontFamily
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.LocalPlinthColors
import com.example.ui.theme.ThemeMode
import com.example.viewmodel.formatDuration
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(
    queue: List<Track>,
    currentTrackId: String?,
    onSelectTrack: (Track) -> Unit,
    onRemoveIndex: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalPlinthColors.current
    val strings = LocalAppStrings.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.canvasTop,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .testTag("queue_sheet"),
        ) {
            Text(
                text = strings.queue,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = palette.inkPrimary,
                modifier = Modifier.padding(bottom = 14.dp),
            )
            if (queue.isEmpty()) {
                Text(
                    text = strings.emptyQueue,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.inkSecondary,
                    modifier = Modifier.padding(vertical = 32.dp),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.heightIn(max = 420.dp),
                ) {
                    itemsIndexed(queue, key = { idx, t -> "${t.id}_$idx" }) { index, track ->
                        CompactVinylTrackRow(
                            track = track,
                            isActive = track.id == currentTrackId,
                            onClick = { onSelectTrack(track) },
                            trailing = {
                                if (queue.size > 1) {
                                    IconButton(
                                        onClick = { onRemoveIndex(index) },
                                        modifier = Modifier.size(44.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = strings.removeFromQueue,
                                            tint = palette.inkSecondary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EqualizerBottomSheet(
    eqState: EqualizerState,
    spectrum: SpectrumSnapshot,
    hapticsEnabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onSelectPreset: (Int) -> Unit,
    onBandChange: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalPlinthColors.current
    val strings = LocalAppStrings.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val presetNames = listOf(
        strings.presetFlat,
        strings.presetWarmVinyl,
        strings.presetBassBoost,
        strings.presetVocal,
        strings.presetAcoustic,
        strings.presetElectronic,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.canvasTop,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 6.dp)
                .testTag("equalizer_sheet"),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.equalizer,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = palette.inkPrimary,
                    )
                    Text(
                        text = if (eqState.hardwareAttached) strings.eqEnabled else strings.eqHardwareFallback,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkSecondary,
                    )
                }
                Switch(
                    checked = eqState.enabled,
                    onCheckedChange = onToggleEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = palette.capsuleBg,
                        checkedTrackColor = palette.inkPrimary,
                    ),
                    modifier = Modifier.testTag("eq_enable_switch"),
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. Live Parametric Frequency-Response Curve & Acoustic Spectrum Scope
            EqualizerResponseScope(
                bandLevelsDb = eqState.bandLevelsDb,
                spectrum = spectrum,
                enabled = eqState.enabled,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Live Bass / Mid / Treble Segmented Telemetry Meters
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(palette.capsuleBg)
                    .border(1.dp, palette.hairline, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                BandMeter(strings.bass, spectrum.bass, Modifier.weight(1f))
                BandMeter(strings.mid, spectrum.mid, Modifier.weight(1f))
                BandMeter(strings.treble, spectrum.treble, Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Hardware Preset Capsules
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                presetNames.forEachIndexed { idx, label ->
                    val selected = eqState.enabled && eqState.presetIndex == idx
                    TactileCapsuleButton(
                        onClick = { onSelectPreset(idx) },
                        contentDescription = label,
                        cornerRadius = 16.dp,
                        hapticsEnabled = hapticsEnabled,
                        active = selected,
                        modifier = Modifier.height(36.dp),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            ),
                            color = if (selected) palette.inkPrimary else palette.inkSecondary,
                            modifier = Modifier.padding(horizontal = 14.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. 5 Tactile Vertical Studio Channel Faders (60Hz .. 14kHz)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(palette.plinthBottom.copy(alpha = 0.7f), palette.capsuleBg)
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(palette.plinthEdgeShadow.copy(alpha = 0.5f), palette.plinthEdgeLight)
                        ),
                        shape = RoundedCornerShape(22.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 14.dp),
            ) {
                eqState.bandLabels.forEachIndexed { idx, freqLabel ->
                    val level = eqState.bandLevelsDb.getOrElse(idx) { 0 }
                    HardwareVerticalFader(
                        freqLabel = freqLabel,
                        levelDb = level,
                        enabled = eqState.enabled,
                        hapticsEnabled = hapticsEnabled,
                        onLevelChange = { newDb -> onBandChange(idx, newDb) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

@Composable
private fun EqualizerResponseScope(
    bandLevelsDb: List<Int>,
    spectrum: SpectrumSnapshot,
    enabled: Boolean,
) {
    val palette = LocalPlinthColors.current
    val scopeShape = RoundedCornerShape(20.dp)
    val scopeBgTop = if (palette.isDark) Color(0xFF0E1015) else Color(0xFF1A1E26)
    val scopeBgBottom = if (palette.isDark) Color(0xFF151922) else Color(0xFF232833)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(116.dp)
            .clip(scopeShape)
            .background(Brush.verticalGradient(listOf(scopeBgTop, scopeBgBottom)))
            .border(1.dp, palette.plinthEdgeShadow.copy(alpha = 0.65f), scopeShape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val midY = h * 0.5f

            // Background faint reactive FFT spectrum silhouette
            val rawBars = spectrum.bars
            if (rawBars.isNotEmpty()) {
                val barW = 3.dp.toPx()
                val gap = (w - rawBars.size * barW) / (rawBars.size - 1).coerceAtLeast(1)
                rawBars.forEachIndexed { i, energy ->
                    val bx = i * (barW + gap)
                    val bh = (h * 0.72f * energy).coerceAtLeast(2.dp.toPx())
                    drawRoundRect(
                        color = palette.warmBrass.copy(alpha = if (enabled) 0.14f else 0.06f),
                        topLeft = Offset(bx, h - bh),
                        size = Size(barW, bh),
                        cornerRadius = CornerRadius(barW / 2f, barW / 2f),
                    )
                }
            }

            // Engraved horizontal dB gridlines (+12, +6, 0, -6, -12)
            val gridFracs = listOf(0.12f, 0.31f, 0.50f, 0.69f, 0.88f)
            gridFracs.forEachIndexed { idx, frac ->
                val gy = h * frac
                drawLine(
                    color = if (idx == 2) Color.White.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.08f),
                    start = Offset(0f, gy),
                    end = Offset(w, gy),
                    strokeWidth = if (idx == 2) 1.dp.toPx() else 0.7.dp.toPx(),
                )
            }

            // Compute 5 band control points
            val count = bandLevelsDb.size.coerceAtLeast(2)
            val padX = w * 0.06f
            val spanW = w - padX * 2f
            val spanH = h * 0.38f // ±12 dB excursion around midY
            val points = List(count) { i ->
                val db = if (enabled) bandLevelsDb[i].coerceIn(-12, 12) else 0
                val x = padX + (spanW * i / (count - 1).coerceAtLeast(1))
                val y = midY - (db / 12f) * spanH
                Offset(x, y)
            }

            // Vertical band axis hairlines
            points.forEach { pt ->
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = Offset(pt.x, 0f),
                    end = Offset(pt.x, h),
                    strokeWidth = 0.7.dp.toPx(),
                )
            }

            // Smooth cubic spline curve path
            val curvePath = Path().apply {
                moveTo(0f, points.first().y)
                lineTo(points.first().x, points.first().y)
                for (i in 0 until points.lastIndex) {
                    val p0 = points[i]
                    val p1 = points[i + 1]
                    val cx = (p0.x + p1.x) * 0.5f
                    cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                }
                lineTo(w, points.last().y)
            }

            val fillPath = Path().apply {
                addPath(curvePath)
                lineTo(w, midY)
                lineTo(0f, midY)
                close()
            }

            val accent = if (enabled) palette.warmBrass else Color(0xFF6B7282)
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.28f), accent.copy(alpha = 0.04f)),
                ),
            )
            drawPath(
                path = curvePath,
                color = accent,
                style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round),
            )

            // Machined brass node indicators on the curve
            points.forEach { pt ->
                drawCircle(
                    color = scopeBgTop,
                    radius = 5.dp.toPx(),
                    center = pt,
                )
                drawCircle(
                    color = accent,
                    radius = 3.2.dp.toPx(),
                    center = pt,
                )
            }
        }

        // Subtle +12 / 0 / -12 scale marks in upper-left corner
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxHeight(),
        ) {
            listOf("+12", "0dB", "-12").forEach { lbl ->
                Text(
                    text = lbl,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = DigitalMonoFontFamily,
                        fontSize = 8.5.sp,
                    ),
                    color = Color.White.copy(alpha = 0.45f),
                )
            }
        }
    }
}

@Composable
private fun HardwareVerticalFader(
    freqLabel: String,
    levelDb: Int,
    enabled: Boolean,
    hapticsEnabled: Boolean,
    onLevelChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPlinthColors.current
    val haptic = LocalHapticFeedback.current
    var trackHeightPx by remember { mutableIntStateOf(1) }

    val fractionFromTop = ((12f - levelDb.coerceIn(-12, 12)) / 24f).coerceIn(0f, 1f)

    fun mapYToDb(yPx: Float): Int {
        val h = trackHeightPx.coerceAtLeast(1).toFloat()
        val frac = (yPx / h).coerceIn(0f, 1f)
        return (12f - frac * 24f).roundToInt().coerceIn(-12, 12)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Text(
            text = if (levelDb > 0) "+${levelDb}" else "$levelDb",
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = DigitalMonoFontFamily,
                fontSize = 11.sp,
                fontWeight = if (levelDb != 0 && enabled) FontWeight.Bold else FontWeight.Medium,
            ),
            color = when {
                !enabled -> palette.inkMuted
                levelDb > 0 -> palette.warmBrass
                levelDb < 0 -> palette.inkPrimary
                else -> palette.inkSecondary
            },
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .width(48.dp)
                .height(142.dp)
                .onSizeChanged { trackHeightPx = it.height }
                .pointerInput(enabled, levelDb) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { offset ->
                        val nextDb = mapYToDb(offset.y)
                        if (nextDb != levelDb) {
                            if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onLevelChange(nextDb)
                        }
                    }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    var lastEmitted = levelDb
                    detectVerticalDragGestures(
                        onDragStart = { start ->
                            val nextDb = mapYToDb(start.y)
                            lastEmitted = nextDb
                            onLevelChange(nextDb)
                        },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            val nextDb = mapYToDb(change.position.y)
                            if (nextDb != lastEmitted) {
                                lastEmitted = nextDb
                                if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onLevelChange(nextDb)
                            }
                        },
                    )
                },
        ) {
            // Engraved fader track + tick marks + active boost/cut bar
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val padV = 12.dp.toPx()
                val usableH = (size.height - padV * 2f).coerceAtLeast(1f)
                val midY = padV + usableH * 0.5f
                val knobY = padV + usableH * fractionFromTop

                // Side dB ruler notches
                for (step in 0..8) {
                    val ty = padV + usableH * (step / 8f)
                    val isCenter = step == 4
                    val tickLen = if (isCenter) 9.dp.toPx() else 5.dp.toPx()
                    val tickCol = if (isCenter) palette.warmBrass.copy(alpha = 0.7f) else palette.hairline
                    drawLine(tickCol, Offset(cx - 13.dp.toPx(), ty), Offset(cx - 13.dp.toPx() + tickLen, ty), 1.dp.toPx())
                    drawLine(tickCol, Offset(cx + 13.dp.toPx() - tickLen, ty), Offset(cx + 13.dp.toPx(), ty), 1.dp.toPx())
                }

                // Recessed fader channel groove
                val grooveW = 4.dp.toPx()
                drawRoundRect(
                    color = if (palette.isDark) Color(0xFF0C0E13) else palette.hairline,
                    topLeft = Offset(cx - grooveW / 2f, padV),
                    size = Size(grooveW, usableH),
                    cornerRadius = CornerRadius(grooveW / 2f, grooveW / 2f),
                )

                // Active Excursion Ribbon from 0 dB center to knob position
                if (enabled && levelDb != 0) {
                    val topY = minOf(midY, knobY)
                    val spanY = kotlin.math.abs(knobY - midY)
                    drawRoundRect(
                        color = if (levelDb > 0) palette.warmBrass else palette.inkPrimary,
                        topLeft = Offset(cx - grooveW / 2f, topY),
                        size = Size(grooveW, spanY),
                        cornerRadius = CornerRadius(grooveW / 2f, grooveW / 2f),
                    )
                }
            }

            // Machined 3D Rectangular Fader Cap
            val capHeightDp = 22.dp
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset {
                        val maxTravelPx = (trackHeightPx - capHeightDp.roundToPx()).coerceAtLeast(0)
                        val y = (fractionFromTop * maxTravelPx).roundToInt().coerceIn(0, maxTravelPx)
                        IntOffset(0, y)
                    }
                    .width(34.dp)
                    .height(capHeightDp)
                    .shadow(4.dp, RoundedCornerShape(6.dp))
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(palette.plinthTop, palette.plinthBottom)
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = if (enabled) palette.plinthEdgeLight else palette.hairline,
                        shape = RoundedCornerShape(6.dp),
                    ),
            ) {
                // Engraved tactile center line on fader cap
                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .height(2.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                !enabled -> palette.inkMuted
                                levelDb > 0 -> palette.warmBrass
                                else -> palette.inkPrimary
                            }
                        ),
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = freqLabel,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = DigitalMonoFontFamily,
                fontSize = 9.5.sp,
            ),
            color = palette.inkSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun BandMeter(label: String, level: Float, modifier: Modifier = Modifier) {
    val palette = LocalPlinthColors.current
    val clamped = level.coerceIn(0f, 1f)
    Column(modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = DigitalMonoFontFamily,
                    fontSize = 10.sp,
                ),
                color = palette.inkSecondary,
            )
            Text(
                text = "${(clamped * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = DigitalMonoFontFamily,
                    fontSize = 9.5.sp,
                ),
                color = if (clamped > 0.72f) palette.warmBrass else palette.inkPrimary,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
        ) {
            val segments = 10
            val gap = 2.dp.toPx()
            val segW = (size.width - gap * (segments - 1)) / segments
            val litCount = (clamped * segments).roundToInt()
            for (i in 0 until segments) {
                val active = i < litCount
                val segColor = when {
                    !active -> palette.hairline
                    i >= 8 -> palette.warmBrass
                    else -> palette.inkPrimary
                }
                drawRoundRect(
                    color = segColor,
                    topLeft = Offset(i * (segW + gap), 0f),
                    size = Size(segW, size.height),
                    cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsBottomSheet(
    preferences: UserPreferences,
    sleepTimerRemainingMs: Long,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetSleepTimerMinutes: (Int) -> Unit,
    onSetHaptics: (Boolean) -> Unit,
    onSetReducedMotion: (Boolean) -> Unit,
    onImportAudio: () -> Unit,
    onScanDevice: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalPlinthColors.current
    val strings = LocalAppStrings.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.canvasTop,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 6.dp)
                .testTag("settings_sheet"),
        ) {
            Text(
                text = strings.settings,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = palette.inkPrimary,
            )

            // Appearance Mode (Studio Light / Titanium Dark / System)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(strings.themeMode, style = MaterialTheme.typography.labelLarge, color = palette.inkSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        ThemeMode.LIGHT to strings.themeLight,
                        ThemeMode.DARK to strings.themeDark,
                        ThemeMode.SYSTEM to strings.themeSystem,
                    ).forEach { (mode, label) ->
                        val active = preferences.themeMode == mode
                        TactileCapsuleButton(
                            onClick = { onSetThemeMode(mode) },
                            contentDescription = label,
                            cornerRadius = 16.dp,
                            hapticsEnabled = preferences.hapticsEnabled,
                            active = active,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("theme_${mode.name.lowercase()}"),
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                ),
                                color = if (active) palette.inkPrimary else palette.inkSecondary,
                            )
                        }
                    }
                }
            }

            // Language (English LTR / فارسی RTL)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(strings.language, style = MaterialTheme.typography.labelLarge, color = palette.inkSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        AppLanguage.EN to "English",
                        AppLanguage.FA to "فارسی (RTL)",
                    ).forEach { (lang, label) ->
                        val active = preferences.language == lang
                        TactileCapsuleButton(
                            onClick = { onSetLanguage(lang) },
                            contentDescription = label,
                            cornerRadius = 16.dp,
                            hapticsEnabled = preferences.hapticsEnabled,
                            active = active,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("lang_${lang.name.lowercase()}"),
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                ),
                                color = if (active) palette.inkPrimary else palette.inkSecondary,
                            )
                        }
                    }
                }
            }

            // Sleep Timer
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(strings.sleepTimer, style = MaterialTheme.typography.labelLarge, color = palette.inkSecondary)
                    if (sleepTimerRemainingMs > 0L) {
                        Text(
                            text = strings.timerRemaining(formatDuration(sleepTimerRemainingMs)),
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = DigitalMonoFontFamily),
                            color = palette.inkPrimary,
                        )
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(0, 5, 15, 30, 60).forEach { mins ->
                        val label = if (mins == 0) strings.timerOff else strings.timerMinutes(mins)
                        TactileCapsuleButton(
                            onClick = { onSetSleepTimerMinutes(mins) },
                            contentDescription = label,
                            cornerRadius = 16.dp,
                            hapticsEnabled = preferences.hapticsEnabled,
                            active = (mins == 0 && sleepTimerRemainingMs == 0L),
                            modifier = Modifier.height(38.dp),
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.inkPrimary,
                                modifier = Modifier.padding(horizontal = 14.dp),
                            )
                        }
                    }
                }
            }

            // Haptics & Reduced Motion Accessibility Toggles
            SettingsToggleRow(
                label = strings.haptics,
                checked = preferences.hapticsEnabled,
                onCheckedChange = onSetHaptics,
            )
            SettingsToggleRow(
                label = strings.reducedMotion,
                checked = preferences.reducedMotion,
                onCheckedChange = onSetReducedMotion,
            )

            // Offline Library Actions
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                TactileCapsuleButton(
                    onClick = {
                        onDismiss()
                        onImportAudio()
                    },
                    contentDescription = strings.importAudio,
                    cornerRadius = 18.dp,
                    hapticsEnabled = preferences.hapticsEnabled,
                    modifier = Modifier.weight(1f).height(46.dp),
                ) {
                    Text(strings.importAudio, style = MaterialTheme.typography.labelLarge, color = palette.inkPrimary)
                }
                TactileCapsuleButton(
                    onClick = {
                        onDismiss()
                        onScanDevice()
                    },
                    contentDescription = strings.scanDevice,
                    cornerRadius = 18.dp,
                    hapticsEnabled = preferences.hapticsEnabled,
                    modifier = Modifier.weight(1f).height(46.dp),
                ) {
                    Text(strings.scanDevice, style = MaterialTheme.typography.labelLarge, color = palette.inkPrimary)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val palette = LocalPlinthColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.capsuleBg)
            .border(1.dp, palette.hairline, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.titleSmall, color = palette.inkPrimary)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = palette.capsuleBg,
                checkedTrackColor = palette.inkPrimary,
            ),
        )
    }
}

@Composable
fun CreatePlaylistDialog(
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalPlinthColors.current
    val strings = LocalAppStrings.current
    var title by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.capsuleBg,
        title = { Text(strings.createPlaylist, color = palette.inkPrimary) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text(strings.playlistNameHint, color = palette.inkSecondary) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("playlist_name_input"),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isNotBlank()) {
                        onCreate(title)
                        onDismiss()
                    }
                },
                modifier = Modifier.testTag("playlist_create_confirm"),
            ) {
                Text(strings.create, color = palette.inkPrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel, color = palette.inkSecondary)
            }
        },
    )
}

@Composable
fun AddToPlaylistDialog(
    track: Track,
    playlists: List<PlaylistSummary>,
    onSelectPlaylist: (PlaylistSummary) -> Unit,
    onCreateNewPlaylist: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalPlinthColors.current
    val strings = LocalAppStrings.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.capsuleBg,
        title = { Text(strings.addToPlaylist, color = palette.inkPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (playlists.isEmpty()) {
                    Text(strings.emptyPlaylists, style = MaterialTheme.typography.bodySmall, color = palette.inkSecondary)
                } else {
                    playlists.forEach { pl ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(palette.canvasTop)
                                .border(1.dp, palette.hairline, RoundedCornerShape(14.dp))
                                .clickable {
                                    onSelectPlaylist(pl)
                                    onDismiss()
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            Text(
                                text = "${pl.name} (${pl.trackIds.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = palette.inkPrimary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onCreateNewPlaylist()
                },
            ) {
                Text(strings.createPlaylist, color = palette.inkPrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel, color = palette.inkSecondary)
            }
        },
    )
}
