package com.example.audio

import androidx.annotation.OptIn
import androidx.compose.runtime.Immutable
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

@Immutable
data class SpectrumSnapshot(
    val bars: List<Float> = List(13) { 0f },
    val bass: Float = 0f,
    val mid: Float = 0f,
    val treble: Float = 0f,
)

object SpectrumBus {
    private val _spectrum = MutableStateFlow(SpectrumSnapshot())
    val spectrum: StateFlow<SpectrumSnapshot> = _spectrum.asStateFlow()

    internal fun publish(snapshot: SpectrumSnapshot) {
        _spectrum.value = snapshot
    }

    fun decayToIdle() {
        _spectrum.value = SpectrumSnapshot()
    }
}

/**
 * Real-time allocation-free PCM FFT analyzer plugged directly into Media3 ExoPlayer's
 * audio pipeline. Extracts authentic Bass, Mid, and Treble spectral energy without
 * requiring microphone (RECORD_AUDIO) permission.
 */
@OptIn(UnstableApi::class)
class AudioSpectrumProcessor : BaseAudioProcessor() {

    private val fftSize = 256
    private val ringBuffer = FloatArray(fftSize)
    private var ringIndex = 0
    private val real = FloatArray(fftSize)
    private val imag = FloatArray(fftSize)
    private val window = FloatArray(fftSize) { i ->
        (0.5f * (1f - cos(2.0 * PI * i / (fftSize - 1)))).toFloat()
    }
    private val smoothedBars = FloatArray(13)
    private var smoothedBass = 0f
    private var smoothedMid = 0f
    private var smoothedTreble = 0f
    private var lastEmitNanos = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining <= 0) return

        val startPos = inputBuffer.position()
        val channelCount = inputAudioFormat.channelCount.coerceAtLeast(1)
        val frameBytes = channelCount * 2

        // Tap PCM samples non-destructively into our FFT ring buffer
        var offset = startPos
        val limit = inputBuffer.limit()
        while (offset + frameBytes <= limit) {
            var monoSum = 0f
            for (ch in 0 until channelCount) {
                val lo = inputBuffer.get(offset + ch * 2).toInt() and 0xFF
                val hi = inputBuffer.get(offset + ch * 2 + 1).toInt()
                val sample = ((hi shl 8) or lo).toShort()
                monoSum += sample / 32768f
            }
            ringBuffer[ringIndex] = monoSum / channelCount
            ringIndex = (ringIndex + 1) and (fftSize - 1)
            offset += frameBytes
        }

        // Throttle FFT emissions to ~45 Hz for buttery smooth low-overhead rendering
        val now = System.nanoTime()
        if (now - lastEmitNanos >= 22_000_000L) {
            lastEmitNanos = now
            computeAndEmitFft()
        }

        // Pass audio samples through completely unmodified
        val output = replaceOutputBuffer(remaining)
        output.put(inputBuffer)
        output.flip()
    }

    override fun onFlush() {
        ringBuffer.fill(0f)
        ringIndex = 0
    }

    override fun onReset() {
        ringBuffer.fill(0f)
        smoothedBars.fill(0f)
        smoothedBass = 0f
        smoothedMid = 0f
        smoothedTreble = 0f
        SpectrumBus.decayToIdle()
    }

    private fun computeAndEmitFft() {
        for (i in 0 until fftSize) {
            val idx = (ringIndex + i) and (fftSize - 1)
            real[i] = ringBuffer[idx] * window[i]
            imag[i] = 0f
        }

        // In-place Cooley-Tukey Radix-2 FFT
        var j = 0
        for (i in 1 until fftSize) {
            var bit = fftSize shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = real[i]
                real[i] = real[j]
                real[j] = tr
                val ti = imag[i]
                imag[i] = imag[j]
                imag[j] = ti
            }
        }

        var len = 2
        while (len <= fftSize) {
            val angle = -2.0 * PI / len
            val wLenR = cos(angle).toFloat()
            val wLenI = sin(angle).toFloat()
            var i = 0
            while (i < fftSize) {
                var wR = 1f
                var wI = 0f
                val half = len shr 1
                for (k in 0 until half) {
                    val uR = real[i + k]
                    val uI = imag[i + k]
                    val vR = real[i + k + half] * wR - imag[i + k + half] * wI
                    val vI = real[i + k + half] * wI + imag[i + k + half] * wR
                    real[i + k] = uR + vR
                    imag[i + k] = uI + vI
                    real[i + k + half] = uR - vR
                    imag[i + k + half] = uI - vI
                    val nextWR = wR * wLenR - wI * wLenI
                    wI = wR * wLenI + wI * wLenR
                    wR = nextWR
                }
                i += len
            }
            len = len shl 1
        }

        // Extract Bass (bins 1..6), Mid (bins 7..28), Treble (bins 29..96)
        val halfN = fftSize / 2
        val rawBass = bandEnergy(1, 6, halfN)
        val rawMid = bandEnergy(7, 28, halfN)
        val rawTreble = bandEnergy(29, 96, halfN)

        smoothedBass = smooth(smoothedBass, rawBass)
        smoothedMid = smooth(smoothedMid, rawMid)
        smoothedTreble = smooth(smoothedTreble, rawTreble)

        // Build 13 visually sculpted symmetrical frequency bars matching the industrial audio deck:
        // Center = Bass + low-mid punch, inner flanks = Mids, outer flanks = Treble shimmer
        val binRanges = arrayOf(
            36 to 62,  // 0 outer-left treble
            24 to 38,  // 1 high-mid
            14 to 24,  // 2 mid
            8 to 15,   // 3 low-mid
            4 to 9,    // 4 upper-bass
            2 to 5,    // 5 bass
            1 to 4,    // 6 sub-bass center core
            2 to 6,    // 7 bass
            5 to 11,   // 8 low-mid
            11 to 20,  // 9 mid
            20 to 34,  // 10 high-mid
            34 to 58,  // 11 treble
            58 to 96,  // 12 outer-right air
        )

        val outBars = MutableList(13) { idx ->
            val (lo, hi) = binRanges[idx]
            val raw = bandEnergy(lo, hi, halfN)
            val s = smooth(smoothedBars[idx], raw)
            smoothedBars[idx] = s
            s
        }

        SpectrumBus.publish(
            SpectrumSnapshot(
                bars = outBars,
                bass = smoothedBass,
                mid = smoothedMid,
                treble = smoothedTreble,
            )
        )
    }

    private fun bandEnergy(startBin: Int, endBin: Int, halfN: Int): Float {
        val lo = startBin.coerceIn(1, halfN - 1)
        val hi = endBin.coerceIn(lo, halfN - 1)
        var sum = 0f
        var count = 0
        for (b in lo..hi) {
            val mag = sqrt(real[b] * real[b] + imag[b] * imag[b]) / halfN
            sum += mag
            count++
        }
        val avg = if (count > 0) sum / count else 0f
        // Perceptual logarithmic loudness scaling
        val logScaled = (ln(1f + avg * 52f) / ln(18f)).coerceIn(0f, 1f)
        return min(1f, logScaled * 1.15f)
    }

    private fun smooth(prev: Float, target: Float): Float {
        val alpha = if (target > prev) 0.42f else 0.18f
        return prev + (target - prev) * alpha
    }
}
