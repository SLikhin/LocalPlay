package com.localplay.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted representation of a single audio track as indexed from MediaStore.
 *
 * Columns that are not available on older API levels (sample rate, bit depth,
 * channel count) are nullable so the entity can always be written regardless
 * of device API.
 */
@Entity(tableName = "songs")
data class SongEntity(

    /** MediaStore [android.provider.MediaStore.Audio.Media._ID]. */
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Long,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "artist")
    val artist: String,

    @ColumnInfo(name = "album")
    val album: String,

    /** Duration in milliseconds. */
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,

    /** Absolute path on the file-system, e.g. `/storage/emulated/0/Music/song.flac`. */
    @ColumnInfo(name = "file_path")
    val filePath: String,

    /** Sampling frequency in Hz (e.g. 44100, 48000). Available from API 29+. */
    @ColumnInfo(name = "sample_rate")
    val sampleRate: Int?,

    /**
     * Number of bits per sample (e.g. 16, 24). Only populated for lossless
     * formats that expose `MediaStore.Audio.Media.BITS_PER_SAMPLE` (API 29+).
     */
    @ColumnInfo(name = "bit_depth")
    val bitDepth: Int?,

    /** Number of audio channels (1 = mono, 2 = stereo, etc.). API 29+. */
    @ColumnInfo(name = "channel_count")
    val channelCount: Int?,

    /**
     * Unix epoch timestamp (seconds) of the last time this row was written.
     * Used to detect stale cache entries during incremental rescans.
     */
    @ColumnInfo(name = "scanned_at")
    val scannedAt: Long = System.currentTimeMillis() / 1_000L,
)
