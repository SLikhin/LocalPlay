package com.localplay.app.data.repository

import android.content.Context
import com.localplay.app.data.db.LocalPlayDatabase
import com.localplay.app.data.db.SongDao
import com.localplay.app.data.model.Song
import com.localplay.app.data.model.toDomain
import com.localplay.app.data.scanner.MediaStoreAudioScanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Central repository for audio library data.
 *
 * Owns the hand-off between the [MediaStoreAudioScanner] (source of truth on
 * disk) and [SongDao] (local cache in Room). All public methods are either
 * `suspend` functions or return [Flow] so they integrate naturally with a
 * `ViewModel` using `viewModelScope` / `stateIn`.
 *
 * ### Scan strategy
 *
 * | Function | When to call |
 * |---|---|
 * | [scan] | App foreground resume, or explicit user pull-to-refresh |
 * | [fullRescan] | "Rescan library" settings action; wipes stale rows |
 *
 * #### Incremental logic inside [scan]
 * 1. Query MediaStore for the full set of songs.
 * 2. Upsert all returned rows (REPLACE handles metadata changes).
 * 3. Collect IDs from MediaStore and delete any cached row whose ID is no
 *    longer present (files deleted by the user).
 *
 * This avoids a full table wipe on every launch while keeping the cache
 * consistent with the file system.
 *
 * @param context Application context used to build the scanner and DB.
 */
class SongRepository(context: Context) {

    private val scanner = MediaStoreAudioScanner(context)
    private val dao: SongDao = LocalPlayDatabase.getInstance(context).songDao()

    // ── Observation ───────────────────────────────────────────────────────

    /** Live stream of all songs, ordered by title. */
    fun observeAll(): Flow<List<Song>> = dao.observeAll().map { it.toDomain() }

    /** Live stream of songs filtered by album name. */
    fun observeByAlbum(album: String): Flow<List<Song>> = dao.observeByAlbum(album).map { it.toDomain() }

    /** Live stream of songs filtered by artist name. */
    fun observeByArtist(artist: String): Flow<List<Song>> = dao.observeByArtist(artist).map { it.toDomain() }

    /**
     * Live stream of songs whose title, artist, or album contains [query]
     * (case-insensitive substring match).
     */
    fun search(query: String): Flow<List<Song>> = dao.search(query).map { it.toDomain() }

    // ── One-shot reads ────────────────────────────────────────────────────

    /** Returns the cached [Song] for [id], or `null` if not yet scanned. */
    suspend fun getSongById(id: Long): Song? = dao.getById(id)?.toDomain()

    // ── Scanning ──────────────────────────────────────────────────────────

    /**
     * Performs an incremental scan:
     * 1. Reads all audio tracks from MediaStore.
     * 2. Upserts every returned track into the cache.
     * 3. Removes cache rows whose IDs are no longer in MediaStore.
     *
     * @return The number of songs found in MediaStore.
     */
    suspend fun scan(): Int {
        val songs = scanner.scan()
        dao.upsertAll(songs)

        if (songs.isNotEmpty()) {
            val liveIds = songs.map { it.id }
            dao.deleteObsolete(liveIds)
        }

        return songs.size
    }

    /**
     * Wipes the entire cache and performs a fresh full scan.
     *
     * Use sparingly — prefer [scan] for normal operation.
     *
     * @return The number of songs found after the rescan.
     */
    suspend fun fullRescan(): Int {
        dao.deleteAll()
        return scan()
    }

    /**
     * Removes a single song from the cache without touching the file system.
     * Useful for responding to a [android.database.ContentObserver] delete
     * notification.
     */
    suspend fun removeSong(id: Long) = dao.deleteById(id)
}
