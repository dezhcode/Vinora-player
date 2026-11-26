package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.audio.FeedbackEvent
import com.example.audio.PlinthFeedback
import com.example.data.GraphicsQuality
import com.example.ui.theme.LocalPlinthColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * High-resolution rasterized Turntable Deck compositing studio-quality pre-rendered WebP layers
 * (`img_turntable_base_*`, `img_turntable_vinyl`, `img_turntable_tonearm`, `img_turntable_tonearm_shadow`)
 * with GPU-accelerated `graphicsLayer` rotation, groove-tracking tonearm kinematics, and tactile controls.
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
    val cdPlayPause = if (isPlaying) stringResource(R.string.cd_pause) else stringResource(R.string.cd_play)
    val cdVolume = stringResource(R.string.cd_volume)

    val latestOnTogglePlay by rememberUpdatedState(onTogglePlayPause)
    val latestOnScrubDeltaMs by rememberUpdatedState(onScrubDeltaMs)
    val latestOnVolumeChange by rememberUpdatedState(onVolumeChange)
    val latestVolume by rememberUpdatedState(volume)

    var discAngleDeg by remember { mutableFloatStateOf(0f) }
    var isScrubbingVinyl by remember { mutableStateOf(false) }
    var swapSweepBoost by remember { mutableFloatStateOf(0f) }
    var lastHandledNonce by remember { mutableIntStateOf(trackChangeNonce) }
    var lastDetentDeg by remember { mutableFloatStateOf(0f) }

    val leftInteraction = remember { MutableInteractionSource() }
    val rightInteraction = remember { MutableInteractionSource() }
    val leftKnobPressed by leftInteraction.collectIsPressedAsState()
    val rightKnobPressed by rightInteraction.collectIsPressedAsState()

    // Subtle tonearm lift & return cue when skipping tracks
    LaunchedEffect(trackChangeNonce) {
        if (trackChangeNonce != lastHandledNonce) {
            lastHandledNonce = trackChangeNonce
            swapSweepBoost = if (trackChangeDirection >= 0) 1f else -1f
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

    val targetArmAngleDeg = remember(progressFraction, swapSweepBoost) {
        val baseAngle = TurntableGeometrySpec.computeGrooveAngleDeg(progressFraction)
        if (abs(swapSweepBoost) > 0.5f) {
            baseAngle - 2.2f
        } else {
            baseAngle
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

    val isTonearmLifted = abs(swapSweepBoost) > 0.5f || (!isPlaying && !isScrubbingVinyl)
    val liftFraction by animateFloatAsState(
        targetValue = if (isTonearmLifted) 1f else 0f,
        animationSpec = tween(durationMillis = if (reducedMotion) 120 else 240),
        label = "tonearmLift",
    )

    val leftKnobScale by animateFloatAsState(
        targetValue = if (leftKnobPressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium),
        label = "leftKnobScale",
    )
    val rightKnobScale by animateFloatAsState(
        targetValue = if (rightKnobPressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium),
        label = "rightKnobScale",
    )

    val highQualityBlur = !batterySaverMode && graphicsQuality != GraphicsQuality.LOW

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .testTag("turntable_deck")
            .semantics { contentDescription = cdTurntableText },
    ) {
        val sideDp = minOf(maxWidth, maxHeight)

        val vinylBoxDp = sideDp * TurntableGeometrySpec.VINYL_ASSET_BOX_RATIO
        val vinylTopLeftX = sideDp * TurntableGeometrySpec.PLATTER_CX - vinylBoxDp / 2f
        val vinylTopLeftY = sideDp * TurntableGeometrySpec.PLATTER_CY - vinylBoxDp / 2f
        val labelDiaDp = sideDp * TurntableGeometrySpec.LABEL_TO_DECK_RATIO

        val tonearmScaleToDeck = TurntableGeometrySpec.EFFECTIVE_LENGTH_RATIO /
            TurntableGeometrySpec.TONEARM_SRC_EFF_LEN_PX
        val tonearmW = sideDp * (TurntableGeometrySpec.TONEARM_IMG_WIDTH * tonearmScaleToDeck)
        val tonearmH = sideDp * (TurntableGeometrySpec.TONEARM_IMG_HEIGHT * tonearmScaleToDeck)
        val tonearmLeftX = sideDp * TurntableGeometrySpec.PIVOT_CX -
            tonearmW * TurntableGeometrySpec.TONEARM_PIVOT_NORM_X
        val tonearmTopY = sideDp * TurntableGeometrySpec.PIVOT_CY -
            tonearmH * TurntableGeometrySpec.TONEARM_PIVOT_NORM_Y

        val leftKnobTapSize = maxOf(48.dp, sideDp * (TurntableGeometrySpec.LEFT_KNOB_R * 2.2f))
        val rightKnobTapSize = maxOf(48.dp, sideDp * (TurntableGeometrySpec.RIGHT_KNOB_R * 2.2f))

        Box(
            modifier = Modifier
                .size(sideDp)
                // Tap vinyl disc to toggle Play / Pause
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val side = minOf(size.width, size.height).toFloat().coerceAtLeast(1f)
                        val cx = side * TurntableGeometrySpec.PLATTER_CX
                        val cy = side * TurntableGeometrySpec.PLATTER_CY
                        val vinylR = side * TurntableGeometrySpec.VINYL_RADIUS_RATIO
                        if (hypot(offset.x - cx, offset.y - cy) <= vinylR * 1.04f) {
                            PlinthFeedback.trigger(FeedbackEvent.PLAY_PAUSE)
                            latestOnTogglePlay()
                        }
                    }
                }
                // Smooth rotational vinyl scratching
                .pointerInput(Unit) {
                    var lastTouchAngle = 0f
                    detectDragGestures(
                        onDragStart = { start ->
                            val side = minOf(size.width, size.height).toFloat().coerceAtLeast(1f)
                            val cx = side * TurntableGeometrySpec.PLATTER_CX
                            val cy = side * TurntableGeometrySpec.PLATTER_CY
                            val vinylR = side * TurntableGeometrySpec.VINYL_RADIUS_RATIO
                            if (hypot(start.x - cx, start.y - cy) <= vinylR * 1.06f) {
                                isScrubbingVinyl = true
                                lastTouchAngle = atan2(start.y - cy, start.x - cx)
                                lastDetentDeg = discAngleDeg
                            }
                        },
                        onDrag = { change, _ ->
                            if (!isScrubbingVinyl) return@detectDragGestures
                            change.consume()
                            val side = minOf(size.width, size.height).toFloat().coerceAtLeast(1f)
                            val cx = side * TurntableGeometrySpec.PLATTER_CX
                            val cy = side * TurntableGeometrySpec.PLATTER_CY
                            val angle = atan2(change.position.y - cy, change.position.x - cx)
                            var delta = angle - lastTouchAngle
                            if (delta > PI.toFloat()) delta -= (2.0 * PI).toFloat()
                            if (delta < -PI.toFloat()) delta += (2.0 * PI).toFloat()
                            lastTouchAngle = angle

                            val deltaDeg = Math.toDegrees(delta.toDouble()).toFloat()
                            discAngleDeg = (discAngleDeg + deltaDeg) % 360f
                            if (abs(discAngleDeg - lastDetentDeg) >= 24f) {
                                lastDetentDeg = discAngleDeg
                                PlinthFeedback.triggerSliderDetent(isMajorNotch = false)
                            }
                            val deltaMs = ((delta / (2.0 * PI)) * 5500.0).roundToInt().toLong()
                            if (deltaMs != 0L) latestOnScrubDeltaMs(deltaMs)
                        },
                        onDragEnd = { isScrubbingVinyl = false },
                        onDragCancel = { isScrubbingVinyl = false },
                    )
                },
        ) {
            // 1. Studio soft ambient & cast chassis shadow behind the plinth
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawChassisStudioShadow(palette = palette, highQualityBlur = highQualityBlur)
            }

            // 2. High-res Photorealistic Plinth Chassis Image (Light / Dark Titanium)
            Image(
                painter = painterResource(
                    id = if (palette.isDark) R.drawable.img_turntable_base_dark else R.drawable.img_turntable_base_light,
                ),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )

            // Subtle tactile press dimming & scale ring feedback over Left & Right dials
            if (leftKnobPressed || rightKnobPressed) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val side = size.minDimension
                    if (leftKnobPressed) {
                        val c = Offset(side * TurntableGeometrySpec.LEFT_KNOB_CX, side * TurntableGeometrySpec.LEFT_KNOB_CY)
                        val r = side * TurntableGeometrySpec.LEFT_KNOB_R * leftKnobScale
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.16f), Color.Transparent),
                                center = c,
                                radius = r,
                            ),
                            radius = r,
                            center = c,
                        )
                    }
                    if (rightKnobPressed) {
                        val c = Offset(side * TurntableGeometrySpec.RIGHT_KNOB_CX, side * TurntableGeometrySpec.RIGHT_KNOB_CY)
                        val r = side * TurntableGeometrySpec.RIGHT_KNOB_R * rightKnobScale
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.16f), Color.Transparent),
                                center = c,
                                radius = r,
                            ),
                            radius = r,
                            center = c,
                        )
                    }
                }
            }

            // 3. Spinning 33⅓ RPM Photorealistic Vinyl Disc + Center Album Art Label
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset(x = vinylTopLeftX, y = vinylTopLeftY)
                    .size(vinylBoxDp)
                    .graphicsLayer { rotationZ = discAngleDeg },
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_turntable_vinyl),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                )
                if (!artworkUri.isNullOrBlank()) {
                    AsyncImage(
                        model = artworkUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(labelDiaDp)
                            .clip(CircleShape),
                    )
                    Canvas(modifier = Modifier.size(labelDiaDp)) {
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.25f),
                            radius = size.minDimension * 0.495f,
                            style = Stroke(width = 1.dp.toPx()),
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.14f),
                            radius = size.minDimension * 0.23f,
                            style = Stroke(width = 0.75.dp.toPx()),
                        )
                    }
                }
            }

            // 4. Stationary Studio Reflection Overlay + Center Spindle Cap (NEVER rotates)
            Canvas(
                modifier = Modifier
                    .offset(x = vinylTopLeftX, y = vinylTopLeftY)
                    .size(vinylBoxDp),
            ) {
                drawStationaryVinylSheenAndSpindle(palette = palette)
            }

            // 5. Accessible 48dp+ Interactive Touch Targets for Left Y-Knob & Right Circular Knob
            Box(
                modifier = Modifier
                    .offset(
                        x = sideDp * TurntableGeometrySpec.LEFT_KNOB_CX - leftKnobTapSize / 2f,
                        y = sideDp * TurntableGeometrySpec.LEFT_KNOB_CY - leftKnobTapSize / 2f,
                    )
                    .size(leftKnobTapSize)
                    .testTag("turntable_left_knob")
                    .clickable(
                        interactionSource = leftInteraction,
                        indication = null,
                        role = Role.Button,
                        onClickLabel = cdPlayPause,
                    ) {
                        PlinthFeedback.trigger(FeedbackEvent.PLAY_PAUSE)
                        latestOnTogglePlay()
                    }
                    .semantics { contentDescription = cdVolume }
                    .pointerInput(Unit) {
                        var startVol = latestVolume
                        var accY = 0f
                        detectDragGestures(
                            onDragStart = {
                                startVol = latestVolume
                                accY = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                accY += dragAmount.y
                                val nextVol = (startVol - accY / 160f).coerceIn(0f, 1f)
                                if ((nextVol * 20f).roundToInt() != (latestVolume * 20f).roundToInt()) {
                                    PlinthFeedback.triggerSliderDetent(isMajorNotch = false)
                                }
                                latestOnVolumeChange(nextVol)
                            },
                        )
                    },
            )

            Box(
                modifier = Modifier
                    .offset(
                        x = sideDp * TurntableGeometrySpec.RIGHT_KNOB_CX - rightKnobTapSize / 2f,
                        y = sideDp * TurntableGeometrySpec.RIGHT_KNOB_CY - rightKnobTapSize / 2f,
                    )
                    .size(rightKnobTapSize)
                    .testTag("turntable_right_knob")
                    .clickable(
                        interactionSource = rightInteraction,
                        indication = null,
                        role = Role.Button,
                        onClickLabel = cdPlayPause,
                    ) {
                        PlinthFeedback.trigger(FeedbackEvent.PLAY_PAUSE)
                        latestOnTogglePlay()
                    }
                    .semantics { contentDescription = cdPlayPause },
            )

            // 6. Synchronized 3D Tonearm Cast Shadow Layer
            Image(
                painter = painterResource(id = R.drawable.img_turntable_tonearm_shadow),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .offset(x = tonearmLeftX, y = tonearmTopY)
                    .size(width = tonearmW, height = tonearmH)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(
                            TurntableGeometrySpec.TONEARM_PIVOT_NORM_X,
                            TurntableGeometrySpec.TONEARM_PIVOT_NORM_Y,
                        )
                        rotationZ = armSweepDeg
                        translationX = (2.6f + liftFraction * 3.2f).dp.toPx()
                        translationY = (4.2f + liftFraction * 3.8f).dp.toPx()
                        alpha = if (isTonearmLifted) 0.72f else 0.94f
                    },
            )

            // 7. High-Resolution 3D Articulated Tonearm Image Rotating Around Exact Gimbal Pivot
            Image(
                painter = painterResource(id = R.drawable.img_turntable_tonearm),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .offset(x = tonearmLeftX, y = tonearmTopY)
                    .size(width = tonearmW, height = tonearmH)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(
                            TurntableGeometrySpec.TONEARM_PIVOT_NORM_X,
                            TurntableGeometrySpec.TONEARM_PIVOT_NORM_Y,
                        )
                        rotationZ = armSweepDeg
                        val liftScale = 1f + liftFraction * 0.014f
                        scaleX = liftScale
                        scaleY = liftScale
                    },
            )
        }
    }
}
