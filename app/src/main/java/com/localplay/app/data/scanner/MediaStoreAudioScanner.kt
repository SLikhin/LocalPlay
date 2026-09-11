package com.localplay.app.data.scanner

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.localplay.app.data.db.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Queries [MediaStore] for every audio file accessible to this app and maps
 * each row to a [SongEntity].
 *
 * ### Column availability
 *
 * | Column | API | SDK constant |
 * |---|---|---|
 * | Sample rate | 29+ | no named constant — raw string `"samplerate"` |
 * | Bits per sample | 29+ | [MediaStore.Audio.AudioColumns.BITS_PER_SAMPLE] |
 * | Channel count | any | [MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER] |
 *
 * `SAMPLERATE` has no named constant in `MediaStore.Audio.Media` or
 * `MediaStore.Audio.AudioColumns`; the raw column name `"samplerate"` is used
 * instead. `CAPTURE_FRAMERATE` is a `VideoColumns` constant and is never
 * populated on audio rows; channel count is therefore read exclusively via
 * [MediaMetadataRetriever].
 *
 * ### Content URI
 * Uses [MediaStore.Audio.Media.EXTERNAL_CONTENT_URI] which covers both external
 * and (on modern Android) internal storage volumes.
 *
 * ### Permissions required
 * - API 33+: `READ_MEDIA_AUDIO`
 * - API 26–32: `READ_EXTERNAL_STORAGE`
 *
 * The caller is responsible for checking/requesting permissions before
 * invoking [scan].
 */
class MediaStoreAudioScanner(private val context: Context) {

    // ── Projection ────────────────────────────────────────────────────────

    /** Columns available on all supported API levels (minSdk 26). */
    private val baseProjection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATA,          // absolute file path
    )

    /**
     * Extra columns added in API 29:
     * - [COL_SAMPLERATE] — raw column name; no named SDK constant exists.
     * - [MediaStore.Audio.AudioColumns.BITS_PER_SAMPLE] — proper constant on
     *   the `AudioColumns` interface (parent of `MediaStore.Audio.Media`).
     */
    @get:RequiresApi(Build.VERSION_CODES.Q)
    private val hiResProjection = arrayOf(
        COL_SAMPLERATE,
        MediaStore.Audio.AudioColumns.BITS_PER_SAMPLE,
    )

    private val fullProjection: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            baseProjection + hiResProjection
        } else {
            baseProjection
        }

    // ── Selection ─────────────────────────────────────────────────────────

    /** Exclude ringtones, alarms, notifications, and podcasts. */
    private val selection = buildString {
        append("${MediaStore.Audio.Media.IS_MUSIC} != 0")
        append(" AND ${MediaStore.Audio.Media.DURATION} > 0")
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Performs a full MediaStore scan and returns a list of [SongEntity].
     *
     * Dispatched to [Dispatchers.IO] — safe to call from any coroutine
     * without blocking the main thread.
     *
     * @return All scanned songs, or an empty list if no rows are found or
     *   permissions have not been granted.
     */
    suspend fun scan(): List<SongEntity> = withContext(Dispatchers.IO) {
        val resolver: ContentResolver = context.contentResolver
        val uri: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val results = mutableListOf<SongEntity>()
        val nowSeconds = System.currentTimeMillis() / 1_000L
        val isQ = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        resolver.query(
            uri,
            fullProjection,
            selection,
            /* selectionArgs = */ null,
            /* sortOrder = */ "${MediaStore.Audio.Media.TITLE} ASC",
        )?.use { cursor ->

            // ── Column indices ────────────────────────────────────────────
            val idCol       = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol     = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            // Hi-res columns are only in the projection on Q+.
            // getColumnIndex returns -1 when the column is absent.
            val sampleRateCol = if (isQ) cursor.getColumnIndex(COL_SAMPLERATE) else -1
            val bitDepthCol   = if (isQ) cursor.getColumnIndex(
                MediaStore.Audio.AudioColumns.BITS_PER_SAMPLE
            ) else -1

            // ── Cursor iteration ──────────────────────────────────────────
            while (cursor.moveToNext()) {
                val id       = cursor.getLong(idCol)
                val title    = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: UNKNOWN
                val artist   = cursor.getString(artistCol)?.takeIf { it.isNotBlank() } ?: UNKNOWN
                val album    = cursor.getString(albumCol)?.takeIf { it.isNotBlank() } ?: UNKNOWN
                val duration = cursor.getLong(durationCol)
                val filePath = cursor.getString(dataCol) ?: continue  // skip rows without a path

                val sampleRate = if (sampleRateCol >= 0) {
                    cursor.getInt(sampleRateCol).takeIf { it > 0 }
                } else null

                val bitDepth = if (bitDepthCol >= 0) {
                    cursor.getInt(bitDepthCol).takeIf { it > 0 }
                } else null

                // MediaStore has no standard channel-count column.
                // MediaMetadataRetriever gives reliable cross-device results.
                val channelCount = resolveChannelCount(filePath)

                results += SongEntity(
                    id           = id,
                    title        = title,
                    artist       = artist,
                    album        = album,
                    durationMs   = duration,
                    filePath     = filePath,
                    sampleRate   = sampleRate,
                    bitDepth     = bitDepth,
                    channelCount = channelCount,
                    scannedAt    = nowSeconds,
                )
            }
        }

        results
    }

    // ── Private helpers ───────────────────────────────────────────────────

    /**
     * Uses [MediaMetadataRetriever] to read the channel count directly from
     * the file. [MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER] holds
     * the number of audio channels for audio-only media on Android.
     *
     * Returns `null` if the value cannot be determined (e.g. file unreadable
     * or format unsupported).
     */
    private fun resolveChannelCount(filePath: String): Int? = runCatching {
        MediaMetadataRetriever().use { mmr ->
            mmr.setDataSource(filePath)
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                ?.toIntOrNull()
        }
    }.getOrNull()

    /**
     * Build a `content://` URI for a MediaStore audio item — handy for album
     * art loading via Coil.
     */
    fun artUri(songId: Long): Uri =
        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)

    companion object {
        /**
         * Raw MediaStore column name for audio sample rate.
         * No named constant exists in [MediaStore.Audio.AudioColumns]; the
         * underlying SQLite column is `"samplerate"` (populated on API 29+).
         */
        private const val COL_SAMPLERATE = "samplerate"

        private const val UNKNOWN = "<Unknown>"
    }
}
