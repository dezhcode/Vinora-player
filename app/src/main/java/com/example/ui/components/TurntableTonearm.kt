package com.example.ui.components

import android.graphics.BlurMaskFilter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.example.ui.theme.PlinthPalette
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Pixel-verified layout coordinates & tonearm kinematics for the pre-rendered
 * high-resolution drawable assets (`img_turntable_base_*`, `img_turntable_vinyl`,
 * `img_turntable_tonearm`, `img_turntable_tonearm_shadow`).
 */
internal object TurntableGeometrySpec {
    const val PLATTER_CX = 0.5000f
    const val PLATTER_CY = 0.4942f
    const val VINYL_RADIUS_RATIO = 0.3144f
    const val VINYL_ASSET_BOX_RATIO = 0.6491f // (VINYL_RADIUS_RATIO * 2) * (512 / 496)
    const val LABEL_TO_DECK_RATIO = 0.2100f // (VINYL_RADIUS_RATIO * 2) * (163 / 488)

    const val PIVOT_CX = 0.8336f
    const val PIVOT_CY = 0.1917f

    const val TONEARM_IMG_WIDTH = 297f
    const val TONEARM_IMG_HEIGHT = 927f
    const val TONEARM_PIVOT_NORM_X = 0.4052f
    const val TONEARM_PIVOT_NORM_Y = 0.2474f
    const val TONEARM_SRC_EFF_LEN_PX = 668.8f
    const val EFFECTIVE_LENGTH_RATIO = 0.3900f
    const val STYLUS_OFFSET_COMPENSATION_DEG = 1.26f

    const val LEFT_KNOB_CX = 0.1739f
    const val LEFT_KNOB_CY = 0.8173f
    const val LEFT_KNOB_R = 0.0530f

    const val RIGHT_KNOB_CX = 0.8267f
    const val RIGHT_KNOB_CY = 0.8203f
    const val RIGHT_KNOB_R = 0.0536f

    fun computeGrooveAngleDeg(progressFraction: Float): Float {
        val p = progressFraction.coerceIn(0f, 1f)
        val dx = PLATTER_CX - PIVOT_CX
        val dy = PLATTER_CY - PIVOT_CY
        val dist = hypot(dx, dy)
        val thetaCenterRad = atan2(-dx.toDouble(), dy.toDouble())
        val outerR = VINYL_RADIUS_RATIO * 0.78f
        val innerR = VINYL_RADIUS_RATIO * 0.43f
        val grooveR = outerR - (outerR - innerR) * p
        val cosAlpha = ((dist * dist + EFFECTIVE_LENGTH_RATIO * EFFECTIVE_LENGTH_RATIO - grooveR * grooveR) /
            (2.0 * dist * EFFECTIVE_LENGTH_RATIO)).coerceIn(-1.0, 1.0)
        val desiredStylusRayDeg = ((thetaCenterRad - acos(cosAlpha)) * 180.0 / PI).toFloat()
        return desiredStylusRayDeg + STYLUS_OFFSET_COMPENSATION_DEG
    }
}

internal fun DrawScope.drawChassisStudioShadow(
    palette: PlinthPalette,
    highQualityBlur: Boolean,
) {
    if (!highQualityBlur) return
    val side = size.minDimension
    val pad = side * 0.064f
    val chassisSize = side - pad * 2f
    val cornerR = side * 0.052f

    drawIntoCanvas { canvas ->
        val blurPx = (side * 0.026f).coerceAtLeast(2f)
        val offsetY = side * 0.014f
        val offsetX = side * 0.008f
        val darkPaint = Paint().apply {
            asFrameworkPaint().apply {
                isAntiAlias = true
                color = palette.shadowDark.copy(alpha = if (palette.isDark) 0.62f else 0.34f).toArgb()
                maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
            }
        }
        canvas.drawRoundRect(
            left = pad + offsetX,
            top = pad + offsetY,
            right = pad + chassisSize + offsetX,
            bottom = pad + chassisSize + offsetY,
            radiusX = cornerR,
            radiusY = cornerR,
            paint = darkPaint,
        )

        val lightPaint = Paint().apply {
            asFrameworkPaint().apply {
                isAntiAlias = true
                color = palette.shadowLight.copy(alpha = if (palette.isDark) 0.16f else 0.82f).toArgb()
                maskFilter = BlurMaskFilter(blurPx * 0.7f, BlurMaskFilter.Blur.NORMAL)
            }
        }
        canvas.drawRoundRect(
            left = pad - offsetX * 0.5f,
            top = pad - offsetY * 0.6f,
            right = pad + chassisSize - offsetX * 0.5f,
            bottom = pad + chassisSize - offsetY * 0.6f,
            radiusX = cornerR,
            radiusY = cornerR,
            paint = lightPaint,
        )
    }
}

internal fun DrawScope.drawStationaryVinylSheenAndSpindle(palette: PlinthPalette) {
    val side = size.minDimension
    val vinylR = side * TurntableGeometrySpec.VINYL_RADIUS_RATIO
    val labelR = side * (TurntableGeometrySpec.LABEL_TO_DECK_RATIO * 0.5f)
    val center = Offset(size.width / 2f, size.height / 2f)

    // Subtle stationary studio reflection overlay so light response feels anchored while vinyl spins
    val sweepMidR = (vinylR * 0.96f + labelR * 1.08f) * 0.5f
    val sweepWidth = (vinylR * 0.96f - labelR * 1.08f).coerceAtLeast(1f)
    drawCircle(
        brush = Brush.sweepGradient(
            colorStops = arrayOf(
                0.00f to Color.Transparent,
                0.11f to Color.White.copy(alpha = 0.02f),
                0.15f to Color.White.copy(alpha = 0.08f),
                0.20f to Color.White.copy(alpha = 0.02f),
                0.26f to Color.Transparent,
                0.60f to Color.Transparent,
                0.65f to Color.White.copy(alpha = 0.03f),
                0.70f to Color.White.copy(alpha = 0.09f),
                0.75f to Color.White.copy(alpha = 0.02f),
                0.81f to Color.Transparent,
                1.00f to Color.Transparent,
            ),
            center = center,
        ),
        radius = sweepMidR,
        center = center,
        style = Stroke(width = sweepWidth),
    )

    // Stationary Machined Center Spindle Pin
    val spindleR = vinylR * 0.036f
    drawCircle(
        color = Color.Black.copy(alpha = if (palette.isDark) 0.45f else 0.22f),
        radius = spindleR * 1.35f,
        center = center + Offset(0.6.dp.toPx(), 1.0.dp.toPx()),
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFFFFFF), Color(0xFFD6DCE6), Color(0xFF7A8496)),
            center = center - Offset(spindleR * 0.35f, spindleR * 0.35f),
            radius = spindleR * 1.25f,
        ),
        radius = spindleR,
        center = center,
    )
}
