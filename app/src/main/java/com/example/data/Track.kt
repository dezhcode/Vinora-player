package com.example.data

import androidx.compose.runtime.Immutable

enum class RepeatMode {
    OFF, ALL, ONE
}

@Immutable
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val contentUri: String,
    val artworkUri: String? = null,
    val isBuiltIn: Boolean = false,
    val dateAdded: Long = 0L,
    val folderName: String = "Plinth Studio",
    val folderPath: String = "Plinth Studio",
)

@Immutable
data class AlbumGroup(
    val name: String,
    val artist: String,
    val artworkUri: String?,
    val tracks: List<Track>,
)

@Immutable
data class ArtistGroup(
    val name: String,
    val artworkUri: String?,
    val tracks: List<Track>,
)

@Immutable
data class FolderGroup(
    val name: String,
    val path: String,
    val artworkUri: String?,
    val tracks: List<Track>,
)

@Immutable
data class PlaylistSummary(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val trackIds: List<String>,
)
