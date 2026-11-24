package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.R
import com.example.ui.theme.LocalPlinthColors
import com.example.ui.theme.StylusCopper
import com.example.ui.theme.TonearmCarbon
import com.example.ui.theme.TonearmHighlight
import com.example.ui.theme.VinylObsidianCore
import com.example.ui.theme.VinylObsidianEdge
import com.example.ui.theme.VinylObsidianMid
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private const val STANDARD_33_RPM_DEG_PER_SEC = 200f // 33.333 RPM * 360 / 60

/**
 * Interactive top-down Braun-inspired luxury vinyl turntable deck.
 * Independent animated layers:
 * 1. Brushed anodized aluminum/titanium plinth + brass RCA jacks + tactile corner knobs
 * 2. 33⅓ RPM physics-driven obsidian vinyl disc with concentric grooves, fixed-light anisotropic sheen,
 *    rotating cover label, chrome spindle, circular touch scratch/scrub, and track swap animation
 * 3. Independent mechanical gimbal tonearm with lift/drop kinematics and live groove tracking
 */
@Composable
fun TurntableDeck(
    artworkUri: String?,
    isPlaying: Boolean,
    progressFraction: Float,
    volume: Float,
    trackChangeNonce: Int,
    trackChangeDirection: Int,
    hapticsEnabled: Boolean,
    reducedMotion: Boolean,
    onTogglePlayPause: () -> Unit,
    onScrubDeltaMs: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPlinthColors.current
    val cdTurntableText = stringResource(R.string.cd_turntable)
    val cdAlbumArtText = stringResource(R.string.cd_album_art)
    val haptic = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var vinylAngle by remember { mutableFloatStateOf(0f) }
    var angularVelocity by remember { mutableFloatStateOf(0f) }
    var isScratching by remember { mutableStateOf(false) }

    // Track change platter slide/swap animation
    val swapOffset = remember { Animatable(0f) }
    val swapAlpha = remember { Animatable(1f) }
    LaunchedEffect(trackChangeNonce) {
        if (trackChangeNonce > 0 && !reducedMotion) {
            swapOffset.snapTo(trackChangeDirection * 0.28f)
            swapAlpha.snapTo(0.25f)
            swapOffset.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
            )
            swapAlpha.animateTo(1f, tween(220))
        }
    }

    // Physics-based 33⅓ RPM turntable motor spin-up & coast-down loop (lifecycle-aware!)
    LaunchedEffect(isPlaying, isScratching, reducedMotion, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var lastNanos = 0L
            while (true) {
                withFrameNanos { frameNanos ->
                    if (lastNanos != 0L) {
                        val dt = ((frameNanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                        val targetSpeed = when {
                            isScratching -> 0f
                            !isPlaying -> 0f
                            reducedMotion -> 36f
                            else -> STANDARD_33_RPM_DEG_PER_SEC
                        }
                        val accelRate = if (targetSpeed > angularVelocity) 3.4f else 2.2f
                        angularVelocity += (targetSpeed - angularVelocity) * (dt * accelRate).coerceAtMost(1f)
                        if (kotlin.math.abs(angularVelocity) < 0.25f && targetSpeed == 0f) {
                            angularVelocity = 0f
                        }
                        if (angularVelocity > 0f) {
                            vinylAngle = (vinylAngle + angularVelocity * dt) % 360f
                        }
                    }
                    lastNanos = frameNanos
                }
                if (!isPlaying && !isScratching && angularVelocity == 0f) break
            }
        }
    }

    // Mechanical Tonearm Kinematics:
    // Stopped at start -> parked at 0° on armrest; Playing -> 19° (outer groove) to 39.5° (inner groove)
    val armEngaged = isPlaying || progressFraction > 0.005f || isScratching
    val targetArmAngle = if (armEngaged) {
        19f + progressFraction.coerceIn(0f, 1f) * 20.5f
    } else {
        0f
    }
    val armAngle by animateFloatAsState(
        targetValue = targetArmAngle,
        animationSpec = spring(
            dampingRatio = 0.76f,
            stiffness = Spring.StiffnessLow,
        ),
        label = "tonearmAngle",
    )

    // Arm lift height: 1f = lifted off groove (paused/resting), 0f = seated smoothly on vinyl
    val armLift by animateFloatAsState(
        targetValue = if (isPlaying || isScratching) 0f else 1f,
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "tonearmLift",
    )

    val plinthCorner = 24.dp

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(1f)
            .testTag("turntable_deck")
            .semantics { contentDescription = cdTurntableText },
        contentAlignment = Alignment.Center,
    ) {
        val deckSizePx = constraints.maxWidth.toFloat()
        val vinylCenterX = deckSizePx * 0.475f
        val vinylCenterY = deckSizePx * 0.515f
        val vinylRadiusPx = deckSizePx * 0.392f
        val labelFraction = 0.335f
        val labelDiameterDp = maxWidth * (0.392f * 2f * labelFraction)

        // Outer Plinth Container with realistic top-edge brass RCA jacks & studio shadow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 4.dp)
                .plinthSoftShadow(
                    cornerRadius = plinthCorner,
                    shadowDark = palette.shadowDark,
                    shadowLight = palette.shadowLight,
                    elevation = 14.dp,
                    darkAlpha = if (palette.isDark) 0.8f else 0.44f,
                    lightAlpha = if (palette.isDark) 0.22f else 0.95f,
                ),
        ) {
            // Layer 1: Brushed Anodized Plinth Surface + RCA Connectors + Corner Controls
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(plinthCorner)),
            ) {
                val w = size.width
                val h = size.height

                // Brushed aluminum / anodized titanium base gradient
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(palette.plinthTop, palette.plinthBottom),
                        start = Offset(0f, 0f),
                        end = Offset(w, h),
                    ),
                    cornerRadius = CornerRadius(plinthCorner.toPx(), plinthCorner.toPx()),
                )

                // Authentic fine horizontal brushed metal micro-grain
                val grainColor = if (palette.isDark) Color.White else Color(0xFF4A5568)
                val lineCount = 86
                for (i in 0 until lineCount) {
                    val y = (h / lineCount) * i
                    val shimmer = if (i % 3 == 0) 0.032f else 0.014f
                    drawLine(
                        color = grainColor.copy(alpha = shimmer),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1f,
                    )
                }

                // Soft studio overhead corner light sweep on chassis
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (palette.isDark) 0.07f else 0.34f),
                            Color.Transparent,
                        ),
                        center = Offset(w * 0.16f, h * 0.14f),
                        radius = w * 0.72f,
                    )
                )

                // Recessed platter well shadow & machined silver collar
                val pCenter = Offset(w * 0.475f, h * 0.515f)
                val pRadius = w * 0.408f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = if (palette.isDark) 0.65f else 0.28f),
                            Color.Transparent,
                        ),
                        center = Offset(pCenter.x + w * 0.012f, pCenter.y + h * 0.02f),
                        radius = pRadius * 1.08f,
                    ),
                    radius = pRadius * 1.08f,
                    center = Offset(pCenter.x + w * 0.012f, pCenter.y + h * 0.02f),
                )
                drawCircle(
                    brush = Brush.linearGradient(
                        colors = listOf(palette.plinthEdgeShadow, palette.plinthEdgeLight),
                        start = Offset(pCenter.x - pRadius, pCenter.y - pRadius),
                        end = Offset(pCenter.x + pRadius, pCenter.y + pRadius),
                    ),
                    radius = pRadius,
                    center = pCenter,
                )

                // Machined inner chamfer border around chassis
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            palette.plinthEdgeLight.copy(alpha = 0.92f),
                            palette.plinthEdgeShadow.copy(alpha = 0.75f),
                        ),
                        start = Offset.Zero,
                        end = Offset(w, h),
                    ),
                    cornerRadius = CornerRadius(plinthCorner.toPx(), plinthCorner.toPx()),
                    style = Stroke(width = 2.2.dp.toPx()),
                )

                // Tiny top-edge brass RCA terminal pins (exact homage to reference deck!)
                val pinY = 1.5.dp.toPx()
                listOf(0.56f, 0.61f).forEach { rx ->
                    drawRoundRect(
                        color = palette.warmBrass,
                        topLeft = Offset(w * rx, 0f),
                        size = Size(5.dp.toPx(), 4.5.dp.toPx()),
                        cornerRadius = CornerRadius(2f, 2f),
                    )
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.35f),
                        radius = 1.2.dp.toPx(),
                        center = Offset(w * rx + 2.5.dp.toPx(), pinY),
                    )
                }

                // Bottom-left 3-spoke rotary control dial (volume/pitch gauge)
                val leftKnobCenter = Offset(w * 0.118f, h * 0.885f)
                val knobR = w * 0.064f
                drawCircle(
                    brush = Brush.linearGradient(
                        colors = listOf(palette.plinthEdgeLight, palette.plinthEdgeShadow),
                        start = Offset(leftKnobCenter.x - knobR, leftKnobCenter.y - knobR),
                        end = Offset(leftKnobCenter.x + knobR, leftKnobCenter.y + knobR),
                    ),
                    radius = knobR,
                    center = leftKnobCenter,
                )
                drawCircle(
                    color = palette.plinthTop,
                    radius = knobR * 0.91f,
                    center = leftKnobCenter,
                )
                drawCircle(
                    color = palette.inkSecondary.copy(alpha = 0.45f),
                    radius = knobR * 0.91f,
                    center = leftKnobCenter,
                    style = Stroke(width = 1.dp.toPx()),
                )
                // 3-spoke engraved dial lines rotated by volume level
                val dialAngle = -135f + volume * 270f
                rotate(dialAngle, pivot = leftKnobCenter) {
                    for (spoke in 0 until 3) {
                        val rad = (spoke * 120f - 90f) * (PI / 180.0)
                        drawLine(
                            color = palette.inkSecondary.copy(alpha = 0.68f),
                            start = leftKnobCenter,
                            end = Offset(
                                leftKnobCenter.x + (knobR * 0.86f * cos(rad)).toFloat(),
                                leftKnobCenter.y + (knobR * 0.86f * sin(rad)).toFloat(),
                            ),
                            strokeWidth = 1.15.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                    // Precision indicator dot on primary spoke
                    drawCircle(
                        color = palette.inkPrimary,
                        radius = 1.8.dp.toPx(),
                        center = Offset(leftKnobCenter.x, leftKnobCenter.y - knobR * 0.65f),
                    )
                }

                // Bottom-right circular start/stop metallic button
                val rightBtnCenter = Offset(w * 0.882f, h * 0.885f)
                drawCircle(
                    brush = Brush.linearGradient(
                        colors = listOf(palette.plinthEdgeLight, palette.plinthEdgeShadow),
                        start = Offset(rightBtnCenter.x - knobR, rightBtnCenter.y - knobR),
                        end = Offset(rightBtnCenter.x + knobR, rightBtnCenter.y + knobR),
                    ),
                    radius = knobR,
                    center = rightBtnCenter,
                )
                drawCircle(
                    color = palette.plinthTop,
                    radius = knobR * 0.9f,
                    center = rightBtnCenter,
                )
                drawCircle(
                    color = if (isPlaying) palette.inkPrimary.copy(alpha = 0.72f) else palette.inkSecondary.copy(alpha = 0.45f),
                    radius = knobR * 0.9f,
                    center = rightBtnCenter,
                    style = Stroke(width = 1.1.dp.toPx()),
                )
                // Subtle power LED pinhole indicator
                drawCircle(
                    color = if (isPlaying) palette.warmBrass else palette.inkMuted.copy(alpha = 0.55f),
                    radius = 2.dp.toPx(),
                    center = rightBtnCenter,
                )
            }

            // Invisible touch zones for bottom-left volume knob & bottom-right power start/stop
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 12.dp)
                    .size(54.dp)
                    .pointerInput(volume) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val next = (volume - dragAmount.y / 220f).coerceIn(0f, 1f)
                            onVolumeChange(next)
                        }
                    },
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 12.dp)
                    .size(54.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onTogglePlayPause()
                    },
            )

            // Layer 2: Glossy Obsidian Vinyl Record with Concentric Grooves, Fixed Light Sheen & Scratch Touch
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = swapOffset.value * deckSizePx
                        alpha = swapAlpha.value
                    }
                    .pointerInput(Unit) {
                        var lastAngleRad = 0f
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                val dx = startOffset.x - vinylCenterX
                                val dy = startOffset.y - vinylCenterY
                                val dist = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                                if (dist <= vinylRadiusPx * 1.04f && dist >= vinylRadiusPx * 0.16f) {
                                    isScratching = true
                                    lastAngleRad = atan2(dy, dx)
                                    if (hapticsEnabled) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                            },
                            onDrag = { change, _ ->
                                if (!isScratching) return@detectDragGestures
                                change.consume()
                                val pos = change.position
                                val curRad = atan2(pos.y - vinylCenterY, pos.x - vinylCenterX)
                                var deltaRad = curRad - lastAngleRad
                                if (deltaRad > PI) deltaRad -= (2 * PI).toFloat()
                                if (deltaRad < -PI) deltaRad += (2 * PI).toFloat()
                                lastAngleRad = curRad

                                val deltaDeg = (deltaRad * 180f / PI).toFloat()
                                vinylAngle = (vinylAngle + deltaDeg) % 360f
                                // Map one full clockwise turn to +5.5 seconds of seeking
                                val seekDeltaMs = ((deltaDeg / 360f) * 5500f).toLong()
                                if (seekDeltaMs != 0L) {
                                    onScrubDeltaMs(seekDeltaMs)
                                }
                            },
                            onDragEnd = { isScratching = false },
                            onDragCancel = { isScratching = false },
                        )
                    },
            ) {
                // Glossy Vinyl Platter Canvas
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width * 0.475f, size.height * 0.515f)
                    val r = size.width * 0.392f

                    // Deep obsidian vinyl body
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(VinylObsidianMid, VinylObsidianCore, VinylObsidianEdge),
                            center = center,
                            radius = r,
                        ),
                        radius = r,
                        center = center,
                    )

                    // Outer glossy vinyl bead lip
                    drawCircle(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.28f),
                                Color.Transparent,
                                Color.White.copy(alpha = 0.14f),
                            ),
                            start = Offset(center.x - r, center.y - r),
                            end = Offset(center.x + r, center.y + r),
                        ),
                        radius = r - 1.dp.toPx(),
                        center = center,
                        style = Stroke(width = 1.6.dp.toPx()),
                    )

                    // Concentric micro-grooves + track separation bands
                    val innerGrooveR = r * 0.39f
                    val outerGrooveR = r * 0.965f
                    val grooveCount = 68
                    val shimmerPhase = (vinylAngle * PI / 180.0)
                    for (g in 0 until grooveCount) {
                        val frac = g.toFloat() / grooveCount
                        val gr = innerGrooveR + (outerGrooveR - innerGrooveR) * frac
                        val isTrackBreak = g == 18 || g == 41 || g == 59
                        val wave = 0.5f + 0.5f * sin(g * 0.9 + shimmerPhase * 0.5).toFloat()
                        val grooveAlpha = when {
                            isTrackBreak -> 0.03f
                            g % 2 == 0 -> 0.09f + wave * 0.045f
                            else -> 0.045f + wave * 0.025f
                        }
                        drawCircle(
                            color = Color(0xFFD8E1F0).copy(alpha = grooveAlpha),
                            radius = gr,
                            center = center,
                            style = Stroke(width = if (isTrackBreak) 1.4f else 0.75f),
                        )
                    }

                    // Fixed-light-source anisotropic conical bowtie sheen (exact physics of glossy vinyl!)
                    val sheenIntensity = if (palette.isDark) 0.24f else 0.29f
                    drawCircle(
                        brush = Brush.sweepGradient(
                            0.00f to Color.Transparent,
                            0.06f to Color.White.copy(alpha = sheenIntensity * 0.35f),
                            0.11f to Color.White.copy(alpha = sheenIntensity),
                            0.15f to Color.White.copy(alpha = sheenIntensity * 0.22f),
                            0.24f to Color.Transparent,
                            0.50f to Color.Transparent,
                            0.56f to Color.White.copy(alpha = sheenIntensity * 0.32f),
                            0.61f to Color.White.copy(alpha = sheenIntensity * 0.95f),
                            0.65f to Color.White.copy(alpha = sheenIntensity * 0.2f),
                            0.74f to Color.Transparent,
                            1.00f to Color.Transparent,
                            center = center,
                        ),
                        radius = r * 0.96f,
                        center = center,
                    )

                    // Narrow secondary chromatic groove reflection line at fixed studio light angle
                    rotate(-34f, pivot = center) {
                        drawLine(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.14f),
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.14f),
                                    Color.Transparent,
                                ),
                                startX = center.x - r,
                                endX = center.x + r,
                            ),
                            start = Offset(center.x - r * 0.95f, center.y),
                            end = Offset(center.x + r * 0.95f, center.y),
                            strokeWidth = r * 0.14f,
                        )
                    }

                    // Run-out dead-wax glossy inner ring around center label
                    drawCircle(
                        color = Color(0xFF0A0C10),
                        radius = r * 0.375f,
                        center = center,
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.12f),
                        radius = r * 0.375f,
                        center = center,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }

                // Rotating Center Album Artwork Label (centered accurately on vinyl center)
                Box(
                    modifier = Modifier
                        .size(labelDiameterDp)
                        .graphicsLayer {
                            translationX = vinylCenterX - (labelDiameterDp.toPx() / 2f)
                            translationY = vinylCenterY - (labelDiameterDp.toPx() / 2f)
                            rotationZ = vinylAngle
                        }
                        .clip(CircleShape)
                        .background(Color(0xFFF3F4F6))
                        .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularArtworkDisc(
                        artworkUri = artworkUri,
                        contentDescription = cdAlbumArtText,
                        showInnerRing = false,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Pressed vinyl label crease + machined chrome spindle
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val c = Offset(size.width / 2f, size.height / 2f)
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.14f),
                            radius = size.minDimension * 0.24f,
                            center = c,
                            style = Stroke(width = 1.dp.toPx()),
                        )
                        // Spindle hole shadow
                        drawCircle(
                            color = Color(0xFF14161B),
                            radius = size.minDimension * 0.082f,
                            center = c,
                        )
                        // Machined chrome spindle pin
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFFFFFFFF), Color(0xFFB3BCCB), Color(0xFF565E6E)),
                                center = Offset(c.x - 1.5f, c.y - 1.5f),
                                radius = size.minDimension * 0.068f,
                            ),
                            radius = size.minDimension * 0.064f,
                            center = c,
                        )
                    }
                }
            }

            // Layer 3: Independent Mechanical Tonearm Assembly (Gimbal, Counterweight, Arm, Headshell & Stylus)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val pivot = Offset(w * 0.878f, h * 0.205f)
                val baseR = w * 0.072f

                // Recessed mounting plate beneath gimbal
                drawCircle(
                    brush = Brush.linearGradient(
                        colors = listOf(palette.plinthEdgeShadow, palette.plinthEdgeLight),
                        start = Offset(pivot.x - baseR * 1.25f, pivot.y - baseR * 1.25f),
                        end = Offset(pivot.x + baseR * 1.25f, pivot.y + baseR * 1.25f),
                    ),
                    radius = baseR * 1.22f,
                    center = pivot,
                )
                drawCircle(
                    color = palette.plinthTop,
                    radius = baseR * 1.12f,
                    center = pivot,
                )

                // Tiny Phillips mounting screws around tonearm plate
                listOf(-145f, -35f, 145f).forEach { deg ->
                    val rad = deg * (PI / 180.0)
                    val sx = pivot.x + (baseR * 1.02f * cos(rad)).toFloat()
                    val sy = pivot.y + (baseR * 1.02f * sin(rad)).toFloat()
                    drawCircle(color = palette.inkSecondary.copy(alpha = 0.65f), radius = 2.1.dp.toPx(), center = Offset(sx, sy))
                    drawCircle(color = Color.White.copy(alpha = 0.55f), radius = 0.9.dp.toPx(), center = Offset(sx - 0.5f, sy - 0.5f))
                }

                // Armrest clip dock on right edge
                val dockCenter = Offset(w * 0.892f, h * 0.435f)
                drawRoundRect(
                    color = palette.inkSecondary.copy(alpha = 0.48f),
                    topLeft = Offset(dockCenter.x - 5.dp.toPx(), dockCenter.y - 3.dp.toPx()),
                    size = Size(10.dp.toPx(), 6.dp.toPx()),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                )

                // Dynamic arm shadow offset responds realistically to armLift height!
                val shadowDx = (3.5f + armLift * 6.5f).dp.toPx()
                val shadowDy = (5.0f + armLift * 8.0f).dp.toPx()
                val armLength = h * 0.455f
                val headAngleOffsetX = -w * 0.042f
                val headLength = h * 0.088f

                // Cast Tonearm Shadow (moves & softens as arm lifts off the vinyl!)
                translate(left = shadowDx, top = shadowDy) {
                    rotate(degrees = armAngle, pivot = pivot) {
                        val shadowPath = Path().apply {
                            moveTo(pivot.x, pivot.y - h * 0.095f)
                            lineTo(pivot.x, pivot.y + armLength)
                            lineTo(pivot.x + headAngleOffsetX, pivot.y + armLength + headLength)
                        }
                        drawPath(
                            path = shadowPath,
                            color = Color.Black.copy(alpha = if (palette.isDark) 0.42f else (0.18f - armLift * 0.05f)),
                            style = Stroke(
                                width = (6.dp + (armLift * 2f).dp).toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                            ),
                        )
                    }
                }

                // Mechanical Tonearm Assembly rotated around true gimbal pivot
                rotate(degrees = armAngle, pivot = pivot) {
                    // Counterweight cylinder behind gimbal
                    val cwWidth = w * 0.058f
                    val cwHeight = h * 0.078f
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color(0xFF4A505D), Color(0xFFCFD6E2), Color(0xFF2B303B)),
                            startX = pivot.x - cwWidth / 2f,
                            endX = pivot.x + cwWidth / 2f,
                        ),
                        topLeft = Offset(pivot.x - cwWidth / 2f, pivot.y - baseR - cwHeight * 0.85f),
                        size = Size(cwWidth, cwHeight),
                        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                    )

                    // Rear rod connecting counterweight to gimbal
                    drawLine(
                        color = TonearmCarbon,
                        start = Offset(pivot.x, pivot.y - baseR * 0.9f),
                        end = Offset(pivot.x, pivot.y),
                        strokeWidth = 3.5.dp.toPx(),
                    )

                    // Gimbal cylindrical bearing housing
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFECEFF6), Color(0xFF9EA7B8), Color(0xFF535B6B)),
                            center = Offset(pivot.x - 2f, pivot.y - 2f),
                            radius = baseR * 0.82f,
                        ),
                        radius = baseR * 0.78f,
                        center = pivot,
                    )
                    drawCircle(
                        color = TonearmCarbon,
                        radius = baseR * 0.34f,
                        center = pivot,
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.6f),
                        radius = baseR * 0.1f,
                        center = Offset(pivot.x - baseR * 0.1f, pivot.y - baseR * 0.1f),
                    )

                    // Main tubular carbon/anodized tonearm wand
                    val elbow = Offset(pivot.x, pivot.y + armLength)
                    val tip = Offset(pivot.x + headAngleOffsetX, pivot.y + armLength + headLength)
                    drawLine(
                        color = TonearmCarbon,
                        start = Offset(pivot.x, pivot.y + baseR * 0.45f),
                        end = elbow,
                        strokeWidth = 4.6.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    // Metallic cylindrical sheen along tonearm wand
                    drawLine(
                        color = TonearmHighlight.copy(alpha = 0.85f),
                        start = Offset(pivot.x - 1.dp.toPx(), pivot.y + baseR * 0.48f),
                        end = Offset(elbow.x - 1.dp.toPx(), elbow.y - 2.dp.toPx()),
                        strokeWidth = 1.2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )

                    // Angled Headshell & Cartridge housing
                    val headPath = Path().apply {
                        moveTo(elbow.x - 3.dp.toPx(), elbow.y - 2.dp.toPx())
                        lineTo(elbow.x + 3.5.dp.toPx(), elbow.y + 1.dp.toPx())
                        lineTo(tip.x + 4.dp.toPx(), tip.y + 2.dp.toPx())
                        lineTo(tip.x - 5.dp.toPx(), tip.y - 3.dp.toPx())
                        close()
                    }
                    drawPath(headPath, color = TonearmCarbon)

                    // Precision finger-lift lever projecting off headshell
                    drawLine(
                        color = Color(0xFFB7C0D0),
                        start = Offset(tip.x + 2.dp.toPx(), tip.y - 6.dp.toPx()),
                        end = Offset(tip.x + 12.dp.toPx(), tip.y + 1.dp.toPx()),
                        strokeWidth = 1.6.dp.toPx(),
                        cap = StrokeCap.Round,
                    )

                    // Copper-red audiophile cartridge coil accent & diamond stylus needle tip
                    drawCircle(
                        color = StylusCopper,
                        radius = 2.6.dp.toPx(),
                        center = Offset(tip.x - 0.5.dp.toPx(), tip.y - 1.5.dp.toPx()),
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.9f),
                        radius = 1.1.dp.toPx(),
                        center = Offset(tip.x - 1.5.dp.toPx(), tip.y + 0.5.dp.toPx()),
                    )
                }
            }
        }
    }
}
