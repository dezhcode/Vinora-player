package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "imported_tracks")
data class ImportedTrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val contentUri: String,
    val artworkUri: String?,
    val dateAdded: Long = System.currentTimeMillis(),
)

@Entity(tableName = "favorites")
data class FavoriteTrackEntity(
    @PrimaryKey val trackId: String,
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "playlist_tracks", primaryKeys = ["playlistId", "trackId"])
data class PlaylistTrackEntity(
    val playlistId: Long,
    val trackId: String,
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "play_history")
data class PlayHistoryEntity(
    @PrimaryKey val trackId: String,
    val lastPlayedAt: Long = System.currentTimeMillis(),
    val playCount: Int = 1,
)

@Dao
interface MusicDao {
    @Query("SELECT * FROM imported_tracks ORDER BY dateAdded DESC")
    fun observeImportedTracks(): Flow<List<ImportedTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImportedTracks(tracks: List<ImportedTrackEntity>)

    @Query("DELETE FROM imported_tracks WHERE id = :id")
    suspend fun deleteImportedTrack(id: String)

    @Query("SELECT trackId FROM favorites ORDER BY addedAt DESC")
    fun observeFavoriteIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(entity: FavoriteTrackEntity)

    @Query("DELETE FROM favorites WHERE trackId = :trackId")
    suspend fun removeFavorite(trackId: String)

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlist_tracks ORDER BY addedAt ASC")
    fun observeAllPlaylistTracks(): Flow<List<PlaylistTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun deleteTracksForPlaylist(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTrackToPlaylist(ref: PlaylistTrackEntity)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String)

    @Query("SELECT * FROM play_history ORDER BY lastPlayedAt DESC LIMIT 12")
    fun observeRecentHistory(): Flow<List<PlayHistoryEntity>>

    @Query("SELECT * FROM play_history WHERE trackId = :trackId LIMIT 1")
    suspend fun getHistoryForTrack(trackId: String): PlayHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(entity: PlayHistoryEntity)
}

@Database(
    entities = [
        ImportedTrackEntity::class,
        FavoriteTrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        PlayHistoryEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao

    companion object {
        @Volatile
        private var INSTANCE: MusicDatabase? = null

        fun getInstance(context: Context): MusicDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MusicDatabase::class.java,
                    "plinth_music.db",
                ).build().also { INSTANCE = it }
            }
        }
    }
}
