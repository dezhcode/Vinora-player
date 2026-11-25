package com.example.ui.components

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.audio.FeedbackEvent
import com.example.audio.PlinthFeedback
import com.example.ui.theme.LocalPlinthColors

/**
 * Draws realistic soft dual-source studio shadows (top-left specular lift + bottom-right ambient cast)
 * using native Canvas blur masks for authentic soft neumorphism & industrial depth.
 */
fun Modifier.plinthSoftShadow(
    cornerRadius: Dp,
    shadowDark: Color,
    shadowLight: Color,
    elevation: Dp = 10.dp,
    darkAlpha: Float = 0.42f,
    lightAlpha: Float = 0.85f,
): Modifier = this.drawBehind {
    val blurPx = (elevation.toPx() * 1.65f).coerceAtLeast(1f)
    val offsetPx = elevation.toPx() * 0.55f
    val radiusPx = cornerRadius.toPx()

    drawIntoCanvas { canvas ->
        val darkPaint = Paint().apply {
            asFrameworkPaint().apply {
                isAntiAlias = true
                color = shadowDark.copy(alpha = darkAlpha).toArgb()
                maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
            }
        }
        canvas.drawRoundRect(
            left = offsetPx * 0.6f,
            top = offsetPx,
            right = size.width + offsetPx * 0.6f,
            bottom = size.height + offsetPx,
            radiusX = radiusPx,
            radiusY = radiusPx,
            paint = darkPaint,
        )

        val lightPaint = Paint().apply {
            asFrameworkPaint().apply {
                isAntiAlias = true
                color = shadowLight.copy(alpha = lightAlpha).toArgb()
                maskFilter = BlurMaskFilter(blurPx * 0.85f, BlurMaskFilter.Blur.NORMAL)
            }
        }
        canvas.drawRoundRect(
            left = -offsetPx * 0.5f,
            top = -offsetPx * 0.65f,
            right = size.width - offsetPx * 0.5f,
            bottom = size.height - offsetPx * 0.65f,
            radiusX = radiusPx,
            radiusY = radiusPx,
            paint = lightPaint,
        )
    }
}

@Composable
fun TactileRoundButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 68.dp,
    hapticsEnabled: Boolean = true,
    active: Boolean = false,
    feedbackEvent: FeedbackEvent = FeedbackEvent.PLAY_PAUSE,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = LocalPlinthColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val pressTravelDp by animateDpAsState(
        targetValue = if (pressed) 3.5.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "roundBtnTravel",
    )
    val tiltDeg by animateFloatAsState(
        targetValue = if (pressed) -5.5f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "roundBtnTilt",
    )
    val shadowElev by animateDpAsState(
        targetValue = if (pressed) 1.5.dp else 10.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "roundBtnElev",
    )

    val capBrush = if (pressed) {
        Brush.radialGradient(
            colors = listOf(palette.buttonPressedBottom, palette.buttonPressedTop),
            center = Offset(90f, 90f),
            radius = 220f,
        )
    } else {
        Brush.linearGradient(
            colors = listOf(palette.buttonTop, palette.buttonBottom),
            start = Offset.Zero,
            end = Offset(180f, 220f),
        )
    }

    val bezelRimBrush = Brush.linearGradient(
        colors = listOf(
            palette.plinthEdgeShadow.copy(alpha = 0.85f),
            palette.plinthEdgeLight.copy(alpha = 0.95f),
        ),
        start = Offset.Zero,
        end = Offset(180f, 200f),
    )

    val skirtBrush = Brush.verticalGradient(
        colors = listOf(
            palette.plinthEdgeLight.copy(alpha = 0.65f),
            palette.plinthEdgeShadow.copy(alpha = 0.95f),
        ),
    )

    // Outer recessed chassis well
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bezelRimBrush)
            .padding(2.5.dp)
            .clip(CircleShape)
            .background(palette.plinthEdgeShadow.copy(alpha = if (palette.isDark) 0.72f else 0.32f))
            .padding(2.dp)
            .semantics { this.contentDescription = contentDescription }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
            ) {
                PlinthFeedback.trigger(feedbackEvent)
                onClick()
            },
    ) {
        // 3D Extruded Physical Keycap Skirt + Ceramic/Metallic Top Face
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = pressTravelDp.toPx()
                    rotationX = tiltDeg
                    cameraDistance = 12f * density
                }
                .plinthSoftShadow(
                    cornerRadius = size / 2,
                    shadowDark = palette.shadowDark,
                    shadowLight = palette.shadowLight,
                    elevation = shadowElev,
                    darkAlpha = if (palette.isDark) 0.82f else 0.56f,
                    lightAlpha = if (palette.isDark) 0.16f else 0.78f,
                )
                .clip(CircleShape)
                .background(skirtBrush)
                .padding(bottom = if (pressed) 1.dp else 3.8.dp, top = 1.dp, start = 1.dp, end = 1.dp)
                .clip(CircleShape)
                .background(capBrush)
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            palette.plinthEdgeLight.copy(alpha = if (pressed) 0.3f else 0.95f),
                            if (active) palette.inkPrimary.copy(alpha = 0.25f) else palette.plinthEdgeShadow.copy(alpha = 0.45f),
                        )
                    ),
                    shape = CircleShape,
                ),
            content = content,
        )
    }
}

@Composable
fun TactileCapsuleButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    hapticsEnabled: Boolean = true,
    active: Boolean = false,
    feedbackEvent: FeedbackEvent = FeedbackEvent.TAP_SUBTLE,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = LocalPlinthColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val pressTravelDp by animateDpAsState(
        targetValue = if (pressed) 2.8.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMediumLow),
        label = "capsuleTravel",
    )
    val tiltDeg by animateFloatAsState(
        targetValue = if (pressed) -4.2f else 0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium),
        label = "capsuleTilt",
    )
    val shadowElev by animateDpAsState(
        targetValue = if (pressed) 1.5.dp else 7.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "capsuleElev",
    )

    val outerShape = RoundedCornerShape(cornerRadius)
    val innerShape = RoundedCornerShape((cornerRadius - 2.dp).coerceAtLeast(6.dp))

    val fillBrush = if (pressed) {
        Brush.verticalGradient(listOf(palette.buttonPressedTop, palette.buttonPressedBottom))
    } else if (active) {
        Brush.verticalGradient(listOf(palette.inkPrimary.copy(alpha = 0.13f), palette.buttonBottom))
    } else {
        Brush.verticalGradient(listOf(palette.buttonTop, palette.buttonBottom))
    }

    val skirtBrush = Brush.verticalGradient(
        listOf(
            palette.plinthEdgeLight.copy(alpha = 0.6f),
            palette.plinthEdgeShadow.copy(alpha = 0.9f),
        ),
    )

    // Recessed chassis bezel housing
    Box(
        contentAlignment = Alignment.Center,
        propagateMinConstraints = true,
        modifier = modifier
            .clip(outerShape)
            .background(palette.plinthEdgeShadow.copy(alpha = if (palette.isDark) 0.55f else 0.24f))
            .border(0.8.dp, palette.hairline, outerShape)
            .semantics { this.contentDescription = contentDescription }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
            ) {
                PlinthFeedback.trigger(feedbackEvent)
                onClick()
            }
            .padding(2.dp),
    ) {
        // 3D Physical Keycap Skirt + Machined Face
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .graphicsLayer {
                    translationY = pressTravelDp.toPx()
                    rotationX = tiltDeg
                    cameraDistance = 12f * density
                }
                .plinthSoftShadow(
                    cornerRadius = cornerRadius,
                    shadowDark = palette.shadowDark,
                    shadowLight = palette.shadowLight,
                    elevation = shadowElev,
                    darkAlpha = if (palette.isDark) 0.72f else 0.45f,
                    lightAlpha = if (palette.isDark) 0.14f else 0.8f,
                )
                .clip(innerShape)
                .background(skirtBrush)
                .padding(bottom = if (pressed) 0.8.dp else 2.8.dp, top = 0.8.dp, start = 0.8.dp, end = 0.8.dp)
                .clip(innerShape)
                .background(fillBrush)
                .border(
                    width = 0.8.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            palette.plinthEdgeLight.copy(alpha = if (pressed) 0.2f else 0.9f),
                            palette.plinthEdgeShadow.copy(alpha = 0.35f),
                        )
                    ),
                    shape = innerShape,
                ),
            content = content,
        )
    }
}

/**
 * Smooth geometric path-morphing Play Triangle <-> Dual Pause Bars icon.
 */
@Composable
fun MorphingPlayPauseIcon(
    isPlaying: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val morph by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "playPauseMorph",
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val pad = w * 0.18f
        val top = h * 0.16f
        val bottom = h * 0.84f
        val midY = h * 0.5f

        if (morph < 0.02f) {
            // Pure crisp rounded play triangle
            val path = Path().apply {
                moveTo(pad + w * 0.08f, top)
                lineTo(w - pad, midY)
                lineTo(pad + w * 0.08f, bottom)
                close()
            }
            drawPath(path, color = tint)
        } else if (morph > 0.98f) {
            // Crisp industrial pause bars
            val barW = w * 0.18f
            val leftX = w * 0.26f
            val rightX = w - leftX - barW
            drawRoundRect(
                color = tint,
                topLeft = Offset(leftX, top),
                size = Size(barW, bottom - top),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f),
            )
            drawRoundRect(
                color = tint,
                topLeft = Offset(rightX, top),
                size = Size(barW, bottom - top),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f),
            )
        } else {
            // Fluid interpolation splitting triangle into two pause bars
            val t = morph
            val leftBarLeft = lerp(pad + w * 0.08f, w * 0.26f, t)
            val leftBarRight = lerp(w * 0.54f, w * 0.44f, t)
            val leftTopRightY = lerp(top + (midY - top) * 0.5f, top, t)
            val leftBotRightY = lerp(bottom - (bottom - midY) * 0.5f, bottom, t)

            val leftPath = Path().apply {
                moveTo(leftBarLeft, top)
                lineTo(leftBarRight, leftTopRightY)
                lineTo(leftBarRight, leftBotRightY)
                lineTo(leftBarLeft, bottom)
                close()
            }
            drawPath(leftPath, color = tint)

            val rightBarLeft = lerp(w * 0.54f, w * 0.56f, t)
            val rightBarRight = lerp(w - pad, w * 0.74f, t)
            val rightTopLeftY = lerp(top + (midY - top) * 0.5f, top, t)
            val rightTopRightY = lerp(midY, top, t)
            val rightBotRightY = lerp(midY, bottom, t)
            val rightBotLeftY = lerp(bottom - (bottom - midY) * 0.5f, bottom, t)

            val rightPath = Path().apply {
                moveTo(rightBarLeft, rightTopLeftY)
                lineTo(rightBarRight, rightTopRightY)
                lineTo(rightBarRight, rightBotRightY)
                lineTo(rightBarLeft, rightBotLeftY)
                close()
            }
            drawPath(rightPath, color = tint)
        }
    }
}

@Composable
fun CircularArtworkDisc(
    artworkUri: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    showInnerRing: Boolean = true,
) {
    val palette = LocalPlinthColors.current
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(palette.plinthTop, palette.plinthBottom)
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (!artworkUri.isNullOrBlank()) {
            AsyncImage(
                model = artworkUri,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        } else {
            // Minimalist dark archival vinyl glyph fallback
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(color = Color(0xFF14171E))
                drawCircle(
                    color = Color(0xFF282E3A),
                    radius = size.minDimension * 0.36f,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f),
                )
                drawCircle(
                    color = palette.plinthTop,
                    radius = size.minDimension * 0.18f,
                )
                drawCircle(
                    color = Color(0xFF14171E),
                    radius = size.minDimension * 0.05f,
                )
            }
        }
        if (showInnerRing) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Subtle glass/sleeve rim light
                drawCircle(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.55f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.18f),
                        )
                    ),
                    radius = size.minDimension / 2f - 1f,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
                )
            }
        }
    }
}

@Composable
fun MinimalSkipIcon(
    isNext: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val top = h * 0.22f
        val bot = h * 0.78f
        val midY = h * 0.5f

        if (isNext) {
            val tri = Path().apply {
                moveTo(w * 0.22f, top)
                lineTo(w * 0.68f, midY)
                lineTo(w * 0.22f, bot)
                close()
            }
            drawPath(tri, color = tint)
            drawLine(
                color = tint,
                start = Offset(w * 0.76f, top + 1f),
                end = Offset(w * 0.76f, bot - 1f),
                strokeWidth = w * 0.11f,
                cap = StrokeCap.Round,
            )
        } else {
            drawLine(
                color = tint,
                start = Offset(w * 0.24f, top + 1f),
                end = Offset(w * 0.24f, bot - 1f),
                strokeWidth = w * 0.11f,
                cap = StrokeCap.Round,
            )
            val tri = Path().apply {
                moveTo(w * 0.78f, top)
                lineTo(w * 0.32f, midY)
                lineTo(w * 0.78f, bot)
                close()
            }
            drawPath(tri, color = tint)
        }
    }
}

private fun lerp(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction
