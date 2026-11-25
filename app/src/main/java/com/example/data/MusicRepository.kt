package com.example.data

import android.content.Context
import android.net.Uri
import com.example.audio.DeviceMediaScanner
import com.example.audio.StudioTrackGenerator
import com.example.ui.i18n.AppLanguage
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class MusicRepository(
    private val context: Context,
    private val dao: MusicDao,
    val preferences: PlaybackPreferences,
) {
    private val studioTracks = MutableStateFlow<List<Track>>(emptyList())
    private val deviceTracks = MutableStateFlow<List<Track>>(emptyList())

    val allTracks: Flow<List<Track>> = combine(
        deviceTracks,
        dao.observeImportedTracks(),
        studioTracks,
    ) { device, imported, studio ->
        val mappedImported = imported.map {
            Track(
                id = it.id,
                title = it.title,
                artist = it.artist,
                album = it.album,
                durationMs = it.durationMs,
                contentUri = it.contentUri,
                artworkUri = it.artworkUri,
                isBuiltIn = false,
                dateAdded = it.dateAdded,
            )
        }
        (mappedImported + device + studio).distinctBy { it.id }
    }

    val favoriteIds: Flow<Set<String>> = dao.observeFavoriteIds().map { it.toSet() }

    val playlists: Flow<List<PlaylistSummary>> = combine(
        dao.observePlaylists(),
        dao.observeAllPlaylistTracks(),
    ) { lists, refs ->
        val byPlaylist = refs.groupBy { it.playlistId }
        lists.map { entity ->
            PlaylistSummary(
                id = entity.id,
                name = entity.name,
                createdAt = entity.createdAt,
                trackIds = byPlaylist[entity.id]?.map { it.trackId }.orEmpty(),
            )
        }
    }

    val recentTrackIds: Flow<List<String>> = dao.observeRecentHistory().map { history ->
        history.map { it.trackId }
    }

    suspend fun initializeStudioLibrary() {
        studioTracks.value = StudioTrackGenerator.ensureStudioTracks(context)
    }

    suspend fun scanDeviceLibrary(): Int {
        val found = DeviceMediaScanner.scanDeviceAudio(context)
        deviceTracks.value = found
        return found.size
    }

    suspend fun importAudioFiles(uris: List<Uri>): Int {
        val imported = DeviceMediaScanner.importAudioUris(context, uris)
        if (imported.isNotEmpty()) {
            dao.insertImportedTracks(imported)
        }
        return imported.size
    }

    suspend fun toggleFavorite(trackId: String, currentlyFavorite: Boolean) {
        if (currentlyFavorite) {
            dao.removeFavorite(trackId)
        } else {
            dao.addFavorite(FavoriteTrackEntity(trackId = trackId))
        }
    }

    suspend fun createPlaylist(name: String): Long {
        return dao.insertPlaylist(PlaylistEntity(name = name.trim()))
    }

    suspend fun deletePlaylist(playlistId: Long) {
        dao.deleteTracksForPlaylist(playlistId)
        dao.deletePlaylist(playlistId)
    }

    suspend fun addTrackToPlaylist(playlistId: Long, trackId: String) {
        dao.addTrackToPlaylist(PlaylistTrackEntity(playlistId = playlistId, trackId = trackId))
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String) {
        dao.removeTrackFromPlaylist(playlistId, trackId)
    }

    suspend fun recordPlay(trackId: String) {
        val existing = dao.getHistoryForTrack(trackId)
        dao.upsertHistory(
            PlayHistoryEntity(
                trackId = trackId,
                lastPlayedAt = System.currentTimeMillis(),
                playCount = (existing?.playCount ?: 0) + 1,
            )
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = preferences.setThemeMode(mode)
    suspend fun setLanguage(language: AppLanguage) = preferences.setLanguage(language)
    suspend fun setHapticsEnabled(enabled: Boolean) = preferences.setHapticsEnabled(enabled)
    suspend fun setUiSoundsEnabled(enabled: Boolean) = preferences.setUiSoundsEnabled(enabled)
    suspend fun setGraphicsQuality(quality: GraphicsQuality) = preferences.setGraphicsQuality(quality)
    suspend fun setBatterySaverMode(enabled: Boolean) = preferences.setBatterySaverMode(enabled)
    suspend fun setReducedMotion(enabled: Boolean) = preferences.setReducedMotion(enabled)
    suspend fun setShuffleEnabled(enabled: Boolean) = preferences.setShuffleEnabled(enabled)
    suspend fun setRepeatMode(mode: RepeatMode) = preferences.setRepeatMode(mode)
    suspend fun saveLastPlayback(trackId: String, positionMs: Long) = preferences.saveLastPlayback(trackId, positionMs)
    suspend fun saveEqualizer(enabled: Boolean, presetIndex: Int, bands: List<Int>) =
        preferences.saveEqualizer(enabled, presetIndex, bands)
}
