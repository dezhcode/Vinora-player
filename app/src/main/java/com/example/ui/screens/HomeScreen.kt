package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.FeedbackEvent
import com.example.audio.PlinthFeedback
import com.example.data.HomeFilterType
import com.example.data.HomeSortField
import com.example.data.HomeTrackSorter
import com.example.data.Track
import com.example.ui.components.CircularArtworkDisc
import com.example.ui.components.MorphingPlayPauseIcon
import com.example.ui.components.PlinthIcons
import com.example.ui.components.TactileCapsuleButton
import com.example.ui.components.TactileRoundButton
import com.example.ui.components.plinthSoftShadow
import com.example.ui.theme.DigitalMonoFontFamily
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.LocalPlinthColors
import com.example.viewmodel.formatDuration

@Composable
fun HomeScreen(
    currentTrack: Track?,
    isPlaying: Boolean,
    recentTracks: List<Track>,
    favoriteTracks: List<Track>,
    allTracks: List<Track>,
    sleepTimerRemainingMs: Long,
    isScanningDevice: Boolean,
    hapticsEnabled: Boolean,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayNext: (Track) -> Unit = {},
    onAddToQueue: (Track) -> Unit = {},
    onOpenAddToPlaylist: (Track) -> Unit = {},
    onTogglePlayPause: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenSettings: () -> Unit,
    onImportAudio: () -> Unit,
    onScanDevice: () -> Unit,
    modifier: Modifier = Modifier,
    favoriteIds: Set<String> = emptySet(),
    sortField: HomeSortField = HomeSortField.DATE,
    sortAscending: Boolean = false,
    filterType: HomeFilterType = HomeFilterType.ALL,
    onSortChange: (HomeSortField, Boolean) -> Unit = { _, _ -> },
    onFilterChange: (HomeFilterType) -> Unit = {},
    onToggleFavorite: (String) -> Unit = {},
) {
    val palette = LocalPlinthColors.current
    val strings = LocalAppStrings.current

    var activeSortField by remember(sortField) { mutableStateOf(sortField) }
    var activeSortAscending by remember(sortAscending) { mutableStateOf(sortAscending) }
    var activeFilter by remember(filterType) { mutableStateOf(filterType) }

    val resolvedFavoriteIds = remember(favoriteIds, favoriteTracks) {
        if (favoriteIds.isNotEmpty()) favoriteIds else favoriteTracks.map { it.id }.toSet()
    }
    val recentTrackIds = remember(recentTracks) { recentTracks.map { it.id }.toSet() }

    val filteredSortedTracks = remember(
        allTracks,
        activeSortField,
        activeSortAscending,
        activeFilter,
        resolvedFavoriteIds,
        recentTrackIds,
    ) {
        HomeTrackSorter.filterAndSort(
            tracks = allTracks,
            sortField = activeSortField,
            sortAscending = activeSortAscending,
            filterType = activeFilter,
            favoriteIds = resolvedFavoriteIds,
            recentTrackIds = recentTrackIds,
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(top = 16.dp, bottom = 128.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen"),
    ) {
        // Editorial Top Bar
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            ) {
                Column {
                    Text(
                        text = strings.appName,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 25.sp,
                        ),
                        color = palette.inkPrimary,
                    )
                    Text(
                        text = strings.offlineReady,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = DigitalMonoFontFamily,
                            letterSpacing = 0.8.sp,
                        ),
                        color = palette.inkSecondary,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = {
                            PlinthFeedback.trigger(FeedbackEvent.TAP_SUBTLE)
                            onOpenSearch()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("home_search_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = strings.search,
                            tint = palette.inkPrimary,
                        )
                    }
                    IconButton(
                        onClick = {
                            PlinthFeedback.trigger(FeedbackEvent.TAP_SUBTLE)
                            onOpenSettings()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("home_settings_button"),
                    ) {
                        Icon(
                            imageVector = PlinthIcons.Tune,
                            contentDescription = strings.settings,
                            tint = palette.inkPrimary,
                        )
                    }
                }
            }
        }

        // Hero "Continue Spinning" Tactile Plinth Card
        if (currentTrack != null) {
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text = strings.continueSpinning,
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.inkSecondary,
                        modifier = Modifier.padding(start = 6.dp, bottom = 10.dp),
                    )
                    val shape = RoundedCornerShape(28.dp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .plinthSoftShadow(
                                cornerRadius = 28.dp,
                                shadowDark = palette.shadowDark,
                                shadowLight = palette.shadowLight,
                                elevation = 12.dp,
                                darkAlpha = if (palette.isDark) 0.72f else 0.36f,
                                lightAlpha = if (palette.isDark) 0.16f else 0.92f,
                            )
                            .clip(shape)
                            .background(
                                Brush.linearGradient(listOf(palette.plinthTop, palette.plinthBottom))
                            )
                            .border(1.dp, palette.plinthEdgeLight.copy(alpha = 0.75f), shape)
                            .clickable {
                                PlinthFeedback.trigger(FeedbackEvent.TAP_SUBTLE)
                                onOpenNowPlaying()
                            }
                            .padding(16.dp),
                    ) {
                        // Mini obsidian platter + cover label
                        Box(
                            modifier = Modifier
                                .size(74.dp)
                                .clip(CircleShape)
                                .background(palette.inkPrimary),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularArtworkDisc(
                                artworkUri = currentTrack.artworkUri,
                                contentDescription = currentTrack.title,
                                modifier = Modifier.size(56.dp),
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentTrack.title,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = palette.inkPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "${currentTrack.artist} • ${currentTrack.album}",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.inkSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        TactileRoundButton(
                            onClick = onTogglePlayPause,
                            contentDescription = if (isPlaying) strings.pause else strings.play,
                            size = 54.dp,
                            hapticsEnabled = hapticsEnabled,
                        ) {
                            MorphingPlayPauseIcon(
                                isPlaying = isPlaying,
                                tint = palette.inkPrimary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }

        // Studio Hardware Quick Tools (Import, Scan Device, Equalizer, Sleep Timer)
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(
                    text = strings.quickTools,
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.inkSecondary,
                    modifier = Modifier.padding(start = 6.dp, bottom = 10.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    StudioToolPill(
                        icon = PlinthIcons.AudioFile,
                        label = strings.importAudio,
                        onClick = onImportAudio,
                        hapticsEnabled = hapticsEnabled,
                        modifier = Modifier.weight(1f).testTag("home_import_audio"),
                    )
                    StudioToolPill(
                        icon = PlinthIcons.FolderOpen,
                        label = if (isScanningDevice) strings.scanning else strings.scanDevice,
                        onClick = onScanDevice,
                        hapticsEnabled = hapticsEnabled,
                        modifier = Modifier.weight(1f).testTag("home_scan_device"),
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    StudioToolPill(
                        icon = PlinthIcons.GraphicEq,
                        label = strings.equalizer,
                        onClick = onOpenEqualizer,
                        hapticsEnabled = hapticsEnabled,
                        modifier = Modifier.weight(1f).testTag("home_equalizer"),
                    )
                    StudioToolPill(
                        icon = PlinthIcons.Bedtime,
                        label = if (sleepTimerRemainingMs > 0L) {
                            formatDuration(sleepTimerRemainingMs)
                        } else {
                            strings.sleepTimer
                        },
                        onClick = onOpenSettings,
                        hapticsEnabled = hapticsEnabled,
                        active = sleepTimerRemainingMs > 0L,
                        modifier = Modifier.weight(1f).testTag("home_sleep_timer"),
                    )
                }
            }
        }

        // Recently Played Vinyl Disc Shelf
        val shelfTracks = if (recentTracks.isNotEmpty()) recentTracks else allTracks.take(6)
        if (shelfTracks.isNotEmpty()) {
            item {
                Column {
                    Text(
                        text = strings.recentlyPlayed,
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.inkSecondary,
                        modifier = Modifier.padding(start = 26.dp, end = 26.dp, bottom = 12.dp),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 22.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(shelfTracks, key = { it.id }) { track ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(86.dp)
                                    .clickable {
                                        PlinthFeedback.trigger(FeedbackEvent.PLAY_PAUSE)
                                        onPlayTrack(track, shelfTracks)
                                    },
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(78.dp)
                                        .plinthSoftShadow(
                                            cornerRadius = 39.dp,
                                            shadowDark = palette.shadowDark,
                                            shadowLight = palette.shadowLight,
                                            elevation = 7.dp,
                                            darkAlpha = if (palette.isDark) 0.7f else 0.4f,
                                            lightAlpha = if (palette.isDark) 0.14f else 0.85f,
                                        )
                                        .clip(CircleShape)
                                        .background(palette.capsuleBg)
                                        .padding(3.dp),
                                ) {
                                    CircularArtworkDisc(
                                        artworkUri = track.artworkUri,
                                        contentDescription = track.title,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.5.sp),
                                    color = palette.inkPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = track.artist,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = palette.inkSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Home Music Browser Header + Sort & Filter Controls
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = strings.allTracks,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = palette.inkPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = strings.tracksCount(filteredSortedTracks.size),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = DigitalMonoFontFamily,
                            ),
                            color = palette.inkSecondary,
                            maxLines = 1,
                            modifier = Modifier.testTag("home_tracks_count"),
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    val orderLabel = if (activeSortAscending) strings.sortAscending else strings.sortDescending
                    TactileCapsuleButton(
                        onClick = {
                            val nextAsc = !activeSortAscending
                            activeSortAscending = nextAsc
                            onSortChange(activeSortField, nextAsc)
                        },
                        contentDescription = orderLabel,
                        cornerRadius = 18.dp,
                        hapticsEnabled = hapticsEnabled,
                        active = true,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("home_sort_order_toggle"),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Icon(
                                imageVector = if (activeSortAscending) {
                                    PlinthIcons.ArrowUpward
                                } else {
                                    PlinthIcons.ArrowDownward
                                },
                                contentDescription = null,
                                tint = palette.inkPrimary,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = orderLabel,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = palette.inkPrimary,
                                maxLines = 1,
                            )
                        }
                    }
                }

                // Sort Field Pills (Date, Track Name, Artist, Album, Duration)
                val sortOptions = listOf(
                    Triple(HomeSortField.DATE, strings.sortDate, "home_sort_date"),
                    Triple(HomeSortField.TITLE, strings.sortTitle, "home_sort_title"),
                    Triple(HomeSortField.ARTIST, strings.sortArtist, "home_sort_artist"),
                    Triple(HomeSortField.ALBUM, strings.sortAlbum, "home_sort_album"),
                    Triple(HomeSortField.DURATION, strings.sortDuration, "home_sort_duration"),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.testTag("home_sort_row"),
                ) {
                    item {
                        Icon(
                            imageVector = PlinthIcons.SwapVert,
                            contentDescription = strings.sortBy,
                            tint = palette.inkSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    items(sortOptions, key = { it.first.name }) { (field, label, tag) ->
                        val isSelected = activeSortField == field
                        TactileCapsuleButton(
                            onClick = {
                                val nextAsc = if (activeSortField == field) {
                                    activeSortAscending
                                } else {
                                    field.defaultAscending()
                                }
                                activeSortField = field
                                activeSortAscending = nextAsc
                                onSortChange(field, nextAsc)
                            },
                            contentDescription = "${strings.sortBy}: $label",
                            cornerRadius = 18.dp,
                            hapticsEnabled = hapticsEnabled,
                            active = isSelected,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag(tag),
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                ),
                                color = if (isSelected) palette.inkPrimary else palette.inkSecondary,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            )
                        }
                    }
                }

                // Quick Filter Pills (All, Favorites, Device Audio, Recent, 3+ Min)
                val filterOptions = listOf(
                    Triple(HomeFilterType.ALL, strings.filterAll, "home_filter_all"),
                    Triple(HomeFilterType.FAVORITES, strings.filterFavorites, "home_filter_favorites"),
                    Triple(HomeFilterType.DEVICE, strings.filterDevice, "home_filter_device"),
                    Triple(HomeFilterType.RECENT, strings.filterRecent, "home_filter_recent"),
                    Triple(HomeFilterType.LONG_TRACKS, strings.filterLongTracks, "home_filter_long"),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.testTag("home_filter_row"),
                ) {
                    item {
                        Icon(
                            imageVector = PlinthIcons.FilterList,
                            contentDescription = strings.filterLabel,
                            tint = palette.inkSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    items(filterOptions, key = { it.first.name }) { (filter, label, tag) ->
                        val isSelected = activeFilter == filter
                        TactileCapsuleButton(
                            onClick = {
                                activeFilter = filter
                                onFilterChange(filter)
                            },
                            contentDescription = "${strings.filterLabel}: $label",
                            cornerRadius = 18.dp,
                            hapticsEnabled = hapticsEnabled,
                            active = isSelected,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag(tag),
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                ),
                                color = if (isSelected) palette.inkPrimary else palette.inkSecondary,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
            }
        }

        // Filtered & Sorted Tracks List
        if (filteredSortedTracks.isEmpty()) {
            item {
                val shape = RoundedCornerShape(24.dp)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(shape)
                        .background(palette.capsuleBg.copy(alpha = 0.72f))
                        .border(1.dp, palette.hairline, shape)
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                        .testTag("home_empty_filtered"),
                ) {
                    Icon(
                        imageVector = PlinthIcons.FilterList,
                        contentDescription = null,
                        tint = palette.inkSecondary,
                        modifier = Modifier.size(28.dp),
                    )
                    Text(
                        text = strings.emptyFilteredTracks,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.inkSecondary,
                        textAlign = TextAlign.Center,
                    )
                    if (activeFilter != HomeFilterType.ALL) {
                        TactileCapsuleButton(
                            onClick = {
                                activeFilter = HomeFilterType.ALL
                                onFilterChange(HomeFilterType.ALL)
                            },
                            contentDescription = strings.showAllTracks,
                            cornerRadius = 18.dp,
                            hapticsEnabled = hapticsEnabled,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("home_reset_filter_button"),
                        ) {
                            Text(
                                text = strings.showAllTracks,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = palette.inkPrimary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
            }
        } else {
            items(filteredSortedTracks, key = { "home_track_${it.id}" }) { track ->
                var menuOpen by remember(track.id) { mutableStateOf(false) }
                val isFavorite = resolvedFavoriteIds.contains(track.id)
                CompactVinylTrackRow(
                    track = track,
                    isActive = track.id == currentTrack?.id,
                    onClick = {
                        PlinthFeedback.trigger(FeedbackEvent.PLAY_PAUSE)
                        onPlayTrack(track, filteredSortedTracks)
                    },
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    PlinthFeedback.trigger(FeedbackEvent.TAP_SUBTLE)
                                    onToggleFavorite(track.id)
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("home_fav_${track.id}"),
                            ) {
                                Icon(
                                    imageVector = if (isFavorite) {
                                        Icons.Rounded.Favorite
                                    } else {
                                        Icons.Outlined.FavoriteBorder
                                    },
                                    contentDescription = strings.favorites,
                                    tint = if (isFavorite) palette.favoriteRed else palette.inkSecondary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Box {
                                IconButton(
                                    onClick = {
                                        PlinthFeedback.trigger(FeedbackEvent.TAP_SUBTLE)
                                        menuOpen = true
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("home_more_${track.id}"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MoreVert,
                                        contentDescription = strings.moreOptions,
                                        tint = palette.inkSecondary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                DropdownMenu(
                                    expanded = menuOpen,
                                    onDismissRequest = { menuOpen = false },
                                    containerColor = palette.capsuleBg,
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(strings.playNext, color = palette.inkPrimary) },
                                        leadingIcon = {
                                            Icon(PlinthIcons.PlaylistPlay, null, tint = palette.inkPrimary)
                                        },
                                        onClick = {
                                            menuOpen = false
                                            PlinthFeedback.trigger(FeedbackEvent.CONFIRM)
                                            onPlayNext(track)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(strings.addToQueue, color = palette.inkPrimary) },
                                        leadingIcon = {
                                            Icon(PlinthIcons.QueueMusic, null, tint = palette.inkPrimary)
                                        },
                                        onClick = {
                                            menuOpen = false
                                            PlinthFeedback.trigger(FeedbackEvent.CONFIRM)
                                            onAddToQueue(track)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(strings.addToPlaylist, color = palette.inkPrimary) },
                                        leadingIcon = {
                                            Icon(PlinthIcons.PlaylistAdd, null, tint = palette.inkPrimary)
                                        },
                                        onClick = {
                                            menuOpen = false
                                            PlinthFeedback.trigger(FeedbackEvent.TAP_SUBTLE)
                                            onOpenAddToPlaylist(track)
                                        },
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .testTag("home_track_row_${track.id}"),
                )
            }
        }
    }
}

@Composable
private fun StudioToolPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    hapticsEnabled: Boolean,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    val palette = LocalPlinthColors.current
    TactileCapsuleButton(
        onClick = onClick,
        contentDescription = label,
        cornerRadius = 20.dp,
        hapticsEnabled = hapticsEnabled,
        active = active,
        modifier = modifier.height(54.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = palette.inkPrimary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.5.sp),
                color = palette.inkPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun CompactVinylTrackRow(
    track: Track,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val palette = LocalPlinthColors.current
    val shape = RoundedCornerShape(22.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (isActive) palette.capsuleBg else palette.capsuleBg.copy(alpha = 0.65f)
            )
            .border(
                width = 1.dp,
                color = if (isActive) palette.inkPrimary.copy(alpha = 0.22f) else palette.hairline.copy(alpha = 0.65f),
                shape = shape,
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        CircularArtworkDisc(
            artworkUri = track.artworkUri,
            contentDescription = track.title,
            modifier = Modifier.size(46.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 14.5.sp,
                ),
                color = palette.inkPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${track.artist} • ${formatDuration(track.durationMs)}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = palette.inkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            trailing()
        }
    }
}
