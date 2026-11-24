package com.example.audio

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.example.data.ImportedTrackEntity
import com.example.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object DeviceMediaScanner {

    suspend fun scanDeviceAudio(context: Context): List<Track> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 3000"
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder,
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val mediaId = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: "Untitled Track"
                    val rawArtist = cursor.getString(artistCol)
                    val artist = if (rawArtist.isNullOrBlank() || rawArtist == "<unknown>") "Unknown Artist" else rawArtist
                    val album = cursor.getString(albumCol)?.takeIf { it.isNotBlank() } ?: "Single"
                    val albumId = cursor.getLong(albumIdCol)
                    val duration = cursor.getLong(durationCol).coerceAtLeast(1000L)
                    val dateAdded = cursor.getLong(dateCol) * 1000L

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        mediaId,
                    ).toString()

                    val albumArtUri = if (albumId > 0L) {
                        ContentUris.withAppendedId(
                            Uri.parse("content://media/external/audio/albumart"),
                            albumId,
                        ).toString()
                    } else {
                        null
                    }

                    tracks.add(
                        Track(
                            id = "device_$mediaId",
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = duration,
                            contentUri = contentUri,
                            artworkUri = albumArtUri,
                            isBuiltIn = false,
                            dateAdded = dateAdded,
                        )
                    )
                }
            }
        }
        tracks
    }

    suspend fun importAudioUris(context: Context, uris: List<Uri>): List<ImportedTrackEntity> =
        withContext(Dispatchers.IO) {
            val coverDir = File(context.filesDir, "imported_covers").apply { mkdirs() }
            val result = mutableListOf<ImportedTrackEntity>()

            for (uri in uris) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }

                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    val fallbackName = queryDisplayName(context, uri)
                        ?.substringBeforeLast('.')
                        ?.takeIf { it.isNotBlank() }
                        ?: "Imported Audio"
                    val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                        ?.takeIf { it.isNotBlank() } ?: fallbackName
                    val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                        ?.takeIf { it.isNotBlank() } ?: "Local Audio"
                    val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                        ?.takeIf { it.isNotBlank() } ?: "Imported"
                    val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull()
                        ?.coerceAtLeast(1000L) ?: 180_000L

                    val id = "import_${uri.toString().hashCode().toUInt()}"
                    val embeddedPicture = retriever.embeddedPicture
                    val artworkUri = if (embeddedPicture != null && embeddedPicture.isNotEmpty()) {
                        val artFile = File(coverDir, "$id.jpg")
                        FileOutputStream(artFile).use { it.write(embeddedPicture) }
                        Uri.fromFile(artFile).toString()
                    } else {
                        null
                    }

                    result.add(
                        ImportedTrackEntity(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = duration,
                            contentUri = uri.toString(),
                            artworkUri = artworkUri,
                        )
                    )
                } catch (_: Throwable) {
                    val fallbackName = queryDisplayName(context, uri)?.substringBeforeLast('.') ?: "Audio File"
                    val id = "import_${uri.toString().hashCode().toUInt()}"
                    result.add(
                        ImportedTrackEntity(
                            id = id,
                            title = fallbackName,
                            artist = "Local Audio",
                            album = "Imported",
                            durationMs = 180_000L,
                            contentUri = uri.toString(),
                            artworkUri = null,
                        )
                    )
                } finally {
                    runCatching { retriever.release() }
                }
            }
            result
        }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull()
    }
}
