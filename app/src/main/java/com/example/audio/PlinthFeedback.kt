package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

enum class FeedbackEvent {
    TAP_SUBTLE,
    PLAY_PAUSE,
    SKIP_METALLIC,
    TOGGLE_STATE,
    SETTINGS_TICK,
    CONFIRM,
}

/**
 * Unified zero-latency Native Haptics + Mechanical UI Sound Effects Engine.
 * - Uses SoundPool (USAGE_ASSISTANCE_SONIFICATION) so UI clicks never interrupt or steal focus from Media3 music playback.
 * - Pre-synthesizes crisp, minimalist analog hardware relay/switch PCM clicks into app cache on IO thread.
 * - Uses modern VibrationEffect APIs with graceful fallback and strict rate-limiting for rapid slider drags.
 */
object PlinthFeedback {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val feedbackExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "PlinthFeedbackWorker").apply { isDaemon = true }
    }

    @Volatile
    var hapticsEnabled: Boolean = true
        private set

    @Volatile
    var uiSoundsEnabled: Boolean = true
        private set

    private var initialized = false
    private var vibrator: Vibrator? = null
    private var hasVibrator = false

    private var soundPool: SoundPool? = null
    private val soundIds = HashMap<FeedbackEvent, Int>()
    private var sliderTickSoundId: Int = 0
    private var sliderTickLoaded = false

    @Volatile
    private var lastSliderHapticMs = 0L

    @Volatile
    private var lastSliderSoundMs = 0L

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            initialized = true
            val appCtx = context.applicationContext

            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                appCtx.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appCtx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            hasVibrator = runCatching { vibrator?.hasVibrator() == true }.getOrDefault(false)

            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val pool = SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(attrs)
                .build()
            soundPool = pool

            scope.launch {
                preloadHardwareClicks(appCtx, pool)
            }
        }
    }

    fun updateSettings(haptics: Boolean, uiSounds: Boolean) {
        hapticsEnabled = haptics
        uiSoundsEnabled = uiSounds
    }

    fun trigger(event: FeedbackEvent) {
        val doVib = hapticsEnabled && hasVibrator && vibrator != null
        val doSound = uiSoundsEnabled && soundPool != null && soundIds.containsKey(event)
        if (!doVib && !doSound) return
        feedbackExecutor.execute {
            if (doVib) vibrateForEvent(event)
            if (doSound) playSoundForEvent(event)
        }
    }

    fun triggerSliderDetent(isMajorNotch: Boolean) {
        val now = System.currentTimeMillis()
        val doVib = hapticsEnabled && hasVibrator && vibrator != null && (now - lastSliderHapticMs >= 28L)
        if (doVib) lastSliderHapticMs = now

        val pool = soundPool
        val doSound = uiSoundsEnabled && pool != null && sliderTickLoaded && sliderTickSoundId != 0 && (now - lastSliderSoundMs >= 38L)
        if (doSound) lastSliderSoundMs = now

        if (!doVib && !doSound) return
        feedbackExecutor.execute {
            if (doVib) vibrateSliderDetent(isMajorNotch)
            if (doSound && pool != null) {
                val vol = if (isMajorNotch) 0.34f else 0.19f
                val rate = if (isMajorNotch) 1.0f else 1.18f
                runCatching { pool.play(sliderTickSoundId, vol, vol, 1, 0, rate) }
            }
        }
    }

    private fun vibrateForEvent(event: FeedbackEvent) {
        val vib = vibrator ?: return
        if (!hasVibrator) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val effectId = when (event) {
                    FeedbackEvent.PLAY_PAUSE -> VibrationEffect.EFFECT_HEAVY_CLICK
                    FeedbackEvent.SKIP_METALLIC,
                    FeedbackEvent.TOGGLE_STATE,
                    FeedbackEvent.CONFIRM -> VibrationEffect.EFFECT_CLICK
                    FeedbackEvent.TAP_SUBTLE,
                    FeedbackEvent.SETTINGS_TICK -> VibrationEffect.EFFECT_TICK
                }
                vib.vibrate(VibrationEffect.createPredefined(effectId))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val (ms, amp) = when (event) {
                    FeedbackEvent.PLAY_PAUSE -> 24L to 170
                    FeedbackEvent.SKIP_METALLIC -> 18L to 135
                    FeedbackEvent.TOGGLE_STATE,
                    FeedbackEvent.CONFIRM -> 16L to 120
                    FeedbackEvent.TAP_SUBTLE,
                    FeedbackEvent.SETTINGS_TICK -> 10L to 80
                }
                vib.vibrate(VibrationEffect.createOneShot(ms, amp))
            } else {
                val ms = when (event) {
                    FeedbackEvent.PLAY_PAUSE -> 22L
                    FeedbackEvent.SKIP_METALLIC, FeedbackEvent.TOGGLE_STATE, FeedbackEvent.CONFIRM -> 15L
                    else -> 9L
                }
                @Suppress("DEPRECATION")
                vib.vibrate(ms)
            }
        }
    }

    private fun vibrateSliderDetent(isMajorNotch: Boolean) {
        val vib = vibrator ?: return
        if (!hasVibrator) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vib.vibrate(
                    VibrationEffect.createPredefined(
                        if (isMajorNotch) VibrationEffect.EFFECT_CLICK else VibrationEffect.EFFECT_TICK
                    )
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ms = if (isMajorNotch) 14L else 8L
                val amp = if (isMajorNotch) 115 else 65
                vib.vibrate(VibrationEffect.createOneShot(ms, amp))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(if (isMajorNotch) 12L else 7L)
            }
        }
    }

    private fun playSoundForEvent(event: FeedbackEvent) {
        val pool = soundPool ?: return
        val sid = soundIds[event] ?: return
        val vol = when (event) {
            FeedbackEvent.PLAY_PAUSE -> 0.42f
            FeedbackEvent.SKIP_METALLIC -> 0.36f
            FeedbackEvent.TOGGLE_STATE -> 0.34f
            FeedbackEvent.CONFIRM -> 0.36f
            FeedbackEvent.TAP_SUBTLE -> 0.24f
            FeedbackEvent.SETTINGS_TICK -> 0.26f
        }
        runCatching { pool.play(sid, vol, vol, 1, 0, 1.0f) }
    }

    private fun preloadHardwareClicks(context: Context, pool: SoundPool) {
        val dir = File(context.cacheDir, "plinth_sfx").apply { mkdirs() }
        val sampleRate = 22050

        fun loadWav(name: String, samples: ShortArray): Int {
            val file = File(dir, "$name.wav")
            writeMonoWav(file, sampleRate, samples)
            return pool.load(file.absolutePath, 1)
        }

        // Tactile analog switch click for Play / Pause (damped low-mid thud + crisp transient)
        soundIds[FeedbackEvent.PLAY_PAUSE] = loadWav(
            "play_pause",
            synthesizeClick(sampleRate, durationMs = 26, f1 = 220.0, f2 = 1380.0, decay1 = 150.0, decay2 = 340.0, noiseAmt = 0.22),
        )
        // Metallic precision relay click for Next / Previous
        soundIds[FeedbackEvent.SKIP_METALLIC] = loadWav(
            "skip_metallic",
            synthesizeClick(sampleRate, durationMs = 22, f1 = 640.0, f2 = 2450.0, decay1 = 210.0, decay2 = 420.0, noiseAmt = 0.16),
        )
        // Anodized rocker toggle switch
        soundIds[FeedbackEvent.TOGGLE_STATE] = loadWav(
            "toggle",
            synthesizeClick(sampleRate, durationMs = 19, f1 = 380.0, f2 = 1650.0, decay1 = 230.0, decay2 = 440.0, noiseAmt = 0.14),
        )
        // Subtle minimalist chassis tap
        soundIds[FeedbackEvent.TAP_SUBTLE] = loadWav(
            "tap_subtle",
            synthesizeClick(sampleRate, durationMs = 13, f1 = 460.0, f2 = 1850.0, decay1 = 360.0, decay2 = 580.0, noiseAmt = 0.10),
        )
        // Crisp short settings detent
        soundIds[FeedbackEvent.SETTINGS_TICK] = loadWav(
            "settings_tick",
            synthesizeClick(sampleRate, durationMs = 15, f1 = 520.0, f2 = 2100.0, decay1 = 310.0, decay2 = 520.0, noiseAmt = 0.12),
        )
        // Dual harmonic confirmation click
        soundIds[FeedbackEvent.CONFIRM] = loadWav(
            "confirm",
            synthesizeClick(sampleRate, durationMs = 28, f1 = 440.0, f2 = 880.0, decay1 = 140.0, decay2 = 220.0, noiseAmt = 0.08),
        )

        sliderTickSoundId = loadWav(
            "slider_notch",
            synthesizeClick(sampleRate, durationMs = 11, f1 = 680.0, f2 = 2600.0, decay1 = 420.0, decay2 = 640.0, noiseAmt = 0.10),
        )
        sliderTickLoaded = sliderTickSoundId != 0
    }

    private fun synthesizeClick(
        sampleRate: Int,
        durationMs: Int,
        f1: Double,
        f2: Double,
        decay1: Double,
        decay2: Double,
        noiseAmt: Double,
    ): ShortArray {
        val totalSamples = (sampleRate * durationMs) / 1000
        val out = ShortArray(totalSamples)
        var rng = 918273L
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate.toDouble()
            val attack = if (i < 16) i / 16.0 else 1.0
            val tone1 = sin(2.0 * PI * f1 * t) * exp(-decay1 * t) * 0.65
            val tone2 = sin(2.0 * PI * f2 * t) * exp(-decay2 * t) * 0.35
            rng = (rng * 1664525L + 1013904223L) and 0xFFFFFFFFL
            val white = ((rng.toDouble() / 0xFFFFFFFFL.toDouble()) * 2.0 - 1.0)
            val noise = white * exp(-decay2 * 1.4 * t) * noiseAmt
            val sample = ((tone1 + tone2 + noise) * attack).coerceIn(-0.98, 0.98)
            out[i] = (sample * 24000.0).toInt().toShort()
        }
        return out
    }

    private fun writeMonoWav(file: File, sampleRate: Int, samples: ShortArray) {
        val dataBytes = samples.size * 2
        val totalLen = 36 + dataBytes
        val byteRate = sampleRate * 2
        FileOutputStream(file).use { fos ->
            val hdr = ByteArray(44)
            hdr[0] = 'R'.code.toByte(); hdr[1] = 'I'.code.toByte(); hdr[2] = 'F'.code.toByte(); hdr[3] = 'F'.code.toByte()
            writeIntLE(hdr, 4, totalLen)
            hdr[8] = 'W'.code.toByte(); hdr[9] = 'A'.code.toByte(); hdr[10] = 'V'.code.toByte(); hdr[11] = 'E'.code.toByte()
            hdr[12] = 'f'.code.toByte(); hdr[13] = 'm'.code.toByte(); hdr[14] = 't'.code.toByte(); hdr[15] = ' '.code.toByte()
            writeIntLE(hdr, 16, 16)
            hdr[20] = 1; hdr[21] = 0
            hdr[22] = 1; hdr[23] = 0
            writeIntLE(hdr, 24, sampleRate)
            writeIntLE(hdr, 28, byteRate)
            hdr[32] = 2; hdr[33] = 0
            hdr[34] = 16; hdr[35] = 0
            hdr[36] = 'd'.code.toByte(); hdr[37] = 'a'.code.toByte(); hdr[38] = 't'.code.toByte(); hdr[39] = 'a'.code.toByte()
            writeIntLE(hdr, 40, dataBytes)
            fos.write(hdr)

            val pcm = ByteArray(dataBytes)
            for (i in samples.indices) {
                val s = samples[i].toInt()
                pcm[i * 2] = (s and 0xFF).toByte()
                pcm[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
            }
            fos.write(pcm)
        }
    }

    private fun writeIntLE(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buf[offset + 2] = ((value shr 16) and 0xFF).toByte()
        buf[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }
}
