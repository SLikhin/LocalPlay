package com.localplay.app.data.model

import com.localplay.app.data.db.SongEntity

/**
 * Mapping functions between the Room persistence layer and the UI domain layer.
 *
 * Kept as extension functions on the entity so the mapping is colocated with
 * the data model and remains easy to find.
 */

/** Maps a single [SongEntity] to its domain representation [Song]. */
fun SongEntity.toDomain(): Song = Song(
    id           = id,
    title        = title,
    artist       = artist,
    album        = album,
    durationMs   = durationMs,
    filePath     = filePath,
    sampleRate   = sampleRate,
    bitDepth     = bitDepth,
    channelCount = channelCount,
)

/** Convenience extension to map a list in one call. */
fun List<SongEntity>.toDomain(): List<Song> = map(SongEntity::toDomain)
