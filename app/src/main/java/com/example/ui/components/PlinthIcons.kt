package com.example.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Lightweight bundled vector icons replacing the full `material-icons-extended` dependency
 * (~45 MB of unused icon classes) to keep APK size minimal and compilation fast.
 */
object PlinthIcons {

    val Tune: ImageVector by lazy {
        icon("Tune") {
            // Top slider track + knob
            moveTo(3f, 17f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(6f)
            verticalLineToRelative(-2f)
            horizontalLineTo(3f)
            close()
            moveTo(3f, 5f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(10f)
            verticalLineTo(5f)
            horizontalLineTo(3f)
            close()
            moveTo(13f, 21f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(8f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(-8f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(6f)
            horizontalLineToRelative(2f)
            close()
            moveTo(7f, 9f)
            verticalLineToRelative(2f)
            horizontalLineTo(3f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(4f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(2f)
            verticalLineTo(9f)
            horizontalLineTo(7f)
            close()
            moveTo(21f, 13f)
            verticalLineToRelative(-2f)
            horizontalLineTo(11f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(10f)
            close()
            moveTo(15f, 9f)
            horizontalLineToRelative(2f)
            verticalLineTo(7f)
            horizontalLineToRelative(4f)
            verticalLineTo(5f)
            horizontalLineToRelative(-4f)
            verticalLineTo(3f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(6f)
            close()
        }
    }

    val AudioFile: ImageVector by lazy {
        icon("AudioFile") {
            moveTo(14f, 2f)
            horizontalLineTo(6f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(16f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(12f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(8f)
            lineToRelative(-6f, -6f)
            close()
            moveTo(13f, 9f)
            verticalLineTo(3.5f)
            lineTo(18.5f, 9f)
            horizontalLineTo(13f)
            close()
            moveTo(16f, 13f)
            horizontalLineToRelative(-3f)
            verticalLineToRelative(3.75f)
            curveToRelative(0f, 1.24f, -1.01f, 2.25f, -2.25f, 2.25f)
            reflectiveCurveTo(8.5f, 17.99f, 8.5f, 16.75f)
            reflectiveCurveToRelative(1.01f, -2.25f, 2.25f, -2.25f)
            curveToRelative(0.46f, 0f, 0.89f, 0.14f, 1.25f, 0.38f)
            verticalLineTo(11f)
            horizontalLineToRelative(4f)
            verticalLineToRelative(2f)
            close()
        }
    }

    val FolderOpen: ImageVector by lazy {
        icon("FolderOpen") {
            moveTo(20f, 6f)
            horizontalLineToRelative(-8f)
            lineToRelative(-2f, -2f)
            horizontalLineTo(4f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(12f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(16f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(8f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            close()
            moveTo(20f, 18f)
            horizontalLineTo(4f)
            verticalLineTo(8f)
            horizontalLineToRelative(16f)
            verticalLineToRelative(10f)
            close()
        }
    }

    val GraphicEq: ImageVector by lazy {
        icon("GraphicEq") {
            moveTo(7f, 18f)
            horizontalLineToRelative(2f)
            verticalLineTo(6f)
            horizontalLineTo(7f)
            verticalLineToRelative(12f)
            close()
            moveTo(11f, 22f)
            horizontalLineToRelative(2f)
            verticalLineTo(2f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(20f)
            close()
            moveTo(3f, 14f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(-4f)
            horizontalLineTo(3f)
            verticalLineToRelative(4f)
            close()
            moveTo(15f, 18f)
            horizontalLineToRelative(2f)
            verticalLineTo(6f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(12f)
            close()
            moveTo(19f, 10f)
            verticalLineToRelative(4f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(-4f)
            horizontalLineToRelative(-2f)
            close()
        }
    }

    val Bedtime: ImageVector by lazy {
        icon("Bedtime") {
            moveTo(12.34f, 2.02f)
            curveTo(6.59f, 1.82f, 2f, 6.42f, 2f, 12f)
            curveToRelative(0f, 5.52f, 4.48f, 10f, 10f, 10f)
            curveToRelative(3.71f, 0f, 6.93f, -2.02f, 8.66f, -5.02f)
            curveToRelative(-7.51f, -0.25f, -12.09f, -8.43f, -8.32f, -14.96f)
            close()
        }
    }

    val ArrowUpward: ImageVector by lazy {
        icon("ArrowUpward") {
            moveTo(4f, 12f)
            lineToRelative(1.41f, 1.41f)
            lineTo(11f, 7.83f)
            verticalLineTo(20f)
            horizontalLineToRelative(2f)
            verticalLineTo(7.83f)
            lineToRelative(5.58f, 5.59f)
            lineTo(20f, 12f)
            lineToRelative(-8f, -8f)
            lineToRelative(-8f, 8f)
            close()
        }
    }

    val ArrowDownward: ImageVector by lazy {
        icon("ArrowDownward") {
            moveTo(20f, 12f)
            lineToRelative(-1.41f, -1.41f)
            lineTo(13f, 16.17f)
            verticalLineTo(4f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(12.17f)
            lineToRelative(-5.58f, -5.59f)
            lineTo(4f, 12f)
            lineToRelative(8f, 8f)
            lineToRelative(8f, -8f)
            close()
        }
    }

    val SwapVert: ImageVector by lazy {
        icon("SwapVert") {
            moveTo(16f, 17.01f)
            verticalLineTo(10f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(7.01f)
            horizontalLineToRelative(-3f)
            lineTo(15f, 21f)
            lineToRelative(4f, -3.99f)
            horizontalLineToRelative(-3f)
            close()
            moveTo(9f, 3f)
            lineTo(5f, 6.99f)
            horizontalLineToRelative(3f)
            verticalLineTo(14f)
            horizontalLineToRelative(2f)
            verticalLineTo(6.99f)
            horizontalLineToRelative(3f)
            lineTo(9f, 3f)
            close()
        }
    }

    val FilterList: ImageVector by lazy {
        icon("FilterList") {
            moveTo(10f, 18f)
            horizontalLineToRelative(4f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(-4f)
            verticalLineToRelative(2f)
            close()
            moveTo(3f, 6f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(18f)
            verticalLineTo(6f)
            horizontalLineTo(3f)
            close()
            moveTo(6f, 13f)
            horizontalLineToRelative(12f)
            verticalLineToRelative(-2f)
            horizontalLineTo(6f)
            verticalLineToRelative(2f)
            close()
        }
    }

    val PlaylistPlay: ImageVector by lazy {
        icon("PlaylistPlay", autoMirror = true) {
            moveTo(3f, 10f)
            horizontalLineToRelative(11f)
            verticalLineToRelative(2f)
            horizontalLineTo(3f)
            verticalLineToRelative(-2f)
            close()
            moveTo(3f, 6f)
            horizontalLineToRelative(11f)
            verticalLineToRelative(2f)
            horizontalLineTo(3f)
            verticalLineTo(6f)
            close()
            moveTo(3f, 14f)
            horizontalLineToRelative(7f)
            verticalLineToRelative(2f)
            horizontalLineTo(3f)
            verticalLineToRelative(-2f)
            close()
            moveTo(16f, 13f)
            verticalLineToRelative(8f)
            lineToRelative(6f, -4f)
            lineToRelative(-6f, -4f)
            close()
        }
    }

    val QueueMusic: ImageVector by lazy {
        icon("QueueMusic", autoMirror = true) {
            moveTo(15f, 6f)
            horizontalLineTo(3f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(12f)
            verticalLineTo(6f)
            close()
            moveTo(15f, 10f)
            horizontalLineTo(3f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(12f)
            verticalLineToRelative(-2f)
            close()
            moveTo(3f, 16f)
            horizontalLineToRelative(8f)
            verticalLineToRelative(-2f)
            horizontalLineTo(3f)
            verticalLineToRelative(2f)
            close()
            moveTo(17f, 6f)
            verticalLineToRelative(8.18f)
            curveToRelative(-0.31f, -0.11f, -0.65f, -0.18f, -1f, -0.18f)
            curveToRelative(-1.66f, 0f, -3f, 1.34f, -3f, 3f)
            reflectiveCurveToRelative(1.34f, 3f, 3f, 3f)
            reflectiveCurveToRelative(3f, -1.34f, 3f, -3f)
            verticalLineTo(8f)
            horizontalLineToRelative(3f)
            verticalLineTo(6f)
            horizontalLineToRelative(-5f)
            close()
        }
    }

    val PlaylistAdd: ImageVector by lazy {
        icon("PlaylistAdd", autoMirror = true) {
            moveTo(14f, 10f)
            horizontalLineTo(3f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(11f)
            verticalLineToRelative(-2f)
            close()
            moveTo(14f, 6f)
            horizontalLineTo(3f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(11f)
            verticalLineTo(6f)
            close()
            moveTo(18f, 14f)
            verticalLineToRelative(-4f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(4f)
            horizontalLineToRelative(-4f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(4f)
            verticalLineToRelative(4f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(-4f)
            horizontalLineToRelative(4f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(-4f)
            close()
            moveTo(3f, 16f)
            horizontalLineToRelative(7f)
            verticalLineToRelative(-2f)
            horizontalLineTo(3f)
            verticalLineToRelative(2f)
            close()
        }
    }

    val MoreHoriz: ImageVector by lazy {
        icon("MoreHoriz") {
            moveTo(6f, 10f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            reflectiveCurveToRelative(0.9f, 2f, 2f, 2f)
            reflectiveCurveToRelative(2f, -0.9f, 2f, -2f)
            reflectiveCurveToRelative(-0.9f, -2f, -2f, -2f)
            close()
            moveTo(18f, 10f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            reflectiveCurveToRelative(0.9f, 2f, 2f, 2f)
            reflectiveCurveToRelative(2f, -0.9f, 2f, -2f)
            reflectiveCurveToRelative(-0.9f, -2f, -2f, -2f)
            close()
            moveTo(12f, 10f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            reflectiveCurveToRelative(0.9f, 2f, 2f, 2f)
            reflectiveCurveToRelative(2f, -0.9f, 2f, -2f)
            reflectiveCurveToRelative(-0.9f, -2f, -2f, -2f)
            close()
        }
    }

    val VolumeUp: ImageVector by lazy {
        icon("VolumeUp", autoMirror = true) {
            moveTo(3f, 9f)
            verticalLineToRelative(6f)
            horizontalLineToRelative(4f)
            lineToRelative(5f, 5f)
            verticalLineTo(4f)
            lineTo(7f, 9f)
            horizontalLineTo(3f)
            close()
            moveTo(16.5f, 12f)
            curveToRelative(0f, -1.77f, -1.02f, -3.29f, -2.5f, -4.03f)
            verticalLineToRelative(8.05f)
            curveToRelative(1.48f, -0.73f, 2.5f, -2.25f, 2.5f, -4.02f)
            close()
            moveTo(14f, 3.23f)
            verticalLineToRelative(2.06f)
            curveToRelative(2.89f, 0.86f, 5f, 3.54f, 5f, 6.71f)
            reflectiveCurveToRelative(-2.11f, 5.85f, -5f, 6.71f)
            verticalLineToRelative(2.06f)
            curveToRelative(4.01f, -0.91f, 7f, -4.49f, 7f, -8.77f)
            reflectiveCurveToRelative(-2.99f, -7.86f, -7f, -8.77f)
            close()
        }
    }

    val Shuffle: ImageVector by lazy {
        icon("Shuffle") {
            moveTo(10.59f, 9.17f)
            lineTo(5.41f, 4f)
            lineTo(4f, 5.41f)
            lineToRelative(5.17f, 5.17f)
            lineToRelative(1.42f, -1.41f)
            close()
            moveTo(14.5f, 4f)
            lineToRelative(2.04f, 2.04f)
            lineTo(4f, 18.59f)
            lineTo(5.41f, 20f)
            lineTo(17.96f, 7.46f)
            lineTo(20f, 9.5f)
            verticalLineTo(4f)
            horizontalLineToRelative(-5.5f)
            close()
            moveTo(14.83f, 13.41f)
            lineToRelative(-1.41f, 1.41f)
            lineToRelative(3.13f, 3.13f)
            lineTo(14.5f, 20f)
            horizontalLineTo(20f)
            verticalLineToRelative(-5.5f)
            lineToRelative(-2.04f, 2.04f)
            lineToRelative(-3.13f, -3.13f)
            close()
        }
    }

    val Repeat: ImageVector by lazy {
        icon("Repeat") {
            moveTo(7f, 7f)
            horizontalLineToRelative(10f)
            verticalLineToRelative(3f)
            lineToRelative(4f, -4f)
            lineToRelative(-4f, -4f)
            verticalLineToRelative(3f)
            horizontalLineTo(5f)
            verticalLineToRelative(6f)
            horizontalLineToRelative(2f)
            verticalLineTo(7f)
            close()
            moveTo(17f, 17f)
            horizontalLineTo(7f)
            verticalLineToRelative(-3f)
            lineToRelative(-4f, 4f)
            lineToRelative(4f, 4f)
            verticalLineToRelative(-3f)
            horizontalLineToRelative(12f)
            verticalLineToRelative(-6f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(4f)
            close()
        }
    }

    val RepeatOne: ImageVector by lazy {
        icon("RepeatOne") {
            moveTo(7f, 7f)
            horizontalLineToRelative(10f)
            verticalLineToRelative(3f)
            lineToRelative(4f, -4f)
            lineToRelative(-4f, -4f)
            verticalLineToRelative(3f)
            horizontalLineTo(5f)
            verticalLineToRelative(6f)
            horizontalLineToRelative(2f)
            verticalLineTo(7f)
            close()
            moveTo(17f, 17f)
            horizontalLineTo(7f)
            verticalLineToRelative(-3f)
            lineToRelative(-4f, 4f)
            lineToRelative(4f, 4f)
            verticalLineToRelative(-3f)
            horizontalLineToRelative(12f)
            verticalLineToRelative(-6f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(4f)
            close()
            moveTo(13f, 15f)
            verticalLineTo(9f)
            horizontalLineToRelative(-1f)
            lineToRelative(-2f, 1f)
            verticalLineToRelative(1f)
            horizontalLineToRelative(1.5f)
            verticalLineToRelative(4f)
            horizontalLineTo(13f)
            close()
        }
    }

    val DeleteOutline: ImageVector by lazy {
        icon("DeleteOutline") {
            moveTo(6f, 19f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(8f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(7f)
            horizontalLineTo(6f)
            verticalLineToRelative(12f)
            close()
            moveTo(8f, 9f)
            horizontalLineToRelative(8f)
            verticalLineToRelative(10f)
            horizontalLineTo(8f)
            verticalLineTo(9f)
            close()
            moveTo(15.5f, 4f)
            lineToRelative(-1f, -1f)
            horizontalLineToRelative(-5f)
            lineToRelative(-1f, 1f)
            horizontalLineTo(5f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(14f)
            verticalLineTo(4f)
            horizontalLineToRelative(-3.5f)
            close()
        }
    }

    val DragHandle: ImageVector by lazy {
        icon("DragHandle") {
            moveTo(20f, 9f)
            horizontalLineTo(4f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(16f)
            verticalLineTo(9f)
            close()
            moveTo(4f, 15f)
            horizontalLineToRelative(16f)
            verticalLineToRelative(-2f)
            horizontalLineTo(4f)
            verticalLineToRelative(2f)
            close()
        }
    }

    val BookmarkAdd: ImageVector by lazy {
        icon("BookmarkAdd") {
            moveTo(21f, 7f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(-2f)
            verticalLineTo(7f)
            horizontalLineToRelative(-2f)
            verticalLineTo(5f)
            horizontalLineToRelative(2f)
            verticalLineTo(3f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(2f)
            close()
            moveTo(19f, 21f)
            lineToRelative(-7f, -3f)
            lineToRelative(-7f, 3f)
            verticalLineTo(5f)
            curveToRelative(0f, -1.1f, 0.9f, -2f, 2f, -2f)
            horizontalLineToRelative(7f)
            curveToRelative(-0.63f, 0.84f, -1f, 1.87f, -1f, 3f)
            curveToRelative(0f, 2.76f, 2.24f, 5f, 5f, 5f)
            curveToRelative(0.34f, 0f, 0.68f, -0.03f, 1f, -0.1f)
            verticalLineTo(21f)
            close()
        }
    }

    private fun icon(
        name: String,
        autoMirror: Boolean = false,
        block: PathBuilder.() -> Unit,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = autoMirror,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            block()
        }
    }.build()
}
