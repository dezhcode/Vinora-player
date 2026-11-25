package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.R
import com.example.audio.FeedbackEvent
import com.example.audio.PlinthFeedback
import com.example.data.GraphicsQuality
import com.example.ui.theme.LocalPlinthColors
import com.example.ui.theme.PlinthPalette
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Pure Jetpack Compose Top-Down Industrial Hardware Turntable Deck.
 * Matches the minimalist studio hardware reference: brushed aluminum-white/titanium square plinth,
 * rear copper/chrome connectors, bottom corner rotary dials, articulated carbon/silver tonearm,
 * and deep black 33⅓ RPM vinyl with stationary dual-lobe studio light reflections and fine circular grooves.
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
    val cdTurntableText = stringResource(R.string.cd_turntable)

    val latestOnTogglePlay by rememberUpdatedState(onTogglePlayPause)
    val latestOnScrubDelta by rememberUpdatedState(onScrubDeltaMs)
    val latestIsPlaying by rememberUpdatedState(isPlaying)

    var discAngleDeg by remember { mutableFloatStateOf(0f) }
    var isScrubbingVinyl by remember { mutableStateOf(false) }
    var lastTouchAngle by remember { mutableFloatStateOf(0f) }
    var lastDetentDeg by remember { mutableFloatStateOf(0f) }
    var swapSweepBoost by remember { mutableFloatStateOf(0f) }
    var lastHandledNonce by remember { mutableIntStateOf(trackChangeNonce) }

    // Subtle needle lift animation on track change
    LaunchedEffect(trackChangeNonce) {
        if (trackChangeNonce != lastHandledNonce) {
            lastHandledNonce = trackChangeNonce
            swapSweepBoost = 1f
            kotlinx.coroutines.delay(240L)
            swapSweepBoost = 0f
        }
    }

    // 33⅓ RPM = 200 degrees per second (throttled smoothly in battery saver / reduced motion)
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

    // Articulated tonearm angle:
    // 4.5° = parked cleanly along outer edge, 9°..29° = tracking outer-to-inner grooves
    val targetArmSweepDeg = when {
        swapSweepBoost > 0.5f -> 3.5f
        isPlaying -> 9.0f + progressFraction.coerceIn(0f, 1f) * 20.5f
        else -> 4.5f
    }

    val armSweepDeg by animateFloatAsState(
        targetValue = targetArmSweepDeg,
        animationSpec = if (reducedMotion) {
            tween(durationMillis = 160)
        } else {
            spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)
        },
        label = "tonearmSweep",
    )

    val grooveCount = when {
        batterySaverMode || graphicsQuality == GraphicsQuality.LOW -> 14
        graphicsQuality == GraphicsQuality.MEDIUM -> 22
        else -> 32
    }

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .testTag("turntable_deck")
            .semantics { contentDescription = cdTurntableText },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .aspectRatio(1.02f, matchHeightConstraintsFirst = maxHeight < maxWidth)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val topPad = h * 0.028f
                        val plinthH = h - topPad
                        val platterCenter = Offset(w * 0.456f, topPad + plinthH * 0.506f)
                        val leftDialCenter = Offset(w * 0.142f, topPad + plinthH * 0.858f)
                        val rightDialCenter = Offset(w * 0.858f, topPad + plinthH * 0.858f)

                        val hitDialLeft = hypot(offset.x - leftDialCenter.x, offset.y - leftDialCenter.y) <= w * 0.115f
                        val hitDialRight = hypot(offset.x - rightDialCenter.x, offset.y - rightDialCenter.y) <= w * 0.115f
                        val hitPlatter = hypot(offset.x - platterCenter.x, offset.y - platterCenter.y) <= w * 0.38f

                        if (hitDialLeft || hitDialRight || hitPlatter) {
                            PlinthFeedback.trigger(FeedbackEvent.PLAY_PAUSE)
                            latestOnTogglePlay()
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { start ->
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val topPad = h * 0.028f
                            val plinthH = h - topPad
                            val platterCenter = Offset(w * 0.456f, topPad + plinthH * 0.506f)
                            val dist = hypot(start.x - platterCenter.x, start.y - platterCenter.y)
                            if (dist <= w * 0.42f) {
                                isScrubbingVinyl = true
                                lastTouchAngle = atan2(start.y - platterCenter.y, start.x - platterCenter.x)
                                lastDetentDeg = discAngleDeg
                            }
                        },
                        onDrag = { change, _ ->
                            if (!isScrubbingVinyl) return@detectDragGestures
                            change.consume()
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val topPad = h * 0.028f
                            val plinthH = h - topPad
                            val platterCenter = Offset(w * 0.456f, topPad + plinthH * 0.506f)
                            val angle = atan2(change.position.y - platterCenter.y, change.position.x - platterCenter.x)
                            var delta = angle - lastTouchAngle
                            if (delta > PI.toFloat()) delta -= (2.0 * PI).toFloat()
                            if (delta < -PI.toFloat()) delta += (2.0 * PI).toFloat()
                            lastTouchAngle = angle

                            val deltaDeg = Math.toDegrees(delta.toDouble()).toFloat()
                            discAngleDeg = (discAngleDeg + deltaDeg) % 360f
                            if (kotlin.math.abs(discAngleDeg - lastDetentDeg) >= 24f) {
                                lastDetentDeg = discAngleDeg
                                PlinthFeedback.triggerSliderDetent(isMajorNotch = false)
                            }
                            val deltaMs = ((delta / (2.0 * PI)) * 5500.0).roundToInt().toLong()
                            if (deltaMs != 0L) latestOnScrubDelta(deltaMs)
                        },
                        onDragEnd = { isScrubbingVinyl = false },
                        onDragCancel = { isScrubbingVinyl = false },
                    )
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val topPad = h * 0.028f
                val plinthH = h - topPad
                val minPlinth = minOf(w, plinthH)
                val cornerR = minPlinth * 0.068f

                // 1. Rear Copper/Chrome Connectors peeking from top edge (slightly right of center)
                drawRearTerminals(w = w, topPad = topPad, palette = palette)

                // 2. Plinth Soft Drop Shadow & Machined Square Housing
                drawPlinthChassis(
                    w = w,
                    topPad = topPad,
                    plinthH = plinthH,
                    minPlinth = minPlinth,
                    cornerR = cornerR,
                    palette = palette,
                )

                // 3. Bottom-Left 3-Spoke Dial & Bottom-Right Tactile Dial
                drawBottomCornerDials(
                    w = w,
                    topPad = topPad,
                    plinthH = plinthH,
                    minPlinth = minPlinth,
                    isPlaying = latestIsPlaying,
                    palette = palette,
                )

                // 4. Sculpted Top-Right Tonearm Bay Recess
                val pivot = Offset(w * 0.856f, topPad + plinthH * 0.146f)
                drawTonearmBayContour(
                    pivot = pivot,
                    minPlinth = minPlinth,
                    palette = palette,
                )

                // 5. Deep Black Vinyl LP + Stationary Dual Specular Studio Cones + Rotating Grooves + Alabaster Label
                val platterCenter = Offset(w * 0.456f, topPad + plinthH * 0.506f)
                val discR = minPlinth * 0.382f
                drawVinylRecordAssembly(
                    center = platterCenter,
                    discR = discR,
                    discAngleDeg = discAngleDeg,
                    grooveCount = grooveCount,
                    palette = palette,
                )

                // 6. Articulated Carbon & Machined Silver Tonearm Assembly
                drawTonearmAssembly(
                    pivot = pivot,
                    minPlinth = minPlinth,
                    armSweepDeg = armSweepDeg,
                    isPlaying = latestIsPlaying,
                    palette = palette,
                )
            }
        }
    }
}

private fun DrawScope.drawRearTerminals(
    w: Float,
    topPad: Float,
    palette: PlinthPalette,
) {
    val pinXs = listOf(w * 0.562f, w * 0.622f)
    val pinW = (w * 0.014f).coerceAtLeast(3.dp.toPx())
    val collarW = pinW * 1.45f
    val pinH = topPad * 0.92f
    val collarH = topPad * 0.38f

    pinXs.forEach { px ->
        // Copper/brass terminal pin
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color(0xFF8C5A32), Color(0xFFD69F68), Color(0xFF7B4C28)),
                startX = px - pinW / 2f,
                endX = px + pinW / 2f,
            ),
            topLeft = Offset(px - pinW / 2f, topPad - pinH),
            size = Size(pinW, pinH + 2.dp.toPx()),
            cornerRadius = CornerRadius(pinW * 0.35f, pinW * 0.35f),
        )
        // Insulating metallic collar seated on plinth rear edge
        drawRoundRect(
            color = if (palette.isDark) Color(0xFF474F5E) else Color(0xFFDDE1EA),
            topLeft = Offset(px - collarW / 2f, topPad - collarH),
            size = Size(collarW, collarH + 1.dp.toPx()),
            cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()),
        )
    }
}

private fun DrawScope.drawPlinthChassis(
    w: Float,
    topPad: Float,
    plinthH: Float,
    minPlinth: Float,
    cornerR: Float,
    palette: PlinthPalette,
) {
    val chassisTopLeft = Offset(0f, topPad)
    val chassisSize = Size(w, plinthH)

    // Soft ambient studio contact shadow underneath plinth
    for (i in 3 downTo 1) {
        val spread = (i * 2.2f).dp.toPx()
        val yShift = (i * 2.0f).dp.toPx()
        drawRoundRect(
            color = palette.shadowDark.copy(alpha = if (palette.isDark) 0.22f / i else 0.14f / i),
            topLeft = Offset(-spread * 0.35f, topPad + yShift),
            size = Size(w + spread * 0.7f, plinthH + spread * 0.45f),
            cornerRadius = CornerRadius(cornerR + spread * 0.3f, cornerR + spread * 0.3f),
        )
    }

    // Main satin machined plinth surface
    val surfaceTop = if (palette.isDark) Color(0xFF2B313C) else Color(0xFFF7F8FB)
    val surfaceMid = if (palette.isDark) Color(0xFF212630) else Color(0xFFEFEFF4)
    val surfaceBottom = if (palette.isDark) Color(0xFF181C24) else Color(0xFFE4E7EE)
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(surfaceTop, surfaceMid, surfaceBottom),
            start = Offset(0f, topPad),
            end = Offset(w, topPad + plinthH),
        ),
        topLeft = chassisTopLeft,
        size = chassisSize,
        cornerRadius = CornerRadius(cornerR, cornerR),
    )

    // Subtle inset machined frame hairline near outer edge
    val inset = minPlinth * 0.012f
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                palette.plinthEdgeLight.copy(alpha = if (palette.isDark) 0.36f else 0.95f),
                palette.plinthEdgeShadow.copy(alpha = if (palette.isDark) 0.55f else 0.65f),
            ),
            start = Offset(0f, topPad),
            end = Offset(w, topPad + plinthH),
        ),
        topLeft = Offset(inset * 0.5f, topPad + inset * 0.5f),
        size = Size(w - inset, plinthH - inset),
        cornerRadius = CornerRadius(cornerR, cornerR),
        style = Stroke(width = 1.4.dp.toPx()),
    )

    // Very subtle embossed top-left square badge recess (signature detail from reference)
    val badgeSize = minPlinth * 0.14f
    val badgePos = Offset(w * 0.055f, topPad + plinthH * 0.055f)
    drawRoundRect(
        color = if (palette.isDark) Color.White.copy(alpha = 0.03f) else Color(0xFFD5DAE5).copy(alpha = 0.38f),
        topLeft = badgePos,
        size = Size(badgeSize, badgeSize),
        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
        style = Stroke(width = 0.8.dp.toPx()),
    )
}

private fun DrawScope.drawBottomCornerDials(
    w: Float,
    topPad: Float,
    plinthH: Float,
    minPlinth: Float,
    isPlaying: Boolean,
    palette: PlinthPalette,
) {
    // Bottom-Left 3-Spoke Selector Dial (matches reference bottom-left Y-spoke knob)
    val leftCenter = Offset(w * 0.142f, topPad + plinthH * 0.858f)
    val leftR = minPlinth * 0.078f

    drawCircle(
        color = palette.plinthEdgeShadow.copy(alpha = if (palette.isDark) 0.65f else 0.72f),
        radius = leftR * 1.04f,
        center = leftCenter,
        style = Stroke(width = 1.2.dp.toPx()),
    )
    drawCircle(
        brush = Brush.linearGradient(
            colors = if (palette.isDark) {
                listOf(Color(0xFF2D333F), Color(0xFF1D2129))
            } else {
                listOf(Color(0xFFF6F7FA), Color(0xFFE7EAF1))
            },
            start = Offset(leftCenter.x - leftR, leftCenter.y - leftR),
            end = Offset(leftCenter.x + leftR, leftCenter.y + leftR),
        ),
        radius = leftR,
        center = leftCenter,
    )
    // 3 radial Y-spokes inside the bottom-left dial
    val seamColor = if (palette.isDark) Color(0xFF566073) else Color(0xFF8C94A4)
    val anglesDeg = listOf(-122.0, 2.0, 108.0)
    anglesDeg.forEach { deg ->
        val rad = Math.toRadians(deg)
        drawLine(
            color = seamColor.copy(alpha = 0.78f),
            start = leftCenter,
            end = Offset(
                leftCenter.x + (leftR * 0.94f * cos(rad)).toFloat(),
                leftCenter.y + (leftR * 0.94f * sin(rad)).toFloat(),
            ),
            strokeWidth = 1.1.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }

    // Bottom-Right Circular Dial (matches reference bottom-right recessed button)
    val rightCenter = Offset(w * 0.858f, topPad + plinthH * 0.858f)
    val rightR = minPlinth * 0.068f
    drawCircle(
        color = palette.plinthEdgeShadow.copy(alpha = if (palette.isDark) 0.7f else 0.75f),
        radius = rightR * 1.06f,
        center = rightCenter,
        style = Stroke(width = 1.3.dp.toPx()),
    )
    drawCircle(
        brush = Brush.linearGradient(
            colors = if (palette.isDark) {
                listOf(Color(0xFF2E3542), Color(0xFF1E222B))
            } else {
                listOf(Color(0xFFF8F9FC), Color(0xFFE8EBF2))
            },
            start = Offset(rightCenter.x - rightR, rightCenter.y - rightR),
            end = Offset(rightCenter.x + rightR, rightCenter.y + rightR),
        ),
        radius = rightR,
        center = rightCenter,
    )
    drawCircle(
        color = if (isPlaying) {
            palette.inkPrimary.copy(alpha = 0.25f)
        } else {
            palette.plinthEdgeLight.copy(alpha = if (palette.isDark) 0.22f else 0.85f)
        },
        radius = rightR * 0.90f,
        center = rightCenter,
        style = Stroke(width = 0.9.dp.toPx()),
    )
}

private fun DrawScope.drawTonearmBayContour(
    pivot: Offset,
    minPlinth: Float,
    palette: PlinthPalette,
) {
    val outerBayR = minPlinth * 0.132f
    // Soft sculpted collar contour curving cleanly around the top-right pivot base
    drawCircle(
        color = palette.plinthEdgeShadow.copy(alpha = if (palette.isDark) 0.45f else 0.48f),
        radius = outerBayR,
        center = pivot,
        style = Stroke(width = 1.4.dp.toPx()),
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = if (palette.isDark) {
                listOf(Color(0xFF2D3340), Color(0xFF20252F))
            } else {
                listOf(Color(0xFFF9FAFC), Color(0xFFECEFF5))
            },
            center = pivot,
            radius = outerBayR,
        ),
        radius = outerBayR * 0.97f,
        center = pivot,
    )
}

private fun DrawScope.drawVinylRecordAssembly(
    center: Offset,
    discR: Float,
    discAngleDeg: Float,
    grooveCount: Int,
    palette: PlinthPalette,
) {
    // Soft platter ambient shadow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.Black.copy(alpha = if (palette.isDark) 0.55f else 0.20f),
                Color.Transparent,
            ),
            center = Offset(center.x + 2.dp.toPx(), center.y + 4.dp.toPx()),
            radius = discR * 1.07f,
        ),
        radius = discR * 1.07f,
        center = Offset(center.x + 2.dp.toPx(), center.y + 4.dp.toPx()),
    )

    // Machined outer platter ring
    drawCircle(
        color = if (palette.isDark) Color(0xFF343B49) else Color(0xFFD2D7E3),
        radius = discR * 1.016f,
        center = center,
    )

    // Deep Black Vinyl Base
    drawCircle(
        color = Color(0xFF060709),
        radius = discR,
        center = center,
    )

    // Rotating Concentric Fine Circular Grooves & Subtle Track Bands
    rotate(degrees = discAngleDeg, pivot = center) {
        val innerStart = 0.365f
        val outerEnd = 0.955f
        for (i in 0 until grooveCount) {
            val t = i.toFloat() / (grooveCount - 1).coerceAtLeast(1)
            val rNorm = innerStart + t * (outerEnd - innerStart)
            val isTrackBreak = (i % 7 == 0) && i > 0
            drawCircle(
                color = if (isTrackBreak) {
                    Color.White.copy(alpha = 0.075f)
                } else {
                    Color.White.copy(alpha = if (i % 2 == 0) 0.032f else 0.018f)
                },
                radius = discR * rNorm,
                center = center,
                style = Stroke(width = if (isTrackBreak) 1.2.dp.toPx() else 0.7.dp.toPx()),
            )
        }
        // Subtle rotating specular micro-glint streaks inside groove field so 33⅓ RPM rotation is clearly visible
        for (k in 0..2) {
            val startA = k * 120f + 14f
            val rArc = discR * (0.52f + k * 0.14f)
            drawArc(
                color = Color.White.copy(alpha = 0.065f),
                startAngle = startA,
                sweepAngle = 22f,
                useCenter = false,
                topLeft = Offset(center.x - rArc, center.y - rArc),
                size = Size(rArc * 2f, rArc * 2f),
                style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }

    // Stationary Dual-Lobe Satin Studio Specular Reflections (~11 o'clock & ~5 o'clock, matching reference)
    val studioSweep = Brush.sweepGradient(
        0.00f to Color.White.copy(alpha = 0.015f),
        0.10f to Color.White.copy(alpha = 0.095f),
        0.16f to Color.White.copy(alpha = 0.21f),
        0.21f to Color.White.copy(alpha = 0.06f),
        0.34f to Color.Transparent,
        0.52f to Color.White.copy(alpha = 0.015f),
        0.60f to Color.White.copy(alpha = 0.10f),
        0.66f to Color.White.copy(alpha = 0.22f),
        0.72f to Color.White.copy(alpha = 0.055f),
        0.86f to Color.Transparent,
        1.00f to Color.White.copy(alpha = 0.015f),
        center = center,
    )
    drawCircle(
        brush = studioSweep,
        radius = discR * 0.96f,
        center = center,
    )

    // Subtle dark vignette mask over outer rim & dead-wax ring for realism
    drawCircle(
        color = Color(0xFF0D0F14),
        radius = discR * 0.985f,
        center = center,
        style = Stroke(width = discR * 0.03f),
    )
    drawCircle(
        color = Color(0xFF0A0C10).copy(alpha = 0.72f),
        radius = discR * 0.348f,
        center = center,
        style = Stroke(width = discR * 0.055f),
    )

    // Minimalist Pressed Alabaster Center Label (exact match to reference)
    val labelR = discR * 0.305f
    drawCircle(
        brush = Brush.radialGradient(
            colors = if (palette.isDark) {
                listOf(Color(0xFF2D3340), Color(0xFF20252F))
            } else {
                listOf(Color(0xFFF3F4F7), Color(0xFFE5E7ED))
            },
            center = Offset(center.x - labelR * 0.2f, center.y - labelR * 0.2f),
            radius = labelR * 1.2f,
        ),
        radius = labelR,
        center = center,
    )
    // Pressed stamper ridge ring inside label
    drawCircle(
        color = if (palette.isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFC5CAD6).copy(alpha = 0.7f),
        radius = labelR * 0.48f,
        center = center,
        style = Stroke(width = 0.85.dp.toPx()),
    )
    drawCircle(
        color = if (palette.isDark) Color.Black.copy(alpha = 0.45f) else Color(0xFFD0D4DF).copy(alpha = 0.8f),
        radius = labelR * 0.985f,
        center = center,
        style = Stroke(width = 0.9.dp.toPx()),
    )

    // Turned Stainless Center Spindle
    val spindleR = discR * 0.032f
    drawCircle(
        color = Color.Black.copy(alpha = 0.28f),
        radius = spindleR * 1.45f,
        center = center,
    )
    drawCircle(
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFFFFFFFF), Color(0xFF9AA2B2)),
            start = Offset(center.x - spindleR, center.y - spindleR),
            end = Offset(center.x + spindleR, center.y + spindleR),
        ),
        radius = spindleR,
        center = center,
    )
}

private fun DrawScope.drawTonearmAssembly(
    pivot: Offset,
    minPlinth: Float,
    armSweepDeg: Float,
    isPlaying: Boolean,
    palette: PlinthPalette,
) {
    // Stationary dark gimbal well
    val wellR = minPlinth * 0.062f
    drawCircle(
        color = if (palette.isDark) Color(0xFF101318) else Color(0xFFBFC5D2),
        radius = wellR,
        center = pivot,
        style = Stroke(width = 2.dp.toPx()),
    )

    // Whole articulated tonearm pivots cleanly around gimbal center
    rotate(degrees = armSweepDeg, pivot = pivot) {
        val wandLen = minPlinth * 0.455f
        val wandW = (minPlinth * 0.017f).coerceAtLeast(3.5.dp.toPx())
        val shadowShift = Offset(-3.dp.toPx(), 4.dp.toPx())

        // 1. Tonearm & Headshell Drop Shadow
        val hsEnd = Offset(pivot.x - minPlinth * 0.025f, pivot.y + wandLen + minPlinth * 0.082f)
        drawLine(
            color = Color.Black.copy(alpha = if (palette.isDark) 0.45f else 0.16f),
            start = pivot + shadowShift,
            end = Offset(pivot.x, pivot.y + wandLen) + shadowShift,
            strokeWidth = wandW * 1.15f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color.Black.copy(alpha = if (palette.isDark) 0.45f else 0.16f),
            start = Offset(pivot.x, pivot.y + wandLen) + shadowShift,
            end = hsEnd + shadowShift,
            strokeWidth = wandW * 2.1f,
            cap = StrokeCap.Round,
        )

        // 2. Rear Cylindrical Counterweight (extending upward above gimbal)
        val cwW = minPlinth * 0.048f
        val cwH = minPlinth * 0.072f
        val cwTop = pivot.y - minPlinth * 0.095f
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color(0xFF9AA1AF), Color(0xFFF3F5F9), Color(0xFF7B8291)),
                startX = pivot.x - cwW / 2f,
                endX = pivot.x + cwW / 2f,
            ),
            topLeft = Offset(pivot.x - cwW / 2f, cwTop),
            size = Size(cwW, cwH),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
        )
        // Dark damping ring on counterweight
        drawRect(
            color = Color(0xFF171A21),
            topLeft = Offset(pivot.x - cwW / 2f, cwTop + cwH * 0.72f),
            size = Size(cwW, cwH * 0.28f),
        )

        // 3. Machined Gimbal Top Cap & Right Anti-Skate/Cueing Bar
        val rightBarStart = Offset(pivot.x + minPlinth * 0.02f, pivot.y)
        val rightBarEnd = Offset(pivot.x + minPlinth * 0.088f, pivot.y)
        drawLine(
            color = Color(0xFF1B1E25),
            start = rightBarStart,
            end = rightBarEnd,
            strokeWidth = 2.2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = if (palette.isDark) {
                    listOf(Color(0xFF424A59), Color(0xFF232833))
                } else {
                    listOf(Color(0xFFFFFFFF), Color(0xFFDCDFE8))
                },
                center = Offset(pivot.x - 2.dp.toPx(), pivot.y - 2.dp.toPx()),
                radius = minPlinth * 0.06f,
            ),
            radius = minPlinth * 0.052f,
            center = pivot,
        )
        drawCircle(
            color = if (palette.isDark) Color(0xFF5D677B) else Color(0xFFB5BCCB),
            radius = minPlinth * 0.052f,
            center = pivot,
            style = Stroke(width = 1.dp.toPx()),
        )

        // 4. Secondary Parallel Silver Cueing/Arm-Rest Rod beside upper wand (reference detail)
        val guideX = pivot.x + minPlinth * 0.024f
        drawLine(
            color = Color(0xFFBFC6D4),
            start = Offset(guideX, pivot.y + minPlinth * 0.055f),
            end = Offset(guideX, pivot.y + minPlinth * 0.255f),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round,
        )

        // 5. Matte-Black Carbon Tonearm Shaft
        val wandEnd = Offset(pivot.x, pivot.y + wandLen)
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(Color(0xFF2D323D), Color(0xFF0E1015), Color(0xFF1F232C)),
                startX = pivot.x - wandW / 2f,
                endX = pivot.x + wandW / 2f,
            ),
            start = Offset(pivot.x, pivot.y + minPlinth * 0.03f),
            end = wandEnd,
            strokeWidth = wandW,
            cap = StrokeCap.Butt,
        )

        // 6. Mid-Lower Metallic Clamp / Locking Sleeve (matches reference silver bracket on wand)
        val sleeveY = pivot.y + wandLen * 0.68f
        val sleeveW = wandW * 1.48f
        val sleeveH = minPlinth * 0.038f
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color(0xFF9BA3B2), Color(0xFFF2F4F8), Color(0xFF7D8594)),
                startX = pivot.x - sleeveW / 2f,
                endX = pivot.x + sleeveW / 2f,
            ),
            topLeft = Offset(pivot.x - sleeveW / 2f, sleeveY),
            size = Size(sleeveW, sleeveH),
            cornerRadius = CornerRadius(1.2.dp.toPx(), 1.2.dp.toPx()),
        )

        // 7. Angled Headshell, Finger Lift, and Cartridge Stylus
        val headshellAngleDeg = 22f // Angled inward toward platter center
        rotate(degrees = headshellAngleDeg, pivot = wandEnd) {
            // Silver bayonet collar at wand->headshell joint
            drawRect(
                color = Color(0xFFD5DBE6),
                topLeft = Offset(wandEnd.x - wandW * 0.72f, wandEnd.y - 1.5.dp.toPx()),
                size = Size(wandW * 1.44f, 3.5.dp.toPx()),
            )
            // Matte-black rectangular headshell body
            val hsW = wandW * 1.95f
            val hsH = minPlinth * 0.088f
            val hsPath = Path().apply {
                moveTo(wandEnd.x - hsW * 0.5f, wandEnd.y + 2.dp.toPx())
                lineTo(wandEnd.x + hsW * 0.5f, wandEnd.y + 2.dp.toPx())
                lineTo(wandEnd.x + hsW * 0.42f, wandEnd.y + hsH)
                lineTo(wandEnd.x - hsW * 0.42f, wandEnd.y + hsH)
                close()
            }
            drawPath(
                path = hsPath,
                color = Color(0xFF14171D),
            )
            drawPath(
                path = hsPath,
                color = Color(0xFF3E4554),
                style = Stroke(width = 0.8.dp.toPx(), join = StrokeJoin.Round),
            )
            // Delicate silver finger-lift tab extending right
            drawLine(
                color = Color(0xFFD8DEE8),
                start = Offset(wandEnd.x + hsW * 0.42f, wandEnd.y + hsH * 0.65f),
                end = Offset(wandEnd.x + hsW * 0.92f, wandEnd.y + hsH * 0.55f),
                strokeWidth = 1.6.dp.toPx(),
                cap = StrokeCap.Round,
            )
            // Tiny cartridge nose & stylus tip
            val tipCenter = Offset(wandEnd.x, wandEnd.y + hsH + 1.5.dp.toPx())
            drawCircle(
                color = if (isPlaying) Color(0xFFD17A5A) else Color(0xFF9EA6B5),
                radius = 2.dp.toPx(),
                center = tipCenter,
            )
        }
    }
}
