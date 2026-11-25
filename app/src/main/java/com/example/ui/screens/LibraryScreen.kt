package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.FeedbackEvent
import com.example.audio.PlinthFeedback
import com.example.data.AlbumGroup
import com.example.data.ArtistGroup
import com.example.data.FolderGroup
import com.example.data.PlaylistSummary
import com.example.data.Track
import com.example.ui.components.CircularArtworkDisc
import com.example.ui.components.TactileCapsuleButton
import com.example.ui.components.plinthSoftShadow
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.LocalPlinthColors

enum class LibraryTab {
    SONGS, ALBUMS, ARTISTS, FOLDERS, PLAYLISTS, FAVORITES
}

private sealed interface LibraryDetail {
    data class Album(val group: AlbumGroup) : LibraryDetail
    data class Artist(val group: ArtistGroup) : LibraryDetail
    data class Folder(val group: FolderGroup) : LibraryDetail
    data class Playlist(val playlist: PlaylistSummary) : LibraryDetail
}

@Composable
fun LibraryScreen(
    tracks: List<Track>,
    albums: List<AlbumGroup>,
    artists: List<ArtistGroup>,
    folders: List<FolderGroup> = emptyList(),
    playlists: List<PlaylistSummary>,
    favoriteIds: Set<String>,
    activeTrackId: String?,
    initialSearchFocused: Boolean,
    hapticsEnabled: Boolean,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayNext: (Track) -> Unit = {},
    onAddToQueue: (Track) -> Unit = {},
    onToggleFavorite: (String) -> Unit,
    onOpenAddToPlaylist: (Track) -> Unit,
    onOpenCreatePlaylist: () -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onRemoveFromPlaylist: (Long, String) -> Unit,
    onImportAudio: () -> Unit,
    onScanDevice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPlinthColors.current
    val strings = LocalAppStrings.current

    var selectedTab by remember { mutableStateOf(LibraryTab.SONGS) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember(initialSearchFocused) { mutableStateOf(initialSearchFocused) }
    var activeDetail by remember { mutableStateOf<LibraryDetail?>(null) }

    if (activeDetail != null) {
        BackHandler { activeDetail = null }
        val detail = activeDetail!!
        val detailTitle: String
        val detailSubtitle: String
        val detailTracks: List<Track>
        val detailPlaylistId: Long?

        when (detail) {
            is LibraryDetail.Album -> {
                detailTitle = detail.group.name
                detailSubtitle = "${detail.group.artist} • ${strings.tracksCount(detail.group.tracks.size)}"
                detailTracks = detail.group.tracks
                detailPlaylistId = null
            }
            is LibraryDetail.Artist -> {
                detailTitle = detail.group.name
                detailSubtitle = strings.tracksCount(detail.group.tracks.size)
                detailTracks = detail.group.tracks
                detailPlaylistId = null
            }
            is LibraryDetail.Folder -> {
                detailTitle = detail.group.name
                detailSubtitle = "${detail.group.path} • ${strings.tracksCount(detail.group.tracks.size)}"
                detailTracks = detail.group.tracks
                detailPlaylistId = null
            }
            is LibraryDetail.Playlist -> {
                val livePlaylist = playlists.firstOrNull { it.id == detail.playlist.id } ?: detail.playlist
                val byId = tracks.associateBy { it.id }
                detailTitle = livePlaylist.name
                detailTracks = livePlaylist.trackIds.mapNotNull { byId[it] }
                detailSubtitle = strings.tracksCount(detailTracks.size)
                detailPlaylistId = livePlaylist.id
            }
        }

        Column(modifier = modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                IconButton(
                    onClick = {
                        PlinthFeedback.trigger(FeedbackEvent.TAP_SUBTLE)
                        activeDetail = null
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = strings.library,
                        tint = palette.inkPrimary,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = detailTitle,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = palette.inkPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = detailSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkSecondary,
                    )
                }
                if (detailTracks.isNotEmpty()) {
                    TactileCapsuleButton(
                        onClick = { onPlayTrack(detailTracks.first(), detailTracks) },
                        contentDescription = strings.playAll,
                        hapticsEnabled = hapticsEnabled,
                        modifier = Modifier.height(40.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp),
                        ) {
                            Icon(Icons.Rounded.PlayArrow, null, tint = palette.inkPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.playAll, style = MaterialTheme.typography.labelLarge, color = palette.inkPrimary)
                        }
                    }
                }
            }

            if (detailTracks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = strings.noTracksInPlaylist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.inkSecondary,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 128.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(detailTracks, key = { "det_${it.id}" }) { track ->
                        CompactVinylTrackRow(
                            track = track,
                            isActive = track.id == activeTrackId,
                            onClick = {
                                PlinthFeedback.trigger(FeedbackEvent.PLAY_PAUSE)
                                onPlayTrack(track, detailTracks)
                            },
                            trailing = {
                                if (detailPlaylistId != null) {
                                    Row {
                                        TrackTrailingActions(
                                            trackId = track.id,
                                            isFavorite = favoriteIds.contains(track.id),
                                            onToggleFavorite = { onToggleFavorite(track.id) },
                                            onPlayNext = { onPlayNext(track) },
                                            onAddToQueue = { onAddToQueue(track) },
                                            onAddToPlaylist = { onOpenAddToPlaylist(track) },
                                        )
                                        IconButton(
                                            onClick = {
                                                PlinthFeedback.trigger(FeedbackEvent.TAP_SUBTLE)
                                                onRemoveFromPlaylist(detailPlaylistId, track.id)
                                            },
                                            modifier = Modifier.size(44.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DeleteOutline,
                                                contentDescription = strings.delete,
                                                tint = palette.inkSecondary,
                                            )
                                        }
                                    }
                                } else {
                                    TrackTrailingActions(
                                        trackId = track.id,
                                        isFavorite = favoriteIds.contains(track.id),
                                        onToggleFavorite = { onToggleFavorite(track.id) },
                                        onPlayNext = { onPlayNext(track) },
                                        onAddToQueue = { onAddToQueue(track) },
                                        onAddToPlaylist = { onOpenAddToPlaylist(track) },
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
        return
    }

    val query = searchQuery.trim()
    val filteredTracks = remember(tracks, query) {
        if (query.isEmpty()) tracks
        else tracks.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.album.contains(query, ignoreCase = true)
        }
    }
    val favoriteTracks = remember(filteredTracks, favoriteIds) {
        filteredTracks.filter { favoriteIds.contains(it.id) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("library_screen"),
    ) {
        // Header: Library Title + Search / Import / Scan actions
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
        ) {
            Text(
                text = strings.library,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 25.sp,
                ),
                color = palette.inkPrimary,
            )

            Row {
                IconButton(
                    onClick = {
                        PlinthFeedback.trigger(FeedbackEvent.TAP_SUBTLE)
                        showSearch = !showSearch
                        if (!showSearch) searchQuery = ""
                    },
                    modifier = Modifier.size(48.dp).testTag("library_search_toggle"),
                ) {
                    Icon(
                        imageVector = if (showSearch) Icons.Rounded.Close else Icons.Rounded.Search,
                        contentDescription = strings.search,
                        tint = palette.inkPrimary,
                    )
                }
                IconButton(
                    onClick = {
                        PlinthFeedback.trigger(FeedbackEvent.TAP_SUBTLE)
                        onImportAudio()
                    },
                    modifier = Modifier.size(48.dp).testTag("library_import_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AudioFile,
                        contentDescription = strings.importAudio,
                        tint = palette.inkPrimary,
                    )
                }
                IconButton(
                    onClick = {
                        PlinthFeedback.trigger(FeedbackEvent.TAP_SUBTLE)
                        onScanDevice()
                    },
                    modifier = Modifier.size(48.dp).testTag("library_scan_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOpen,
                        contentDescription = strings.scanDevice,
                        tint = palette.inkPrimary,
                    )
                }
            }
        }

        // Tactile Search Input
        if (showSearch) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(strings.searchPlaceholder, color = palette.inkSecondary) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = palette.inkSecondary) },
                singleLine = true,
                shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = palette.capsuleBg,
                    unfocusedContainerColor = palette.capsuleBg.copy(alpha = 0.8f),
                    focusedBorderColor = palette.inkPrimary.copy(alpha = 0.35f),
                    unfocusedBorderColor = palette.hairline,
                    focusedTextColor = palette.inkPrimary,
                    unfocusedTextColor = palette.inkPrimary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .testTag("library_search_field"),
            )
        }

        // Industrial Pill Segment Bar
        val tabs = listOf(
            LibraryTab.SONGS to strings.songs,
            LibraryTab.ALBUMS to strings.albums,
            LibraryTab.ARTISTS to strings.artists,
            LibraryTab.FOLDERS to strings.folders,
            LibraryTab.PLAYLISTS to strings.playlists,
            LibraryTab.FAVORITES to strings.favorites,
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(tabs) { (tab, label) ->
                val isSelected = selectedTab == tab
                TactileCapsuleButton(
                    onClick = { selectedTab = tab },
                    contentDescription = label,
                    cornerRadius = 20.dp,
                    hapticsEnabled = hapticsEnabled,
                    active = isSelected,
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("library_tab_${tab.name.lowercase()}"),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        ),
                        color = if (isSelected) palette.inkPrimary else palette.inkSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }

        // Active Tab Content
        when (selectedTab) {
            LibraryTab.SONGS -> {
                if (filteredTracks.isEmpty()) {
                    EmptyLibraryHint(strings.emptySearch)
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 128.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(filteredTracks, key = { it.id }) { track ->
                            CompactVinylTrackRow(
                                track = track,
                                isActive = track.id == activeTrackId,
                                onClick = {
                                    PlinthFeedback.trigger(FeedbackEvent.PLAY_PAUSE)
                                    onPlayTrack(track, filteredTracks)
                                },
                                trailing = {
                                    TrackTrailingActions(
                                        trackId = track.id,
                                        isFavorite = favoriteIds.contains(track.id),
                                        onToggleFavorite = { onToggleFavorite(track.id) },
                                        onPlayNext = { onPlayNext(track) },
                                        onAddToQueue = { onAddToQueue(track) },
                                        onAddToPlaylist = { onOpenAddToPlaylist(track) },
                                    )
                                },
                            )
                        }
                    }
                }
            }

            LibraryTab.ALBUMS -> {
                val filteredAlbums = remember(albums, query) {
                    if (query.isEmpty()) albums
                    else albums.filter {
                        it.name.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
                    }
                }
                if (filteredAlbums.isEmpty()) {
                    EmptyLibraryHint(strings.emptySearch)
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 128.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(filteredAlbums, key = { it.name }) { album ->
                            val shape = RoundedCornerShape(24.dp)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .plinthSoftShadow(
                                        cornerRadius = 24.dp,
                                        shadowDark = palette.shadowDark,
                                        shadowLight = palette.shadowLight,
                                        elevation = 8.dp,
                                        darkAlpha = if (palette.isDark) 0.65f else 0.34f,
                                        lightAlpha = if (palette.isDark) 0.14f else 0.85f,
                                    )
                                    .clip(shape)
                                    .background(Brush.verticalGradient(listOf(palette.plinthTop, palette.plinthBottom)))
                                    .border(1.dp, palette.hairline, shape)
                                    .clickable {
                                        PlinthFeedback.trigger(FeedbackEvent.TAP_SUBTLE)
                                        activeDetail = LibraryDetail.Album(album)
                                    }
                                    .padding(14.dp),
                            ) {
                                CircularArtworkDisc(
                                    artworkUri = album.artworkUri,
                                    contentDescription = album.name,
                                    modifier = Modifier.fillMaxWidth(0.76f).aspectRatio(1f),
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = album.name,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = palette.inkPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = album.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.inkSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            LibraryTab.ARTISTS -> {
                val filteredArtists = remember(artists, query) {
                    if (query.isEmpty()) artists
                    else artists.filter { it.name.contains(query, ignoreCase = true) }
                }
                if (filteredArtists.isEmpty()) {
                    EmptyLibraryHint(strings.emptySearch)
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 128.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(filteredArtists, key = { it.name }) { artist ->
                            val shape = RoundedCornerShape(22.dp)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(shape)
                                    .background(palette.capsuleBg.copy(alpha = 0.82f))
                                    .border(1.dp, palette.hairline, shape)
                                    .clickable {
                                        PlinthFeedback.trigger(FeedbackEvent.TAP_SUBTLE)
                                        activeDetail = LibraryDetail.Artist(artist)
                                    }
                                    .padding(12.dp),
                            ) {
                                CircularArtworkDisc(
                                    artworkUri = artist.artworkUri,
                                    contentDescription = artist.name,
                                    modifier = Modifier.size(50.dp),
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = artist.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = palette.inkPrimary,
                                    )
                                    Text(
                                        text = strings.tracksCount(artist.tracks.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = palette.inkSecondary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            LibraryTab.FOLDERS -> {
                val filteredFolders = remember(folders, query) {
                    if (query.isEmpty()) folders
                    else folders.filter { folder ->
                        folder.name.contains(query, ignoreCase = true) ||
                            folder.path.contains(query, ignoreCase = true) ||
                            folder.tracks.any { it.title.contains(query, ignoreCase = true) }
                    }
                }
                if (filteredFolders.isEmpty()) {
                    EmptyLibraryHint(strings.emptySearch)
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 128.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(filteredFolders, key = { it.path }) { folder ->
                            val shape = RoundedCornerShape(22.dp)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(shape)
                                    .background(palette.capsuleBg.copy(alpha = 0.85f))
                                    .border(1.dp, palette.hairline, shape)
                                    .clickable {
                                        PlinthFeedback.trigger(FeedbackEvent.TAP_SUBTLE)
                                        activeDetail = LibraryDetail.Folder(folder)
                                    }
                                    .padding(12.dp)
                                    .testTag("folder_card_${folder.path}"),
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Brush.verticalGradient(listOf(palette.plinthTop, palette.plinthBottom)))
                                        .border(1.dp, palette.hairline, CircleShape),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FolderOpen,
                                        contentDescription = folder.name,
                                        tint = palette.inkPrimary,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = folder.name,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = palette.inkPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${folder.path} • ${strings.tracksCount(folder.tracks.size)}",
                                        style = MaterialTheme.typography.bodySmall,
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

            LibraryTab.PLAYLISTS -> {
                LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 128.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        TactileCapsuleButton(
                            onClick = onOpenCreatePlaylist,
                            contentDescription = strings.createPlaylist,
                            cornerRadius = 22.dp,
                            hapticsEnabled = hapticsEnabled,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("create_playlist_button"),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Add, null, tint = palette.inkPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = strings.createPlaylist,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = palette.inkPrimary,
                                )
                            }
                        }
                    }
                    if (playlists.isEmpty()) {
                        item { EmptyLibraryHint(strings.emptyPlaylists) }
                    } else {
                        items(playlists, key = { it.id }) { pl ->
                            val shape = RoundedCornerShape(22.dp)
                            val firstTrackArt = tracks.firstOrNull { pl.trackIds.contains(it.id) }?.artworkUri
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(shape)
                                    .background(palette.capsuleBg.copy(alpha = 0.85f))
                                    .border(1.dp, palette.hairline, shape)
                                    .clickable {
                                        PlinthFeedback.trigger(FeedbackEvent.TAP_SUBTLE)
                                        activeDetail = LibraryDetail.Playlist(pl)
                                    }
                                    .padding(12.dp),
                            ) {
                                CircularArtworkDisc(
                                    artworkUri = firstTrackArt,
                                    contentDescription = pl.name,
                                    modifier = Modifier.size(48.dp),
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = pl.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = palette.inkPrimary,
                                    )
                                    Text(
                                        text = strings.tracksCount(pl.trackIds.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = palette.inkSecondary,
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        PlinthFeedback.trigger(FeedbackEvent.TAP_SUBTLE)
                                        onDeletePlaylist(pl.id)
                                    },
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = strings.delete,
                                        tint = palette.inkSecondary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            LibraryTab.FAVORITES -> {
                if (favoriteTracks.isEmpty()) {
                    EmptyLibraryHint(strings.emptyFavorites)
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 128.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(favoriteTracks, key = { "fav_tab_${it.id}" }) { track ->
                            CompactVinylTrackRow(
                                track = track,
                                isActive = track.id == activeTrackId,
                                onClick = {
                                    PlinthFeedback.trigger(FeedbackEvent.PLAY_PAUSE)
                                    onPlayTrack(track, favoriteTracks)
                                },
                                trailing = {
                                    TrackTrailingActions(
                                        trackId = track.id,
                                        isFavorite = true,
                                        onToggleFavorite = { onToggleFavorite(track.id) },
                                        onPlayNext = { onPlayNext(track) },
                                        onAddToQueue = { onAddToQueue(track) },
                                        onAddToPlaylist = { onOpenAddToPlaylist(track) },
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackTrailingActions(
    trackId: String,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
) {
    val palette = LocalPlinthColors.current
    val strings = LocalAppStrings.current
    var menuExpanded by remember { mutableStateOf(false) }

    Row {
        IconButton(
            onClick = {
                PlinthFeedback.trigger(FeedbackEvent.TOGGLE_STATE)
                onToggleFavorite()
            },
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = strings.favorites,
                tint = if (isFavorite) palette.favoriteRed else palette.inkMuted,
                modifier = Modifier.size(20.dp),
            )
        }
        Box {
            IconButton(
                onClick = {
                    PlinthFeedback.trigger(FeedbackEvent.TAP_SUBTLE)
                    menuExpanded = true
                },
                modifier = Modifier
                    .size(44.dp)
                    .testTag("track_more_$trackId"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = strings.moreOptions,
                    tint = palette.inkSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                containerColor = palette.capsuleBg,
            ) {
                DropdownMenuItem(
                    text = { Text(strings.playNext, color = palette.inkPrimary) },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null, tint = palette.inkPrimary)
                    },
                    onClick = {
                        menuExpanded = false
                        PlinthFeedback.trigger(FeedbackEvent.CONFIRM)
                        onPlayNext()
                    },
                    modifier = Modifier.testTag("menu_play_next_$trackId"),
                )
                DropdownMenuItem(
                    text = { Text(strings.addToQueue, color = palette.inkPrimary) },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = palette.inkPrimary)
                    },
                    onClick = {
                        menuExpanded = false
                        PlinthFeedback.trigger(FeedbackEvent.CONFIRM)
                        onAddToQueue()
                    },
                    modifier = Modifier.testTag("menu_add_to_queue_$trackId"),
                )
                DropdownMenuItem(
                    text = { Text(strings.addToPlaylist, color = palette.inkPrimary) },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null, tint = palette.inkPrimary)
                    },
                    onClick = {
                        menuExpanded = false
                        PlinthFeedback.trigger(FeedbackEvent.TAP_SUBTLE)
                        onAddToPlaylist()
                    },
                    modifier = Modifier.testTag("menu_add_to_playlist_$trackId"),
                )
            }
        }
    }
}

@Composable
private fun EmptyLibraryHint(text: String) {
    val palette = LocalPlinthColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.inkSecondary,
        )
    }
}
