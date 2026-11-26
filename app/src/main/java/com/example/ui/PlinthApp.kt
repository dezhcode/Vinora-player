package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Track
import com.example.ui.components.FloatingMiniPlayer
import com.example.ui.components.MainTab
import com.example.ui.components.PlinthBottomNav
import com.example.ui.screens.AddToPlaylistDialog
import com.example.ui.screens.CreatePlaylistDialog
import com.example.ui.screens.EqualizerBottomSheet
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.NowPlayingScreen
import com.example.ui.screens.QueueBottomSheet
import com.example.ui.screens.SettingsBottomSheet
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.LocalPlinthColors
import com.example.viewmodel.PlayerViewModel

@Composable
fun PlinthApp(
    viewModel: PlayerViewModel,
    onDarkModeResolved: (Boolean) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val eqState by viewModel.equalizerState.collectAsStateWithLifecycle()

    val palette = LocalPlinthColors.current
    val strings = LocalAppStrings.current
    val context = LocalContext.current

    LaunchedEffect(palette.isDark) {
        onDarkModeResolved(palette.isDark)
    }

    var selectedTab by remember { mutableStateOf(MainTab.EXPLORE) }
    var isNowPlayingOpen by remember { mutableStateOf(false) }
    var openLibraryWithSearch by remember { mutableStateOf(false) }

    var showQueueSheet by remember { mutableStateOf(false) }
    var showEqualizerSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var trackForPlaylistDialog by remember { mutableStateOf<Track?>(null) }
    var askedNotificationOnce by remember { mutableStateOf(false) }

    // Zero-permission SAF Audio File Importer (MP3, WAV, FLAC, AAC, OGG, M4A)
    val importAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importAudioFiles(uris, strings.importedCount)
        }
    }

    // Device MediaStore Audio Scanner Permission Launcher
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.scanDeviceMusic(strings.deviceMusicFound)
        } else {
            viewModel.showStatus(strings.permissionNeeded)
        }
    }

    // Android 13+ Notification permission for Media3 lock-screen / status bar controls
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ -> }

    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !askedNotificationOnce) {
            askedNotificationOnce = true
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    fun triggerScanDevice() {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED) {
            viewModel.scanDeviceMusic(strings.deviceMusicFound)
        } else {
            audioPermissionLauncher.launch(perm)
        }
    }

    fun triggerImportAudio() {
        importAudioLauncher.launch(arrayOf("audio/*"))
    }

    var requestedStartupScan by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!requestedStartupScan) {
            requestedStartupScan = true
            triggerScanDevice()
        }
    }

    // Fluid spring transition progress between Main Shell (0f) and Full Turntable Now Playing (1f)
    val expandProgress by animateFloatAsState(
        targetValue = if (isNowPlayingOpen) 1f else 0f,
        animationSpec = spring(
            dampingRatio = if (uiState.preferences.reducedMotion) 1f else 0.85f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "nowPlayingTransition",
    )

    val safeDuration = uiState.durationMs.coerceAtLeast(1L)
    val progressFraction = (uiState.positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(palette.canvasTop, palette.canvasBottom))
            ),
    ) {
        // Primary Shell Layer (Home / Explore / Library + Floating Mini Player + Minimal Bottom Nav)
        if (expandProgress < 0.99f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val s = 1f - expandProgress * 0.055f
                        scaleX = s
                        scaleY = s
                        alpha = 1f - expandProgress
                    },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                ) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (selectedTab) {
                            MainTab.EXPLORE -> {
                                ExploreScreen(
                                    tracks = uiState.tracks,
                                    activeTrackId = uiState.currentTrack?.id,
                                    isPlaying = uiState.isPlaying,
                                    hapticsEnabled = uiState.preferences.hapticsEnabled,
                                    reducedMotion = uiState.preferences.reducedMotion,
                                    onSelectTrackAndOpenPlayer = { track ->
                                        ensureNotificationPermission()
                                        viewModel.playTrack(track, uiState.tracks)
                                        isNowPlayingOpen = true
                                    },
                                    onPlayNext = { track ->
                                        viewModel.playNext(track, strings.playingNext(track.title))
                                    },
                                    onAddToQueue = { track ->
                                        viewModel.addToQueue(track, strings.addedToQueue(track.title))
                                    },
                                    onImportAudio = ::triggerImportAudio,
                                    onScanDevice = ::triggerScanDevice,
                                    onOpenEqualizer = { showEqualizerSheet = true },
                                    onOpenSettings = { showSettingsSheet = true },
                                )
                            }

                            MainTab.HOME -> {
                                val favTracks = remember(uiState.tracks, uiState.favoriteIds) {
                                    uiState.tracks.filter { uiState.favoriteIds.contains(it.id) }
                                }
                                HomeScreen(
                                    currentTrack = uiState.currentTrack,
                                    isPlaying = uiState.isPlaying,
                                    recentTracks = uiState.recentTracks,
                                    favoriteTracks = favTracks,
                                    allTracks = uiState.tracks,
                                    sleepTimerRemainingMs = uiState.sleepTimerRemainingMs,
                                    isScanningDevice = uiState.isScanningDevice,
                                    hapticsEnabled = uiState.preferences.hapticsEnabled,
                                    onPlayTrack = { track, queue ->
                                        ensureNotificationPermission()
                                        viewModel.playTrack(track, queue)
                                        isNowPlayingOpen = true
                                    },
                                    onPlayNext = { track ->
                                        viewModel.playNext(track, strings.playingNext(track.title))
                                    },
                                    onAddToQueue = { track ->
                                        viewModel.addToQueue(track, strings.addedToQueue(track.title))
                                    },
                                    onOpenAddToPlaylist = { track -> trackForPlaylistDialog = track },
                                    onTogglePlayPause = {
                                        ensureNotificationPermission()
                                        viewModel.togglePlayPause()
                                    },
                                    onOpenNowPlaying = { isNowPlayingOpen = true },
                                    onOpenSearch = {
                                        openLibraryWithSearch = true
                                        selectedTab = MainTab.LIBRARY
                                    },
                                    onOpenEqualizer = { showEqualizerSheet = true },
                                    onOpenSettings = { showSettingsSheet = true },
                                    onImportAudio = ::triggerImportAudio,
                                    onScanDevice = ::triggerScanDevice,
                                    favoriteIds = uiState.favoriteIds,
                                    sortField = uiState.preferences.homeSortField,
                                    sortAscending = uiState.preferences.homeSortAscending,
                                    filterType = uiState.preferences.homeFilterType,
                                    onSortChange = viewModel::setHomeSort,
                                    onFilterChange = viewModel::setHomeFilter,
                                    onToggleFavorite = viewModel::toggleFavorite,
                                )
                            }

                            MainTab.LIBRARY -> {
                                LibraryScreen(
                                    tracks = uiState.tracks,
                                    albums = uiState.albums,
                                    artists = uiState.artists,
                                    folders = uiState.folders,
                                    playlists = uiState.playlists,
                                    favoriteIds = uiState.favoriteIds,
                                    activeTrackId = uiState.currentTrack?.id,
                                    initialSearchFocused = openLibraryWithSearch,
                                    hapticsEnabled = uiState.preferences.hapticsEnabled,
                                    onPlayTrack = { track, queue ->
                                        ensureNotificationPermission()
                                        viewModel.playTrack(track, queue)
                                        isNowPlayingOpen = true
                                    },
                                    onPlayNext = { track ->
                                        viewModel.playNext(track, strings.playingNext(track.title))
                                    },
                                    onAddToQueue = { track ->
                                        viewModel.addToQueue(track, strings.addedToQueue(track.title))
                                    },
                                    onToggleFavorite = viewModel::toggleFavorite,
                                    onOpenAddToPlaylist = { track -> trackForPlaylistDialog = track },
                                    onOpenCreatePlaylist = { showCreatePlaylistDialog = true },
                                    onDeletePlaylist = viewModel::deletePlaylist,
                                    onRemoveFromPlaylist = viewModel::removeTrackFromPlaylist,
                                    onImportAudio = ::triggerImportAudio,
                                    onScanDevice = ::triggerScanDevice,
                                )
                            }
                        }

                        // Floating Mini Player perched smoothly above Bottom Nav when a track is loaded/playing
                        val currentTrack = uiState.currentTrack
                        val showMiniPlayer = currentTrack != null && (uiState.isPlaying || selectedTab != MainTab.EXPLORE)
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showMiniPlayer,
                            enter = slideInVertically { it } + fadeIn(),
                            exit = slideOutVertically { it } + fadeOut(),
                            modifier = Modifier.align(Alignment.BottomCenter),
                        ) {
                            if (currentTrack != null) {
                                FloatingMiniPlayer(
                                    track = currentTrack,
                                    isPlaying = uiState.isPlaying,
                                    progressFraction = progressFraction,
                                    hapticsEnabled = uiState.preferences.hapticsEnabled,
                                    batterySaverMode = uiState.preferences.batterySaverMode,
                                    reducedMotion = uiState.preferences.reducedMotion,
                                    onTogglePlayPause = {
                                        ensureNotificationPermission()
                                        viewModel.togglePlayPause()
                                    },
                                    onExpand = { isNowPlayingOpen = true },
                                )
                            }
                        }
                    }

                    PlinthBottomNav(
                        selectedTab = selectedTab,
                        hapticsEnabled = uiState.preferences.hapticsEnabled,
                        onSelectTab = { tab ->
                            openLibraryWithSearch = false
                            selectedTab = tab
                        },
                    )
                }
            }
        }

        // Fullscreen Turntable Now Playing Overlay with physics-driven spring bloom transition
        val activeTrack = uiState.currentTrack
        if (expandProgress > 0.01f && activeTrack != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val s = 0.88f + expandProgress * 0.12f
                        scaleX = s
                        scaleY = s
                        translationY = (1f - expandProgress) * 90f
                        alpha = expandProgress
                    },
            ) {
                NowPlayingScreen(
                    track = activeTrack,
                    isPlaying = uiState.isPlaying,
                    positionMs = uiState.positionMs,
                    durationMs = uiState.durationMs,
                    volume = uiState.volume,
                    shuffleEnabled = uiState.preferences.shuffleEnabled,
                    repeatMode = uiState.preferences.repeatMode,
                    isFavorite = uiState.favoriteIds.contains(activeTrack.id),
                    sleepTimerRemainingMs = uiState.sleepTimerRemainingMs,
                    spectrumFlow = viewModel.spectrum,
                    trackChangeNonce = uiState.trackChangeNonce,
                    trackChangeDirection = uiState.trackChangeDirection,
                    hapticsEnabled = uiState.preferences.hapticsEnabled,
                    graphicsQuality = uiState.preferences.graphicsQuality,
                    batterySaverMode = uiState.preferences.batterySaverMode,
                    reducedMotion = uiState.preferences.reducedMotion,
                    onBack = { isNowPlayingOpen = false },
                    onTogglePlayPause = {
                        ensureNotificationPermission()
                        viewModel.togglePlayPause()
                    },
                    onPrevious = viewModel::previousTrack,
                    onNext = viewModel::nextTrack,
                    onSeekTo = viewModel::seekTo,
                    onScrubDeltaMs = viewModel::scrubByDeltaMs,
                    onToggleShuffle = viewModel::toggleShuffle,
                    onCycleRepeat = viewModel::cycleRepeatMode,
                    onToggleFavorite = { viewModel.toggleFavorite(activeTrack.id) },
                    onVolumeChange = viewModel::setVolume,
                    onOpenQueue = { showQueueSheet = true },
                    onOpenEqualizer = { showEqualizerSheet = true },
                    onOpenAddToPlaylist = { trackForPlaylistDialog = activeTrack },
                    onOpenSettings = { showSettingsSheet = true },
                )
            }
        }

        // Subtle Floating Status Toast Capsule
        AnimatedVisibility(
            visible = uiState.statusMessage != null,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp, start = 24.dp, end = 24.dp),
        ) {
            val msg = uiState.statusMessage.orEmpty()
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(palette.inkPrimary)
                    .border(1.dp, palette.hairline, RoundedCornerShape(22.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text(
                    text = msg,
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.capsuleBg,
                )
            }
        }
    }

    // Modal Hardware Bottom Sheets & Playlist Dialogs
    if (showQueueSheet) {
        QueueBottomSheet(
            queue = uiState.queue,
            currentTrackId = uiState.currentTrack?.id,
            onSelectTrack = { track ->
                viewModel.playTrack(track, uiState.queue)
                showQueueSheet = false
            },
            onMoveItem = viewModel::moveQueueItem,
            onRemoveIndex = viewModel::removeFromQueue,
            onDismiss = { showQueueSheet = false },
        )
    }

    if (showEqualizerSheet) {
        EqualizerBottomSheet(
            eqState = eqState,
            spectrumFlow = viewModel.spectrum,
            hapticsEnabled = uiState.preferences.hapticsEnabled,
            customPresets = uiState.preferences.customEqPresets,
            onToggleEnabled = viewModel::setEqualizerEnabled,
            onSelectPreset = viewModel::selectEqualizerPreset,
            onBandChange = viewModel::setEqualizerBand,
            onSaveCustomPreset = viewModel::saveCustomEqualizerPreset,
            onSelectCustomPreset = viewModel::selectCustomEqualizerPreset,
            onRenameCustomPreset = viewModel::renameCustomEqualizerPreset,
            onDeleteCustomPreset = viewModel::deleteCustomEqualizerPreset,
            onDismiss = { showEqualizerSheet = false },
        )
    }

    if (showSettingsSheet) {
        SettingsBottomSheet(
            preferences = uiState.preferences,
            sleepTimerRemainingMs = uiState.sleepTimerRemainingMs,
            onSetThemeMode = viewModel::setThemeMode,
            onSetLanguage = viewModel::setLanguage,
            onSetSleepTimerMinutes = viewModel::setSleepTimerMinutes,
            onSetHaptics = viewModel::setHapticsEnabled,
            onSetUiSounds = viewModel::setUiSoundsEnabled,
            onSetGraphicsQuality = viewModel::setGraphicsQuality,
            onSetBatterySaver = viewModel::setBatterySaverMode,
            onSetReducedMotion = viewModel::setReducedMotion,
            onSetCrossfade = viewModel::setCrossfadeEnabled,
            onImportAudio = ::triggerImportAudio,
            onScanDevice = ::triggerScanDevice,
            onDismiss = { showSettingsSheet = false },
        )
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onCreate = viewModel::createPlaylist,
            onDismiss = { showCreatePlaylistDialog = false },
        )
    }

    val pendingTrack = trackForPlaylistDialog
    if (pendingTrack != null) {
        AddToPlaylistDialog(
            track = pendingTrack,
            playlists = uiState.playlists,
            onSelectPlaylist = { pl ->
                viewModel.addTrackToPlaylist(
                    playlistId = pl.id,
                    trackId = pendingTrack.id,
                    playlistName = pl.name,
                    feedback = strings.addedToPlaylist(pl.name),
                )
            },
            onCreateNewPlaylist = { showCreatePlaylistDialog = true },
            onDismiss = { trackForPlaylistDialog = null },
        )
    }
}
