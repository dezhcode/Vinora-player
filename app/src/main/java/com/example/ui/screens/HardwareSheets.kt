package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.EqualizerState
import com.example.audio.SpectrumSnapshot
import com.example.data.PlaylistSummary
import com.example.data.Track
import com.example.data.UserPreferences
import com.example.ui.components.TactileCapsuleButton
import com.example.ui.i18n.AppLanguage
import com.example.ui.theme.DigitalMonoFontFamily
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.LocalPlinthColors
import com.example.ui.theme.ThemeMode
import com.example.viewmodel.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(
    queue: List<Track>,
    currentTrackId: String?,
    onSelectTrack: (Track) -> Unit,
    onRemoveIndex: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalPlinthColors.current
    val strings = LocalAppStrings.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.canvasTop,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .testTag("queue_sheet"),
        ) {
            Text(
                text = strings.queue,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = palette.inkPrimary,
                modifier = Modifier.padding(bottom = 14.dp),
            )
            if (queue.isEmpty()) {
                Text(
                    text = strings.emptyQueue,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.inkSecondary,
                    modifier = Modifier.padding(vertical = 32.dp),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.heightIn(max = 420.dp),
                ) {
                    itemsIndexed(queue, key = { idx, t -> "${t.id}_$idx" }) { index, track ->
                        CompactVinylTrackRow(
                            track = track,
                            isActive = track.id == currentTrackId,
                            onClick = { onSelectTrack(track) },
                            trailing = {
                                if (queue.size > 1) {
                                    IconButton(
                                        onClick = { onRemoveIndex(index) },
                                        modifier = Modifier.size(44.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = strings.removeFromQueue,
                                            tint = palette.inkSecondary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EqualizerBottomSheet(
    eqState: EqualizerState,
    spectrum: SpectrumSnapshot,
    hapticsEnabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onSelectPreset: (Int) -> Unit,
    onBandChange: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalPlinthColors.current
    val strings = LocalAppStrings.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val presetNames = listOf(
        strings.presetFlat,
        strings.presetWarmVinyl,
        strings.presetBassBoost,
        strings.presetVocal,
        strings.presetAcoustic,
        strings.presetElectronic,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.canvasTop,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 6.dp)
                .testTag("equalizer_sheet"),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.equalizer,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = palette.inkPrimary,
                    )
                    Text(
                        text = if (eqState.hardwareAttached) strings.eqEnabled else strings.eqHardwareFallback,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkSecondary,
                    )
                }
                Switch(
                    checked = eqState.enabled,
                    onCheckedChange = onToggleEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = palette.capsuleBg,
                        checkedTrackColor = palette.inkPrimary,
                    ),
                    modifier = Modifier.testTag("eq_enable_switch"),
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Live Bass / Mid / Treble FFT Telemetry Bar
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(palette.capsuleBg)
                    .border(1.dp, palette.hairline, RoundedCornerShape(18.dp))
                    .padding(14.dp),
            ) {
                BandMeter(strings.bass, spectrum.bass, Modifier.weight(1f))
                BandMeter(strings.mid, spectrum.mid, Modifier.weight(1f))
                BandMeter(strings.treble, spectrum.treble, Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Preset Chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                presetNames.forEachIndexed { idx, label ->
                    val selected = eqState.enabled && eqState.presetIndex == idx
                    TactileCapsuleButton(
                        onClick = { onSelectPreset(idx) },
                        contentDescription = label,
                        cornerRadius = 16.dp,
                        hapticsEnabled = hapticsEnabled,
                        active = selected,
                        modifier = Modifier.height(36.dp),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            ),
                            color = if (selected) palette.inkPrimary else palette.inkSecondary,
                            modifier = Modifier.padding(horizontal = 14.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 5 Hardware Band Sliders
            eqState.bandLabels.forEachIndexed { idx, freqLabel ->
                val level = eqState.bandLevelsDb.getOrElse(idx) { 0 }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = freqLabel,
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = DigitalMonoFontFamily),
                        color = palette.inkSecondary,
                        modifier = Modifier.width(52.dp),
                    )
                    Slider(
                        value = level.toFloat(),
                        onValueChange = { onBandChange(idx, it.toInt()) },
                        valueRange = -12f..12f,
                        enabled = eqState.enabled,
                        colors = SliderDefaults.colors(
                            thumbColor = palette.inkPrimary,
                            activeTrackColor = palette.inkPrimary,
                            inactiveTrackColor = palette.hairline,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (level >= 0) "+${level}dB" else "${level}dB",
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = DigitalMonoFontFamily),
                        color = palette.inkPrimary,
                        modifier = Modifier.width(46.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

@Composable
private fun BandMeter(label: String, level: Float, modifier: Modifier = Modifier) {
    val palette = LocalPlinthColors.current
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = DigitalMonoFontFamily,
                fontSize = 10.sp,
            ),
            color = palette.inkSecondary,
        )
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { level.coerceIn(0f, 1f) },
            color = palette.inkPrimary,
            trackColor = palette.hairline,
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsBottomSheet(
    preferences: UserPreferences,
    sleepTimerRemainingMs: Long,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetSleepTimerMinutes: (Int) -> Unit,
    onSetHaptics: (Boolean) -> Unit,
    onSetReducedMotion: (Boolean) -> Unit,
    onImportAudio: () -> Unit,
    onScanDevice: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalPlinthColors.current
    val strings = LocalAppStrings.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.canvasTop,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 6.dp)
                .testTag("settings_sheet"),
        ) {
            Text(
                text = strings.settings,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = palette.inkPrimary,
            )

            // Appearance Mode (Studio Light / Titanium Dark / System)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(strings.themeMode, style = MaterialTheme.typography.labelLarge, color = palette.inkSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        ThemeMode.LIGHT to strings.themeLight,
                        ThemeMode.DARK to strings.themeDark,
                        ThemeMode.SYSTEM to strings.themeSystem,
                    ).forEach { (mode, label) ->
                        val active = preferences.themeMode == mode
                        TactileCapsuleButton(
                            onClick = { onSetThemeMode(mode) },
                            contentDescription = label,
                            cornerRadius = 16.dp,
                            hapticsEnabled = preferences.hapticsEnabled,
                            active = active,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("theme_${mode.name.lowercase()}"),
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                ),
                                color = if (active) palette.inkPrimary else palette.inkSecondary,
                            )
                        }
                    }
                }
            }

            // Language (English LTR / فارسی RTL)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(strings.language, style = MaterialTheme.typography.labelLarge, color = palette.inkSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        AppLanguage.EN to "English",
                        AppLanguage.FA to "فارسی (RTL)",
                    ).forEach { (lang, label) ->
                        val active = preferences.language == lang
                        TactileCapsuleButton(
                            onClick = { onSetLanguage(lang) },
                            contentDescription = label,
                            cornerRadius = 16.dp,
                            hapticsEnabled = preferences.hapticsEnabled,
                            active = active,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("lang_${lang.name.lowercase()}"),
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                ),
                                color = if (active) palette.inkPrimary else palette.inkSecondary,
                            )
                        }
                    }
                }
            }

            // Sleep Timer
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(strings.sleepTimer, style = MaterialTheme.typography.labelLarge, color = palette.inkSecondary)
                    if (sleepTimerRemainingMs > 0L) {
                        Text(
                            text = strings.timerRemaining(formatDuration(sleepTimerRemainingMs)),
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = DigitalMonoFontFamily),
                            color = palette.inkPrimary,
                        )
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(0, 5, 15, 30, 60).forEach { mins ->
                        val label = if (mins == 0) strings.timerOff else strings.timerMinutes(mins)
                        TactileCapsuleButton(
                            onClick = { onSetSleepTimerMinutes(mins) },
                            contentDescription = label,
                            cornerRadius = 16.dp,
                            hapticsEnabled = preferences.hapticsEnabled,
                            active = (mins == 0 && sleepTimerRemainingMs == 0L),
                            modifier = Modifier.height(38.dp),
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.inkPrimary,
                                modifier = Modifier.padding(horizontal = 14.dp),
                            )
                        }
                    }
                }
            }

            // Haptics & Reduced Motion Accessibility Toggles
            SettingsToggleRow(
                label = strings.haptics,
                checked = preferences.hapticsEnabled,
                onCheckedChange = onSetHaptics,
            )
            SettingsToggleRow(
                label = strings.reducedMotion,
                checked = preferences.reducedMotion,
                onCheckedChange = onSetReducedMotion,
            )

            // Offline Library Actions
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                TactileCapsuleButton(
                    onClick = {
                        onDismiss()
                        onImportAudio()
                    },
                    contentDescription = strings.importAudio,
                    cornerRadius = 18.dp,
                    hapticsEnabled = preferences.hapticsEnabled,
                    modifier = Modifier.weight(1f).height(46.dp),
                ) {
                    Text(strings.importAudio, style = MaterialTheme.typography.labelLarge, color = palette.inkPrimary)
                }
                TactileCapsuleButton(
                    onClick = {
                        onDismiss()
                        onScanDevice()
                    },
                    contentDescription = strings.scanDevice,
                    cornerRadius = 18.dp,
                    hapticsEnabled = preferences.hapticsEnabled,
                    modifier = Modifier.weight(1f).height(46.dp),
                ) {
                    Text(strings.scanDevice, style = MaterialTheme.typography.labelLarge, color = palette.inkPrimary)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val palette = LocalPlinthColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.capsuleBg)
            .border(1.dp, palette.hairline, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.titleSmall, color = palette.inkPrimary)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = palette.capsuleBg,
                checkedTrackColor = palette.inkPrimary,
            ),
        )
    }
}

@Composable
fun CreatePlaylistDialog(
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalPlinthColors.current
    val strings = LocalAppStrings.current
    var title by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.capsuleBg,
        title = { Text(strings.createPlaylist, color = palette.inkPrimary) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text(strings.playlistNameHint, color = palette.inkSecondary) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("playlist_name_input"),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isNotBlank()) {
                        onCreate(title)
                        onDismiss()
                    }
                },
                modifier = Modifier.testTag("playlist_create_confirm"),
            ) {
                Text(strings.create, color = palette.inkPrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel, color = palette.inkSecondary)
            }
        },
    )
}

@Composable
fun AddToPlaylistDialog(
    track: Track,
    playlists: List<PlaylistSummary>,
    onSelectPlaylist: (PlaylistSummary) -> Unit,
    onCreateNewPlaylist: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalPlinthColors.current
    val strings = LocalAppStrings.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.capsuleBg,
        title = { Text(strings.addToPlaylist, color = palette.inkPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (playlists.isEmpty()) {
                    Text(strings.emptyPlaylists, style = MaterialTheme.typography.bodySmall, color = palette.inkSecondary)
                } else {
                    playlists.forEach { pl ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(palette.canvasTop)
                                .border(1.dp, palette.hairline, RoundedCornerShape(14.dp))
                                .clickable {
                                    onSelectPlaylist(pl)
                                    onDismiss()
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            Text(
                                text = "${pl.name} (${pl.trackIds.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = palette.inkPrimary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onCreateNewPlaylist()
                },
            ) {
                Text(strings.createPlaylist, color = palette.inkPrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel, color = palette.inkSecondary)
            }
        },
    )
}
