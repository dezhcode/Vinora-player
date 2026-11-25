package com.example.audio

import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi

@OptIn(UnstableApi::class)
class CrossfadeSessionPlayer(
    private val engine: CrossfadeAudioEngine,
) : Player {

    private val active: Player
        get() = engine.activeDeck

    override fun getApplicationLooper(): Looper = active.applicationLooper

    override fun addListener(listener: Player.Listener) {
        engine.sessionListeners.add(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        engine.sessionListeners.remove(listener)
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>) {
        engine.cancelCrossfadeAndRestoreActive()
        active.setMediaItems(mediaItems)
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) {
        engine.cancelCrossfadeAndRestoreActive()
        active.setMediaItems(mediaItems, resetPosition)
    }

    override fun setMediaItems(
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ) {
        engine.cancelCrossfadeAndRestoreActive()
        active.setMediaItems(mediaItems, startIndex, startPositionMs)
    }

    override fun setMediaItem(mediaItem: MediaItem) {
        engine.cancelCrossfadeAndRestoreActive()
        active.setMediaItem(mediaItem)
    }

    override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) {
        engine.cancelCrossfadeAndRestoreActive()
        active.setMediaItem(mediaItem, startPositionMs)
    }

    override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) {
        engine.cancelCrossfadeAndRestoreActive()
        active.setMediaItem(mediaItem, resetPosition)
    }

    override fun addMediaItem(mediaItem: MediaItem) {
        active.addMediaItem(mediaItem)
        engine.invalidateStandbyOnQueueOrModeChange()
    }

    override fun addMediaItem(index: Int, mediaItem: MediaItem) {
        active.addMediaItem(index, mediaItem)
        engine.invalidateStandbyOnQueueOrModeChange()
    }

    override fun addMediaItems(mediaItems: MutableList<MediaItem>) {
        active.addMediaItems(mediaItems)
        engine.invalidateStandbyOnQueueOrModeChange()
    }

    override fun addMediaItems(index: Int, mediaItems: MutableList<MediaItem>) {
        active.addMediaItems(index, mediaItems)
        engine.invalidateStandbyOnQueueOrModeChange()
    }

    override fun moveMediaItem(currentIndex: Int, newIndex: Int) {
        active.moveMediaItem(currentIndex, newIndex)
        engine.invalidateStandbyOnQueueOrModeChange()
    }

    override fun moveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) {
        active.moveMediaItems(fromIndex, toIndex, newIndex)
        engine.invalidateStandbyOnQueueOrModeChange()
    }

    override fun replaceMediaItem(index: Int, mediaItem: MediaItem) {
        active.replaceMediaItem(index, mediaItem)
        engine.invalidateStandbyOnQueueOrModeChange()
    }

    override fun replaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: MutableList<MediaItem>) {
        active.replaceMediaItems(fromIndex, toIndex, mediaItems)
        engine.invalidateStandbyOnQueueOrModeChange()
    }

    override fun removeMediaItem(index: Int) {
        active.removeMediaItem(index)
        engine.invalidateStandbyOnQueueOrModeChange()
    }

    override fun removeMediaItems(fromIndex: Int, toIndex: Int) {
        active.removeMediaItems(fromIndex, toIndex)
        engine.invalidateStandbyOnQueueOrModeChange()
    }

    override fun clearMediaItems() {
        engine.cancelCrossfadeAndRestoreActive()
        active.clearMediaItems()
    }

    override fun isCommandAvailable(command: Int): Boolean = active.isCommandAvailable(command)

    override fun canAdvertiseSession(): Boolean = active.canAdvertiseSession()

    override fun getAvailableCommands(): Player.Commands = active.availableCommands

    override fun prepare() {
        active.prepare()
    }

    override fun getPlaybackState(): Int = active.playbackState

    override fun getPlaybackSuppressionReason(): Int = active.playbackSuppressionReason

    override fun isPlaying(): Boolean = active.isPlaying

    override fun getPlayerError(): PlaybackException? = active.playerError

    override fun play() {
        active.play()
        engine.onActivePlaybackCommand()
    }

    override fun pause() {
        engine.cancelCrossfadeAndRestoreActive()
        active.pause()
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (!playWhenReady) {
            engine.cancelCrossfadeAndRestoreActive()
        } else {
            engine.onActivePlaybackCommand()
        }
        active.playWhenReady = playWhenReady
    }

    override fun getPlayWhenReady(): Boolean = active.playWhenReady

    override fun setRepeatMode(repeatMode: Int) {
        active.repeatMode = repeatMode
        engine.invalidateStandbyOnQueueOrModeChange()
    }

    override fun getRepeatMode(): Int = active.repeatMode

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        active.shuffleModeEnabled = shuffleModeEnabled
        engine.invalidateStandbyOnQueueOrModeChange()
    }

    override fun getShuffleModeEnabled(): Boolean = active.shuffleModeEnabled

    override fun isLoading(): Boolean = active.isLoading

    override fun seekToDefaultPosition() {
        engine.cancelCrossfadeAndRestoreActive()
        active.seekToDefaultPosition()
    }

    override fun seekToDefaultPosition(mediaItemIndex: Int) {
        engine.cancelCrossfadeAndRestoreActive()
        active.seekToDefaultPosition(mediaItemIndex)
    }

    override fun seekTo(positionMs: Long) {
        engine.cancelCrossfadeAndRestoreActive()
        active.seekTo(positionMs)
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        engine.cancelCrossfadeAndRestoreActive()
        active.seekTo(mediaItemIndex, positionMs)
    }

    override fun getSeekBackIncrement(): Long = active.seekBackIncrement

    override fun seekBack() {
        engine.cancelCrossfadeAndRestoreActive()
        active.seekBack()
    }

    override fun getSeekForwardIncrement(): Long = active.seekForwardIncrement

    override fun seekForward() {
        engine.cancelCrossfadeAndRestoreActive()
        active.seekForward()
    }

    override fun hasPreviousMediaItem(): Boolean = active.hasPreviousMediaItem()

    @Deprecated("Deprecated in Java")
    override fun seekToPreviousWindow() {
        engine.cancelCrossfadeAndRestoreActive()
        active.seekToPreviousWindow()
    }

    override fun seekToPreviousMediaItem() {
        engine.cancelCrossfadeAndRestoreActive()
        active.seekToPreviousMediaItem()
    }

    override fun getMaxSeekToPreviousPosition(): Long = active.maxSeekToPreviousPosition

    override fun seekToPrevious() {
        engine.cancelCrossfadeAndRestoreActive()
        active.seekToPrevious()
    }

    @Deprecated("Deprecated in Java")
    override fun hasNext(): Boolean = active.hasNext()

    @Deprecated("Deprecated in Java")
    override fun hasNextWindow(): Boolean = active.hasNextWindow()

    override fun hasNextMediaItem(): Boolean = active.hasNextMediaItem()

    @Deprecated("Deprecated in Java")
    override fun next() {
        engine.cancelCrossfadeAndRestoreActive()
        active.next()
    }

    @Deprecated("Deprecated in Java")
    override fun seekToNextWindow() {
        engine.cancelCrossfadeAndRestoreActive()
        active.seekToNextWindow()
    }

    override fun seekToNextMediaItem() {
        engine.cancelCrossfadeAndRestoreActive()
        active.seekToNextMediaItem()
    }

    override fun seekToNext() {
        engine.cancelCrossfadeAndRestoreActive()
        active.seekToNext()
    }

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        active.playbackParameters = playbackParameters
    }

    override fun setPlaybackSpeed(speed: Float) {
        active.setPlaybackSpeed(speed)
    }

    override fun getPlaybackParameters(): PlaybackParameters = active.playbackParameters

    override fun stop() {
        engine.cancelCrossfadeAndRestoreActive()
        active.stop()
    }

    override fun release() {
        engine.release()
    }

    override fun getCurrentTracks(): Tracks = active.currentTracks

    override fun getTrackSelectionParameters(): TrackSelectionParameters = active.trackSelectionParameters

    override fun setTrackSelectionParameters(parameters: TrackSelectionParameters) {
        active.trackSelectionParameters = parameters
    }

    override fun getMediaMetadata(): MediaMetadata = active.mediaMetadata

    override fun getPlaylistMetadata(): MediaMetadata = active.playlistMetadata

    override fun setPlaylistMetadata(mediaMetadata: MediaMetadata) {
        active.playlistMetadata = mediaMetadata
    }

    override fun getCurrentManifest(): Any? = active.currentManifest

    override fun getCurrentTimeline(): Timeline = active.currentTimeline

    override fun getCurrentPeriodIndex(): Int = active.currentPeriodIndex

    @Deprecated("Deprecated in Java")
    override fun getCurrentWindowIndex(): Int = active.currentWindowIndex

    override fun getCurrentMediaItemIndex(): Int = active.currentMediaItemIndex

    @Deprecated("Deprecated in Java")
    override fun getNextWindowIndex(): Int = active.nextWindowIndex

    override fun getNextMediaItemIndex(): Int = active.nextMediaItemIndex

    @Deprecated("Deprecated in Java")
    override fun getPreviousWindowIndex(): Int = active.previousWindowIndex

    override fun getPreviousMediaItemIndex(): Int = active.previousMediaItemIndex

    override fun getCurrentMediaItem(): MediaItem? = active.currentMediaItem

    override fun getMediaItemCount(): Int = active.mediaItemCount

    override fun getMediaItemAt(index: Int): MediaItem = active.getMediaItemAt(index)

    override fun getDuration(): Long = active.duration

    override fun getCurrentPosition(): Long = active.currentPosition

    override fun getBufferedPosition(): Long = active.bufferedPosition

    override fun getBufferedPercentage(): Int = active.bufferedPercentage

    override fun getTotalBufferedDuration(): Long = active.totalBufferedDuration

    @Deprecated("Deprecated in Java")
    override fun isCurrentWindowDynamic(): Boolean = active.isCurrentWindowDynamic

    override fun isCurrentMediaItemDynamic(): Boolean = active.isCurrentMediaItemDynamic

    @Deprecated("Deprecated in Java")
    override fun isCurrentWindowLive(): Boolean = active.isCurrentWindowLive

    override fun isCurrentMediaItemLive(): Boolean = active.isCurrentMediaItemLive

    override fun getCurrentLiveOffset(): Long = active.currentLiveOffset

    @Deprecated("Deprecated in Java")
    override fun isCurrentWindowSeekable(): Boolean = active.isCurrentWindowSeekable

    override fun isCurrentMediaItemSeekable(): Boolean = active.isCurrentMediaItemSeekable

    override fun isPlayingAd(): Boolean = active.isPlayingAd

    override fun getCurrentAdGroupIndex(): Int = active.currentAdGroupIndex

    override fun getCurrentAdIndexInAdGroup(): Int = active.currentAdIndexInAdGroup

    override fun getContentDuration(): Long = active.contentDuration

    override fun getContentPosition(): Long = active.contentPosition

    override fun getContentBufferedPosition(): Long = active.contentBufferedPosition

    override fun getAudioAttributes(): AudioAttributes = active.audioAttributes

    override fun setVolume(volume: Float) {
        engine.setUserVolume(volume)
    }

    override fun getVolume(): Float = engine.userVolume

    override fun clearVideoSurface() = active.clearVideoSurface()

    override fun clearVideoSurface(surface: Surface?) = active.clearVideoSurface(surface)

    override fun setVideoSurface(surface: Surface?) = active.setVideoSurface(surface)

    override fun setVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) = active.setVideoSurfaceHolder(surfaceHolder)

    override fun clearVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) = active.clearVideoSurfaceHolder(surfaceHolder)

    override fun setVideoSurfaceView(surfaceView: SurfaceView?) = active.setVideoSurfaceView(surfaceView)

    override fun clearVideoSurfaceView(surfaceView: SurfaceView?) = active.clearVideoSurfaceView(surfaceView)

    override fun setVideoTextureView(textureView: TextureView?) = active.setVideoTextureView(textureView)

    override fun clearVideoTextureView(textureView: TextureView?) = active.clearVideoTextureView(textureView)

    override fun getVideoSize(): VideoSize = active.videoSize

    override fun getSurfaceSize(): Size = active.surfaceSize

    override fun getCurrentCues(): CueGroup = active.currentCues

    override fun getDeviceInfo(): DeviceInfo = active.deviceInfo

    override fun getDeviceVolume(): Int = active.deviceVolume

    override fun isDeviceMuted(): Boolean = active.isDeviceMuted

    @Deprecated("Deprecated in Java")
    override fun setDeviceVolume(volume: Int) = active.setDeviceVolume(volume)

    override fun setDeviceVolume(volume: Int, flags: Int) = active.setDeviceVolume(volume, flags)

    @Deprecated("Deprecated in Java")
    override fun increaseDeviceVolume() = active.increaseDeviceVolume()

    override fun increaseDeviceVolume(flags: Int) = active.increaseDeviceVolume(flags)

    @Deprecated("Deprecated in Java")
    override fun decreaseDeviceVolume() = active.decreaseDeviceVolume()

    override fun decreaseDeviceVolume(flags: Int) = active.decreaseDeviceVolume(flags)

    @Deprecated("Deprecated in Java")
    override fun setDeviceMuted(muted: Boolean) = active.setDeviceMuted(muted)

    override fun setDeviceMuted(muted: Boolean, flags: Int) = active.setDeviceMuted(muted, flags)

    override fun setAudioAttributes(audioAttributes: AudioAttributes, handleAudioFocus: Boolean) {
        engine.deckA.setAudioAttributes(audioAttributes, handleAudioFocus)
        engine.deckB.setAudioAttributes(audioAttributes, handleAudioFocus)
    }
}
