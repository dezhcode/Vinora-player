package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitDragOrCancellation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.audio.FeedbackEvent
import com.example.audio.PlinthFeedback
import com.example.data.Track
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.LocalPlinthColors
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Signature curved vertical orbital disc wheel inspired directly by the Explore reference.
 * All per-frame orbital positioning, scaling, tilt, and opacity execute in the GPU graphicsLayer
 * phase with zero recomposition per drag/fling frame.
 */
@Composable
fun CurvedDiscCarousel(
    tracks: List<Track>,
    activeTrackId: String?,
    isPlaying: Boolean,
    hapticsEnabled: Boolean,
    reducedMotion: Boolean,
    onSelectAndPlay: (Track) -> Unit,
    onFocusedTrackChange: (Track) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (tracks.isEmpty()) return

    val palette = LocalPlinthColors.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    val defaultInitialIdx = remember(tracks) {
        tracks.indexOfFirst { it.id == "studio_daylight" }
            .takeIf { it >= 0 }
            ?: (tracks.size / 2).coerceIn(0, tracks.lastIndex)
    }
    var savedSlotIndex by rememberSaveable { mutableIntStateOf(defaultInitialIdx) }
    val scrollIndex = remember { Animatable(savedSlotIndex.coerceIn(0, tracks.lastIndex).toFloat()) }
    var lastHapticIndex by remember { mutableIntStateOf(savedSlotIndex.coerceIn(0, tracks.lastIndex)) }

    val centerRounded by remember(tracks.size) {
        derivedStateOf { scrollIndex.value.roundToInt().coerceIn(0, tracks.lastIndex) }
    }

    LaunchedEffect(tracks.size) {
        val clamped = scrollIndex.value.coerceIn(0f, tracks.lastIndex.toFloat())
        if (clamped != scrollIndex.value) {
            scrollIndex.snapTo(clamped)
        }
    }

    // Fire crisp subtle tactile detent only when integer slot boundary crosses
    LaunchedEffect(centerRounded, tracks) {
        savedSlotIndex = centerRounded
        onFocusedTrackChange(tracks[centerRounded])
        if (centerRounded != lastHapticIndex) {
            lastHapticIndex = centerRounded
            PlinthFeedback.triggerSliderDetent(isMajorNotch = false)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("explore_curved_carousel")
            .pointerInput(tracks.size, reducedMotion) {
                val pxPerSlot = with(density) { 104.dp.toPx() }
                coroutineScope {
                    val gestureScope = this
                    while (true) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val tracker = VelocityTracker()
                            tracker.addPosition(down.uptimeMillis, down.position)
                            var overSlop = 0f
                            val dragStart = awaitVerticalTouchSlopOrCancellation(down.id) { change, over ->
                                change.consume()
                                overSlop = over
                            } ?: return@awaitEachGesture

                            var dragValue = (scrollIndex.value - overSlop / pxPerSlot)
                                .coerceIn(-0.35f, tracks.lastIndex + 0.35f)
                            gestureScope.launch {
                                scrollIndex.stop()
                                scrollIndex.updateBounds(null, null)
                                scrollIndex.snapTo(dragValue)
                            }

                            var pointerId = dragStart.id
                            while (true) {
                                val event = awaitDragOrCancellation(pointerId) ?: break
                                val dy = event.positionChange().y
                                event.consume()
                                tracker.addPosition(event.uptimeMillis, event.position)
                                dragValue = (dragValue - dy / pxPerSlot)
                                    .coerceIn(-0.35f, tracks.lastIndex + 0.35f)
                                val target = dragValue
                                gestureScope.launch {
                                    scrollIndex.snapTo(target)
                                }
                                pointerId = event.id
                            }

                            val velY = tracker.calculateVelocity().y
                            val indexVelocity = -velY / pxPerSlot
                            gestureScope.launch {
                                scrollIndex.updateBounds(
                                    lowerBound = 0f,
                                    upperBound = tracks.lastIndex.toFloat(),
                                )
                                if (abs(indexVelocity) > 0.55f) {
                                    scrollIndex.animateDecay(
                                        initialVelocity = indexVelocity,
                                        animationSpec = exponentialDecay(frictionMultiplier = 2.2f),
                                    )
                                }
                                val nearest = scrollIndex.value.roundToInt().coerceIn(0, tracks.lastIndex)
                                scrollIndex.animateTo(
                                    targetValue = nearest.toFloat(),
                                    animationSpec = spring(
                                        dampingRatio = if (reducedMotion) 1f else 0.78f,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                                )
                            }
                        }
                    }
                }
            },
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val centerY = heightPx * 0.49f

        val arcRadiusPx = (widthPx * 1.06f).coerceAtLeast(with(density) { 360.dp.toPx() })
        val angleStepDeg = 16.5f
        val baseDiscSize = 78.dp
        val baseDiscSizePx = with(density) { baseDiscSize.toPx() }
        val baseStartMarginPx = with(density) { 20.dp.toPx() }
        val rowHeight = 92.dp
        val rowHeightPx = with(density) { rowHeight.toPx() }
        val capsuleEndPad = with(density) { (baseStartMarginPx * 2f).toDp() }

        // Subtle atmospheric orbital guide arc on background
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pivotX = if (isRtl) {
                widthPx - baseStartMarginPx - baseDiscSizePx / 2f - arcRadiusPx
            } else {
                baseStartMarginPx + baseDiscSizePx / 2f + arcRadiusPx
            }
            drawCircle(
                color = palette.hairline.copy(alpha = if (palette.isDark) 0.22f else 0.45f),
                radius = arcRadiusPx,
                center = Offset(pivotX, centerY),
                style = Stroke(width = 1.dp.toPx()),
            )
        }

        val visibleRange = (centerRounded - 4).coerceAtLeast(0)..(centerRounded + 4).coerceAtMost(tracks.lastIndex)

        for (index in visibleRange) {
            val track = tracks[index]
            key(track.id) {
                val isCentered = index == centerRounded
                CurvedDiscSlot(
                    track = track,
                    index = index,
                    isCentered = isCentered,
                    distanceSlots = abs(index - centerRounded),
                    isTrackActive = track.id == activeTrackId && isPlaying,
                    hapticsEnabled = hapticsEnabled,
                    reducedMotion = reducedMotion,
                    isRtl = isRtl,
                    centerY = centerY,
                    arcRadiusPx = arcRadiusPx,
                    angleStepDeg = angleStepDeg,
                    baseDiscSize = baseDiscSize,
                    baseStartMarginPx = baseStartMarginPx,
                    rowHeight = rowHeight,
                    rowHeightPx = rowHeightPx,
                    capsuleEndPad = capsuleEndPad,
                    scrollValueProvider = { scrollIndex.value },
                    onSelectAndPlay = onSelectAndPlay,
                    onSnapToIndex = { targetIdx ->
                        scope.launch {
                            scrollIndex.animateTo(
                                targetValue = targetIdx.toFloat(),
                                animationSpec = spring(
                                    dampingRatio = 0.8f,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun CurvedDiscSlot(
    track: Track,
    index: Int,
    isCentered: Boolean,
    distanceSlots: Int,
    isTrackActive: Boolean,
    hapticsEnabled: Boolean,
    reducedMotion: Boolean,
    isRtl: Boolean,
    centerY: Float,
    arcRadiusPx: Float,
    angleStepDeg: Float,
    baseDiscSize: Dp,
    baseStartMarginPx: Float,
    rowHeight: Dp,
    rowHeightPx: Float,
    capsuleEndPad: Dp,
    scrollValueProvider: () -> Float,
    onSelectAndPlay: (Track) -> Unit,
    onSnapToIndex: (Int) -> Unit,
) {
    val palette = LocalPlinthColors.current
    val strings = LocalAppStrings.current
    val interactionSource = remember { MutableInteractionSource() }
    val capsuleShape = remember { RoundedCornerShape(41.dp) }
    val capsuleBorderBrush = remember(palette) {
        Brush.verticalGradient(
            listOf(
                palette.plinthEdgeLight.copy(alpha = 0.9f),
                palette.hairline.copy(alpha = 0.65f),
            )
        )
    }

    val focusAlpha by animateFloatAsState(
        targetValue = if (isCentered) 1f else 0f,
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 150),
        label = "discFocusAlpha",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .zIndex(if (isCentered) 10f else (8f - distanceSlots))
            .graphicsLayer {
                val delta = index - scrollValueProvider()
                val absDelta = abs(delta)
                val angleRad = delta * angleStepDeg * (PI / 180.0)
                val radialInwardPx = (arcRadiusPx * (1.0 - cos(angleRad))).toFloat()
                val yOffsetPx = (arcRadiusPx * sin(angleRad)).toFloat()

                val scale = (1f - absDelta * 0.085f).coerceIn(0.68f, 1f)
                val itemAlpha = (1f - absDelta * 0.25f).coerceIn(0.14f, 1f)
                val tiltDeg = if (reducedMotion) {
                    0f
                } else {
                    val rawTilt = -delta * 12.8f
                    if (isRtl) -rawTilt else rawTilt
                }

                translationX = if (isRtl) {
                    -(baseStartMarginPx + radialInwardPx)
                } else {
                    baseStartMarginPx + radialInwardPx
                }
                translationY = centerY + yOffsetPx - rowHeightPx / 2f
                scaleX = scale
                scaleY = scale
                alpha = itemAlpha
                rotationZ = tiltDeg
                transformOrigin = TransformOrigin(if (isRtl) 0.85f else 0.12f, 0.5f)
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = capsuleEndPad)
                .height(82.dp)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                ) {
                    if (isCentered) {
                        onSelectAndPlay(track)
                    } else {
                        onSnapToIndex(index)
                    }
                },
        ) {
            // Centered glowing capsule backdrop rendered on its own graphicsLayer for zero shadow-cache thrashing
            if (focusAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = focusAlpha }
                        .plinthSoftShadow(
                            cornerRadius = 41.dp,
                            shadowDark = palette.shadowDark,
                            shadowLight = palette.shadowLight,
                            elevation = 10.dp,
                            darkAlpha = if (palette.isDark) 0.65f else 0.36f,
                            lightAlpha = if (palette.isDark) 0.16f else 0.90f,
                        )
                        .clip(capsuleShape)
                        .background(palette.capsuleBg.copy(alpha = 0.98f))
                        .border(
                            width = 1.dp,
                            brush = capsuleBorderBrush,
                            shape = capsuleShape,
                        ),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 4.dp, end = 14.dp),
            ) {
                // Circular Album Disc with cached soft cast shadow
                Box(
                    modifier = Modifier
                        .size(baseDiscSize)
                        .plinthSoftShadow(
                            cornerRadius = baseDiscSize / 2,
                            shadowDark = palette.shadowDark,
                            shadowLight = palette.shadowLight,
                            elevation = 7.dp,
                            darkAlpha = if (palette.isDark) 0.72f else 0.44f,
                            lightAlpha = if (palette.isDark) 0.16f else 0.8f,
                        )
                        .clip(CircleShape)
                        .background(palette.capsuleBg)
                        .padding(2.5.dp),
                ) {
                    CircularArtworkDisc(
                        artworkUri = track.artworkUri,
                        contentDescription = track.title,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = if (isCentered) FontWeight.Bold else FontWeight.SemiBold,
                            fontSize = if (isCentered) 16.5.sp else 15.5.sp,
                        ),
                        color = palette.inkPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                        color = palette.inkSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (focusAlpha > 0.05f) {
                    Spacer(modifier = Modifier.width(8.dp))
                    TactileCapsuleButton(
                        onClick = { onSelectAndPlay(track) },
                        contentDescription = if (isTrackActive) strings.playing else strings.play,
                        cornerRadius = 18.dp,
                        hapticsEnabled = hapticsEnabled,
                        active = isTrackActive,
                        feedbackEvent = FeedbackEvent.PLAY_PAUSE,
                        modifier = Modifier
                            .testTag("explore_play_button_${track.id}")
                            .graphicsLayer { alpha = focusAlpha }
                            .height(36.dp)
                            .width(66.dp),
                    ) {
                        Text(
                            text = if (isTrackActive) strings.pause else strings.play,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = palette.inkPrimary,
                        )
                    }
                }
            }
        }
    }
}
