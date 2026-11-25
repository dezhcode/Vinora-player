package com.example.audio

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
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
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.DATE_ADDED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Media.RELATIVE_PATH)
            }
            @Suppress("DEPRECATION")
            add(MediaStore.Audio.Media.DATA)
        }.toTypedArray()
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
                val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                } else {
                    -1
                }
                @Suppress("DEPRECATION")
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)

                while (cursor.moveToNext()) {
                    val mediaId = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: "Untitled Track"
                    val rawArtist = cursor.getString(artistCol)
                    val artist = if (rawArtist.isNullOrBlank() || rawArtist == "<unknown>") "Unknown Artist" else rawArtist
                    val album = cursor.getString(albumCol)?.takeIf { it.isNotBlank() } ?: "Single"
                    val albumId = cursor.getLong(albumIdCol)
                    val duration = cursor.getLong(durationCol).coerceAtLeast(1000L)
                    val dateAdded = cursor.getLong(dateCol) * 1000L

                    val relPath = if (relPathCol >= 0) cursor.getString(relPathCol) else null
                    val dataPath = if (dataCol >= 0) cursor.getString(dataCol) else null
                    val (folderName, folderPath) = resolveMediaStoreFolder(relPath, dataPath)

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
                            folderName = folderName,
                            folderPath = folderPath,
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

    fun resolveMediaStoreFolder(relativePath: String?, dataPath: String?): Pair<String, String> {
        val cleanedRel = relativePath?.trim()?.trimEnd('/', '\\')?.takeIf { it.isNotBlank() }
        if (cleanedRel != null) {
            val name = cleanedRel.substringAfterLast('/').substringAfterLast('\\').ifBlank { cleanedRel }
            return name to cleanedRel
        }
        val parentFile = dataPath?.takeIf { it.isNotBlank() }?.let { File(it).parentFile }
        if (parentFile != null) {
            val name = parentFile.name.ifBlank { "Device Storage" }
            val path = parentFile.path.ifBlank { name }
            return name to path
        }
        return "Device Audio" to "Device Audio"
    }

    fun resolveFolderFromUri(uriString: String): Pair<String, String> {
        val decoded = runCatching { Uri.decode(uriString) }.getOrDefault(uriString)
        val parsed = runCatching { Uri.parse(uriString) }.getOrNull()
        if (parsed?.scheme == "file") {
            val parent = parsed.path?.let { File(it).parentFile }
            if (parent != null) {
                val name = parent.name.ifBlank { "Local Files" }
                return name to (parent.path.ifBlank { name })
            }
        }
        // SAF document URIs often contain "primary:Music/Rock/song.mp3" or ".../document/..."
        val docPart = decoded.substringAfter("/document/", "").ifBlank { decoded.substringAfterLast('/') }
        val cleanDoc = docPart.substringAfter(':', docPart).trim('/')
        val parentDir = cleanDoc.substringBeforeLast('/', "")
        if (parentDir.isNotBlank()) {
            val name = parentDir.substringAfterLast('/').ifBlank { parentDir }
            return name to parentDir
        }
        return "Imported" to "Imported"
    }
}
