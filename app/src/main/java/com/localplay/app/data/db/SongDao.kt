package com.localplay.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the [SongEntity] table.
 *
 * All query functions return [Flow] so callers can observe live updates
 * (e.g. while a background scan is still running). Write functions are
 * `suspend` so they must be called from a coroutine.
 */
@Dao
interface SongDao {

    // ── Reads ─────────────────────────────────────────────────────────────

    /** Observe every song ordered by title ascending. */
    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun observeAll(): Flow<List<SongEntity>>

    /** Observe all songs belonging to a specific album. */
    @Query("SELECT * FROM songs WHERE album = :album ORDER BY title ASC")
    fun observeByAlbum(album: String): Flow<List<SongEntity>>

    /** Observe all songs by a specific artist. */
    @Query("SELECT * FROM songs WHERE artist = :artist ORDER BY title ASC")
    fun observeByArtist(artist: String): Flow<List<SongEntity>>

    /** One-shot lookup by MediaStore ID. Returns `null` if not cached. */
    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getById(id: Long): SongEntity?

    /** Full-text search across title, artist, and album (case-insensitive). */
    @Query(
        """
        SELECT * FROM songs
        WHERE title LIKE '%' || :query || '%'
           OR artist LIKE '%' || :query || '%'
           OR album LIKE '%' || :query || '%'
        ORDER BY title ASC
        """
    )
    fun search(query: String): Flow<List<SongEntity>>

    /** Returns all MediaStore IDs currently in the cache. */
    @Query("SELECT id FROM songs")
    suspend fun getAllIds(): List<Long>

    // ── Writes ────────────────────────────────────────────────────────────

    /**
     * Upserts a single song. [OnConflictStrategy.REPLACE] means that if a
     * track's metadata has changed (e.g. after a re-tag), the row is
     * atomically replaced.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(song: SongEntity)

    /**
     * Bulk upsert for efficiency during initial or full rescans.
     * Runs as a single transaction.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(songs: List<SongEntity>)

    /** Remove a song that was deleted from the file system. */
    @Query("DELETE FROM songs WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Remove every cached song whose MediaStore ID is NOT in [validIds].
     * Call this after a full scan to evict stale rows.
     */
    @Query("DELETE FROM songs WHERE id NOT IN (:validIds)")
    suspend fun deleteObsolete(validIds: List<Long>)

    /** Wipe the entire cache — useful for a forced full-rescan. */
    @Query("DELETE FROM songs")
    suspend fun deleteAll()
}
