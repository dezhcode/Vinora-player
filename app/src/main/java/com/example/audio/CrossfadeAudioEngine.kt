package com.example.audio

import android.content.Context
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.ShuffleOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArraySet

@OptIn(UnstableApi::class)
class CrossfadeAudioEngine(
    context: Context,
    private val serviceScope: CoroutineScope,
    audioAttributes: AudioAttributes,
) {
    val spectrumA = AudioSpectrumProcessor(publishToSpectrumBus = true)
    val spectrumB = AudioSpectrumProcessor(publishToSpectrumBus = false)

    val deckA: ExoPlayer = buildDeck(context, spectrumA, audioAttributes)
    val deckB: ExoPlayer = buildDeck(context, spectrumB, audioAttributes)

    var activeDeck: ExoPlayer = deckA
        private set
    var standbyDeck: ExoPlayer = deckB
        private set
    private var activeSpectrum: AudioSpectrumProcessor = spectrumA
    private var standbySpectrum: AudioSpectrumProcessor = spectrumB

    val sessionListeners = CopyOnWriteArraySet<Player.Listener>()

    @Volatile
    var crossfadeEnabled: Boolean = true
        private set

    @Volatile
    var userVolume: Float = 1f
        private set

    @Volatile
    var isCrossfading: Boolean = false
        private set

    private var preparedNextIndex: Int = C.INDEX_UNSET
    private var failedNextIndex: Int = C.INDEX_UNSET
    private var fadeStartElapsedMs: Long = 0L
    private var activeFadeDurationMs: Long = CrossfadeMath.DEFAULT_CROSSFADE_MS
    private var monitorJob: Job? = null
    private var suppressDeckTransitionCallback: Boolean = false

    private val deckAListener = DeckListener(deckA)
    private val deckBListener = DeckListener(deckB)

    init {
        deckA.addListener(deckAListener)
        deckB.addListener(deckBListener)

        if (deckA.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            AudioEqualizerController.attachToSession(deckA.audioSessionId)
        }
        if (deckB.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            AudioEqualizerController.attachToSession(deckB.audioSessionId)
        }
        startMonitorIfNeeded()
    }

    private fun buildDeck(
        context: Context,
        processor: AudioSpectrumProcessor,
        audioAttributes: AudioAttributes,
    ): ExoPlayer {
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(processor))
                    .build()
            }
        }
        return ExoPlayer.Builder(context, renderersFactory)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    fun setCrossfadeEnabled(enabled: Boolean) {
        if (crossfadeEnabled == enabled) return
        crossfadeEnabled = enabled
        if (!enabled) {
            cancelCrossfadeAndRestoreActive()
        } else {
            startMonitorIfNeeded()
        }
    }

    fun setUserVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        userVolume = clamped
        if (isCrossfading) {
            applyCurrentFadeGains()
        } else {
            activeDeck.volume = clamped
        }
        sessionListeners.forEach { it.onVolumeChanged(clamped) }
    }

    fun onActivePlaybackCommand() {
        startMonitorIfNeeded()
    }

    fun cancelCrossfadeAndRestoreActive() {
        val wasFading = isCrossfading
        isCrossfading = false
        preparedNextIndex = C.INDEX_UNSET
        failedNextIndex = C.INDEX_UNSET
        fadeStartElapsedMs = 0L
        activeFadeDurationMs = CrossfadeMath.DEFAULT_CROSSFADE_MS

        runCatching {
            standbyDeck.playWhenReady = false
            standbyDeck.pause()
            standbyDeck.stop()
            standbyDeck.clearMediaItems()
            standbyDeck.volume = userVolume
        }
        activeDeck.volume = userVolume
        if (wasFading) {
            activeSpectrum.publishToSpectrumBus = true
            standbySpectrum.publishToSpectrumBus = false
        }
    }

    fun invalidateStandbyOnQueueOrModeChange() {
        if (isCrossfading) {
            cancelCrossfadeAndRestoreActive()
        } else if (preparedNextIndex != C.INDEX_UNSET || failedNextIndex != C.INDEX_UNSET) {
            preparedNextIndex = C.INDEX_UNSET
            failedNextIndex = C.INDEX_UNSET
            runCatching {
                standbyDeck.playWhenReady = false
                standbyDeck.stop()
                standbyDeck.clearMediaItems()
                standbyDeck.volume = userVolume
            }
        }
    }

    private fun startMonitorIfNeeded() {
        if (monitorJob?.isActive == true) return
        monitorJob = serviceScope.launch {
            while (isActive) {
                tickCrossfadeEngine()
                delay(if (isCrossfading) 25L else 90L)
            }
        }
    }

    private fun tickCrossfadeEngine() {
        if (!crossfadeEnabled) {
            if (isCrossfading || preparedNextIndex != C.INDEX_UNSET) {
                cancelCrossfadeAndRestoreActive()
            }
            return
        }

        val active = activeDeck
        if (!active.isPlaying || active.playbackState != Player.STATE_READY) {
            return
        }

        val duration = active.duration
        val position = active.currentPosition
        if (duration <= 0L || position < 0L) return

        if (isCrossfading) {
            val elapsed = (SystemClock.elapsedRealtime() - fadeStartElapsedMs).coerceAtLeast(0L)
            val progress = (elapsed.toFloat() / activeFadeDurationMs.toFloat()).coerceIn(0f, 1f)
            applyCurrentFadeGains(progress)
            val remaining = (duration - position).coerceAtLeast(0L)
            if (progress >= 1.0f || remaining <= 90L) {
                finishCrossfade()
            }
            return
        }

        val nextIdx = CrossfadeMath.resolveNextWindowIndex(
            timeline = active.currentTimeline,
            currentIndex = active.currentMediaItemIndex,
            repeatMode = active.repeatMode,
            shuffleEnabled = active.shuffleModeEnabled,
        )
        if (nextIdx == C.INDEX_UNSET || nextIdx == failedNextIndex) return

        val remaining = (duration - position).coerceAtLeast(0L)
        val prepareLeadMs = CrossfadeMath.DEFAULT_CROSSFADE_MS + CrossfadeMath.PRE_PREPARE_LEAD_MS
        if (remaining > prepareLeadMs && duration > prepareLeadMs) {
            return
        }

        if (preparedNextIndex != nextIdx) {
            prepareStandby(nextIdx)
            return
        }

        if (standbyDeck.playbackState == Player.STATE_READY) {
            val fadeMs = CrossfadeMath.effectiveCrossfadeDurationMs(
                currentDurationMs = duration,
                nextDurationMs = standbyDeck.duration,
                nominalMs = CrossfadeMath.DEFAULT_CROSSFADE_MS,
            )
            if (fadeMs > 0L && remaining <= fadeMs) {
                startCrossfade(fadeMs, remaining)
            }
        }
    }

    private fun prepareStandby(nextIdx: Int) {
        val active = activeDeck
        val count = active.mediaItemCount
        if (nextIdx !in 0 until count) return

        val items = List(count) { i -> active.getMediaItemAt(i) }
        val shuffleEnabled = active.shuffleModeEnabled
        val repeatMode = active.repeatMode
        val shuffleOrderIndices = if (shuffleEnabled) {
            CrossfadeMath.extractShuffleOrder(active.currentTimeline)
        } else {
            null
        }

        preparedNextIndex = nextIdx
        standbySpectrum.publishToSpectrumBus = false
        standbyDeck.playWhenReady = false
        standbyDeck.volume = 0f
        standbyDeck.setMediaItems(items, nextIdx, 0L)
        if (shuffleOrderIndices != null) {
            standbyDeck.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(shuffleOrderIndices, 0L))
        }
        standbyDeck.shuffleModeEnabled = shuffleEnabled
        standbyDeck.repeatMode = repeatMode
        standbyDeck.prepare()
    }

    private fun startCrossfade(nominalFadeMs: Long, remainingMs: Long) {
        val effectiveFade = minOf(nominalFadeMs, remainingMs).coerceAtLeast(350L)
        activeFadeDurationMs = effectiveFade
        fadeStartElapsedMs = SystemClock.elapsedRealtime()
        isCrossfading = true
        standbyDeck.volume = 0f
        standbyDeck.play()
        applyCurrentFadeGains(0f)
    }

    private fun applyCurrentFadeGains(explicitProgress: Float? = null) {
        val progress = explicitProgress ?: run {
            val elapsed = (SystemClock.elapsedRealtime() - fadeStartElapsedMs).coerceAtLeast(0L)
            (elapsed.toFloat() / activeFadeDurationMs.toFloat()).coerceIn(0f, 1f)
        }
        val gains = CrossfadeMath.computeEqualPowerGains(
            progress = progress,
            userVolume = userVolume,
            outgoingPeak = activeSpectrum.peakAmplitude.takeIf { it > 0.01f } ?: 0.85f,
            incomingPeak = standbySpectrum.peakAmplitude.takeIf { it > 0.01f } ?: 0.85f,
        )
        activeDeck.volume = gains.outgoingVolume
        standbyDeck.volume = gains.incomingVolume
    }

    private fun finishCrossfade() {
        if (!isCrossfading) return
        isCrossfading = false
        preparedNextIndex = C.INDEX_UNSET
        failedNextIndex = C.INDEX_UNSET

        val outgoing = activeDeck
        val incoming = standbyDeck
        val oldPosInfo = Player.PositionInfo(
            null,
            outgoing.currentMediaItemIndex,
            outgoing.currentMediaItem,
            null,
            outgoing.currentPeriodIndex,
            outgoing.currentPosition,
            outgoing.contentPosition,
            outgoing.currentAdGroupIndex,
            outgoing.currentAdIndexInAdGroup,
        )

        incoming.volume = userVolume
        if (!incoming.isPlaying && incoming.playbackState != Player.STATE_ENDED) {
            incoming.play()
        }

        // Swap decks WITHOUT restarting incoming track — it continues seamlessly from its live position
        suppressDeckTransitionCallback = true
        try {
            outgoing.playWhenReady = false
            outgoing.pause()
            outgoing.stop()
            outgoing.clearMediaItems()
            outgoing.volume = userVolume

            activeDeck = incoming
            standbyDeck = outgoing
            val oldActiveSpec = activeSpectrum
            activeSpectrum = standbySpectrum
            standbySpectrum = oldActiveSpec
            activeSpectrum.publishToSpectrumBus = true
            standbySpectrum.publishToSpectrumBus = false
        } finally {
            suppressDeckTransitionCallback = false
        }

        val newPosInfo = Player.PositionInfo(
            null,
            activeDeck.currentMediaItemIndex,
            activeDeck.currentMediaItem,
            null,
            activeDeck.currentPeriodIndex,
            activeDeck.currentPosition,
            activeDeck.contentPosition,
            activeDeck.currentAdGroupIndex,
            activeDeck.currentAdIndexInAdGroup,
        )

        sessionListeners.forEach { listener ->
            listener.onTimelineChanged(activeDeck.currentTimeline, Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            listener.onPositionDiscontinuity(
                oldPosInfo,
                newPosInfo,
                Player.DISCONTINUITY_REASON_AUTO_TRANSITION,
            )
            listener.onMediaItemTransition(
                activeDeck.currentMediaItem,
                Player.MEDIA_ITEM_TRANSITION_REASON_AUTO,
            )
            listener.onMediaMetadataChanged(activeDeck.mediaMetadata)
            listener.onPlaybackStateChanged(activeDeck.playbackState)
            listener.onIsPlayingChanged(activeDeck.isPlaying)
        }
    }

    fun release() {
        monitorJob?.cancel()
        monitorJob = null
        AudioEqualizerController.release()
        SpectrumBus.decayToIdle()
        deckA.removeListener(deckAListener)
        deckB.removeListener(deckBListener)
        deckA.release()
        deckB.release()
        sessionListeners.clear()
    }

    private inner class DeckListener(private val owner: ExoPlayer) : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            AudioEqualizerController.attachToSession(audioSessionId)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (owner !== activeDeck) return
            if (!isPlaying && isCrossfading) {
                cancelCrossfadeAndRestoreActive()
            }
            if (!isPlaying && !isCrossfading) {
                SpectrumBus.decayToIdle()
            } else if (isPlaying) {
                startMonitorIfNeeded()
            }
            if (!suppressDeckTransitionCallback) {
                sessionListeners.forEach { it.onIsPlayingChanged(isPlaying) }
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (owner !== activeDeck || suppressDeckTransitionCallback) return
            sessionListeners.forEach { it.onPlayWhenReadyChanged(playWhenReady, reason) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (owner === standbyDeck) {
                if (playbackState == Player.STATE_READY && crossfadeEnabled && !isCrossfading) {
                    tickCrossfadeEngine()
                }
                return
            }
            if (isCrossfading && playbackState == Player.STATE_ENDED) {
                finishCrossfade()
                return
            }
            if (!suppressDeckTransitionCallback) {
                sessionListeners.forEach { it.onPlaybackStateChanged(playbackState) }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (owner !== activeDeck || suppressDeckTransitionCallback) return
            if (isCrossfading && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                finishCrossfade()
                return
            }
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                invalidateStandbyOnQueueOrModeChange()
            }
            sessionListeners.forEach { it.onMediaItemTransition(mediaItem, reason) }
        }

        override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
            if (owner !== activeDeck || suppressDeckTransitionCallback) return
            sessionListeners.forEach { it.onMediaMetadataChanged(mediaMetadata) }
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (owner !== activeDeck || suppressDeckTransitionCallback) return
            sessionListeners.forEach { it.onTimelineChanged(timeline, reason) }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (owner !== activeDeck || suppressDeckTransitionCallback) return
            sessionListeners.forEach { it.onPositionDiscontinuity(oldPosition, newPosition, reason) }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            if (owner !== activeDeck || suppressDeckTransitionCallback) return
            sessionListeners.forEach { it.onShuffleModeEnabledChanged(shuffleModeEnabled) }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            if (owner !== activeDeck || suppressDeckTransitionCallback) return
            sessionListeners.forEach { it.onRepeatModeChanged(repeatMode) }
        }

        override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
            if (owner !== activeDeck || suppressDeckTransitionCallback) return
            sessionListeners.forEach { it.onAvailableCommandsChanged(availableCommands) }
        }

        override fun onPlayerError(error: PlaybackException) {
            if (owner === standbyDeck) {
                // Standby preparation failed: gracefully fall back to normal single-deck playback
                failedNextIndex = preparedNextIndex
                preparedNextIndex = C.INDEX_UNSET
                cancelCrossfadeAndRestoreActive()
                return
            }
            cancelCrossfadeAndRestoreActive()
            sessionListeners.forEach { it.onPlayerError(error) }
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (owner !== activeDeck || suppressDeckTransitionCallback) return
            sessionListeners.forEach { it.onEvents(player, events) }
        }
    }
}
