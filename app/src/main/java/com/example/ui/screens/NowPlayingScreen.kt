package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.audio.SpectrumSnapshot
import com.example.data.RepeatMode
import com.example.data.Track
import com.example.ui.components.LiveAudioVisualizerSection
import com.example.ui.components.MinimalSkipIcon
import com.example.ui.components.MorphingPlayPauseIcon
import com.example.ui.components.TactileCapsuleButton
import com.example.ui.components.TactileRoundButton
import com.example.ui.components.TurntableDeck
import com.example.ui.theme.DigitalMonoFontFamily
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.LocalPlinthColors
import com.example.viewmodel.formatDuration

@Composable
fun NowPlayingScreen(
    track: Track,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    volume: Float,
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    isFavorite: Boolean,
    sleepTimerRemainingMs: Long,
    spectrum: SpectrumSnapshot,
    trackChangeNonce: Int,
    trackChangeDirection: Int,
    hapticsEnabled: Boolean,
    reducedMotion: Boolean,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onScrubDeltaMs: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onOpenQueue: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenAddToPlaylist: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPlinthColors.current
    val strings = LocalAppStrings.current
    var menuExpanded by remember { mutableStateOf(false) }
    var showVolumeStrip by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    val safeDuration = durationMs.coerceAtLeast(1L)
    val progressFraction = (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(palette.canvasTop, palette.canvasBottom))
            ),
    ) {
        // Architectural Lower Convex Dome Backdrop (signature reference detail!)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val domeStartY = h * 0.685f
            val domePeakY = h * 0.615f

            val domePath = Path().apply {
                moveTo(0f, domeStartY)
                quadraticTo(w * 0.5f, domePeakY, w, domeStartY)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(
                path = domePath,
                brush = Brush.verticalGradient(
                    colors = listOf(palette.domeTop, palette.domeBottom),
                    startY = domePeakY,
                    endY = h,
                ),
            )

            // Delicate studio specular crest along the convex dome edge
            val crestPath = Path().apply {
                moveTo(0f, domeStartY)
                quadraticTo(w * 0.5f, domePeakY, w, domeStartY)
            }
            drawPath(
                path = crestPath,
                color = palette.plinthEdgeLight.copy(alpha = if (palette.isDark) 0.25f else 0.9f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.4.dp.toPx()),
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .widthIn(max = 560.dp)
                .align(Alignment.TopCenter),
        ) {
            // Minimal Top Navigation Bar ("<" back + optional timer pill + "..." menu)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("now_playing_back_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = palette.inkPrimary,
                        modifier = Modifier.size(28.dp),
                    )
                }

                // Subtle RPM / Sleep Timer status badge
                Text(
                    text = if (sleepTimerRemainingMs > 0L) {
                        strings.timerRemaining(formatDuration(sleepTimerRemainingMs))
                    } else {
                        strings.rpmLabel
                    },
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = DigitalMonoFontFamily,
                        letterSpacing = 1.2.sp,
                    ),
                    color = palette.inkSecondary,
                )

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("now_playing_menu_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreHoriz,
                            contentDescription = stringResource(R.string.cd_menu),
                            tint = palette.inkPrimary,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        containerColor = palette.capsuleBg,
                    ) {
                        DropdownMenuItem(
                            text = { Text(strings.addToPlaylist, color = palette.inkPrimary) },
                            onClick = {
                                menuExpanded = false
                                onOpenAddToPlaylist()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(strings.equalizer, color = palette.inkPrimary) },
                            onClick = {
                                menuExpanded = false
                                onOpenEqualizer()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(strings.queue, color = palette.inkPrimary) },
                            onClick = {
                                menuExpanded = false
                                onOpenQueue()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(strings.settings, color = palette.inkPrimary) },
                            onClick = {
                                menuExpanded = false
                                onOpenSettings()
                            },
                        )
                    }
                }
            }

            // Hero Top-Down Luxury Turntable Deck
            TurntableDeck(
                artworkUri = track.artworkUri,
                isPlaying = isPlaying,
                progressFraction = progressFraction,
                volume = volume,
                trackChangeNonce = trackChangeNonce,
                trackChangeDirection = trackChangeDirection,
                hapticsEnabled = hapticsEnabled,
                reducedMotion = reducedMotion,
                onTogglePlayPause = onTogglePlayPause,
                onScrubDeltaMs = onScrubDeltaMs,
                onVolumeChange = onVolumeChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .weight(1f, fill = false),
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Track Title & Artist (Centered, generous minimalist hierarchy)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 21.sp,
                    ),
                    color = palette.inkPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("now_playing_title"),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                    color = palette.inkSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("now_playing_artist"),
                )
            }

            Spacer(modifier = Modifier.weight(0.55f))

            // Live FFT Audio Visualizer + Digital Timer + Interactive Scrub Bar
            LiveAudioVisualizerSection(
                spectrum = spectrum,
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                onSeekTo = onSeekTo,
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Optional collapsible tactile hardware volume slider
            AnimatedVisibility(
                visible = showVolumeStrip,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(palette.capsuleBg.copy(alpha = 0.85f))
                        .border(1.dp, palette.hairline, RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 2.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = strings.volume,
                        tint = palette.inkSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Slider(
                        value = volume,
                        onValueChange = onVolumeChange,
                        colors = SliderDefaults.colors(
                            thumbColor = palette.inkPrimary,
                            activeTrackColor = palette.inkPrimary,
                            inactiveTrackColor = palette.hairline,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("volume_slider"),
                    )
                }
            }

            // Primary Tactile Hardware Transport Row (Exact Reference Silhouette!)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            ) {
                // Left Capsule: Previous Track
                TactileCapsuleButton(
                    onClick = onPrevious,
                    contentDescription = stringResource(R.string.cd_previous),
                    cornerRadius = 26.dp,
                    hapticsEnabled = hapticsEnabled,
                    modifier = Modifier
                        .width(88.dp)
                        .height(50.dp)
                        .testTag("btn_previous"),
                ) {
                    MinimalSkipIcon(
                        isNext = false,
                        tint = palette.inkPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }

                // Center Large Circular Hardware Dial: Play / Pause
                TactileRoundButton(
                    onClick = onTogglePlayPause,
                    contentDescription = if (isPlaying) stringResource(R.string.cd_pause) else stringResource(R.string.cd_play),
                    size = 76.dp,
                    hapticsEnabled = hapticsEnabled,
                    active = isPlaying,
                    modifier = Modifier.testTag("btn_play_pause"),
                ) {
                    // Subtle Braun top notch indicator + Morphing Play/Pause center glyph
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawLine(
                                color = if (isPlaying) palette.inkPrimary else palette.inkMuted,
                                start = Offset(size.width / 2f, 9.dp.toPx()),
                                end = Offset(size.width / 2f, 15.dp.toPx()),
                                strokeWidth = 2.dp.toPx(),
                                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                            )
                        }
                        MorphingPlayPauseIcon(
                            isPlaying = isPlaying,
                            tint = palette.inkPrimary,
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .size(22.dp),
                        )
                    }
                }

                // Right Capsule: Next Track
                TactileCapsuleButton(
                    onClick = onNext,
                    contentDescription = stringResource(R.string.cd_next),
                    cornerRadius = 26.dp,
                    hapticsEnabled = hapticsEnabled,
                    modifier = Modifier
                        .width(88.dp)
                        .height(50.dp)
                        .testTag("btn_next"),
                ) {
                    MinimalSkipIcon(
                        isNext = true,
                        tint = palette.inkPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Secondary Minimal Controls Row (Shuffle, Favorite, Volume, Queue, Repeat — all 48dp targets)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 6.dp),
            ) {
                IconButton(
                    onClick = onToggleShuffle,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("btn_shuffle"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = stringResource(R.string.cd_shuffle),
                        tint = if (shuffleEnabled) palette.inkPrimary else palette.inkMuted,
                        modifier = Modifier.size(21.dp),
                    )
                }

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("btn_favorite"),
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = stringResource(R.string.cd_favorite),
                        tint = if (isFavorite) palette.favoriteRed else palette.inkMuted,
                        modifier = Modifier.size(21.dp),
                    )
                }

                IconButton(
                    onClick = { showVolumeStrip = !showVolumeStrip },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("btn_volume"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = stringResource(R.string.cd_volume),
                        tint = if (showVolumeStrip) palette.inkPrimary else palette.inkMuted,
                        modifier = Modifier.size(21.dp),
                    )
                }

                IconButton(
                    onClick = onOpenQueue,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("btn_queue"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = stringResource(R.string.cd_queue),
                        tint = palette.inkMuted,
                        modifier = Modifier.size(22.dp),
                    )
                }

                IconButton(
                    onClick = onCycleRepeat,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("btn_repeat"),
                ) {
                    Icon(
                        imageVector = if (repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        contentDescription = stringResource(R.string.cd_repeat),
                        tint = if (repeatMode != RepeatMode.OFF) palette.inkPrimary else palette.inkMuted,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}
