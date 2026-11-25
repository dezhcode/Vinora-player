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
    val outerBayR = minPlinth * 0.136f
    val innerRimR = minPlinth * 0.102f

    // 1. Soft concave outer bezel depression into plinth surface
    drawCircle(
        brush = Brush.linearGradient(
            colors = if (palette.isDark) {
                listOf(Color(0xFF101319), Color(0xFF343B49))
            } else {
                listOf(Color(0xFFCCD2DF), Color(0xFFFFFFFF))
            },
            start = Offset(pivot.x - outerBayR, pivot.y - outerBayR),
            end = Offset(pivot.x + outerBayR, pivot.y + outerBayR),
        ),
        radius = outerBayR,
        center = pivot,
        style = Stroke(width = 2.2.dp.toPx()),
    )

    // 2. Machined circular mounting deck plateau
    drawCircle(
        brush = Brush.radialGradient(
            colors = if (palette.isDark) {
                listOf(Color(0xFF2F3644), Color(0xFF232833), Color(0xFF1B1F28))
            } else {
                listOf(Color(0xFFFCFDFF), Color(0xFFF1F3F8), Color(0xFFE6E9F1))
            },
            center = Offset(pivot.x - outerBayR * 0.2f, pivot.y - outerBayR * 0.2f),
            radius = outerBayR * 1.15f,
        ),
        radius = outerBayR * 0.965f,
        center = pivot,
    )

    // 3. Stepped inner collar ring around tonearm pedestal base
    drawCircle(
        color = palette.plinthEdgeShadow.copy(alpha = if (palette.isDark) 0.55f else 0.58f),
        radius = innerRimR,
        center = pivot,
        style = Stroke(width = 1.2.dp.toPx()),
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
    val shadowAlphaBase = if (palette.isDark) 0.48f else 0.19f

    // --- A. Stationary Plinth-Mounted Hardware (Gimbal Well, Cueing Lever, & Armrest Cradle) ---
    val wellR = minPlinth * 0.080f
    // Recessed dark gimbal basin with inner depth gradient
    drawCircle(
        brush = Brush.radialGradient(
            colors = if (palette.isDark) {
                listOf(Color(0xFF0A0C10), Color(0xFF1A1E28), Color(0xFF333B4A))
            } else {
                listOf(Color(0xFF98A0B0), Color(0xFFBDC3D1), Color(0xFFECEFF6))
            },
            center = Offset(pivot.x + wellR * 0.22f, pivot.y + wellR * 0.25f),
            radius = wellR * 1.1f,
        ),
        radius = wellR,
        center = pivot,
    )
    drawCircle(
        color = if (palette.isDark) Color(0xFF06070A) else Color(0xFF7D8596),
        radius = wellR * 0.78f,
        center = pivot,
    )

    // Fixed Hydraulic Cueing Lift Bar & Lever on right of gimbal (remains mounted on plinth)
    val cuePostCenter = Offset(pivot.x + minPlinth * 0.068f, pivot.y + minPlinth * 0.058f)
    drawCircle(
        color = Color.Black.copy(alpha = shadowAlphaBase),
        radius = minPlinth * 0.022f,
        center = Offset(cuePostCenter.x - 1.5.dp.toPx(), cuePostCenter.y + 2.dp.toPx()),
    )
    // Chrome cueing lever handle sticking right
    drawLine(
        color = Color.Black.copy(alpha = shadowAlphaBase * 0.8f),
        start = Offset(cuePostCenter.x - 1.dp.toPx(), cuePostCenter.y + 2.dp.toPx()),
        end = Offset(cuePostCenter.x + minPlinth * 0.042f, cuePostCenter.y + minPlinth * 0.028f + 2.dp.toPx()),
        strokeWidth = 2.6.dp.toPx(),
        cap = StrokeCap.Round,
    )
    drawLine(
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFFE7EBF3), Color(0xFF7E8696)),
            start = cuePostCenter,
            end = Offset(cuePostCenter.x + minPlinth * 0.042f, cuePostCenter.y + minPlinth * 0.028f),
        ),
        start = cuePostCenter,
        end = Offset(cuePostCenter.x + minPlinth * 0.042f, cuePostCenter.y + minPlinth * 0.028f),
        strokeWidth = 2.2.dp.toPx(),
        cap = StrokeCap.Round,
    )
    drawCircle(
        color = Color(0xFF181B22),
        radius = 2.4.dp.toPx(),
        center = Offset(cuePostCenter.x + minPlinth * 0.042f, cuePostCenter.y + minPlinth * 0.028f),
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFF5F7FB), Color(0xFF8C94A4)),
            center = Offset(cuePostCenter.x - 1.dp.toPx(), cuePostCenter.y - 1.dp.toPx()),
            radius = minPlinth * 0.024f,
        ),
        radius = minPlinth * 0.019f,
        center = cuePostCenter,
    )

    // Fixed Plinth Armrest Post & U-Clip at rest position (4.5° parked line)
    val restRad = Math.toRadians(4.5)
    val restDist = minPlinth * 0.215f
    val restPost = Offset(
        pivot.x - (restDist * sin(restRad)).toFloat(),
        pivot.y + (restDist * cos(restRad)).toFloat(),
    )
    drawCircle(
        color = Color.Black.copy(alpha = shadowAlphaBase),
        radius = minPlinth * 0.021f,
        center = Offset(restPost.x - 1.5.dp.toPx(), restPost.y + 2.2.dp.toPx()),
    )
    // Metallic pedestal stem & dark matte U-cradle
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFE4E8F0), Color(0xFF7F8796)),
            center = Offset(restPost.x - 1.dp.toPx(), restPost.y - 1.dp.toPx()),
            radius = minPlinth * 0.022f,
        ),
        radius = minPlinth * 0.018f,
        center = restPost,
    )
    drawRoundRect(
        color = Color(0xFF171A21),
        topLeft = Offset(restPost.x - minPlinth * 0.018f, restPost.y - minPlinth * 0.008f),
        size = Size(minPlinth * 0.036f, minPlinth * 0.016f),
        cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()),
    )

    // --- B. Pivoting 3D Articulated Tonearm Assembly ---
    // Compute world-locked light shadow vector transformed into rotated arm coordinates
    // Light originates from upper-left, casting shadow down-right (+X, +Y) or down-left depending on plinth side;
    // Plinth shadow goes subtly down (+Y) and slightly left/right. Matching turntable platter (+2, +4):
    val worldShadow = Offset(-3.2.dp.toPx(), 5.2.dp.toPx())
    val armRad = Math.toRadians(armSweepDeg.toDouble())
    val cosA = cos(armRad).toFloat()
    val sinA = sin(armRad).toFloat()
    // Inverse rotation by (-armSweepDeg) to convert world offset into local rotated Canvas coords
    val localShadowFar = Offset(
        worldShadow.x * cosA + worldShadow.y * sinA,
        -worldShadow.x * sinA + worldShadow.y * cosA,
    )
    val localShadowNear = Offset(localShadowFar.x * 0.45f, localShadowFar.y * 0.45f)

    rotate(degrees = armSweepDeg, pivot = pivot) {
        val wandLen = minPlinth * 0.455f
        val wandW = (minPlinth * 0.019f).coerceAtLeast(4.dp.toPx())
        val cwW = minPlinth * 0.056f
        val cwH = minPlinth * 0.076f
        val cwTop = pivot.y - minPlinth * 0.118f
        val wandEnd = Offset(pivot.x, pivot.y + wandLen)
        val hsEndAngleDeg = 22f
        val hsRad = Math.toRadians(hsEndAngleDeg.toDouble())
        val hsLen = minPlinth * 0.092f
        val hsTipLocal = Offset(
            wandEnd.x - (hsLen * sin(hsRad)).toFloat(),
            wandEnd.y + (hsLen * cos(hsRad)).toFloat(),
        )

        // 1. Multi-Pass Soft 3D Cast Shadow (wide penumbra + sharper core)
        listOf(1.35f to 0.35f, 1.0f to 0.65f, 0.72f to 0.9f).forEach { (scale, alphaMul) ->
            val shOff = Offset(localShadowFar.x * scale, localShadowFar.y * scale)
            val shNear = Offset(localShadowNear.x * scale, localShadowNear.y * scale)
            val shAlpha = shadowAlphaBase * alphaMul * 0.55f

            // Counterweight shadow
            drawRoundRect(
                color = Color.Black.copy(alpha = shAlpha),
                topLeft = Offset(pivot.x - cwW / 2f + shOff.x, cwTop + shOff.y),
                size = Size(cwW, cwH),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            )
            // Gimbal turret shadow
            drawCircle(
                color = Color.Black.copy(alpha = shAlpha),
                radius = minPlinth * 0.068f,
                center = pivot + shOff * 0.85f,
            )
            // Wand shadow (tapers closer near stylus end)
            drawLine(
                color = Color.Black.copy(alpha = shAlpha),
                start = pivot + shOff,
                end = wandEnd + shNear * 1.15f,
                strokeWidth = wandW * (1.25f + 0.3f * scale),
                cap = StrokeCap.Round,
            )
            // Headshell shadow (close to vinyl surface)
            drawLine(
                color = Color.Black.copy(alpha = shAlpha * 1.15f),
                start = wandEnd + shNear * 1.15f,
                end = hsTipLocal + shNear * 0.7f,
                strokeWidth = wandW * 2.1f,
                cap = StrokeCap.Round,
            )
        }

        // 2. Rear Chrome Stub Shaft + Precision Calibrated Counterweight Cylinder
        val stubW = wandW * 0.82f
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color(0xFF7B8393), Color(0xFFE7EBF3), Color(0xFF5D6473)),
                startX = pivot.x - stubW / 2f,
                endX = pivot.x + stubW / 2f,
            ),
            topLeft = Offset(pivot.x - stubW / 2f, cwTop + cwH * 0.5f),
            size = Size(stubW, minPlinth * 0.075f),
        )
        // Main counterweight metallic barrel with 3D cylindrical specular highlight
        drawRoundRect(
            brush = Brush.horizontalGradient(
                0.00f to Color(0xFF5F6776),
                0.22f to Color(0xFFD5DCE7),
                0.38f to Color(0xFFFFFFFF),
                0.62f to Color(0xFF9EA6B6),
                0.85f to Color(0xFF4F5665),
                1.00f to Color(0xFF777F8F),
                startX = pivot.x - cwW / 2f,
                endX = pivot.x + cwW / 2f,
            ),
            topLeft = Offset(pivot.x - cwW / 2f, cwTop),
            size = Size(cwW, cwH),
            cornerRadius = CornerRadius(2.4.dp.toPx(), 2.4.dp.toPx()),
        )
        // Chamfered top silver cap line & bottom black tracking-force dial collar
        drawLine(
            color = Color.White.copy(alpha = 0.72f),
            start = Offset(pivot.x - cwW * 0.42f, cwTop + 1.2.dp.toPx()),
            end = Offset(pivot.x + cwW * 0.42f, cwTop + 1.2.dp.toPx()),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round,
        )
        // Center isolation rubber O-ring groove
        drawRect(
            color = Color(0xFF181B22),
            topLeft = Offset(pivot.x - cwW / 2f, cwTop + cwH * 0.34f),
            size = Size(cwW, cwH * 0.11f),
        )
        // Front calibrated tracking-force ring with engraved tick marks
        val dialTop = cwTop + cwH * 0.72f
        val dialH = cwH * 0.28f
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color(0xFF171A21), Color(0xFF373E4C), Color(0xFF111318)),
                startX = pivot.x - cwW / 2f,
                endX = pivot.x + cwW / 2f,
            ),
            topLeft = Offset(pivot.x - cwW / 2f, dialTop),
            size = Size(cwW, dialH),
            cornerRadius = CornerRadius(1.2.dp.toPx(), 1.2.dp.toPx()),
        )
        for (tick in -2..2) {
            val tx = pivot.x + tick * (cwW * 0.17f)
            drawLine(
                color = Color(0xFFD5DCE7).copy(alpha = 0.65f),
                start = Offset(tx, dialTop + dialH * 0.22f),
                end = Offset(tx, dialTop + dialH * 0.78f),
                strokeWidth = 0.7.dp.toPx(),
            )
        }

        // 3. Machined Gimbal Yoke Architecture (Outer Bridge + Crossbar + Bearing Screws + Top Cap)
        val yokeR = minPlinth * 0.062f
        // Lateral anti-skate / tonearm balance side-lug
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFE5E9F1), Color(0xFF7B8393)),
                startY = pivot.y - minPlinth * 0.013f,
                endY = pivot.y + minPlinth * 0.013f,
            ),
            topLeft = Offset(pivot.x - yokeR * 1.18f, pivot.y - minPlinth * 0.012f),
            size = Size(yokeR * 2.36f, minPlinth * 0.024f),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
        )
        // Left & right bearing screw heads on gimbal bridge
        listOf(-yokeR * 0.94f, yokeR * 0.94f).forEach { dx ->
            val sx = pivot.x + dx
            drawCircle(
                color = Color(0xFF3E4554),
                radius = 2.3.dp.toPx(),
                center = Offset(sx, pivot.y),
            )
            drawLine(
                color = Color(0xFFCFD6E2),
                start = Offset(sx - 1.3.dp.toPx(), pivot.y),
                end = Offset(sx + 1.3.dp.toPx(), pivot.y),
                strokeWidth = 0.8.dp.toPx(),
            )
        }

        // Stepped aluminum U-yoke housing around center bearing
        drawCircle(
            brush = Brush.radialGradient(
                colors = if (palette.isDark) {
                    listOf(Color(0xFF566074), Color(0xFF2B3240), Color(0xFF191D25))
                } else {
                    listOf(Color(0xFFFFFFFF), Color(0xFFE4E8F0), Color(0xFFB8C0CF))
                },
                center = Offset(pivot.x - yokeR * 0.25f, pivot.y - yokeR * 0.25f),
                radius = yokeR * 1.2f,
            ),
            radius = yokeR,
            center = pivot,
        )
        drawCircle(
            color = if (palette.isDark) Color(0xFF141820) else Color(0xFF9AA2B2),
            radius = yokeR * 0.96f,
            center = pivot,
            style = Stroke(width = 1.dp.toPx()),
        )
        // Inner anodized turret crown + center brass/steel pivot jewel screw
        drawCircle(
            brush = Brush.radialGradient(
                colors = if (palette.isDark) {
                    listOf(Color(0xFF384050), Color(0xFF1C2029))
                } else {
                    listOf(Color(0xFFF6F8FC), Color(0xFFD2D7E3))
                },
                center = Offset(pivot.x - 1.5.dp.toPx(), pivot.y - 1.5.dp.toPx()),
                radius = yokeR * 0.7f,
            ),
            radius = yokeR * 0.65f,
            center = pivot,
        )
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFFFFFFF), Color(0xFF6B7384)),
                start = Offset(pivot.x - 3.dp.toPx(), pivot.y - 3.dp.toPx()),
                end = Offset(pivot.x + 3.dp.toPx(), pivot.y + 3.dp.toPx()),
            ),
            radius = yokeR * 0.24f,
            center = pivot,
        )

        // 4. Tapered Titanium Exit Collar transitioning gimbal into the carbon wand
        val exitTop = pivot.y + yokeR * 0.72f
        val exitH = minPlinth * 0.042f
        drawRoundRect(
            brush = Brush.horizontalGradient(
                0.00f to Color(0xFF6B7383),
                0.35f to Color(0xFFF3F6FA),
                0.70f to Color(0xFF9EA6B6),
                1.00f to Color(0xFF535A68),
                startX = pivot.x - wandW * 0.8f,
                endX = pivot.x + wandW * 0.8f,
            ),
            topLeft = Offset(pivot.x - wandW * 0.8f, exitTop),
            size = Size(wandW * 1.6f, exitH),
            cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()),
        )

        // 5. Parallel Chrome Anti-Skate Guide Wire / Rod beside upper tube
        val guideX = pivot.x + minPlinth * 0.025f
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(Color(0xFF7D8596), Color(0xFFF8FAFD), Color(0xFF6E7687)),
                startX = guideX - 1.dp.toPx(),
                endX = guideX + 1.dp.toPx(),
            ),
            start = Offset(guideX, pivot.y + yokeR * 0.6f),
            end = Offset(guideX, pivot.y + minPlinth * 0.245f),
            strokeWidth = 1.7.dp.toPx(),
            cap = StrokeCap.Round,
        )

        // 6. 3D Shaded Cylindrical Matte-Black Carbon Tonearm Tube
        val tubeStartY = exitTop + exitH * 0.85f
        val tubeLen = (wandEnd.y - tubeStartY).coerceAtLeast(1f)
        drawRect(
            brush = Brush.horizontalGradient(
                0.00f to Color(0xFF282D37),
                0.24f to Color(0xFF5A6375), // cylindrical top-left sheen along tube length
                0.48f to Color(0xFF15181F),
                0.82f to Color(0xFF0A0C10), // core shadow side
                1.00f to Color(0xFF222731), // subtle rim bounce
                startX = pivot.x - wandW / 2f,
                endX = pivot.x + wandW / 2f,
            ),
            topLeft = Offset(pivot.x - wandW / 2f, tubeStartY),
            size = Size(wandW, tubeLen),
        )

        // 7. Mid-Lower Machined Aluminum Locking Clamp Collar (with knurled bevel bands)
        val sleeveY = pivot.y + wandLen * 0.67f
        val sleeveW = wandW * 1.52f
        val sleeveH = minPlinth * 0.042f
        drawRoundRect(
            brush = Brush.horizontalGradient(
                0.00f to Color(0xFF676F7E),
                0.32f to Color(0xFFFFFFFF),
                0.65f to Color(0xFF9EA6B6),
                1.00f to Color(0xFF4C5361),
                startX = pivot.x - sleeveW / 2f,
                endX = pivot.x + sleeveW / 2f,
            ),
            topLeft = Offset(pivot.x - sleeveW / 2f, sleeveY),
            size = Size(sleeveW, sleeveH),
            cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()),
        )
        drawLine(
            color = Color(0xFF1D2129).copy(alpha = 0.55f),
            start = Offset(pivot.x - sleeveW * 0.48f, sleeveY + sleeveH * 0.5f),
            end = Offset(pivot.x + sleeveW * 0.48f, sleeveY + sleeveH * 0.5f),
            strokeWidth = 0.8.dp.toPx(),
        )

        // 8. Angled 3D Headshell, Knurled Bayonet Lock, Slotted Cartridge Mount, Finger Lift & Stylus
        rotate(degrees = hsEndAngleDeg, pivot = wandEnd) {
            // Knurled silver bayonet coupling nut
            val nutW = wandW * 1.58f
            val nutH = 5.5.dp.toPx()
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    0.00f to Color(0xFF687080),
                    0.32f to Color(0xFFFFFFFF),
                    0.68f to Color(0xFFA0A8B8),
                    1.00f to Color(0xFF4E5563),
                    startX = wandEnd.x - nutW / 2f,
                    endX = wandEnd.x + nutW / 2f,
                ),
                topLeft = Offset(wandEnd.x - nutW / 2f, wandEnd.y - 2.dp.toPx()),
                size = Size(nutW, nutH),
                cornerRadius = CornerRadius(1.2.dp.toPx(), 1.2.dp.toPx()),
            )
            // Knurl ring line
            drawLine(
                color = Color(0xFF2A303B).copy(alpha = 0.45f),
                start = Offset(wandEnd.x - nutW * 0.46f, wandEnd.y + 0.7.dp.toPx()),
                end = Offset(wandEnd.x + nutW * 0.46f, wandEnd.y + 0.7.dp.toPx()),
                strokeWidth = 0.8.dp.toPx(),
            )

            // Sculpted beveled matte-black headshell body
            val hsW = wandW * 2.15f
            val hsTop = wandEnd.y + 2.8.dp.toPx()
            val hsH = minPlinth * 0.088f
            val hsPath = Path().apply {
                moveTo(wandEnd.x - hsW * 0.42f, hsTop)
                lineTo(wandEnd.x + hsW * 0.42f, hsTop)
                lineTo(wandEnd.x + hsW * 0.52f, hsTop + hsH * 0.22f)
                lineTo(wandEnd.x + hsW * 0.45f, hsTop + hsH)
                lineTo(wandEnd.x - hsW * 0.45f, hsTop + hsH)
                lineTo(wandEnd.x - hsW * 0.52f, hsTop + hsH * 0.22f)
                close()
            }
            drawPath(
                path = hsPath,
                brush = Brush.horizontalGradient(
                    colors = listOf(Color(0xFF2B303B), Color(0xFF161920), Color(0xFF0A0C10)),
                    startX = wandEnd.x - hsW * 0.52f,
                    endX = wandEnd.x + hsW * 0.52f,
                ),
            )
            // Crisp specular edge bevel on headshell top & left chamfer
            drawPath(
                path = hsPath,
                color = Color(0xFF4F5768),
                style = Stroke(width = 0.9.dp.toPx(), join = StrokeJoin.Round),
            )

            // Dual machined mounting slots & tiny silver cartridge screws on headshell top
            listOf(-hsW * 0.17f, hsW * 0.17f).forEach { sx ->
                val slotX = wandEnd.x + sx
                drawLine(
                    color = Color(0xFF060709),
                    start = Offset(slotX, hsTop + hsH * 0.24f),
                    end = Offset(slotX, hsTop + hsH * 0.74f),
                    strokeWidth = 1.2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFFFFFFF), Color(0xFF7D8596)),
                        center = Offset(slotX - 0.5.dp.toPx(), hsTop + hsH * 0.46f),
                        radius = 1.6.dp.toPx(),
                    ),
                    radius = 1.35.dp.toPx(),
                    center = Offset(slotX, hsTop + hsH * 0.48f),
                )
            }

            // Curved 3D Chrome Finger-Lift Hook extending gracefully right
            val liftPath = Path().apply {
                moveTo(wandEnd.x + hsW * 0.45f, hsTop + hsH * 0.55f)
                quadraticTo(
                    wandEnd.x + hsW * 0.88f,
                    hsTop + hsH * 0.52f,
                    wandEnd.x + hsW * 1.05f,
                    hsTop + hsH * 0.34f,
                )
            }
            drawPath(
                path = liftPath,
                color = Color.Black.copy(alpha = shadowAlphaBase),
                style = Stroke(width = 2.0.dp.toPx(), cap = StrokeCap.Round),
            )
            drawPath(
                path = liftPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFB5BDCB), Color(0xFFFFFFFF)),
                    start = Offset(wandEnd.x + hsW * 0.45f, hsTop + hsH * 0.55f),
                    end = Offset(wandEnd.x + hsW * 1.05f, hsTop + hsH * 0.34f),
                ),
                style = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round),
            )

            // Visible Cartridge Nose Block, Cantilever Needle & Stylus Tip
            val cartW = hsW * 0.56f
            val cartH = minPlinth * 0.018f
            val cartTop = hsTop + hsH - 0.5.dp.toPx()
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color(0xFF9EA6B5), Color(0xFFE7EBF3), Color(0xFF6B7383)),
                    startX = wandEnd.x - cartW / 2f,
                    endX = wandEnd.x + cartW / 2f,
                ),
                topLeft = Offset(wandEnd.x - cartW / 2f, cartTop),
                size = Size(cartW, cartH),
                cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
            )
            val cantileverEnd = Offset(wandEnd.x, cartTop + cartH + 2.8.dp.toPx())
            drawLine(
                color = Color(0xFFD5DCE7),
                start = Offset(wandEnd.x, cartTop + cartH),
                end = cantileverEnd,
                strokeWidth = 1.1.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = if (isPlaying) Color(0xFFD9724E) else Color(0xFFA7B0C0),
                radius = 1.7.dp.toPx(),
                center = cantileverEnd,
            )
        }
    }
}
