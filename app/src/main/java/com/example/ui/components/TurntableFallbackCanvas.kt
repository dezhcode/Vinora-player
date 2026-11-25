package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.dp
import com.example.audio.FeedbackEvent
import com.example.audio.PlinthFeedback
import com.example.data.GraphicsQuality
import com.example.ui.theme.LocalPlinthColors
import com.example.ui.theme.PlinthPalette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Vector fallback deck rendered ONLY if pre-rendered 3D WebP assets are not present in assets/turntable/.
 */
@Composable
internal fun TurntableFallbackCanvasDeck(
    isPlaying: Boolean,
    discAngleDeg: Float,
    armSweepDeg: Float,
    graphicsQuality: GraphicsQuality,
    batterySaverMode: Boolean,
    onTogglePlayPause: () -> Unit,
    onScrubStart: () -> Unit,
    onScrubDeltaDeg: (Float) -> Unit,
    onScrubDeltaMs: (Long) -> Unit,
    onScrubEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPlinthColors.current
    val latestOnTogglePlay by rememberUpdatedState(onTogglePlayPause)
    val latestOnScrubStart by rememberUpdatedState(onScrubStart)
    val latestOnScrubDeltaDeg by rememberUpdatedState(onScrubDeltaDeg)
    val latestOnScrubDeltaMs by rememberUpdatedState(onScrubDeltaMs)
    val latestOnScrubEnd by rememberUpdatedState(onScrubEnd)

    var lastTouchAngle by remember { mutableFloatStateOf(0f) }
    var lastDetentDeg by remember { mutableFloatStateOf(0f) }
    var isScrubbing by remember { mutableStateOf(false) }

    val grooveCount = when {
        batterySaverMode || graphicsQuality == GraphicsQuality.LOW -> 14
        graphicsQuality == GraphicsQuality.MEDIUM -> 22
        else -> 32
    }

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .aspectRatio(1.0f, matchHeightConstraintsFirst = maxHeight < maxWidth)
                .testTag("turntable_fallback_canvas")
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val platterCenter = Offset(w * 0.425f, h * 0.502f)
                        val leftDialCenter = Offset(w * 0.182f, h * 0.798f)
                        val rightDialCenter = Offset(w * 0.818f, h * 0.798f)
                        val hitLeft = hypot(offset.x - leftDialCenter.x, offset.y - leftDialCenter.y) <= w * 0.08f
                        val hitRight = hypot(offset.x - rightDialCenter.x, offset.y - rightDialCenter.y) <= w * 0.08f
                        val hitPlatter = hypot(offset.x - platterCenter.x, offset.y - platterCenter.y) <= w * 0.34f
                        if (hitLeft || hitRight || hitPlatter) {
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
                            val platterCenter = Offset(w * 0.425f, h * 0.502f)
                            if (hypot(start.x - platterCenter.x, start.y - platterCenter.y) <= w * 0.36f) {
                                isScrubbing = true
                                lastTouchAngle = atan2(start.y - platterCenter.y, start.x - platterCenter.x)
                                lastDetentDeg = discAngleDeg
                                latestOnScrubStart()
                            }
                        },
                        onDrag = { change, _ ->
                            if (!isScrubbing) return@detectDragGestures
                            change.consume()
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val platterCenter = Offset(w * 0.425f, h * 0.502f)
                            val angle = atan2(change.position.y - platterCenter.y, change.position.x - platterCenter.x)
                            var delta = angle - lastTouchAngle
                            if (delta > PI.toFloat()) delta -= (2.0 * PI).toFloat()
                            if (delta < -PI.toFloat()) delta += (2.0 * PI).toFloat()
                            lastTouchAngle = angle
                            latestOnScrubDeltaDeg(Math.toDegrees(delta.toDouble()).toFloat())
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
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val m = minOf(w, h)
                val cornerR = m * 0.068f
                val pad = m * 0.045f
                val chassisW = w - pad * 2f
                val chassisH = h - pad * 2f

                // Outer frame
                drawRoundRect(
                    color = if (palette.isDark) Color(0xFF1E232D) else Color(0xFFEAE8E2),
                    topLeft = Offset(pad, pad),
                    size = Size(chassisW, chassisH),
                    cornerRadius = CornerRadius(cornerR, cornerR),
                )

                // Vinyl disc
                val center = Offset(w * 0.425f, h * 0.502f)
                val discR = m * 0.306f
                drawCircle(color = Color(0xFFC8C5BE), radius = discR * 1.055f, center = center)
                drawCircle(color = Color(0xFF060709), radius = discR, center = center)
                rotate(degrees = discAngleDeg, pivot = center) {
                    for (i in 0 until grooveCount) {
                        val rNorm = 0.38f + (i.toFloat() / (grooveCount - 1).coerceAtLeast(1)) * 0.56f
                        drawCircle(
                            color = Color.White.copy(alpha = 0.03f),
                            radius = discR * rNorm,
                            center = center,
                            style = Stroke(width = 0.8.dp.toPx()),
                        )
                    }
                }
                drawCircle(
                    color = if (palette.isDark) Color(0xFF232833) else Color(0xFFECEAE4),
                    radius = discR * 0.31f,
                    center = center,
                )
                drawCircle(color = Color(0xFFD5DAE2), radius = discR * 0.035f, center = center)

                // Simple tonearm fallback
                val pivot = Offset(w * 0.811f, h * 0.250f)
                drawCircle(color = Color(0xFF14171D), radius = m * 0.058f, center = pivot)
                rotate(degrees = -armSweepDeg, pivot = pivot) {
                    val wandEnd = Offset(pivot.x, pivot.y + m * 0.44f)
                    drawLine(
                        color = Color(0xFF181B22),
                        start = pivot,
                        end = wandEnd,
                        strokeWidth = m * 0.015f,
                        cap = StrokeCap.Round,
                    )
                    drawRoundRect(
                        brush = Brush.verticalGradient(listOf(Color(0xFFE7EBF2), Color(0xFFB8C0CE))),
                        topLeft = Offset(wandEnd.x - m * 0.02f, wandEnd.y - m * 0.01f),
                        size = Size(m * 0.04f, m * 0.07f),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                    )
                }
            }
        }
    }
}
