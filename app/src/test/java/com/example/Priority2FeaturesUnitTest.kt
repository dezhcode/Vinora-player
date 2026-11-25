package com.example

import com.example.audio.DeviceMediaScanner
import com.example.data.CustomEqPreset
import com.example.data.PlaybackPreferences
import com.example.data.Track
import com.example.viewmodel.MediaQueueAction
import com.example.viewmodel.QueueOperations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Priority2FeaturesUnitTest {

    private fun track(id: String, folder: String = "Plinth Studio") = Track(
        id = id,
        title = "Title $id",
        artist = "Artist $id",
        album = "Album $id",
        durationMs = 180_000L,
        contentUri = "file:///music/$folder/$id.mp3",
        folderName = folder,
        folderPath = "Music/$folder",
    )

    @Test
    fun queuePlayNext_insertsAfterCurrentWithoutReplacingQueue() {
        val q = listOf(track("a"), track("b"), track("c"))
        val newTrack = track("x")

        val res = QueueOperations.playNext(q, currentTrackId = "a", track = newTrack)

        assertEquals(listOf("a", "x", "b", "c"), res.queue.map { it.id })
        assertEquals(0, res.currentIndex)
        assertEquals(MediaQueueAction.Insert(1, newTrack), res.mediaControllerAction)
    }

    @Test
    fun queuePlayNext_movesExistingBeforeAndAfterCurrentAccurately() {
        val q = listOf(track("a"), track("b"), track("c"), track("d"))

        // Existing track after current ('d' -> right after 'b')
        val resAfter = QueueOperations.playNext(q, currentTrackId = "b", track = track("d"))
        assertEquals(listOf("a", "b", "d", "c"), resAfter.queue.map { it.id })
        assertEquals(1, resAfter.currentIndex)
        assertEquals(MediaQueueAction.Move(3, 2), resAfter.mediaControllerAction)

        // Existing track before current ('a' -> right after 'b', so current 'b' shifts to 0)
        val resBefore = QueueOperations.playNext(q, currentTrackId = "b", track = track("a"))
        assertEquals(listOf("b", "a", "c", "d"), resBefore.queue.map { it.id })
        assertEquals(0, resBefore.currentIndex)
        assertEquals(MediaQueueAction.Move(0, 1), resBefore.mediaControllerAction)

        // Already currently playing track -> no-op
        val resSame = QueueOperations.playNext(q, currentTrackId = "b", track = track("b"))
        assertEquals(MediaQueueAction.None, resSame.mediaControllerAction)
        assertEquals(1, resSame.currentIndex)
    }

    @Test
    fun queueAddToQueue_appendsNewAndMovesExistingToEnd() {
        val q = listOf(track("a"), track("b"), track("c"))
        val tNew = track("z")

        val resAppend = QueueOperations.addToQueue(q, currentTrackId = "b", track = tNew)
        assertEquals(listOf("a", "b", "c", "z"), resAppend.queue.map { it.id })
        assertEquals(1, resAppend.currentIndex)
        assertEquals(MediaQueueAction.Append(tNew), resAppend.mediaControllerAction)

        val resMove = QueueOperations.addToQueue(q, currentTrackId = "b", track = track("a"))
        assertEquals(listOf("b", "c", "a"), resMove.queue.map { it.id })
        assertEquals(0, resMove.currentIndex)
        assertEquals(MediaQueueAction.Move(0, 2), resMove.mediaControllerAction)
    }

    @Test
    fun queueMoveItem_reordersAndKeepsCurrentIndexTrackingPlayingTrack() {
        val q = listOf(track("a"), track("b"), track("c"), track("d"))

        // Move 'd' (3) above current 'b' (1) to index 0 -> 'b' becomes index 2
        val res1 = QueueOperations.moveQueueItem(q, currentTrackId = "b", fromIndex = 3, toIndex = 0)!!
        assertEquals(listOf("d", "a", "b", "c"), res1.queue.map { it.id })
        assertEquals(2, res1.currentIndex)
        assertEquals(MediaQueueAction.Move(3, 0), res1.mediaControllerAction)

        // Move current 'b' itself from 2 -> 3
        val res2 = QueueOperations.moveQueueItem(res1.queue, currentTrackId = "b", fromIndex = 2, toIndex = 3)!!
        assertEquals(listOf("d", "a", "c", "b"), res2.queue.map { it.id })
        assertEquals(3, res2.currentIndex)
        assertEquals(MediaQueueAction.Move(2, 3), res2.mediaControllerAction)

        // Invalid indices or same index -> null
        assertNull(QueueOperations.moveQueueItem(q, "b", fromIndex = 1, toIndex = 1))
        assertNull(QueueOperations.moveQueueItem(q, "b", fromIndex = -1, toIndex = 2))
        assertNull(QueueOperations.moveQueueItem(q, "b", fromIndex = 0, toIndex = 99))
    }

    @Test
    fun customEqPresets_encodeDecodeRoundTripAndSanitization() {
        val input = listOf(
            CustomEqPreset(id = "c1", name = "Late Night", bandsDb = listOf(3, 1, -2, 4, 2)),
            CustomEqPreset(id = "c2", name = "Sub Boost", bandsDb = listOf(20, -20, 0, 5, -3)),
        )
        val json = PlaybackPreferences.encodeCustomPresets(input)
        val decoded = PlaybackPreferences.decodeCustomPresets(json)

        assertEquals(2, decoded.size)
        assertEquals("c1", decoded[0].id)
        assertEquals("Late Night", decoded[0].name)
        assertEquals(listOf(3, 1, -2, 4, 2), decoded[0].bandsDb)

        // Clamped to [-12, 12]
        assertEquals(listOf(12, -12, 0, 5, -3), decoded[1].bandsDb)

        // Resilient to blank or corrupt JSON
        assertTrue(PlaybackPreferences.decodeCustomPresets(null).isEmpty())
        assertTrue(PlaybackPreferences.decodeCustomPresets("not-json").isEmpty())
        assertTrue(PlaybackPreferences.decodeCustomPresets("[{\"id\":\"bad\",\"name\":\"x\",\"bands\":[1,2]}]").isEmpty())
    }

    @Test
    fun folderResolution_handlesMediaStoreAndUriFormats() {
        // Android Q+ RELATIVE_PATH
        val (n1, p1) = DeviceMediaScanner.resolveMediaStoreFolder("Music/Synthwave/", "/storage/emulated/0/Music/Synthwave/song.mp3")
        assertEquals("Synthwave", n1)
        assertEquals("Music/Synthwave", p1)

        // Legacy DATA column fallback
        val (n2, p2) = DeviceMediaScanner.resolveMediaStoreFolder(null, "/storage/emulated/0/Download/Mixes/set.mp3")
        assertEquals("Mixes", n2)
        assertEquals("/storage/emulated/0/Download/Mixes", p2)

        // SAF encoded document URI
        val (n3, p3) = DeviceMediaScanner.resolveFolderFromUri(
            "content://com.android.externalstorage.documents/document/primary%3AMusic%2FLoFi%2Ftrack.flac"
        )
        assertEquals("LoFi", n3)
        assertEquals("Music/LoFi", p3)

        // file:// URI
        val (n4, p4) = DeviceMediaScanner.resolveFolderFromUri("file:///sdcard/Albums/Jazz/blue.wav")
        assertEquals("Jazz", n4)
        assertEquals("/sdcard/Albums/Jazz", p4)
    }
}
