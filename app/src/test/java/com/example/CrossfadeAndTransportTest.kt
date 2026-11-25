package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.source.ShuffleOrder
import com.example.audio.AudioEqualizerController
import com.example.audio.AudioSpectrumProcessor
import com.example.audio.CrossfadeMath
import com.example.audio.SpectrumBus
import com.example.audio.SpectrumSnapshot
import com.example.data.GraphicsQuality
import com.example.data.RepeatMode
import com.example.data.Track
import com.example.data.UserPreferences
import com.example.ui.i18n.AppLanguage
import com.example.ui.screens.NowPlayingScreen
import com.example.ui.screens.SettingsBottomSheet
import com.example.ui.theme.PlinthTheme
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CrossfadeAndTransportTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun equalPowerCurve_maintainsConstantPowerAndPreventsClippingOnTwoLoudTracks() {
        // 1. Dynamic-range music (0.65 peak): pure Equal Power (out^2 + in^2 == userVol^2)
        val userVol = 0.8f
        for (step in 0..20) {
            val t = step / 20f
            val g = CrossfadeMath.computeEqualPowerGains(
                progress = t,
                userVolume = userVol,
                outgoingPeak = 0.65f,
                incomingPeak = 0.65f,
            )
            val powerSum = g.outgoingVolume * g.outgoingVolume + g.incomingVolume * g.incomingVolume
            assertEquals("RMS power should stay constant at t=$t", userVol * userVol, powerSum, 0.002f)
        }

        // 2. Two brickwall-loud 0 dBFS tracks (peak = 1.0f) mixed simultaneously
        for (step in 0..100) {
            val t = step / 100f
            val g = CrossfadeMath.computeEqualPowerGains(
                progress = t,
                userVolume = 1.0f,
                outgoingPeak = 1.0f,
                incomingPeak = 1.0f,
            )
            // Worst-case in-phase loud sine sum must never clip (> 1.0f)
            val sampleA = sin(step * 0.7) .toFloat().let { if (it >= 0f) 1.0f else -1.0f }
            val sampleB = sampleA // worst-case aligned 0 dBFS peaks
            val mixed = abs(g.outgoingVolume * sampleA + g.incomingVolume * sampleB)
            assertTrue("Combined peak $mixed exceeded 1.0f at t=$t", mixed <= 1.0001f)
        }

        // 3. Endpoints match user-selected volume cleanly
        val start = CrossfadeMath.computeEqualPowerGains(0f, userVolume = 0.6f, outgoingPeak = 1f, incomingPeak = 1f)
        val end = CrossfadeMath.computeEqualPowerGains(1f, userVolume = 0.6f, outgoingPeak = 1f, incomingPeak = 1f)
        assertEquals(0.6f, start.outgoingVolume, 0.0001f)
        assertEquals(0f, start.incomingVolume, 0.0001f)
        assertEquals(0f, end.outgoingVolume, 0.0001f)
        assertEquals(0.6f, end.incomingVolume, 0.0001f)
    }

    @Test
    fun effectiveDuration_scalesProportionallyForShortTracks() {
        assertEquals(7_000L, CrossfadeMath.effectiveCrossfadeDurationMs(180_000L, 200_000L))
        assertEquals(4_500L, CrossfadeMath.effectiveCrossfadeDurationMs(10_000L, 180_000L))
        assertEquals(1_800L, CrossfadeMath.effectiveCrossfadeDurationMs(4_000L, 6_000L))
        assertEquals(0L, CrossfadeMath.effectiveCrossfadeDurationMs(900L, 180_000L))
        assertEquals(0L, CrossfadeMath.effectiveCrossfadeDurationMs(180_000L, 900L))
    }

    @Test
    fun media3TimelineResolution_respectsRepeatAndShuffleOrder() {
        val shuffleOrder = ShuffleOrder.DefaultShuffleOrder(intArrayOf(2, 0, 3, 1), 0L)
        val timeline = object : Timeline() {
            override fun getWindowCount(): Int = 4
            override fun getPeriodCount(): Int = 4
            override fun getIndexOfPeriod(uid: Any): Int = 0
            override fun getUidOfPeriod(periodIndex: Int): Any = periodIndex

            override fun getFirstWindowIndex(shuffleModeEnabled: Boolean): Int =
                if (shuffleModeEnabled) shuffleOrder.firstIndex else 0

            override fun getLastWindowIndex(shuffleModeEnabled: Boolean): Int =
                if (shuffleModeEnabled) shuffleOrder.lastIndex else 3

            override fun getNextWindowIndex(windowIndex: Int, repeatMode: Int, shuffleModeEnabled: Boolean): Int {
                if (repeatMode == Player.REPEAT_MODE_ONE) return windowIndex
                if (!shuffleModeEnabled) {
                    return if (windowIndex + 1 < 4) windowIndex + 1
                    else if (repeatMode == Player.REPEAT_MODE_ALL) 0
                    else C.INDEX_UNSET
                }
                val next = shuffleOrder.getNextIndex(windowIndex)
                return if (next != C.INDEX_UNSET) next
                else if (repeatMode == Player.REPEAT_MODE_ALL) shuffleOrder.firstIndex
                else C.INDEX_UNSET
            }

            override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window {
                return window.set(
                    windowIndex,
                    MediaItem.Builder().setMediaId("t$windowIndex").build(),
                    null, 0L, 0L, 0L, true, false, null, 0L, 180_000_000L, windowIndex, windowIndex, 0L
                )
            }

            override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period {
                return period.set(periodIndex, periodIndex, periodIndex, 180_000_000L, 0L)
            }
        }

        // Repeat Off at last sequential item -> INDEX_UNSET (no crossfade)
        assertEquals(C.INDEX_UNSET, CrossfadeMath.resolveNextWindowIndex(timeline, 3, Player.REPEAT_MODE_OFF, false))
        // Repeat All wraps around to 0
        assertEquals(0, CrossfadeMath.resolveNextWindowIndex(timeline, 3, Player.REPEAT_MODE_ALL, false))
        // Repeat One keeps current index
        assertEquals(2, CrossfadeMath.resolveNextWindowIndex(timeline, 2, Player.REPEAT_MODE_ONE, false))
        // Shuffle mode follows Media3 ShuffleOrder [2 -> 0 -> 3 -> 1]
        assertEquals(0, CrossfadeMath.resolveNextWindowIndex(timeline, 2, Player.REPEAT_MODE_OFF, true))
        assertEquals(3, CrossfadeMath.resolveNextWindowIndex(timeline, 0, Player.REPEAT_MODE_OFF, true))
        assertEquals(C.INDEX_UNSET, CrossfadeMath.resolveNextWindowIndex(timeline, 1, Player.REPEAT_MODE_OFF, true))
        assertEquals(2, CrossfadeMath.resolveNextWindowIndex(timeline, 1, Player.REPEAT_MODE_ALL, true))

        val extracted = CrossfadeMath.extractShuffleOrder(timeline)
        assertNotNull(extracted)
        assertEquals(listOf(2, 0, 3, 1), extracted!!.toList())
    }

    @Test
    fun spectrumProcessor_tracksLoudPeakOnStandbyWithoutPublishingToBus() {
        SpectrumBus.decayToIdle()
        val standbyProc = AudioSpectrumProcessor(publishToSpectrumBus = false)
        val format = androidx.media3.common.audio.AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        standbyProc.configure(format)
        standbyProc.flush()

        val pcmBuffer = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN)
        repeat(256) {
            pcmBuffer.putShort(32760)
        }
        pcmBuffer.flip()

        standbyProc.queueInput(pcmBuffer)
        assertTrue("Standby peak should capture full-scale signal", standbyProc.peakAmplitude > 0.98f)
        assertEquals(0f, SpectrumBus.spectrum.value.bass, 0.0001f)
    }

    @Test
    fun equalizerController_maintainsStateAndHandlesDetachGracefully() {
        AudioEqualizerController.applySettings(
            enabled = true,
            presetIndex = 2,
            bandLevelsDb = listOf(7, 4, 0, 1, 2),
        )
        assertEquals(true, AudioEqualizerController.state.value.enabled)
        assertEquals(listOf(7, 4, 0, 1, 2), AudioEqualizerController.state.value.bandLevelsDb)
        AudioEqualizerController.detachSession(999)
        AudioEqualizerController.release()
    }

    @Test
    fun settingsBottomSheet_crossfadeToggleWorksInEnglishAndPersian() {
        RuntimeEnvironment.setQualifiers("w360dp-h760dp-mdpi")
        var crossfadePref by mutableStateOf(false)

        composeTestRule.setContent {
            var prefs by remember { mutableStateOf(UserPreferences(crossfadeEnabled = false)) }
            PlinthTheme(themeMode = ThemeMode.LIGHT, language = AppLanguage.FA) {
                SettingsBottomSheet(
                    preferences = prefs,
                    sleepTimerRemainingMs = 0L,
                    onSetThemeMode = {},
                    onSetLanguage = {},
                    onSetSleepTimerMinutes = {},
                    onSetHaptics = {},
                    onSetUiSounds = {},
                    onSetGraphicsQuality = {},
                    onSetBatterySaver = {},
                    onSetReducedMotion = {},
                    onSetCrossfade = { enabled ->
                        crossfadePref = enabled
                        prefs = prefs.copy(crossfadeEnabled = enabled)
                    },
                    onImportAudio = {},
                    onScanDevice = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("کراس‌فید (Crossfade)").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("toggle_crossfade").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertTrue("Crossfade toggle should turn on", crossfadePref)
    }

    @Test
    fun nowPlaying_previousAndNextButtonsKeepOriginalCapsuleAndCentered18dpIcons() {
        RuntimeEnvironment.setQualifiers("w360dp-h760dp-mdpi")
        val spectrumFlow = MutableStateFlow(SpectrumSnapshot())
        var prevClicks = 0
        var nextClicks = 0

        composeTestRule.setContent {
            PlinthTheme(themeMode = ThemeMode.LIGHT, language = AppLanguage.EN) {
                NowPlayingScreen(
                    track = Track(
                        id = "t1",
                        title = "One Week Last",
                        artist = "Studio Deck",
                        album = "Plinth",
                        durationMs = 165_000L,
                        contentUri = "file:///t1.wav",
                    ),
                    isPlaying = false,
                    positionMs = 32_000L,
                    durationMs = 165_000L,
                    volume = 0.8f,
                    shuffleEnabled = false,
                    repeatMode = RepeatMode.ALL,
                    isFavorite = false,
                    sleepTimerRemainingMs = 0L,
                    spectrumFlow = spectrumFlow,
                    trackChangeNonce = 0,
                    trackChangeDirection = 1,
                    hapticsEnabled = false,
                    graphicsQuality = GraphicsQuality.MEDIUM,
                    batterySaverMode = false,
                    reducedMotion = false,
                    onBack = {},
                    onTogglePlayPause = {},
                    onPrevious = { prevClicks++ },
                    onNext = { nextClicks++ },
                    onSeekTo = {},
                    onScrubDeltaMs = {},
                    onToggleShuffle = {},
                    onCycleRepeat = {},
                    onToggleFavorite = {},
                    onVolumeChange = {},
                    onOpenQueue = {},
                    onOpenEqualizer = {},
                    onOpenAddToPlaylist = {},
                    onOpenSettings = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        val prevBounds = composeTestRule.onNodeWithTag("btn_previous").getUnclippedBoundsInRoot()
        val nextBounds = composeTestRule.onNodeWithTag("btn_next").getUnclippedBoundsInRoot()
        assertEquals(90.dp.value, (prevBounds.right - prevBounds.left).value, 1.5f)
        assertEquals(52.dp.value, (prevBounds.bottom - prevBounds.top).value, 1.5f)
        assertEquals(90.dp.value, (nextBounds.right - nextBounds.left).value, 1.5f)
        assertEquals(52.dp.value, (nextBounds.bottom - nextBounds.top).value, 1.5f)

        // Ensure inner skip icons stayed crisp 18dp instead of stretching to capsule width
        val prevIconBounds = composeTestRule.onNodeWithTag("icon_previous", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val nextIconBounds = composeTestRule.onNodeWithTag("icon_next", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(18.dp.value, (prevIconBounds.right - prevIconBounds.left).value, 1.5f)
        assertEquals(18.dp.value, (prevIconBounds.bottom - prevIconBounds.top).value, 1.5f)
        assertEquals(18.dp.value, (nextIconBounds.right - nextIconBounds.left).value, 1.5f)
        assertEquals(18.dp.value, (nextIconBounds.bottom - nextIconBounds.top).value, 1.5f)

        composeTestRule.onNodeWithTag("btn_previous").performClick()
        composeTestRule.onNodeWithTag("btn_next").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, prevClicks)
        assertEquals(1, nextClicks)
    }

    @Test
    fun crossfadeEngineAndSessionPlayer_syncsVolumeModesAndCollapsesOnTransportActions() {
        val context = RuntimeEnvironment.getApplication()
        val scope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate
        )
        val audioAttrs = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val engine = com.example.audio.CrossfadeAudioEngine(context, scope, audioAttrs)
        val sessionPlayer = com.example.audio.CrossfadeSessionPlayer(engine)

        try {
            engine.setCrossfadeEnabled(true)
            assertTrue(engine.crossfadeEnabled)

            sessionPlayer.volume = 0.65f
            assertEquals(0.65f, sessionPlayer.volume, 0.0001f)
            assertEquals(0.65f, engine.userVolume, 0.0001f)

            val items = mutableListOf(
                MediaItem.Builder().setMediaId("s1").setUri("file:///tmp/s1.wav").build(),
                MediaItem.Builder().setMediaId("s2").setUri("file:///tmp/s2.wav").build(),
            )
            sessionPlayer.setMediaItems(items, 0, 0L)
            assertEquals(2, sessionPlayer.mediaItemCount)

            sessionPlayer.shuffleModeEnabled = true
            sessionPlayer.repeatMode = Player.REPEAT_MODE_ALL
            assertTrue(sessionPlayer.shuffleModeEnabled)
            assertEquals(Player.REPEAT_MODE_ALL, sessionPlayer.repeatMode)

            // Transport interruptions restore active volume & clear crossfade state cleanly
            sessionPlayer.pause()
            sessionPlayer.seekTo(1500L)
            sessionPlayer.seekToNextMediaItem()
            sessionPlayer.seekToPreviousMediaItem()
            assertEquals(false, engine.isCrossfading)
            assertEquals(0.65f, engine.activeDeck.volume, 0.0001f)
        } finally {
            sessionPlayer.release()
            scope.cancel()
        }
    }
}
