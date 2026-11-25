package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.audio.FeedbackEvent
import com.example.audio.PlinthFeedback
import com.example.data.Track
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.LocalPlinthColors
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Signature curved vertical orbital disc wheel inspired directly by the Explore reference.
 * Features inertial physics drag, velocity decay, spring snapping, radial text rotation,
 * haptic detents, and a luminous floating center capsule with tactile Play button.
 */
@Composable
fun CurvedDiscCarousel(
    tracks: List<Track>,
    activeTrackId: String?,
    isPlaying: Boolean,
    hapticsEnabled: Boolean,
    reducedMotion: Boolean,
    onSelectAndPlay: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tracks.isEmpty()) return

    val palette = LocalPlinthColors.current
    val strings = LocalAppStrings.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    val initialIdx = remember(tracks) {
        tracks.indexOfFirst { it.id == "studio_daylight" }
            .takeIf { it >= 0 }
            ?: (tracks.size / 2).coerceIn(0, tracks.lastIndex)
    }
    val scrollIndex = remember { Animatable(initialIdx.toFloat()) }
    var lastHapticIndex by remember { mutableIntStateOf(initialIdx) }

    LaunchedEffect(tracks.size) {
        val clamped = scrollIndex.value.coerceIn(0f, tracks.lastIndex.toFloat())
        if (clamped != scrollIndex.value) {
            scrollIndex.snapTo(clamped)
        }
    }

    // Fire crisp subtle tactile + mechanical detent whenever the centered disc crosses a track
    LaunchedEffect(scrollIndex.value.roundToInt()) {
        val snapped = scrollIndex.value.roundToInt().coerceIn(0, tracks.lastIndex)
        if (snapped != lastHapticIndex) {
            lastHapticIndex = snapped
            PlinthFeedback.triggerSliderDetent(isMajorNotch = false)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("explore_curved_carousel")
            .pointerInput(tracks.size) {
                val velocityTracker = VelocityTracker()
                val pxPerSlot = with(density) { 104.dp.toPx() }
                detectVerticalDragGestures(
                    onDragStart = {
                        velocityTracker.resetTracking()
                        scope.launch {
                            scrollIndex.stop()
                            scrollIndex.updateBounds(null, null)
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        val deltaIndex = -dragAmount / pxPerSlot
                        val target = (scrollIndex.value + deltaIndex)
                            .coerceIn(-0.35f, tracks.lastIndex + 0.35f)
                        scope.launch { scrollIndex.snapTo(target) }
                    },
                    onDragEnd = {
                        val velY = velocityTracker.calculateVelocity().y
                        val indexVelocity = -velY / pxPerSlot
                        scope.launch {
                            scrollIndex.updateBounds(
                                lowerBound = 0f,
                                upperBound = tracks.lastIndex.toFloat(),
                            )
                            if (abs(indexVelocity) > 0.6f) {
                                scrollIndex.animateDecay(
                                    initialVelocity = indexVelocity,
                                    animationSpec = exponentialDecay(frictionMultiplier = 2.4f),
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
                    },
                    onDragCancel = {
                        scope.launch {
                            val nearest = scrollIndex.value.roundToInt().coerceIn(0, tracks.lastIndex)
                            scrollIndex.animateTo(nearest.toFloat())
                        }
                    },
                )
            },
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val centerY = heightPx * 0.49f

        // Invisible giant orbital wheel geometry anchored off to the trailing side
        val arcRadiusPx = (widthPx * 1.06f).coerceAtLeast(with(density) { 360.dp.toPx() })
        val angleStepDeg = 16.5f
        val baseDiscSize = 78.dp
        val baseDiscSizePx = with(density) { baseDiscSize.toPx() }
        val baseStartMarginPx = with(density) { 20.dp.toPx() }

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
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
            )
        }

        val currentFloat = scrollIndex.value
        val centerRounded = currentFloat.roundToInt().coerceIn(0, tracks.lastIndex)
        val visibleRange = (centerRounded - 4).coerceAtLeast(0)..(centerRounded + 4).coerceAtMost(tracks.lastIndex)

        for (index in visibleRange) {
            val track = tracks[index]
            val delta = index - currentFloat
            val absDelta = abs(delta)

            // Orbital arc position
            val angleDeg = delta * angleStepDeg
            val angleRad = angleDeg * (PI / 180.0)
            val radialInwardPx = (arcRadiusPx * (1.0 - cos(angleRad))).toFloat()
            val yOffsetPx = (arcRadiusPx * sin(angleRad)).toFloat()

            val scale = (1f - absDelta * 0.085f).coerceIn(0.68f, 1f)
            val itemAlpha = (1f - absDelta * 0.25f).coerceIn(0.14f, 1f)
            val focusWeight = (1f - absDelta / 0.55f).coerceIn(0f, 1f)

            // Tilt text radially along the wheel (above center tilts down-right, below tilts up-right)
            val tiltDeg = if (reducedMotion) {
                0f
            } else {
                val rawTilt = -delta * 12.8f
                if (isRtl) -rawTilt else rawTilt
            }

            val isTrackActive = track.id == activeTrackId && isPlaying
            val rowHeight = 92.dp
            val rowHeightPx = with(density) { rowHeight.toPx() }

            val translationXValue = if (isRtl) {
                -(baseStartMarginPx + radialInwardPx)
            } else {
                baseStartMarginPx + radialInwardPx
            }
            val translationYValue = centerY + yOffsetPx - rowHeightPx / 2f

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .zIndex(10f - absDelta)
                    .graphicsLayer {
                        translationX = translationXValue
                        translationY = translationYValue
                        scaleX = scale
                        scaleY = scale
                        alpha = itemAlpha
                        rotationZ = tiltDeg
                        transformOrigin = TransformOrigin(if (isRtl) 0.85f else 0.12f, 0.5f)
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                // Floating white/gunmetal capsule wrapping only the centered focused item
                val capsuleEndPad = with(density) { (baseStartMarginPx * 2f).toDp() }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = capsuleEndPad)
                        .height(82.dp)
                        .then(
                            if (focusWeight > 0.05f) {
                                Modifier
                                    .plinthSoftShadow(
                                        cornerRadius = 41.dp,
                                        shadowDark = palette.shadowDark,
                                        shadowLight = palette.shadowLight,
                                        elevation = (11f * focusWeight).dp,
                                        darkAlpha = (if (palette.isDark) 0.65f else 0.36f) * focusWeight,
                                        lightAlpha = (if (palette.isDark) 0.16f else 0.92f) * focusWeight,
                                    )
                                    .clip(RoundedCornerShape(41.dp))
                                    .background(palette.capsuleBg.copy(alpha = focusWeight * 0.98f))
                                    .border(
                                        width = 1.dp,
                                        brush = Brush.verticalGradient(
                                            listOf(
                                                palette.plinthEdgeLight.copy(alpha = focusWeight * 0.9f),
                                                palette.hairline.copy(alpha = focusWeight * 0.65f),
                                            )
                                        ),
                                        shape = RoundedCornerShape(41.dp),
                                    )
                            } else {
                                Modifier
                            }
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (absDelta < 0.4f) {
                                onSelectAndPlay(track)
                            } else {
                                scope.launch {
                                    scrollIndex.animateTo(
                                        targetValue = index.toFloat(),
                                        animationSpec = spring(
                                            dampingRatio = 0.8f,
                                            stiffness = Spring.StiffnessMediumLow,
                                        ),
                                    )
                                }
                            }
                        },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 4.dp, end = 14.dp),
                    ) {
                        // Circular Album Disc with physical rim & soft cast shadow
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

                        // Track Title & Artist
                        Column(
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = if (focusWeight > 0.5f) FontWeight.Bold else FontWeight.SemiBold,
                                    fontSize = (15.5f + focusWeight * 1f).sp,
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

                        // Tactile Play Pill Button (visible smoothly inside the focused capsule)
                        if (focusWeight > 0.25f) {
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
                                    .graphicsLayer { alpha = focusWeight }
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
    }
}
