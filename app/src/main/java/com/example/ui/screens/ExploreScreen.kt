package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.audio.FeedbackEvent
import com.example.audio.PlinthFeedback
import com.example.data.Track
import com.example.ui.components.CurvedDiscCarousel
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.LocalPlinthColors

@Composable
fun ExploreScreen(
    tracks: List<Track>,
    activeTrackId: String?,
    isPlaying: Boolean,
    hapticsEnabled: Boolean,
    reducedMotion: Boolean,
    onSelectTrackAndOpenPlayer: (Track) -> Unit,
    onImportAudio: () -> Unit,
    onScanDevice: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPlinthColors.current
    val strings = LocalAppStrings.current
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        // Minimal Reference Header: "Explore" left + "..." right
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 14.dp, top = 16.dp, bottom = 4.dp),
        ) {
            Text(
                text = strings.explore,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 25.sp,
                ),
                color = palette.inkPrimary,
            )

            Box {
                IconButton(
                    onClick = {
                        PlinthFeedback.trigger(FeedbackEvent.TAP_SUBTLE)
                        menuExpanded = true
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("explore_menu_button"),
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
                        text = { Text(strings.importAudio, color = palette.inkPrimary) },
                        onClick = {
                            PlinthFeedback.trigger(FeedbackEvent.TAP_SUBTLE)
                            menuExpanded = false
                            onImportAudio()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(strings.scanDevice, color = palette.inkPrimary) },
                        onClick = {
                            PlinthFeedback.trigger(FeedbackEvent.TAP_SUBTLE)
                            menuExpanded = false
                            onScanDevice()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(strings.equalizer, color = palette.inkPrimary) },
                        onClick = {
                            PlinthFeedback.trigger(FeedbackEvent.TAP_SUBTLE)
                            menuExpanded = false
                            onOpenEqualizer()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(strings.settings, color = palette.inkPrimary) },
                        onClick = {
                            PlinthFeedback.trigger(FeedbackEvent.TAP_SUBTLE)
                            menuExpanded = false
                            onOpenSettings()
                        },
                    )
                }
            }
        }

        // Signature Orbital Curved Disc Carousel
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            CurvedDiscCarousel(
                tracks = tracks,
                activeTrackId = activeTrackId,
                isPlaying = isPlaying,
                hapticsEnabled = hapticsEnabled,
                reducedMotion = reducedMotion,
                onSelectAndPlay = onSelectTrackAndOpenPlayer,
            )
        }
    }
}
