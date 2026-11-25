package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.audio.FeedbackEvent
import com.example.audio.PlinthFeedback
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * GPU-accelerated Pre-Rendered Layered Turntable Runtime.
 * Composites pre-rendered 3D Three.js WebP layers + JSON coordinate manifest using ONLY
 * hardware-accelerated `graphicsLayer` transforms (zero runtime Three.js, zero WebGL, zero per-frame Canvas redraws).
 */
@Composable
fun PreRenderedTurntableCompositor(
    assets: PreRenderedTurntableAssets,
    artworkUri: String?,
    isPlaying: Boolean,
    isTonearmLifted: Boolean,
    discAngleDeg: Float,
    armSweepDeg: Float,
    volume: Float,
    onTogglePlayPause: () -> Unit,
    onScrubStart: () -> Unit,
    onScrubDeltaDeg: (Float) -> Unit,
    onScrubDeltaMs: (Long) -> Unit,
    onScrubEnd: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val manifest = assets.manifest
    val geom = manifest.geometry
    val layers = manifest.layers

    val latestOnTogglePlay by rememberUpdatedState(onTogglePlayPause)
    val latestOnScrubStart by rememberUpdatedState(onScrubStart)
    val latestOnScrubDeltaDeg by rememberUpdatedState(onScrubDeltaDeg)
    val latestOnScrubDeltaMs by rememberUpdatedState(onScrubDeltaMs)
    val latestOnScrubEnd by rememberUpdatedState(onScrubEnd)
    val latestOnVolumeChange by rememberUpdatedState(onVolumeChange)
    val latestVolume by rememberUpdatedState(volume)

    var leftKnobPressed by remember { mutableStateOf(false) }
    var rightKnobPressed by remember { mutableStateOf(false) }
    var lastDetentDeg by remember { mutableFloatStateOf(0f) }

    val tonearmSelection by remember(armSweepDeg, geom.tonearmKeyframeAnglesDeg) {
        derivedStateOf {
            TurntableKinematics.selectTonearmKeyframe(
                targetAngleDeg = armSweepDeg,
                keyframeAnglesDeg = geom.tonearmKeyframeAnglesDeg,
            )
        }
    }

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier,
    ) {
        val aspect = manifest.aspectRatio.coerceAtLeast(0.5f)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .aspectRatio(aspect, matchHeightConstraintsFirst = maxHeight < maxWidth)
                .testTag("turntable_prerendered_layer_stack")
                // Immediate tactile pressed states for bottom hardware dials + tap actions
                .pointerInput(geom) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        val nx = down.position.x / w
                        val ny = down.position.y / h

                        val distLeft = hypot(nx - geom.leftKnobCenter.x, ny - geom.leftKnobCenter.y)
                        val distRight = hypot(nx - geom.rightKnobCenter.x, ny - geom.rightKnobCenter.y)
                        val distPlatter = hypot(nx - geom.vinylCenter.x, ny - geom.vinylCenter.y)

                        val hitLeft = distLeft <= geom.leftKnobRadius * 1.45f
                        val hitRight = distRight <= geom.rightKnobRadius * 1.45f
                        val hitVinyl = distPlatter <= geom.vinylOuterRadius * 1.02f

                        if (hitLeft) leftKnobPressed = true
                        if (hitRight) rightKnobPressed = true

                        var totalMove = 0f
                        var prevY = down.position.y
                        var volumeDragging = false
                        var startVol = latestVolume
                        var volumeAcc = 0f

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                if (totalMove < 14f && (hitLeft || hitRight || hitVinyl) && !volumeDragging) {
                                    PlinthFeedback.trigger(FeedbackEvent.PLAY_PAUSE)
                                    latestOnTogglePlay()
                                }
                                leftKnobPressed = false
                                rightKnobPressed = false
                                break
                            }
                            val dy = change.position.y - prevY
                            prevY = change.position.y
                            totalMove += hypot(
                                change.position.x - down.position.x,
                                change.position.y - down.position.y,
                            ).coerceAtLeast(abs(dy))

                            if (hitLeft && abs(change.position.y - down.position.y) > 10f) {
                                volumeDragging = true
                                change.consume()
                                volumeAcc -= dy / (h * 0.42f)
                                val nextVol = (startVol + volumeAcc).coerceIn(0f, 1f)
                                if ((nextVol * 20f).roundToInt() != (latestVolume * 20f).roundToInt()) {
                                    PlinthFeedback.triggerSliderDetent(isMajorNotch = false)
                                }
                                latestOnVolumeChange(nextVol)
                            }
                        }
                    }
                }
                // Smooth rotational vinyl scratching
                .pointerInput(geom) {
                    var isScrubbing = false
                    var lastTouchAngle = 0f
                    detectDragGestures(
                        onDragStart = { start ->
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            val cx = geom.vinylCenter.x * w
                            val cy = geom.vinylCenter.y * h
                            val dist = hypot(start.x - cx, start.y - cy)
                            if (dist <= geom.vinylOuterRadius * w * 1.05f) {
                                isScrubbing = true
                                lastTouchAngle = atan2(start.y - cy, start.x - cx)
                                lastDetentDeg = discAngleDeg
                                latestOnScrubStart()
                            }
                        },
                        onDrag = { change, _ ->
                            if (!isScrubbing) return@detectDragGestures
                            change.consume()
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            val cx = geom.vinylCenter.x * w
                            val cy = geom.vinylCenter.y * h
                            val angle = atan2(change.position.y - cy, change.position.x - cx)
                            var delta = angle - lastTouchAngle
                            if (delta > PI.toFloat()) delta -= (2.0 * PI).toFloat()
                            if (delta < -PI.toFloat()) delta += (2.0 * PI).toFloat()
                            lastTouchAngle = angle

                            val deltaDeg = Math.toDegrees(delta.toDouble()).toFloat()
                            latestOnScrubDeltaDeg(deltaDeg)
                            if (abs(discAngleDeg - lastDetentDeg) >= 24f) {
                                lastDetentDeg = discAngleDeg
                                PlinthFeedback.triggerSliderDetent(isMajorNotch = false)
                            }
                            val deltaMs = ((delta / (2.0 * PI)) * 5500.0).roundToInt().toLong()
                            if (deltaMs != 0L) latestOnScrubDeltaMs(deltaMs)
                        },
                        onDragEnd = {
                            if (isScrubbing) {
                                isScrubbing = false
                                latestOnScrubEnd()
                            }
                        },
                        onDragCancel = {
                            if (isScrubbing) {
                                isScrubbing = false
                                latestOnScrubEnd()
                            }
                        },
                    )
                },
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val deckW = maxWidth
                val deckH = maxHeight

                // 1. Static Chassis, Two-Tone Brushed/Matte Deck, Rear Hinges & Gimbal Basin
                Image(
                    bitmap = assets.turntableBase,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                )

                // 2. Machined Brushed Outer Platter Bevel Ring
                PositionedLayerImage(
                    bitmap = assets.metalPlatter,
                    bounds = layers.metalPlatter.bounds,
                    deckW = deckW,
                    deckH = deckH,
                )

                // 3. Bottom-Left & Bottom-Right Tactile Knobs (Normal / Pressed sprites)
                PositionedLayerImage(
                    bitmap = if (leftKnobPressed) assets.leftKnobPressed else assets.leftKnobNormal,
                    bounds = layers.leftKnobNormal.bounds,
                    deckW = deckW,
                    deckH = deckH,
                    modifier = Modifier.testTag("turntable_left_knob"),
                )
                PositionedLayerImage(
                    bitmap = if (rightKnobPressed) assets.rightKnobPressed else assets.rightKnobNormal,
                    bounds = layers.rightKnobNormal.bounds,
                    deckW = deckW,
                    deckH = deckH,
                    modifier = Modifier.testTag("turntable_right_knob"),
                )

                // 4. Spinning 33⅓ RPM Vinyl LP + Dynamic Center Label Artwork (GPU graphicsLayer only)
                val vinylBounds = layers.vinylRecord.bounds
                val labelDiameterDp = deckW * (geom.albumLabelRadius * 2f)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .offset(x = deckW * vinylBounds.x, y = deckH * vinylBounds.y)
                        .size(width = deckW * vinylBounds.width, height = deckH * vinylBounds.height)
                        .graphicsLayer {
                            rotationZ = discAngleDeg
                        },
                ) {
                    Image(
                        bitmap = assets.vinylRecord,
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
                                .size(labelDiameterDp)
                                .clip(CircleShape),
                        )
                        // Pressed stamper ring hairline over custom artwork
                        Canvas(modifier = Modifier.size(labelDiameterDp)) {
                            drawCircle(
                                color = Color.Black.copy(alpha = 0.28f),
                                radius = size.minDimension * 0.495f,
                                style = Stroke(width = 1.dp.toPx()),
                            )
                            drawCircle(
                                color = Color.White.copy(alpha = 0.14f),
                                radius = size.minDimension * 0.24f,
                                style = Stroke(width = 0.75.dp.toPx()),
                            )
                        }
                    }
                }

                // 5. Stationary Studio Specular Dual-Cone Reflection Overlay (NEVER rotates)
                PositionedLayerImage(
                    bitmap = assets.vinylReflection,
                    bounds = layers.vinylReflection.bounds,
                    deckW = deckW,
                    deckH = deckH,
                )

                // 6. Stationary Machined Stainless Center Spindle Cap
                PositionedLayerImage(
                    bitmap = assets.centerSpindle,
                    bounds = layers.centerSpindle.bounds,
                    deckW = deckW,
                    deckH = deckH,
                )

                // 7. Synchronized Multi-Angle Pre-Rendered Tonearm Shadow + Residual Pivot Rotation
                val shadowBounds = layers.tonearmShadow.bounds
                val shadowOrigin = layers.tonearmShadow.transformOrigin
                val shadowBmp = assets.tonearmShadowFrames[tonearmSelection.frameIndex.coerceIn(0, assets.tonearmShadowFrames.lastIndex)]
                Image(
                    bitmap = shadowBmp,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .offset(x = deckW * shadowBounds.x, y = deckH * shadowBounds.y)
                        .size(width = deckW * shadowBounds.width, height = deckH * shadowBounds.height)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(shadowOrigin.x, shadowOrigin.y)
                            rotationZ = tonearmSelection.residualRotationZDeg
                            if (isTonearmLifted) {
                                translationX = (-2.0f).dp.toPx()
                                translationY = 3.2f.dp.toPx()
                                alpha = 0.72f
                            } else {
                                alpha = 0.94f
                            }
                        },
                )

                // 8. Multi-Angle Pre-Rendered Articulated 3D Tonearm + Smooth Sub-Angle Tracking
                val armBounds = layers.tonearmArm.bounds
                val armOrigin = layers.tonearmArm.transformOrigin
                val armBmp = assets.tonearmArmFrames[tonearmSelection.frameIndex.coerceIn(0, assets.tonearmArmFrames.lastIndex)]
                Image(
                    bitmap = armBmp,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .offset(x = deckW * armBounds.x, y = deckH * armBounds.y)
                        .size(width = deckW * armBounds.width, height = deckH * armBounds.height)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(armOrigin.x, armOrigin.y)
                            rotationZ = tonearmSelection.residualRotationZDeg
                            if (isTonearmLifted && !isPlaying) {
                                scaleX = 1.006f
                                scaleY = 1.006f
                            }
                        },
                )
            }
        }
    }
}

@Composable
private fun PositionedLayerImage(
    bitmap: androidx.compose.ui.graphics.ImageBitmap,
    bounds: NormBounds,
    deckW: Dp,
    deckH: Dp,
    modifier: Modifier = Modifier,
) {
    Image(
        bitmap = bitmap,
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = modifier
            .offset(x = deckW * bounds.x, y = deckH * bounds.y)
            .size(width = deckW * bounds.width, height = deckH * bounds.height),
    )
}
