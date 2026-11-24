package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.LocalPlinthColors

enum class MainTab {
    HOME, EXPLORE, LIBRARY
}

/**
 * Ultra-minimal 3-icon bottom navigation bar crafted after the reference image:
 * Home (rounded house + arch), Explore (iconic orbital Saturn/vinyl planet), Library (bookmark crate).
 */
@Composable
fun PlinthBottomNav(
    selectedTab: MainTab,
    hapticsEnabled: Boolean,
    onSelectTab: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPlinthColors.current
    val strings = LocalAppStrings.current
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.navBarBg)
            .border(width = 0.75.dp, color = palette.hairline.copy(alpha = 0.65f))
            .navigationBarsPadding(),
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp),
        ) {
            NavIconSlot(
                label = strings.home,
                selected = selectedTab == MainTab.HOME,
                testTag = "nav_home",
                onClick = {
                    if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelectTab(MainTab.HOME)
                },
            ) { color, selected ->
                MinimalHomeIcon(color = color, filled = selected, modifier = Modifier.size(23.dp))
            }

            NavIconSlot(
                label = strings.explore,
                selected = selectedTab == MainTab.EXPLORE,
                testTag = "nav_explore",
                onClick = {
                    if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelectTab(MainTab.EXPLORE)
                },
            ) { color, selected ->
                OrbitalPlanetIcon(color = color, filled = selected, modifier = Modifier.size(26.dp))
            }

            NavIconSlot(
                label = strings.library,
                selected = selectedTab == MainTab.LIBRARY,
                testTag = "nav_library",
                onClick = {
                    if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelectTab(MainTab.LIBRARY)
                },
            ) { color, selected ->
                MinimalLibraryIcon(color = color, filled = selected, modifier = Modifier.size(23.dp))
            }
        }
    }
}

@Composable
private fun NavIconSlot(
    label: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    icon: @Composable (Color, Boolean) -> Unit,
) {
    val palette = LocalPlinthColors.current
    val tint = if (selected) palette.inkPrimary else palette.inkSecondary.copy(alpha = 0.72f)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(56.dp)
            .testTag(testTag)
            .semantics { contentDescription = label }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
    ) {
        icon(tint, selected)
    }
}

@Composable
private fun MinimalHomeIcon(color: Color, filled: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()
        val housePath = Path().apply {
            moveTo(w * 0.14f, h * 0.44f)
            lineTo(w * 0.50f, h * 0.14f)
            lineTo(w * 0.86f, h * 0.44f)
            lineTo(w * 0.86f, h * 0.82f)
            quadraticTo(w * 0.86f, h * 0.88f, w * 0.78f, h * 0.88f)
            lineTo(w * 0.22f, h * 0.88f)
            quadraticTo(w * 0.14f, h * 0.88f, w * 0.14f, h * 0.82f)
            close()
        }
        if (filled) {
            drawPath(housePath, color = color)
        } else {
            drawPath(
                housePath,
                color = color,
                style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            // Tiny warm smile/door detail inside matching the reference icon
            drawArc(
                color = color,
                startAngle = 20f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(w * 0.36f, h * 0.56f),
                size = Size(w * 0.28f, h * 0.18f),
                style = Stroke(width = stroke * 0.9f, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun OrbitalPlanetIcon(color: Color, filled: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension * 0.30f
        val stroke = 1.9.dp.toPx()

        if (filled) {
            drawCircle(color = color, radius = r, center = Offset(cx, cy))
        } else {
            drawCircle(
                color = color,
                radius = r,
                center = Offset(cx, cy),
                style = Stroke(width = stroke),
            )
        }

        // Iconic tilted orbital ring matching the reference Saturn Explore icon!
        rotate(-28f, pivot = Offset(cx, cy)) {
            drawOval(
                color = color,
                topLeft = Offset(cx - size.width * 0.46f, cy - size.height * 0.14f),
                size = Size(size.width * 0.92f, size.height * 0.28f),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun MinimalLibraryIcon(color: Color, filled: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()

        // Outer rounded crate/album frame
        if (filled) {
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.16f, h * 0.14f),
                size = Size(w * 0.68f, h * 0.74f),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            )
        } else {
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.16f, h * 0.14f),
                size = Size(w * 0.68f, h * 0.74f),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                style = Stroke(width = stroke),
            )
            // Bookmark notch inside
            val notch = Path().apply {
                moveTo(w * 0.38f, h * 0.15f)
                lineTo(w * 0.38f, h * 0.56f)
                lineTo(w * 0.52f, h * 0.45f)
                lineTo(w * 0.66f, h * 0.56f)
                lineTo(w * 0.66f, h * 0.15f)
            }
            drawPath(
                notch,
                color = color,
                style = Stroke(width = stroke * 0.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}
