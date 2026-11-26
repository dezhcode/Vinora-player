package com.example.viewmodel

import android.app.Application
import android.content.ComponentName
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.example.audio.AudioEqualizerController
import com.example.audio.EqualizerState
import com.example.audio.PlaybackService
import com.example.audio.PlinthFeedback
import com.example.audio.SpectrumBus
import com.example.audio.SpectrumSnapshot
import com.example.data.AlbumGroup
import com.example.data.ArtistGroup
import com.example.data.CustomEqPreset
import com.example.data.FolderGroup
import com.example.data.GraphicsQuality
import com.example.data.HomeFilterType
import com.example.data.HomeSortField
import com.example.data.MusicDatabase
import com.example.data.MusicRepository
import com.example.data.PlaybackPreferences
import com.example.data.PlaylistSummary
import com.example.data.RepeatMode
import com.example.data.Track
import com.example.data.UserPreferences
import com.example.ui.i18n.AppLanguage
import com.example.ui.theme.ThemeMode
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Immutable
data class PlayerUiState(
    val tracks: List<Track> = emptyList(),
    val queue: List<Track> = emptyList(),
    val currentTrack: Track? = null,
    val currentQueueIndex: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 0.78f,
    val favoriteIds: Set<String> = emptySet(),
    val playlists: List<PlaylistSummary> = emptyList(),
    val recentTracks: List<Track> = emptyList(),
    val albums: List<AlbumGroup> = emptyList(),
    val artists: List<ArtistGroup> = emptyList(),
    val folders: List<FolderGroup> = emptyList(),
    val preferences: UserPreferences = UserPreferences(),
    val sleepTimerRemainingMs: Long = 0L,
    val isScanningDevice: Boolean = false,
    val statusMessage: String? = null,
    val trackChangeNonce: Int = 0,
    val trackChangeDirection: Int = 1,
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)

    private val repository = MusicRepository(
        context = appContext,
        dao = MusicDatabase.getInstance(appContext).musicDao(),
        preferences = PlaybackPreferences(appContext),
    )

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    private val _currentTrack = MutableStateFlow<Track?>(null)
    private val _currentIndex = MutableStateFlow(-1)
    private val _isPlaying = MutableStateFlow(false)
    private val _positionMs = MutableStateFlow(0L)
    private val _durationMs = MutableStateFlow(0L)
    private val _volume = MutableStateFlow(readSystemMusicVolume())
    private val _sleepTimerRemainingMs = MutableStateFlow(0L)
    private val _isScanning = MutableStateFlow(false)
    private val _statusMessage = MutableStateFlow<String?>(null)
    private val _trackChangeNonce = MutableStateFlow(0)
    private val _trackChangeDirection = MutableStateFlow(1)

    val spectrum: StateFlow<SpectrumSnapshot> = SpectrumBus.spectrum
    val equalizerState: StateFlow<EqualizerState> = AudioEqualizerController.state
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private var sleepTimerJob: Job? = null
    private var lastSavedTickMs = 0L
    private var restoredInitialSession = false

    private val playbackCoreFlow = combine(
        _queue,
        _currentTrack,
        _currentIndex,
        _isPlaying,
    ) { queue, current, index, playing ->
        PlaybackCore(queue, current, index, playing)
    }

    private val libraryDerivedFlow = combine(
        repository.allTracks,
        repository.recentTrackIds,
    ) { tracks, recentIds ->
        val byId = tracks.associateBy { it.id }
        val recent = recentIds.mapNotNull { byId[it] }
        val albums = tracks.groupBy { it.album }.map { (albumName, items) ->
            AlbumGroup(
                name = albumName,
                artist = items.firstOrNull()?.artist.orEmpty(),
                artworkUri = items.firstOrNull { it.artworkUri != null }?.artworkUri,
                tracks = items,
            )
        }.sortedBy { it.name }
        val artists = tracks.groupBy { it.artist }.map { (artistName, items) ->
            ArtistGroup(
                name = artistName,
                artworkUri = items.firstOrNull { it.artworkUri != null }?.artworkUri,
                tracks = items,
            )
        }.sortedBy { it.name }
        val folders = tracks.groupBy { it.folderPath }.map { (path, items) ->
            FolderGroup(
                name = items.firstOrNull()?.folderName ?: path,
                path = path,
                artworkUri = items.firstOrNull { it.artworkUri != null }?.artworkUri,
                tracks = items,
            )
        }.sortedBy { it.name.lowercase() }
        LibraryDerived(tracks, recent, albums, artists, folders)
    }

    private val libraryFlow = combine(
        libraryDerivedFlow,
        repository.favoriteIds,
        repository.playlists,
        repository.preferences.preferencesFlow,
    ) { derived, favs, playlists, prefs ->
        LibraryBundle(derived, favs, playlists, prefs)
    }

    private val auxFlow = combine(
        _durationMs,
        _volume,
        _sleepTimerRemainingMs,
        _isScanning,
        _statusMessage,
    ) { dur, vol, sleep, scanning, msg ->
        AuxBundle(dur, vol, sleep, scanning, msg)
    }

    private val transitionFlow = combine(_trackChangeNonce, _trackChangeDirection) { nonce, dir ->
        nonce to dir
    }

    val uiState: StateFlow<PlayerUiState> = combine(
        playbackCoreFlow,
        libraryFlow,
        auxFlow,
        transitionFlow,
    ) { core, lib, aux, transition ->
        PlayerUiState(
            tracks = lib.derived.tracks,
            queue = core.queue,
            currentTrack = core.current,
            currentQueueIndex = core.index,
            isPlaying = core.playing,
            positionMs = 0L,
            durationMs = if (aux.durationMs > 0L) aux.durationMs else core.current?.durationMs ?: 0L,
            volume = aux.volume,
            favoriteIds = lib.favs,
            playlists = lib.playlists,
            recentTracks = lib.derived.recentTracks,
            albums = lib.derived.albums,
            artists = lib.derived.artists,
            folders = lib.derived.folders,
            preferences = lib.prefs,
            sleepTimerRemainingMs = aux.sleepTimerMs,
            isScanningDevice = aux.scanning,
            statusMessage = aux.message,
            trackChangeNonce = transition.first,
            trackChangeDirection = transition.second,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PlayerUiState(),
    )

    init {
        PlinthFeedback.init(appContext)
        connectToPlaybackService()
        bootstrapLibrary()
        startPositionTicker()
        observeFeedbackPreferences()
    }

    private fun observeFeedbackPreferences() {
        viewModelScope.launch {
            repository.preferences.preferencesFlow.collect { prefs ->
                PlinthFeedback.updateSettings(
                    haptics = prefs.hapticsEnabled,
                    uiSounds = prefs.uiSoundsEnabled,
                )
            }
        }
    }

    private fun bootstrapLibrary() {
        viewModelScope.launch {
            repository.initializeStudioLibrary()
            val prefs = repository.preferences.preferencesFlow.first()
            AudioEqualizerController.applySettings(
                enabled = prefs.eqEnabled,
                presetIndex = prefs.eqPresetIndex,
                bandLevelsDb = prefs.eqBands,
                activeCustomPresetId = prefs.eqCustomPresetId,
            )
            // Attempt passive MediaStore scan if permission already granted
            repository.scanDeviceLibrary()

            // Restore last played session without auto-blasting sound
            val tracks = repository.allTracks.first()
            if (tracks.isNotEmpty() && !restoredInitialSession) {
                restoredInitialSession = true
                val target = tracks.firstOrNull { it.id == prefs.lastTrackId }
                    ?: tracks.firstOrNull { it.id == "studio_daylight" }
                    ?: tracks.first()
                _queue.value = tracks
                val idx = tracks.indexOfFirst { it.id == target.id }.coerceAtLeast(0)
                _currentIndex.value = idx
                _currentTrack.value = target
                _durationMs.value = target.durationMs
                val resumePos = if (prefs.lastTrackId == target.id) {
                    prefs.lastPositionMs.coerceIn(0L, target.durationMs)
                } else {
                    0L
                }
                _positionMs.value = resumePos
                syncQueueToControllerIfIdle(tracks, idx, resumePos, autoPlay = false)
            }
        }
    }

    private fun connectToPlaybackService() {
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching {
                    val controller = future.get()
                    mediaController = controller
                    attachPlayerListener(controller)
                    applySavedModesToController(controller)
                    val q = _queue.value
                    val idx = _currentIndex.value
                    if (controller.mediaItemCount == 0 && q.isNotEmpty() && idx in q.indices) {
                        syncQueueToControllerIfIdle(q, idx, _positionMs.value, autoPlay = _isPlaying.value)
                    } else if (controller.mediaItemCount > 0) {
                        syncStateFromController(controller)
                    }
                }
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    private fun attachPlayerListener(controller: MediaController) {
        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (!isPlaying) {
                    persistCurrentPlayback()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val id = mediaItem?.mediaId ?: return
                val q = _queue.value
                val newIndex = q.indexOfFirst { it.id == id }
                val prevIndex = _currentIndex.value
                if (newIndex >= 0) {
                    _trackChangeDirection.value = if (newIndex >= prevIndex) 1 else -1
                    _trackChangeNonce.value += 1
                    _currentIndex.value = newIndex
                    _currentTrack.value = q[newIndex]
                    _durationMs.value = controller.duration.takeIf { it > 0 } ?: q[newIndex].durationMs
                    _positionMs.value = controller.currentPosition.coerceAtLeast(0L)
                    viewModelScope.launch { repository.recordPlay(id) }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val dur = controller.duration
                    if (dur > 0) _durationMs.value = dur
                }
            }
        })
    }

    private fun applySavedModesToController(controller: MediaController) {
        viewModelScope.launch {
            val prefs = repository.preferences.preferencesFlow.first()
            controller.shuffleModeEnabled = prefs.shuffleEnabled
            controller.repeatMode = prefs.repeatMode.toMedia3RepeatMode()
            sendCrossfadeCommand(controller, prefs.crossfadeEnabled)
        }
    }

    private fun sendCrossfadeCommand(controller: MediaController, enabled: Boolean) {
        runCatching {
            val args = Bundle().apply { putBoolean(PlaybackService.ARG_ENABLED, enabled) }
            controller.sendCustomCommand(
                SessionCommand(PlaybackService.CMD_SET_CROSSFADE, Bundle.EMPTY),
                args,
            )
        }
    }

    private fun syncStateFromController(controller: MediaController) {
        _isPlaying.value = controller.isPlaying
        _positionMs.value = controller.currentPosition.coerceAtLeast(0L)
        if (controller.duration > 0) _durationMs.value = controller.duration
        val activeId = controller.currentMediaItem?.mediaId
        if (activeId != null) {
            val idx = _queue.value.indexOfFirst { it.id == activeId }
            if (idx >= 0) {
                _currentIndex.value = idx
                _currentTrack.value = _queue.value[idx]
            }
        }
    }

    private fun syncQueueToControllerIfIdle(
        tracks: List<Track>,
        startIndex: Int,
        startPositionMs: Long,
        autoPlay: Boolean,
    ) {
        val controller = mediaController ?: return
        val items = tracks.map { it.toMediaItem() }
        controller.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), startPositionMs)
        controller.prepare()
        if (autoPlay) {
            controller.play()
        }
    }

    private fun startPositionTicker() {
        viewModelScope.launch {
            while (isActive) {
                val controller = mediaController
                if (controller != null && controller.isPlaying) {
                    val pos = controller.currentPosition.coerceAtLeast(0L)
                    _positionMs.value = pos
                    val dur = controller.duration
                    if (dur > 0L) _durationMs.value = dur
                    val now = System.currentTimeMillis()
                    if (now - lastSavedTickMs > 5000L) {
                        lastSavedTickMs = now
                        persistCurrentPlayback()
                    }
                }
                delay(120L)
            }
        }
    }

    fun playTrack(track: Track, contextQueue: List<Track>? = null) {
        val targetQueue = contextQueue?.takeIf { it.isNotEmpty() }
            ?: uiState.value.tracks.takeIf { it.isNotEmpty() }
            ?: listOf(track)
        val idx = targetQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        val prevId = _currentTrack.value?.id

        if (prevId == track.id && mediaController != null && mediaController?.mediaItemCount ?: 0 > 0) {
            mediaController?.play()
            _isPlaying.value = true
            return
        }

        _queue.value = targetQueue
        _currentIndex.value = idx
        _currentTrack.value = targetQueue[idx]
        _durationMs.value = targetQueue[idx].durationMs
        _positionMs.value = 0L
        _trackChangeDirection.value = 1
        _trackChangeNonce.value += 1

        val controller = mediaController
        if (controller != null) {
            val items = targetQueue.map { it.toMediaItem() }
            controller.setMediaItems(items, idx, 0L)
            controller.prepare()
            controller.play()
            _isPlaying.value = true
        }

        viewModelScope.launch {
            repository.recordPlay(track.id)
            repository.saveLastPlayback(track.id, 0L)
        }
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.mediaItemCount == 0) {
            val current = _currentTrack.value ?: uiState.value.tracks.firstOrNull() ?: return
            playTrack(current, uiState.value.tracks)
            return
        }
        if (controller.isPlaying) {
            controller.pause()
            _isPlaying.value = false
            persistCurrentPlayback()
        } else {
            if (controller.playbackState == Player.STATE_ENDED) {
                controller.seekTo(0L)
            }
            controller.play()
            _isPlaying.value = true
        }
    }

    fun nextTrack() {
        val controller = mediaController
        val q = _queue.value
        if (q.isEmpty()) return
        _trackChangeDirection.value = 1
        _trackChangeNonce.value += 1
        if (controller != null && controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem()
            controller.play()
        } else if (q.isNotEmpty()) {
            val nextIdx = ((_currentIndex.value + 1) % q.size)
            playTrack(q[nextIdx], q)
        }
    }

    fun previousTrack() {
        val controller = mediaController
        val q = _queue.value
        if (q.isEmpty()) return
        if (_positionMs.value > 3500L) {
            seekTo(0L)
            return
        }
        _trackChangeDirection.value = -1
        _trackChangeNonce.value += 1
        if (controller != null && controller.hasPreviousMediaItem()) {
            controller.seekToPreviousMediaItem()
            controller.play()
        } else if (q.isNotEmpty()) {
            val prevIdx = if (_currentIndex.value > 0) _currentIndex.value - 1 else q.lastIndex
            playTrack(q[prevIdx], q)
        }
    }

    fun seekTo(positionMs: Long) {
        val maxDur = _durationMs.value.coerceAtLeast(1L)
        val clamped = positionMs.coerceIn(0L, maxDur)
        _positionMs.value = clamped
        mediaController?.seekTo(clamped)
    }

    fun scrubByDeltaMs(deltaMs: Long) {
        val maxDur = _durationMs.value.coerceAtLeast(1L)
        val target = (_positionMs.value + deltaMs).coerceIn(0L, maxDur)
        _positionMs.value = target
        mediaController?.seekTo(target)
    }

    fun toggleShuffle() {
        val next = !uiState.value.preferences.shuffleEnabled
        mediaController?.shuffleModeEnabled = next
        viewModelScope.launch { repository.setShuffleEnabled(next) }
    }

    fun cycleRepeatMode() {
        val next = when (uiState.value.preferences.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        mediaController?.repeatMode = next.toMedia3RepeatMode()
        viewModelScope.launch { repository.setRepeatMode(next) }
    }

    fun setVolume(fraction: Float) {
        val clamped = fraction.coerceIn(0f, 1f)
        _volume.value = clamped
        mediaController?.volume = clamped
        runCatching {
            val max = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, (clamped * max).toInt(), 0)
        }
    }

    fun toggleFavorite(trackId: String) {
        val isFav = uiState.value.favoriteIds.contains(trackId)
        viewModelScope.launch { repository.toggleFavorite(trackId, isFav) }
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.createPlaylist(name) }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch { repository.deletePlaylist(playlistId) }
    }

    fun addTrackToPlaylist(playlistId: Long, trackId: String, playlistName: String, feedback: String) {
        viewModelScope.launch {
            repository.addTrackToPlaylist(playlistId, trackId)
            showStatus(feedback)
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackId: String) {
        viewModelScope.launch { repository.removeTrackFromPlaylist(playlistId, trackId) }
    }

    fun removeFromQueue(index: Int) {
        val q = _queue.value.toMutableList()
        if (index !in q.indices || q.size <= 1) return
        q.removeAt(index)
        _queue.value = q
        mediaController?.removeMediaItem(index)
        val newIdx = q.indexOfFirst { it.id == _currentTrack.value?.id }
        if (newIdx >= 0) _currentIndex.value = newIdx
    }

    fun playNext(track: Track, feedbackMessage: String? = null) {
        val currentQueue = _queue.value
        val currentId = _currentTrack.value?.id
        if (currentQueue.isEmpty() || currentId == null) {
            playTrack(track, listOf(track))
            feedbackMessage?.let(::showStatus)
            return
        }
        val result = QueueOperations.playNext(currentQueue, currentId, track)
        _queue.value = result.queue
        if (result.currentIndex >= 0) _currentIndex.value = result.currentIndex
        applyMediaQueueAction(result.mediaControllerAction)
        feedbackMessage?.let(::showStatus)
    }

    fun addToQueue(track: Track, feedbackMessage: String? = null) {
        val currentQueue = _queue.value
        val currentId = _currentTrack.value?.id
        if (currentQueue.isEmpty() || currentId == null) {
            playTrack(track, listOf(track))
            feedbackMessage?.let(::showStatus)
            return
        }
        val result = QueueOperations.addToQueue(currentQueue, currentId, track)
        _queue.value = result.queue
        if (result.currentIndex >= 0) _currentIndex.value = result.currentIndex
        applyMediaQueueAction(result.mediaControllerAction)
        feedbackMessage?.let(::showStatus)
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val currentQueue = _queue.value
        val currentId = _currentTrack.value?.id
        val result = QueueOperations.moveQueueItem(currentQueue, currentId, fromIndex, toIndex) ?: return
        _queue.value = result.queue
        if (result.currentIndex >= 0) _currentIndex.value = result.currentIndex
        applyMediaQueueAction(result.mediaControllerAction)
    }

    private fun applyMediaQueueAction(action: MediaQueueAction) {
        val controller = mediaController ?: return
        when (action) {
            MediaQueueAction.None -> Unit
            is MediaQueueAction.Move -> {
                if (action.fromIndex in 0 until controller.mediaItemCount &&
                    action.toIndex in 0 until controller.mediaItemCount &&
                    action.fromIndex != action.toIndex
                ) {
                    controller.moveMediaItem(action.fromIndex, action.toIndex)
                }
            }
            is MediaQueueAction.Insert -> {
                val safeIdx = action.index.coerceIn(0, controller.mediaItemCount)
                controller.addMediaItem(safeIdx, action.track.toMediaItem())
            }
            is MediaQueueAction.Append -> {
                controller.addMediaItem(action.track.toMediaItem())
            }
        }
    }

    fun scanDeviceMusic(foundFormatter: (Int) -> String) {
        viewModelScope.launch {
            _isScanning.value = true
            val count = repository.scanDeviceLibrary()
            _isScanning.value = false
            showStatus(foundFormatter(count))
        }
    }

    fun importAudioFiles(uris: List<Uri>, importedFormatter: (Int) -> String) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val count = repository.importAudioFiles(uris)
            if (count > 0) {
                showStatus(importedFormatter(count))
            }
        }
    }

    fun setSleepTimerMinutes(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _sleepTimerRemainingMs.value = 0L
            return
        }
        val endAt = System.currentTimeMillis() + minutes * 60_000L
        _sleepTimerRemainingMs.value = minutes * 60_000L
        sleepTimerJob = viewModelScope.launch {
            while (isActive) {
                val left = (endAt - System.currentTimeMillis()).coerceAtLeast(0L)
                _sleepTimerRemainingMs.value = left
                if (left <= 0L) {
                    mediaController?.pause()
                    _isPlaying.value = false
                    break
                }
                delay(1000L)
            }
        }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        val current = equalizerState.value
        AudioEqualizerController.applySettings(
            enabled = enabled,
            presetIndex = current.presetIndex,
            bandLevelsDb = current.bandLevelsDb,
            activeCustomPresetId = current.activeCustomPresetId,
        )
        viewModelScope.launch {
            repository.saveEqualizer(
                enabled = enabled,
                presetIndex = current.presetIndex,
                bands = current.bandLevelsDb,
                customPresetId = current.activeCustomPresetId,
            )
        }
    }

    fun selectEqualizerPreset(presetIndex: Int) {
        val preset = AudioEqualizerController.presets.getOrNull(presetIndex) ?: return
        AudioEqualizerController.applySettings(
            enabled = true,
            presetIndex = preset.index,
            bandLevelsDb = preset.bandsDb,
            activeCustomPresetId = null,
        )
        viewModelScope.launch {
            repository.saveEqualizer(true, preset.index, preset.bandsDb, customPresetId = null)
        }
    }

    fun setEqualizerBand(bandIndex: Int, dbValue: Int) {
        val current = equalizerState.value
        if (bandIndex !in current.bandLevelsDb.indices) return
        val updated = current.bandLevelsDb.toMutableList()
        updated[bandIndex] = dbValue.coerceIn(-12, 12)
        AudioEqualizerController.applySettings(
            enabled = true,
            presetIndex = -1,
            bandLevelsDb = updated,
            activeCustomPresetId = null,
        )
        viewModelScope.launch {
            repository.saveEqualizer(true, -1, updated, customPresetId = null)
        }
    }

    fun saveCustomEqualizerPreset(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        val bands = equalizerState.value.bandLevelsDb.takeIf { it.size == 5 } ?: listOf(0, 0, 0, 0, 0)
        val created = CustomEqPreset(
            id = "custom_${System.currentTimeMillis()}",
            name = trimmed,
            bandsDb = bands.map { it.coerceIn(-12, 12) },
        )
        val nextPresets = uiState.value.preferences.customEqPresets + created
        AudioEqualizerController.applySettings(
            enabled = true,
            presetIndex = -1,
            bandLevelsDb = created.bandsDb,
            activeCustomPresetId = created.id,
        )
        viewModelScope.launch {
            repository.saveCustomEqPresets(nextPresets)
            repository.saveEqualizer(true, -1, created.bandsDb, customPresetId = created.id)
        }
    }

    fun selectCustomEqualizerPreset(presetId: String) {
        val preset = uiState.value.preferences.customEqPresets.firstOrNull { it.id == presetId } ?: return
        AudioEqualizerController.applySettings(
            enabled = true,
            presetIndex = -1,
            bandLevelsDb = preset.bandsDb,
            activeCustomPresetId = preset.id,
        )
        viewModelScope.launch {
            repository.saveEqualizer(true, -1, preset.bandsDb, customPresetId = preset.id)
        }
    }

    fun renameCustomEqualizerPreset(presetId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        val updated = uiState.value.preferences.customEqPresets.map { preset ->
            if (preset.id == presetId) preset.copy(name = trimmed) else preset
        }
        viewModelScope.launch {
            repository.saveCustomEqPresets(updated)
        }
    }

    fun deleteCustomEqualizerPreset(presetId: String) {
        val updated = uiState.value.preferences.customEqPresets.filterNot { it.id == presetId }
        val current = equalizerState.value
        val wasActive = current.activeCustomPresetId == presetId
        if (wasActive) {
            AudioEqualizerController.applySettings(
                enabled = current.enabled,
                presetIndex = current.presetIndex,
                bandLevelsDb = current.bandLevelsDb,
                activeCustomPresetId = null,
            )
        }
        viewModelScope.launch {
            repository.saveCustomEqPresets(updated)
            if (wasActive) {
                repository.saveEqualizer(
                    enabled = current.enabled,
                    presetIndex = current.presetIndex,
                    bands = current.bandLevelsDb,
                    customPresetId = null,
                )
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { repository.setLanguage(language) }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setHapticsEnabled(enabled) }
    }

    fun setUiSoundsEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setUiSoundsEnabled(enabled) }
    }

    fun setGraphicsQuality(quality: GraphicsQuality) {
        viewModelScope.launch { repository.setGraphicsQuality(quality) }
    }

    fun setBatterySaverMode(enabled: Boolean) {
        viewModelScope.launch { repository.setBatterySaverMode(enabled) }
    }

    fun setReducedMotion(enabled: Boolean) {
        viewModelScope.launch { repository.setReducedMotion(enabled) }
    }

    fun setCrossfadeEnabled(enabled: Boolean) {
        mediaController?.let { sendCrossfadeCommand(it, enabled) }
        viewModelScope.launch { repository.setCrossfadeEnabled(enabled) }
    }

    fun setHomeSort(field: HomeSortField, ascending: Boolean) {
        viewModelScope.launch { repository.setHomeSort(field, ascending) }
    }

    fun setHomeFilter(filter: HomeFilterType) {
        viewModelScope.launch { repository.setHomeFilter(filter) }
    }

    fun showStatus(message: String) {
        _statusMessage.value = message
        viewModelScope.launch {
            delay(2800L)
            if (_statusMessage.value == message) {
                _statusMessage.value = null
            }
        }
    }

    private fun persistCurrentPlayback() {
        val id = _currentTrack.value?.id ?: return
        val pos = _positionMs.value
        viewModelScope.launch { repository.saveLastPlayback(id, pos) }
    }

    private fun readSystemMusicVolume(): Float {
        return runCatching {
            val max = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 15
            val cur = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 11
            (cur.toFloat() / max.toFloat()).coerceIn(0f, 1f)
        }.getOrDefault(0.75f)
    }

    override fun onCleared() {
        persistCurrentPlayback()
        sleepTimerJob?.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }

    private data class PlaybackCore(
        val queue: List<Track>,
        val current: Track?,
        val index: Int,
        val playing: Boolean,
    )

    private data class LibraryDerived(
        val tracks: List<Track>,
        val recentTracks: List<Track>,
        val albums: List<AlbumGroup>,
        val artists: List<ArtistGroup>,
        val folders: List<FolderGroup>,
    )

    private data class LibraryBundle(
        val derived: LibraryDerived,
        val favs: Set<String>,
        val playlists: List<PlaylistSummary>,
        val prefs: UserPreferences,
    )

    private data class AuxBundle(
        val durationMs: Long,
        val volume: Float,
        val sleepTimerMs: Long,
        val scanning: Boolean,
        val message: String?,
    )
}

private fun Track.toMediaItem(): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .apply {
            artworkUri?.let { setArtworkUri(Uri.parse(it)) }
        }
        .build()

    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(Uri.parse(contentUri))
        .setMediaMetadata(metadata)
        .build()
}

private fun RepeatMode.toMedia3RepeatMode(): Int = when (this) {
    RepeatMode.OFF -> Player.REPEAT_MODE_OFF
    RepeatMode.ALL -> Player.REPEAT_MODE_ALL
    RepeatMode.ONE -> Player.REPEAT_MODE_ONE
}

fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return "$mins:${secs.toString().padStart(2, '0')}"
}

sealed interface MediaQueueAction {
    data object None : MediaQueueAction
    data class Move(val fromIndex: Int, val toIndex: Int) : MediaQueueAction
    data class Insert(val index: Int, val track: Track) : MediaQueueAction
    data class Append(val track: Track) : MediaQueueAction
}

data class QueueMutationResult(
    val queue: List<Track>,
    val currentIndex: Int,
    val mediaControllerAction: MediaQueueAction,
)

object QueueOperations {
    fun playNext(queue: List<Track>, currentTrackId: String?, track: Track): QueueMutationResult {
        val currentIdx = queue.indexOfFirst { it.id == currentTrackId }
        if (queue.isEmpty() || currentIdx < 0) {
            return QueueMutationResult(listOf(track), 0, MediaQueueAction.Append(track))
        }
        val existingIdx = queue.indexOfFirst { it.id == track.id }
        if (existingIdx == currentIdx) {
            return QueueMutationResult(queue, currentIdx, MediaQueueAction.None)
        }
        val mutable = queue.toMutableList()
        val action = if (existingIdx >= 0) {
            val destIdx = if (existingIdx < currentIdx) {
                currentIdx
            } else {
                (currentIdx + 1).coerceAtMost(queue.lastIndex)
            }
            if (existingIdx == destIdx) {
                MediaQueueAction.None
            } else {
                val item = mutable.removeAt(existingIdx)
                mutable.add(destIdx, item)
                MediaQueueAction.Move(existingIdx, destIdx)
            }
        } else {
            val insertIdx = (currentIdx + 1).coerceIn(0, mutable.size)
            mutable.add(insertIdx, track)
            MediaQueueAction.Insert(insertIdx, track)
        }
        val nextCurrentIdx = mutable.indexOfFirst { it.id == currentTrackId }.coerceAtLeast(0)
        return QueueMutationResult(mutable, nextCurrentIdx, action)
    }

    fun addToQueue(queue: List<Track>, currentTrackId: String?, track: Track): QueueMutationResult {
        val currentIdx = queue.indexOfFirst { it.id == currentTrackId }
        if (queue.isEmpty() || currentIdx < 0) {
            return QueueMutationResult(listOf(track), 0, MediaQueueAction.Append(track))
        }
        val existingIdx = queue.indexOfFirst { it.id == track.id }
        if (existingIdx == currentIdx) {
            return QueueMutationResult(queue, currentIdx, MediaQueueAction.None)
        }
        val mutable = queue.toMutableList()
        val action = if (existingIdx >= 0) {
            val destIdx = mutable.lastIndex
            if (existingIdx == destIdx) {
                MediaQueueAction.None
            } else {
                val item = mutable.removeAt(existingIdx)
                mutable.add(destIdx, item)
                MediaQueueAction.Move(existingIdx, destIdx)
            }
        } else {
            mutable.add(track)
            MediaQueueAction.Append(track)
        }
        val nextCurrentIdx = mutable.indexOfFirst { it.id == currentTrackId }.coerceAtLeast(0)
        return QueueMutationResult(mutable, nextCurrentIdx, action)
    }

    fun moveQueueItem(
        queue: List<Track>,
        currentTrackId: String?,
        fromIndex: Int,
        toIndex: Int,
    ): QueueMutationResult? {
        if (fromIndex !in queue.indices || toIndex !in queue.indices || fromIndex == toIndex) {
            return null
        }
        val mutable = queue.toMutableList()
        val moved = mutable.removeAt(fromIndex)
        mutable.add(toIndex, moved)
        val nextCurrentIdx = if (currentTrackId != null) {
            mutable.indexOfFirst { it.id == currentTrackId }
        } else {
            -1
        }
        return QueueMutationResult(
            queue = mutable,
            currentIndex = nextCurrentIdx,
            mediaControllerAction = MediaQueueAction.Move(fromIndex, toIndex),
        )
    }
}
