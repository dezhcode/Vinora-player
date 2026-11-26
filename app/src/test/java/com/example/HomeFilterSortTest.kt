package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.example.data.HomeFilterType
import com.example.data.HomeSortField
import com.example.data.HomeTrackSorter
import com.example.data.Track
import com.example.ui.i18n.AppLanguage
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.PlinthTheme
import com.example.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HomeFilterSortTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val trackAlpha = Track(
        id = "t_alpha",
        title = "Amber Dawn",
        artist = "Zara Vance",
        album = "Horizon",
        durationMs = 140_000L,
        contentUri = "file:///music/amber.mp3",
        isBuiltIn = true,
        dateAdded = 1000L,
    )

    private val trackBeta = Track(
        id = "t_beta",
        title = "Midnight Echo",
        artist = "Aaron Cole",
        album = "Analog Sessions",
        durationMs = 240_000L,
        contentUri = "file:///music/midnight.mp3",
        isBuiltIn = false,
        dateAdded = 3000L,
    )

    private val trackGamma = Track(
        id = "t_gamma",
        title = "Velvet Static",
        artist = "Mina Sol",
        album = "Deep Grooves",
        durationMs = 195_000L,
        contentUri = "file:///music/velvet.mp3",
        isBuiltIn = false,
        dateAdded = 2000L,
    )

    private val allTracks = listOf(trackAlpha, trackBeta, trackGamma)

    @Test
    fun homeTrackSorter_sortsByDateTitleArtistAlbumAndDuration() {
        // Date descending (newest first)
        assertEquals(
            listOf("t_beta", "t_gamma", "t_alpha"),
            HomeTrackSorter.filterAndSort(allTracks, HomeSortField.DATE, false, HomeFilterType.ALL).map { it.id },
        )
        // Date ascending (oldest first)
        assertEquals(
            listOf("t_alpha", "t_gamma", "t_beta"),
            HomeTrackSorter.filterAndSort(allTracks, HomeSortField.DATE, true, HomeFilterType.ALL).map { it.id },
        )
        // Track Title A-Z and Z-A
        assertEquals(
            listOf("t_alpha", "t_beta", "t_gamma"),
            HomeTrackSorter.filterAndSort(allTracks, HomeSortField.TITLE, true, HomeFilterType.ALL).map { it.id },
        )
        assertEquals(
            listOf("t_gamma", "t_beta", "t_alpha"),
            HomeTrackSorter.filterAndSort(allTracks, HomeSortField.TITLE, false, HomeFilterType.ALL).map { it.id },
        )
        // Artist A-Z
        assertEquals(
            listOf("t_beta", "t_gamma", "t_alpha"),
            HomeTrackSorter.filterAndSort(allTracks, HomeSortField.ARTIST, true, HomeFilterType.ALL).map { it.id },
        )
        // Album A-Z
        assertEquals(
            listOf("t_beta", "t_gamma", "t_alpha"),
            HomeTrackSorter.filterAndSort(allTracks, HomeSortField.ALBUM, true, HomeFilterType.ALL).map { it.id },
        )
        // Duration descending (longest first) and ascending (shortest first)
        assertEquals(
            listOf("t_beta", "t_gamma", "t_alpha"),
            HomeTrackSorter.filterAndSort(allTracks, HomeSortField.DURATION, false, HomeFilterType.ALL).map { it.id },
        )
        assertEquals(
            listOf("t_alpha", "t_gamma", "t_beta"),
            HomeTrackSorter.filterAndSort(allTracks, HomeSortField.DURATION, true, HomeFilterType.ALL).map { it.id },
        )
    }

    @Test
    fun homeTrackSorter_appliesQuickFilters() {
        val favorites = setOf("t_alpha", "t_gamma")
        val recents = setOf("t_gamma")

        assertEquals(
            listOf("t_gamma", "t_alpha"),
            HomeTrackSorter.filterAndSort(allTracks, HomeSortField.DATE, false, HomeFilterType.FAVORITES, favorites, recents).map { it.id },
        )
        assertEquals(
            listOf("t_beta", "t_gamma"),
            HomeTrackSorter.filterAndSort(allTracks, HomeSortField.DATE, false, HomeFilterType.DEVICE, favorites, recents).map { it.id },
        )
        assertEquals(
            listOf("t_gamma"),
            HomeTrackSorter.filterAndSort(allTracks, HomeSortField.DATE, false, HomeFilterType.RECENT, favorites, recents).map { it.id },
        )
        assertEquals(
            listOf("t_beta", "t_gamma"),
            HomeTrackSorter.filterAndSort(allTracks, HomeSortField.DATE, false, HomeFilterType.LONG_TRACKS, favorites, recents).map { it.id },
        )
    }

    @Test
    fun homeScreen_sortAndFilterControls_updateDisplayedTracksAndQueue() {
        RuntimeEnvironment.setQualifiers("w360dp-h760dp-mdpi")
        var lastSort: Pair<HomeSortField, Boolean>? = null
        var lastFilter: HomeFilterType? = null
        var playedQueueIds: List<String> = emptyList()

        composeTestRule.setContent {
            PlinthTheme(themeMode = ThemeMode.LIGHT, language = AppLanguage.EN) {
                HomeScreen(
                    currentTrack = null,
                    isPlaying = false,
                    recentTracks = listOf(trackGamma),
                    favoriteTracks = listOf(trackAlpha, trackGamma),
                    favoriteIds = setOf("t_alpha", "t_gamma"),
                    allTracks = allTracks,
                    sleepTimerRemainingMs = 0L,
                    isScanningDevice = false,
                    hapticsEnabled = false,
                    onPlayTrack = { _, queue -> playedQueueIds = queue.map { it.id } },
                    onTogglePlayPause = {},
                    onOpenNowPlaying = {},
                    onOpenSearch = {},
                    onOpenEqualizer = {},
                    onOpenSettings = {},
                    onImportAudio = {},
                    onScanDevice = {},
                    onSortChange = { f, asc -> lastSort = f to asc },
                    onFilterChange = { lastFilter = it },
                )
            }
        }
        composeTestRule.waitForIdle()

        // Sort by Track Name -> ascending
        composeTestRule.onNodeWithTag("home_sort_title").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(HomeSortField.TITLE to true, lastSort)

        // Toggle sort order -> descending
        composeTestRule.onNodeWithTag("home_sort_order_toggle").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(HomeSortField.TITLE to false, lastSort)

        // Filter by Favorites -> excludes t_beta
        composeTestRule.onNodeWithTag("home_filter_favorites").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(HomeFilterType.FAVORITES, lastFilter)
        assertEquals(0, composeTestRule.onAllNodesWithTag("home_track_row_t_beta").fetchSemanticsNodes().size)
        composeTestRule.onNodeWithTag("home_track_row_t_gamma").performScrollTo().assertIsDisplayed()

        // Playing a filtered track passes the filtered & sorted queue (Velvet Static before Amber Dawn)
        composeTestRule.onNodeWithTag("home_track_row_t_gamma").performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf("t_gamma", "t_alpha"), playedQueueIds)

        // Sort by Artist
        composeTestRule.onNodeWithTag("home_sort_artist").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(HomeSortField.ARTIST to true, lastSort)
    }

    @Test
    fun homeScreen_emptyFilteredState_resetsBackToAllTracks_andSupportsPersianRtl() {
        RuntimeEnvironment.setQualifiers("w320dp-h640dp-mdpi")
        val builtInOnly = listOf(trackAlpha)

        composeTestRule.setContent {
            PlinthTheme(themeMode = ThemeMode.DARK, language = AppLanguage.FA) {
                HomeScreen(
                    currentTrack = null,
                    isPlaying = false,
                    recentTracks = emptyList(),
                    favoriteTracks = emptyList(),
                    allTracks = builtInOnly,
                    sleepTimerRemainingMs = 0L,
                    isScanningDevice = false,
                    hapticsEnabled = false,
                    onPlayTrack = { _, _ -> },
                    onTogglePlayPause = {},
                    onOpenNowPlaying = {},
                    onOpenSearch = {},
                    onOpenEqualizer = {},
                    onOpenSettings = {},
                    onImportAudio = {},
                    onScanDevice = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        // Scroll to Sort/Filter section inside LazyColumn
        composeTestRule.onNodeWithTag("home_screen").performScrollToNode(hasTestTag("home_filter_row"))
        composeTestRule.waitForIdle()

        // Persian labels visible
        composeTestRule.onNodeWithText("تاریخ").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("نام موزیک").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("نام خواننده").performScrollTo().assertIsDisplayed()

        // Selecting Device Audio filter when only built-in tracks exist displays empty state + Show All reset button
        composeTestRule.onNodeWithTag("home_filter_row").performScrollToNode(hasTestTag("home_filter_device"))
        composeTestRule.onNodeWithTag("home_filter_device").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("home_screen").performScrollToNode(hasTestTag("home_empty_filtered"))
        composeTestRule.onNodeWithTag("home_empty_filtered").assertIsDisplayed()

        composeTestRule.onNodeWithTag("home_reset_filter_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("home_screen").performScrollToNode(hasTestTag("home_track_row_t_alpha"))
        composeTestRule.onNodeWithTag("home_track_row_t_alpha").assertIsDisplayed()
    }
}
