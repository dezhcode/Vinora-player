package com.example.audio

import android.media.audiofx.Equalizer
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Immutable
data class EqPreset(
    val index: Int,
    val bandsDb: List<Int>, // 5 bands in dB (-12..+12)
)

@Immutable
data class EqualizerState(
    val enabled: Boolean = false,
    val hardwareAttached: Boolean = false,
    val presetIndex: Int = 0,
    val activeCustomPresetId: String? = null,
    val bandLabels: List<String> = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz"),
    val bandLevelsDb: List<Int> = listOf(0, 0, 0, 0, 0),
)

object AudioEqualizerController {

    val presets: List<EqPreset> = listOf(
        EqPreset(0, listOf(0, 0, 0, 0, 0)),       // Flat
        EqPreset(1, listOf(4, 2, 0, -1, 2)),      // Warm Vinyl
        EqPreset(2, listOf(7, 4, 0, 1, 2)),       // Deep Bass
        EqPreset(3, listOf(-2, 1, 5, 4, 1)),      // Studio Vocal
        EqPreset(4, listOf(2, 1, 2, 3, 2)),       // Acoustic
        EqPreset(5, listOf(6, 3, -1, 4, 6)),      // Electronic
    )

    private val sessionEqualizers = linkedMapOf<Int, Equalizer>()

    private val _state = MutableStateFlow(EqualizerState())
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    @Synchronized
    fun attachToSession(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        val existing = sessionEqualizers[audioSessionId]
        if (existing != null) {
            applyToHardware(existing, _state.value)
            return
        }
        runCatching {
            val eq = Equalizer(0, audioSessionId)
            sessionEqualizers[audioSessionId] = eq
            val numBands = eq.numberOfBands.toInt().coerceAtMost(5)
            val labels = if (numBands == 5) {
                (0 until 5).map { b ->
                    val hz = eq.getCenterFreq(b.toShort()) / 1000
                    if (hz >= 1000) String.format("%.1fkHz", hz / 1000f) else "${hz}Hz"
                }
            } else {
                listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")
            }
            _state.value = _state.value.copy(
                hardwareAttached = true,
                bandLabels = labels,
            )
            applyToHardware(eq, _state.value)
        }.onFailure {
            if (sessionEqualizers.isEmpty()) {
                _state.value = _state.value.copy(hardwareAttached = false)
            }
        }
    }

    @Synchronized
    fun detachSession(audioSessionId: Int) {
        val removed = sessionEqualizers.remove(audioSessionId) ?: return
        runCatching { removed.release() }
        if (sessionEqualizers.isEmpty()) {
            _state.value = _state.value.copy(hardwareAttached = false)
        }
    }

    @Synchronized
    fun activeSessionIds(): Set<Int> = sessionEqualizers.keys.toSet()

    @Synchronized
    fun applySettings(
        enabled: Boolean,
        presetIndex: Int,
        bandLevelsDb: List<Int>,
        activeCustomPresetId: String? = null,
    ) {
        val clamped = if (bandLevelsDb.size == 5) {
            bandLevelsDb.map { it.coerceIn(-12, 12) }
        } else {
            listOf(0, 0, 0, 0, 0)
        }
        val next = _state.value.copy(
            enabled = enabled,
            presetIndex = presetIndex,
            activeCustomPresetId = activeCustomPresetId,
            bandLevelsDb = clamped,
        )
        _state.value = next
        sessionEqualizers.values.forEach { eq -> applyToHardware(eq, next) }
    }

    private fun applyToHardware(eq: Equalizer, state: EqualizerState) {
        runCatching {
            eq.enabled = state.enabled
            val range = eq.bandLevelRange
            val minMb = range[0].toInt()
            val maxMb = range[1].toInt()
            val bands = eq.numberOfBands.toInt().coerceAtMost(state.bandLevelsDb.size)
            for (i in 0 until bands) {
                val targetMb = (state.bandLevelsDb[i] * 100).coerceIn(minMb, maxMb)
                eq.setBandLevel(i.toShort(), targetMb.toShort())
            }
        }
    }

    @Synchronized
    fun release() {
        sessionEqualizers.values.forEach { eq ->
            runCatching { eq.release() }
        }
        sessionEqualizers.clear()
        _state.value = _state.value.copy(hardwareAttached = false)
    }
}
