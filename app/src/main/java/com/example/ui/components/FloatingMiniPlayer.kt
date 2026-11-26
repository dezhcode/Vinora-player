package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.audio.FeedbackEvent
import com.example.audio.PlinthFeedback
import com.example.data.Track
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.LocalPlinthColors
import kotlinx.coroutines.flow.StateFlow

/**
 * Floating tactile capsule Mini Player with miniature spinning vinyl disc,
 * thin progress track, morphing Play/Pause control, and swipe-up expansion to Now Playing.
 */
@Composable
fun FloatingMiniPlayer(
    track: Track,
    isPlaying: Boolean,
    progressFraction: Float,
    hapticsEnabled: Boolean,
    batterySaverMode: Boolean,
    reducedMotion: Boolean,
    onTogglePlayPause: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    positionMsFlow: StateFlow<Long>? = null,
    durationMs: Long = 1L,
) {
    val palette = LocalPlinthColors.current
    val strings = LocalAppStrings.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var discAngle by remember { mutableFloatStateOf(0f) }
    var dragAccum by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying, reducedMotion, batterySaverMode, lifecycleOwner) {
        if (!isPlaying || batterySaverMode) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = 0L
            while (true) {
                withFrameNanos { now ->
                    if (last != 0L) {
                        val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                        val speed = if (reducedMotion) 24f else 140f
                        discAngle = (discAngle + speed * dt) % 360f
                    }
                    last = now
                }
            }
        }
    }

    val shape = RoundedCornerShape(34.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(66.dp)
            .testTag("floating_mini_player")
            .plinthSoftShadow(
                cornerRadius = 34.dp,
                shadowDark = palette.shadowDark,
                shadowLight = palette.shadowLight,
                elevation = 10.dp,
                darkAlpha = if (palette.isDark) 0.72f else 0.36f,
                lightAlpha = if (palette.isDark) 0.16f else 0.9f,
            )
            .clip(shape)
            .background(palette.capsuleBg.copy(alpha = 0.96f))
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(listOf(palette.plinthEdgeLight, palette.hairline)),
                shape = shape,
            )
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragAccum = 0f },
                    onVerticalDrag = { _, dragAmount ->
                        dragAccum += dragAmount
                        if (dragAccum < -42f) {
                            dragAccum = 0f
                            PlinthFeedback.trigger(FeedbackEvent.TAP_SUBTLE)
                            onExpand()
                        }
                    },
                )
            }
            .clickable {
                PlinthFeedback.trigger(FeedbackEvent.TAP_SUBTLE)
                onExpand()
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp, end = 10.dp),
        ) {
            // Miniature obsidian vinyl + spinning cover label
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer { rotationZ = discAngle }
                    .clip(CircleShape)
                    .background(Color(0xFF101218)),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.12f),
                        radius = size.minDimension * 0.42f,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f),
                    )
                }
                CircularArtworkDisc(
                    artworkUri = track.artworkUri,
                    contentDescription = track.title,
                    showInnerRing = false,
                    modifier = Modifier.size(30.dp),
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(palette.capsuleBg)
                        .border(1.dp, Color(0xFF1A1D24), CircleShape),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    ),
                    color = palette.inkPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = palette.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            TactileRoundButton(
                onClick = onTogglePlayPause,
                contentDescription = if (isPlaying) strings.pause else strings.play,
                size = 48.dp,
                hapticsEnabled = hapticsEnabled,
                modifier = Modifier.testTag("mini_player_play_pause"),
            ) {
                MorphingPlayPauseIcon(
                    isPlaying = isPlaying,
                    tint = palette.inkPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // Ultra-thin bottom hairline progress track (isolated from parent recomposition)
        MiniPlayerProgressTrack(
            fallbackProgress = progressFraction,
            positionMsFlow = positionMsFlow,
            durationMs = durationMs,
            trackColor = palette.hairline.copy(alpha = 0.55f),
            fillColor = palette.inkPrimary.copy(alpha = 0.72f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(2.5.dp),
        )
    }
}

@Composable
private fun MiniPlayerProgressTrack(
    fallbackProgress: Float,
    positionMsFlow: StateFlow<Long>?,
    durationMs: Long,
    trackColor: Color,
    fillColor: Color,
    modifier: Modifier = Modifier,
) {
    val livePos = positionMsFlow?.collectAsStateWithLifecycle()?.value
    val fraction = if (livePos != null) {
        (livePos.toFloat() / durationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
    } else {
        fallbackProgress.coerceIn(0f, 1f)
    }
    Canvas(modifier = modifier) {
        drawRect(
            color = trackColor,
            size = Size(size.width, size.height),
        )
        drawRect(
            color = fillColor,
            topLeft = Offset.Zero,
            size = Size(size.width * fraction, size.height),
        )
    }
}
