package com.example.audio

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin

data class CrossfadeGains(
    val outgoingVolume: Float,
    val incomingVolume: Float,
)

object CrossfadeMath {
    const val DEFAULT_CROSSFADE_MS: Long = 7_000L
    const val PRE_PREPARE_LEAD_MS: Long = 5_000L
    private const val MIN_TRACK_FOR_CROSSFADE_MS: Long = 1_200L
    private const val MIN_EFFECTIVE_CROSSFADE_MS: Long = 500L
    private const val MAX_TRACK_OVERLAP_FRACTION: Double = 0.45

    /**
     * Computes effective crossfade overlap duration in ms.
     * Standard tracks get the full 7,000 ms; shorter tracks proportionally shrink
     * the overlap so neither track is mostly swallowed by the fade.
     */
    fun effectiveCrossfadeDurationMs(
        currentDurationMs: Long,
        nextDurationMs: Long,
        nominalMs: Long = DEFAULT_CROSSFADE_MS,
    ): Long {
        if (currentDurationMs < MIN_TRACK_FOR_CROSSFADE_MS || nominalMs <= 0L) return 0L
        val limitingDuration = if (nextDurationMs > 0L) {
            if (nextDurationMs < MIN_TRACK_FOR_CROSSFADE_MS) return 0L
            minOf(currentDurationMs, nextDurationMs)
        } else {
            currentDurationMs
        }
        val proportionalCap = (limitingDuration * MAX_TRACK_OVERLAP_FRACTION).roundToLong()
        val effective = minOf(nominalMs, proportionalCap)
        return if (effective >= MIN_EFFECTIVE_CROSSFADE_MS) effective else 0L
    }

    /**
     * Equal-power crossfade curve: cos(t * pi / 2) and sin(t * pi / 2), preserving
     * constant RMS loudness (cos^2 + sin^2 == 1) across the transition, combined with
     * dynamic peak-sum anti-clipping protection when two loud tracks overlap so
     * (outgoing * outgoingPeak + incoming * incomingPeak) never exceeds userVolume.
     */
    fun computeEqualPowerGains(
        progress: Float,
        userVolume: Float,
        outgoingPeak: Float = 0.85f,
        incomingPeak: Float = 0.85f,
    ): CrossfadeGains {
        val t = progress.coerceIn(0f, 1f)
        val master = userVolume.coerceIn(0f, 1f)
        if (master <= 0f) return CrossfadeGains(0f, 0f)
        if (t <= 0f) return CrossfadeGains(outgoingVolume = master, incomingVolume = 0f)
        if (t >= 1f) return CrossfadeGains(outgoingVolume = 0f, incomingVolume = master)

        val angle = t.toDouble() * (PI / 2.0)
        val rawOut = cos(angle).toFloat().coerceIn(0f, 1f)
        val rawIn = sin(angle).toFloat().coerceIn(0f, 1f)

        val peakOut = outgoingPeak.coerceIn(0f, 1f)
        val peakIn = incomingPeak.coerceIn(0f, 1f)
        val predictedPeakSum = rawOut * peakOut + rawIn * peakIn
        val antiClipScale = if (predictedPeakSum > 1.0f) {
            (1.0f / predictedPeakSum).coerceIn(0f, 1f)
        } else {
            1f
        }

        return CrossfadeGains(
            outgoingVolume = (rawOut * antiClipScale * master).coerceIn(0f, 1f),
            incomingVolume = (rawIn * antiClipScale * master).coerceIn(0f, 1f),
        )
    }

    /**
     * Resolves the upcoming Media3 queue window index respecting shuffle order and repeat mode.
     */
    fun resolveNextWindowIndex(
        timeline: Timeline,
        currentIndex: Int,
        repeatMode: Int,
        shuffleEnabled: Boolean,
    ): Int {
        if (timeline.isEmpty || currentIndex !in 0 until timeline.windowCount) {
            return C.INDEX_UNSET
        }
        return timeline.getNextWindowIndex(currentIndex, repeatMode, shuffleEnabled)
    }

    /**
     * Extracts the exact playback order currently encoded in a Media3 Timeline so a standby
     * player deck inherits the identical ShuffleOrder across a crossfade handoff.
     */
    fun extractShuffleOrder(timeline: Timeline): IntArray? {
        if (timeline.isEmpty) return null
        val count = timeline.windowCount
        val order = IntArray(count)
        var idx = timeline.getFirstWindowIndex(/* shuffleModeEnabled= */ true)
        var pos = 0
        while (idx != C.INDEX_UNSET && pos < count) {
            order[pos++] = idx
            idx = timeline.getNextWindowIndex(idx, Player.REPEAT_MODE_OFF, /* shuffleModeEnabled= */ true)
        }
        return if (pos == count) order else null
    }
}
