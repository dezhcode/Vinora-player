package com.example

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.Density
import com.example.audio.AudioEqualizerController
import com.example.audio.EqualizerState
import com.example.audio.SpectrumSnapshot
import com.example.data.AlbumGroup
import com.example.data.ArtistGroup
import com.example.data.CustomEqPreset
import com.example.data.FolderGroup
import com.example.data.Track
import com.example.ui.i18n.AppLanguage
import com.example.ui.screens.EqualizerBottomSheet
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.QueueBottomSheet
import com.example.ui.theme.PlinthTheme
import com.example.ui.theme.ThemeMode
import com.example.viewmodel.QueueOperations
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Priority2UiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun sampleTrack(id: String, title: String, folderName: String, folderPath: String) = Track(
        id = id,
        title = title,
        artist = "Studio Artist",
        album = "Reference Sessions",
        durationMs = 210_000L,
        contentUri = "file:///$folderPath/$id.mp3",
        folderName = folderName,
        folderPath = folderPath,
    )

    @Test
    fun queueSheet_reorderViaMenuAndDrag_doesNotRestartPlayback() {
        RuntimeEnvironment.setQualifiers("w360dp-h760dp-mdpi")
        val initialTracks = listOf(
            sampleTrack("t1", "Daylight", "Plinth Studio", "Plinth Studio"),
            sampleTrack("t2", "Nocturne", "Plinth Studio", "Plinth Studio"),
            sampleTrack("t3", "Velvet", "Vinyl Rips", "Music/Vinyl Rips"),
        )
        var queueOrder by mutableStateOf(initialTracks)
        var selectTrackCalls = 0
        var moveCalls = 0
        var dismissed = false

        composeTestRule.setContent {
            PlinthTheme(themeMode = ThemeMode.LIGHT, language = AppLanguage.EN) {
                QueueBottomSheet(
                    queue = queueOrder,
                    currentTrackId = "t1",
                    onSelectTrack = { selectTrackCalls += 1 },
                    onMoveItem = { from, to ->
                        moveCalls += 1
                        QueueOperations.moveQueueItem(queueOrder, "t1", from, to)?.let {
                            queueOrder = it.queue
                        }
                    },
                    onRemoveIndex = { idx ->
                        queueOrder = queueOrder.toMutableList().apply { removeAt(idx) }
                    },
                    onDismiss = { dismissed = true },
                )
            }
        }
        composeTestRule.waitForIdle()

        // Move t2 up via menu
        composeTestRule.onNodeWithTag("queue_menu_t2").performClick()
        composeTestRule.onNodeWithTag("queue_move_up_t2").performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf("t2", "t1", "t3"), queueOrder.map { it.id })
        assertEquals(1, moveCalls)
        assertEquals(0, selectTrackCalls)

        // Drag handle on t2 downward moves it without dismissing sheet or restarting track
        composeTestRule.onNodeWithTag("queue_drag_handle_t2", useUnmergedTree = true).performTouchInput {
            swipeDown(startY = centerY, endY = centerY + 180f)
        }
        composeTestRule.waitForIdle()
        assertTrue(moveCalls >= 2)
        assertEquals(0, selectTrackCalls)
        assertTrue(!dismissed)
        assertEquals("t1", queueOrder.first { it.id == "t1" }.id)
    }

    @Test
    fun equalizerSheet_customPresetSaveSelectRenameAndDeleteFlow() {
        RuntimeEnvironment.setQualifiers("w360dp-h760dp-mdpi")
        val spectrumFlow = MutableStateFlow(SpectrumSnapshot())
        var observedPresets: List<CustomEqPreset> = emptyList()
        var observedState = EqualizerState()

        composeTestRule.setContent {
            val baseDensity = LocalDensity.current
            var eqState by remember {
                mutableStateOf(
                    EqualizerState(
                        enabled = true,
                        hardwareAttached = true,
                        presetIndex = -1,
                        bandLevelsDb = listOf(5, 2, 0, -1, 3),
                    )
                )
            }
            val customPresets = remember { mutableStateListOf<CustomEqPreset>() }
            observedPresets = customPresets.toList()
            observedState = eqState

            CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, 1.0f)) {
                PlinthTheme(themeMode = ThemeMode.LIGHT, language = AppLanguage.EN) {
                    EqualizerBottomSheet(
                        eqState = eqState,
                        spectrumFlow = spectrumFlow,
                        hapticsEnabled = false,
                        customPresets = customPresets,
                        onToggleEnabled = { enabled ->
                            eqState = eqState.copy(enabled = enabled)
                            observedState = eqState
                        },
                        onSelectPreset = { idx ->
                            val p = AudioEqualizerController.presets[idx]
                            eqState = eqState.copy(
                                enabled = true,
                                presetIndex = p.index,
                                bandLevelsDb = p.bandsDb,
                                activeCustomPresetId = null,
                            )
                            observedState = eqState
                        },
                        onBandChange = { bandIdx, db ->
                            val next = eqState.bandLevelsDb.toMutableList()
                            next[bandIdx] = db
                            eqState = eqState.copy(enabled = true, presetIndex = -1, bandLevelsDb = next, activeCustomPresetId = null)
                            observedState = eqState
                        },
                        onSaveCustomPreset = { name ->
                            val created = CustomEqPreset(id = "c1", name = name.trim(), bandsDb = eqState.bandLevelsDb)
                            customPresets.add(created)
                            eqState = eqState.copy(enabled = true, presetIndex = -1, activeCustomPresetId = created.id)
                            observedPresets = customPresets.toList()
                            observedState = eqState
                        },
                        onSelectCustomPreset = { id ->
                            val found = customPresets.first { it.id == id }
                            eqState = eqState.copy(enabled = true, presetIndex = -1, bandLevelsDb = found.bandsDb, activeCustomPresetId = id)
                            observedState = eqState
                        },
                        onRenameCustomPreset = { id, newName ->
                            val idx = customPresets.indexOfFirst { it.id == id }
                            if (idx >= 0) customPresets[idx] = customPresets[idx].copy(name = newName.trim())
                            observedPresets = customPresets.toList()
                        },
                        onDeleteCustomPreset = { id ->
                            customPresets.removeAll { it.id == id }
                            if (eqState.activeCustomPresetId == id) {
                                eqState = eqState.copy(activeCustomPresetId = null)
                            }
                            observedPresets = customPresets.toList()
                            observedState = eqState
                        },
                        onDismiss = {},
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        // 1. Save custom preset
        composeTestRule.onNodeWithTag("eq_save_preset_button").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("eq_preset_name_input").performScrollTo().performTextInput("Studio Warm")
        composeTestRule.onNodeWithTag("eq_preset_save_confirm").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, observedPresets.size)
        assertEquals("Studio Warm", observedPresets[0].name)
        assertEquals(listOf(5, 2, 0, -1, 3), observedPresets[0].bandsDb)
        assertEquals("c1", observedState.activeCustomPresetId)
        composeTestRule.onNodeWithTag("eq_custom_preset_c1").performScrollTo().assertIsDisplayed()

        // 2. Switch to built-in Flat, then re-select custom preset
        composeTestRule.onNodeWithTag("eq_preset_0").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf(0, 0, 0, 0, 0), observedState.bandLevelsDb)

        composeTestRule.onNodeWithTag("eq_custom_preset_c1").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf(5, 2, 0, -1, 3), observedState.bandLevelsDb)
        assertEquals("c1", observedState.activeCustomPresetId)

        // 3. Rename custom preset
        composeTestRule.onNodeWithTag("eq_custom_menu_c1").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("eq_custom_rename_c1").performClick()
        composeTestRule.onNodeWithTag("eq_preset_rename_input").performScrollTo().performTextClearance()
        composeTestRule.onNodeWithTag("eq_preset_rename_input").performScrollTo().performTextInput("Jazz Club")
        composeTestRule.onNodeWithTag("eq_preset_rename_confirm").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals("Jazz Club", observedPresets[0].name)
        composeTestRule.onNodeWithText("Jazz Club").performScrollTo().assertIsDisplayed()

        // 4. Delete custom preset
        composeTestRule.onNodeWithTag("eq_custom_menu_c1").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("eq_custom_delete_c1").performClick()
        composeTestRule.waitForIdle()

        assertTrue(observedPresets.isEmpty())
        composeTestRule.onNodeWithTag("eq_custom_preset_c1").assertDoesNotExist()
    }

    @Test
    fun libraryScreen_foldersTabSearchDetailAndTrackQueueActions() {
        RuntimeEnvironment.setQualifiers("w360dp-h760dp-mdpi")
        val t1 = sampleTrack("t1", "Daylight", "Plinth Studio", "Plinth Studio")
        val t2 = sampleTrack("t2", "Sub Bass", "Electronic", "Music/Electronic")
        val tracks = listOf(t1, t2)
        val folders = listOf(
            FolderGroup(name = "Electronic", path = "Music/Electronic", artworkUri = null, tracks = listOf(t2)),
            FolderGroup(name = "Plinth Studio", path = "Plinth Studio", artworkUri = null, tracks = listOf(t1)),
        )
        val playNextCalled = mutableListOf<String>()
        val addToQueueCalled = mutableListOf<String>()
        val playedFromFolder = mutableListOf<String>()

        composeTestRule.setContent {
            PlinthTheme(themeMode = ThemeMode.LIGHT, language = AppLanguage.EN) {
                LibraryScreen(
                    tracks = tracks,
                    albums = listOf(AlbumGroup("Reference Sessions", "Studio Artist", null, tracks)),
                    artists = listOf(ArtistGroup("Studio Artist", null, tracks)),
                    folders = folders,
                    playlists = emptyList(),
                    favoriteIds = emptySet(),
                    activeTrackId = "t1",
                    initialSearchFocused = false,
                    hapticsEnabled = false,
                    onPlayTrack = { track, _ -> playedFromFolder.add(track.id) },
                    onPlayNext = { playNextCalled.add(it.id) },
                    onAddToQueue = { addToQueueCalled.add(it.id) },
                    onToggleFavorite = {},
                    onOpenAddToPlaylist = {},
                    onOpenCreatePlaylist = {},
                    onDeletePlaylist = {},
                    onRemoveFromPlaylist = { _, _ -> },
                    onImportAudio = {},
                    onScanDevice = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        // 1. Track menu in Songs tab triggers Play Next & Add to Queue
        composeTestRule.onNodeWithTag("track_more_t2").performClick()
        composeTestRule.onNodeWithTag("menu_play_next_t2").performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf("t2"), playNextCalled)

        composeTestRule.onNodeWithTag("track_more_t2").performClick()
        composeTestRule.onNodeWithTag("menu_add_to_queue_t2").performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf("t2"), addToQueueCalled)

        // 2. Switch to Folders tab, filter, open folder detail, and play
        composeTestRule.onNodeWithTag("library_tab_folders").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("folder_card_Music/Electronic").assertIsDisplayed()
        composeTestRule.onNodeWithTag("folder_card_Plinth Studio").assertIsDisplayed()

        composeTestRule.onNodeWithTag("library_search_toggle").performClick()
        composeTestRule.onNodeWithTag("library_search_field").performTextInput("Electronic")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("folder_card_Music/Electronic").assertIsDisplayed()
        composeTestRule.onNodeWithTag("folder_card_Plinth Studio").assertDoesNotExist()

        composeTestRule.onNodeWithTag("folder_card_Music/Electronic").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Sub Bass").assertIsDisplayed()
        composeTestRule.onNodeWithText("Play All").performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf("t2"), playedFromFolder)
    }
}
