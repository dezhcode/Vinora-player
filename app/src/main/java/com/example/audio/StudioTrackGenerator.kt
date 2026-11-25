package com.example.audio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import com.example.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Generates authentic offline studio vinyl demo WAV tracks and editorial disc artworks
 * on first launch so the turntable, Media3 player, Equalizer, and live FFT Audio Visualizer
 * work immediately out-of-the-box even on devices with an empty MediaStore.
 */
object StudioTrackGenerator {

    private data class StudioBlueprint(
        val id: String,
        val title: String,
        val artist: String,
        val album: String,
        val durationSec: Int,
        val bpm: Double,
        val chordFreqs: List<DoubleArray>,
        val bassFreqs: DoubleArray,
        val arpFreqs: DoubleArray,
        val artStyle: Int,
    )

    private val blueprints = listOf(
        StudioBlueprint(
            id = "studio_forgotten",
            title = "Forgotten",
            artist = "Nguyen, Shane",
            album = "Quiet Studies",
            durationSec = 24,
            bpm = 84.0,
            chordFreqs = listOf(
                doubleArrayOf(220.0, 261.63, 329.63, 392.0),
                doubleArrayOf(174.61, 220.0, 261.63, 329.63),
                doubleArrayOf(196.0, 246.94, 293.66, 392.0),
                doubleArrayOf(164.81, 196.0, 246.94, 329.63),
            ),
            bassFreqs = doubleArrayOf(55.0, 43.65, 49.0, 41.2),
            arpFreqs = doubleArrayOf(440.0, 523.25, 659.25, 587.33),
            artStyle = 0,
        ),
        StudioBlueprint(
            id = "studio_dreams",
            title = "Dreams",
            artist = "Miles, Esther",
            album = "Monochrome",
            durationSec = 26,
            bpm = 92.0,
            chordFreqs = listOf(
                doubleArrayOf(261.63, 329.63, 392.0, 493.88),
                doubleArrayOf(220.0, 261.63, 329.63, 440.0),
                doubleArrayOf(174.61, 220.0, 261.63, 349.23),
                doubleArrayOf(196.0, 246.94, 293.66, 392.0),
            ),
            bassFreqs = doubleArrayOf(65.41, 55.0, 43.65, 49.0),
            arpFreqs = doubleArrayOf(523.25, 659.25, 783.99, 659.25),
            artStyle = 1,
        ),
        StudioBlueprint(
            id = "studio_horizon",
            title = "Horizon",
            artist = "Kristin",
            album = "Solar Pressings",
            durationSec = 28,
            bpm = 100.0,
            chordFreqs = listOf(
                doubleArrayOf(293.66, 349.23, 440.0, 523.25),
                doubleArrayOf(233.08, 293.66, 349.23, 440.0),
                doubleArrayOf(261.63, 329.63, 392.0, 493.88),
                doubleArrayOf(220.0, 261.63, 329.63, 392.0),
            ),
            bassFreqs = doubleArrayOf(73.42, 58.27, 65.41, 55.0),
            arpFreqs = doubleArrayOf(587.33, 698.46, 880.0, 783.99),
            artStyle = 2,
        ),
        StudioBlueprint(
            id = "studio_daylight",
            title = "Day light",
            artist = "Kernin Joki",
            album = "The Archive",
            durationSec = 30,
            bpm = 96.0,
            chordFreqs = listOf(
                doubleArrayOf(246.94, 293.66, 369.99, 440.0),
                doubleArrayOf(196.0, 246.94, 293.66, 369.99),
                doubleArrayOf(220.0, 277.18, 329.63, 440.0),
                doubleArrayOf(185.0, 220.0, 277.18, 369.99),
            ),
            bassFreqs = doubleArrayOf(61.74, 49.0, 55.0, 46.25),
            arpFreqs = doubleArrayOf(493.88, 587.33, 739.99, 659.25),
            artStyle = 3,
        ),
        StudioBlueprint(
            id = "studio_cassette",
            title = "Cassette",
            artist = "Black, Marvin",
            album = "Analog Tape",
            durationSec = 26,
            bpm = 88.0,
            chordFreqs = listOf(
                doubleArrayOf(196.0, 233.08, 293.66, 349.23),
                doubleArrayOf(155.56, 196.0, 233.08, 293.66),
                doubleArrayOf(174.61, 220.0, 261.63, 349.23),
                doubleArrayOf(146.83, 174.61, 220.0, 293.66),
            ),
            bassFreqs = doubleArrayOf(49.0, 38.89, 43.65, 36.71),
            arpFreqs = doubleArrayOf(392.0, 466.16, 587.33, 523.25),
            artStyle = 4,
        ),
        StudioBlueprint(
            id = "studio_crimson",
            title = "Crimson",
            artist = "Henry, Arthur",
            album = "Mindset Vol. I",
            durationSec = 28,
            bpm = 104.0,
            chordFreqs = listOf(
                doubleArrayOf(261.63, 311.13, 392.0, 466.16),
                doubleArrayOf(207.65, 261.63, 311.13, 392.0),
                doubleArrayOf(233.08, 293.66, 349.23, 466.16),
                doubleArrayOf(196.0, 246.94, 293.66, 392.0),
            ),
            bassFreqs = doubleArrayOf(65.41, 51.91, 58.27, 49.0),
            arpFreqs = doubleArrayOf(523.25, 622.25, 783.99, 698.46),
            artStyle = 5,
        ),
        StudioBlueprint(
            id = "studio_one_week_last",
            title = "One Week Last",
            artist = "Justin Bibber",
            album = "Silver Plinth LP",
            durationSec = 32,
            bpm = 98.0,
            chordFreqs = listOf(
                doubleArrayOf(220.0, 277.18, 329.63, 415.30),
                doubleArrayOf(185.0, 220.0, 277.18, 329.63),
                doubleArrayOf(146.83, 185.0, 220.0, 277.18),
                doubleArrayOf(164.81, 207.65, 246.94, 329.63),
            ),
            bassFreqs = doubleArrayOf(55.0, 46.25, 73.42, 41.20),
            arpFreqs = doubleArrayOf(440.0, 554.37, 659.25, 830.61),
            artStyle = 6,
        ),
        StudioBlueprint(
            id = "studio_phantom",
            title = "Phantom",
            artist = "Flores, Juanita",
            album = "Velvet Air",
            durationSec = 24,
            bpm = 82.0,
            chordFreqs = listOf(
                doubleArrayOf(207.65, 246.94, 311.13, 392.0),
                doubleArrayOf(164.81, 207.65, 246.94, 311.13),
                doubleArrayOf(185.0, 233.08, 277.18, 369.99),
                doubleArrayOf(155.56, 185.0, 233.08, 311.13),
            ),
            bassFreqs = doubleArrayOf(51.91, 41.20, 46.25, 38.89),
            arpFreqs = doubleArrayOf(415.30, 493.88, 622.25, 554.37),
            artStyle = 7,
        ),
    )

    suspend fun ensureStudioTracks(context: Context): List<Track> = withContext(Dispatchers.IO) {
        val audioDir = File(context.filesDir, "studio_sessions").apply { mkdirs() }
        val coverDir = File(context.filesDir, "studio_covers").apply { mkdirs() }

        blueprints.mapIndexed { index, bp ->
            val wavFile = File(audioDir, "${bp.id}.wav")
            if (!wavFile.exists() || wavFile.length() < 1024) {
                synthesizeStudioWav(wavFile, bp)
            }
            val coverFile = File(coverDir, "${bp.id}.png")
            if (!coverFile.exists() || coverFile.length() < 512) {
                renderEditorialCover(coverFile, bp)
            }
            Track(
                id = bp.id,
                title = bp.title,
                artist = bp.artist,
                album = bp.album,
                durationMs = bp.durationSec * 1000L,
                contentUri = Uri.fromFile(wavFile).toString(),
                artworkUri = Uri.fromFile(coverFile).toString(),
                isBuiltIn = true,
                dateAdded = 1_700_000_000_000L + index * 1000L,
                folderName = "Plinth Studio",
                folderPath = "Plinth Studio",
            )
        }
    }

    private fun synthesizeStudioWav(target: File, bp: StudioBlueprint) {
        val sampleRate = 22050
        val totalSamples = sampleRate * bp.durationSec
        val beatDuration = 60.0 / bp.bpm
        val barDuration = beatDuration * 4.0
        val dataSize = totalSamples * 2 * 2 // stereo 16-bit

        BufferedOutputStream(FileOutputStream(target), 32768).use { out ->
            // Write standard 44-byte PCM WAV header
            out.write("RIFF".toByteArray())
            writeIntLE(out, 36 + dataSize)
            out.write("WAVE".toByteArray())
            out.write("fmt ".toByteArray())
            writeIntLE(out, 16)
            writeShortLE(out, 1) // PCM
            writeShortLE(out, 2) // Stereo
            writeIntLE(out, sampleRate)
            writeIntLE(out, sampleRate * 4)
            writeShortLE(out, 4)
            writeShortLE(out, 16)
            out.write("data".toByteArray())
            writeIntLE(out, dataSize)

            val twoPi = 2.0 * PI
            val chunk = ByteArray(4096)
            var chunkPos = 0

            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                val barIdx = (t / barDuration).toInt()
                val chordIdx = barIdx % bp.chordFreqs.size
                val barT = t - barIdx * barDuration
                val beatIdx = (t / beatDuration).toInt()
                val beatT = t - beatIdx * beatDuration
                val eighthIdx = (t / (beatDuration * 0.5)).toInt()
                val eighthT = t - eighthIdx * (beatDuration * 0.5)

                // 1. Warm Rhodes electric piano chord with gentle bell overtone
                val chordEnv = (1.0 - exp(-barT * 18.0)) * exp(-barT * 0.55)
                var padL = 0.0
                var padR = 0.0
                val chord = bp.chordFreqs[chordIdx]
                for (n in chord.indices) {
                    val f = chord[n]
                    val detune = if (n % 2 == 0) 1.0018 else 0.9982
                    val osc = sin(twoPi * f * t) + 0.28 * sin(twoPi * f * 2.0 * t) * exp(-barT * 2.4)
                    val oscR = sin(twoPi * f * detune * t) + 0.25 * sin(twoPi * f * 2.0 * detune * t) * exp(-barT * 2.4)
                    padL += osc
                    padR += oscR
                }
                padL *= chordEnv * 0.11
                padR *= chordEnv * 0.11

                // 2. Deep analog sub-bass pulse (drives Bass FFT bins strongly!)
                val bassF = bp.bassFreqs[chordIdx]
                val bassBeat = beatIdx % 4
                val bassTrig = if (bassBeat == 0 || bassBeat == 2) beatT else beatT + 0.5
                val bassEnv = (1.0 - exp(-bassTrig * 45.0)) * exp(-bassTrig * 2.2)
                val bass = (sin(twoPi * bassF * t) + 0.35 * sin(twoPi * bassF * 2.0 * t)) * bassEnv * 0.34

                // 3. Soft punchy studio kick on beats 0 and 2
                val kickEnv = if (bassBeat == 0 || bassBeat == 2) exp(-beatT * 18.0) else 0.0
                val kickPitch = 48.0 + 95.0 * exp(-beatT * 38.0)
                val kick = sin(twoPi * kickPitch * beatT) * kickEnv * 0.32

                // 4. Shimmering arpeggio plucked notes (drives Mid & Treble FFT bins!)
                val arpF = bp.arpFreqs[eighthIdx % bp.arpFreqs.size]
                val arpEnv = (1.0 - exp(-eighthT * 80.0)) * exp(-eighthT * 6.5)
                val arpWave = (sin(twoPi * arpF * t) + 0.42 * sin(twoPi * arpF * 2.0 * t) + 0.18 * sin(twoPi * arpF * 3.0 * t)) * arpEnv * 0.14
                val pan = 0.5 + 0.38 * sin(eighthIdx * 1.3)

                // 5. Crisp brushed hi-hat shimmer on off-beats (drives High Treble FFT!)
                val hatEnv = exp(-eighthT * 34.0) * if (eighthIdx % 2 == 1) 0.11 else 0.045
                val pseudoNoise = sin(i * 12.9898 + 78.233) * 43758.5453
                val noise = (pseudoNoise - pseudoNoise.toInt()) * 2.0 - 1.0
                val hat = noise * hatEnv

                // Fade in/out envelope for seamless loop & crisp starts
                val fadeIn = (t / 0.15).coerceAtMost(1.0)
                val fadeOut = ((bp.durationSec - t) / 0.6).coerceIn(0.0, 1.0)
                val master = fadeIn * fadeOut

                val sampleL = tanh((padL + bass + kick + arpWave * (1.0 - pan) + hat * 0.85) * 1.15) * master
                val sampleR = tanh((padR + bass + kick + arpWave * pan + hat) * 1.15) * master

                val sL = (sampleL * 30000.0).toInt().coerceIn(-32767, 32767)
                val sR = (sampleR * 30000.0).toInt().coerceIn(-32767, 32767)

                chunk[chunkPos++] = (sL and 0xFF).toByte()
                chunk[chunkPos++] = ((sL shr 8) and 0xFF).toByte()
                chunk[chunkPos++] = (sR and 0xFF).toByte()
                chunk[chunkPos++] = ((sR shr 8) and 0xFF).toByte()

                if (chunkPos == chunk.size) {
                    out.write(chunk, 0, chunkPos)
                    chunkPos = 0
                }
            }
            if (chunkPos > 0) {
                out.write(chunk, 0, chunkPos)
            }
        }
    }

    private fun renderEditorialCover(file: File, bp: StudioBlueprint) {
        val size = 512
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = size / 2f
        val cy = size / 2f

        when (bp.artStyle) {
            0 -> {
                // Forgotten: Pale silver mist & sculptural crescent silhouette
                paint.shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), Color.parseColor("#ECEFF4"), Color.parseColor("#CFD6E2"), Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
                paint.shader = null
                paint.color = Color.parseColor("#B4BDCC")
                canvas.drawCircle(cx + 40f, cy - 30f, 165f, paint)
                paint.color = Color.parseColor("#F5F7FA")
                canvas.drawCircle(cx - 35f, cy + 20f, 145f, paint)
            }
            1 -> {
                // Dreams: Bold monochrome geometric typography stripes (matching reference #2!)
                canvas.drawColor(Color.parseColor("#F3F4F6"))
                paint.color = Color.parseColor("#181A1F")
                canvas.save()
                canvas.rotate(-34f, cx, cy)
                for (k in -4..4) {
                    val top = cy + k * 76f - 22f
                    canvas.drawRect(-120f, top, size + 120f, top + 36f, paint)
                }
                canvas.restore()
                paint.color = Color.parseColor("#F4F5F8")
                canvas.drawCircle(cx, cy, 86f, paint)
                paint.color = Color.parseColor("#181A1F")
                paint.textAlign = Paint.Align.CENTER
                paint.textSize = 26f
                paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                canvas.drawText("DREAMS", cx, cy + 9f, paint)
            }
            2 -> {
                // Horizon: High-contrast monochrome portrait circle + electric yellow editorial typography (matching reference #3!)
                paint.shader = RadialGradient(cx - 50f, cy - 50f, 320f, Color.parseColor("#E6E8EC"), Color.parseColor("#181A1E"), Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
                paint.shader = null
                paint.color = Color.parseColor("#121418")
                canvas.drawCircle(cx + 75f, cy + 30f, 175f, paint)
                paint.color = Color.parseColor("#F5E628")
                paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                paint.textAlign = Paint.Align.CENTER
                paint.textSize = 54f
                canvas.save()
                canvas.rotate(-90f, cx, cy)
                canvas.drawText("CREATE", cx, cy - 12f, paint)
                paint.textSize = 44f
                canvas.drawText("HORIZON", cx, cy + 44f, paint)
                canvas.restore()
            }
            3 -> {
                // Day light: Deep noir "THE ARCHIVE" editorial sleeve (matching reference center disc!)
                paint.shader = RadialGradient(cx - 80f, cy - 90f, 360f, Color.parseColor("#2A2D34"), Color.parseColor("#0A0B0E"), Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
                paint.shader = null
                // Dramatic studio key light slash
                paint.color = Color.argb(46, 255, 255, 255)
                canvas.save()
                canvas.rotate(-28f, cx, cy)
                canvas.drawRect(cx - 210f, -80f, cx - 125f, size + 80f, paint)
                canvas.restore()
                paint.color = Color.WHITE
                paint.textAlign = Paint.Align.CENTER
                paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                paint.textSize = 62f
                canvas.save()
                canvas.rotate(-90f, cx, cy)
                canvas.drawText("ARCHIVE", cx, cy + 20f, paint)
                paint.textSize = 22f
                paint.color = Color.parseColor("#B8C0CE")
                canvas.drawText("THE", cx - 104f, cy - 38f, paint)
                canvas.restore()
            }
            4 -> {
                // Cassette: Minimalist high-key white + soft dark chiaroscuro silhouette (matching reference #5!)
                canvas.drawColor(Color.parseColor("#F6F7FA"))
                paint.shader = RadialGradient(cx + 35f, cy + 20f, 220f, Color.parseColor("#1D1A21"), Color.parseColor("#3B3342"), Shader.TileMode.CLAMP)
                canvas.drawOval(RectF(75f, 120f, 430f, 425f), paint)
                paint.shader = null
                paint.color = Color.argb(65, 255, 255, 255)
                canvas.drawCircle(cx - 50f, cy - 30f, 70f, paint)
            }
            5 -> {
                // Crimson: Architectural silver geometry + diagonal dark facet (matching reference #6!)
                canvas.drawColor(Color.parseColor("#ECEDF0"))
                paint.color = Color.parseColor("#8C929E")
                canvas.drawArc(RectF(64f, 64f, size - 64f, size - 64f), -45f, 180f, true, paint)
                paint.color = Color.parseColor("#2B2F38")
                canvas.drawCircle(cx + 45f, cy - 45f, 72f, paint)
                paint.color = Color.parseColor("#F5F6F9")
                canvas.drawCircle(cx, cy, 48f, paint)
            }
            6 -> {
                // One Week Last: Minimalist archival cream label with fine hairline rings (matching reference right turntable center label!)
                paint.shader = RadialGradient(cx, cy, 280f, Color.parseColor("#FAF9F5"), Color.parseColor("#E3E4E8"), Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
                paint.shader = null
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f
                paint.color = Color.parseColor("#C8CED9")
                canvas.drawCircle(cx, cy, 185f, paint)
                canvas.drawCircle(cx, cy, 145f, paint)
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#474E5C")
                paint.textAlign = Paint.Align.CENTER
                paint.textSize = 24f
                paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                canvas.drawText("SIDE A • 33⅓", cx, cy - 92f, paint)
                paint.textSize = 20f
                paint.color = Color.parseColor("#7D8596")
                canvas.drawText("STEREO HI-FI", cx, cy + 114f, paint)
            }
            else -> {
                // Phantom: Soft frosted mist & silver wave arcs
                paint.shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), Color.parseColor("#E8ECF4"), Color.parseColor("#C7D0DF"), Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
                paint.shader = null
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 18f
                for (r in 1..4) {
                    paint.color = Color.argb(40 + r * 22, 34, 42, 58)
                    canvas.drawCircle(cx + 90f, cy + 90f, r * 68f, paint)
                }
            }
        }

        FileOutputStream(file).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 92, out)
        }
        bmp.recycle()
    }

    private fun writeIntLE(out: BufferedOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
        out.write((value shr 16) and 0xFF)
        out.write((value shr 24) and 0xFF)
    }

    private fun writeShortLE(out: BufferedOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
    }
}
