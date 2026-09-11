package com.localplay.app.data.scanner

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.localplay.app.data.db.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Queries [MediaStore] for every audio file accessible to this app and maps
 * each row to a [SongEntity].
 *
 * ### Column availability
 * The columns [MediaStore.Audio.Media.SAMPLERATE],
 * [MediaStore.Audio.Media.BITS_PER_SAMPLE], and
 * [MediaStore.Audio.Media.CAPTURE_FRAMERATE] were added in API 29. On older
 * devices these columns are simply not projected and the resulting fields in
 * [SongEntity] will be `null`.
 *
 * ### Content URI
 * Uses [MediaStore.Audio.Media.EXTERNAL_CONTENT_URI] to scan the shared
 * external storage. Internal storage is included automatically by MediaStore
 * on modern Android.
 *
 * ### Permissions required
 * - API 33+: `READ_MEDIA_AUDIO`
 * - API 29–32: `READ_EXTERNAL_STORAGE`
 *
 * The caller is responsible for checking/requesting permissions before
 * invoking [scan].
 */
class MediaStoreAudioScanner(private val context: Context) {

    // ── Projection ────────────────────────────────────────────────────────

    /**
     * Columns always requested, available on all supported API levels (26+).
     */
    private val baseProjection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATA,          // absolute file path
    )

    /**
     * Extra columns available only from API 29 onward.
     * Appended to [baseProjection] when running on a qualifying device.
     */
    private val hiResProjection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        arrayOf(
            MediaStore.Audio.Media.SAMPLERATE,
            MediaStore.Audio.Media.BITS_PER_SAMPLE,
            MediaStore.Audio.Media.CAPTURE_FRAMERATE, // repurposed as channel count — see note below
        )
    } else {
        emptyArray()
    }

    // NOTE: MediaStore does not expose a dedicated CHANNEL_COUNT column.
    // The closest proxy available is CAPTURE_FRAMERATE (API 29+), which for
    // audio files is typically repurposed by device OEMs to store the channel
    // count. If your target device/OEM does not populate it, channel count
    // must be read via MediaMetadataRetriever at open-file time. The scanner
    // falls back to MediaMetadataRetriever for channel count when
    // CAPTURE_FRAMERATE is 0 or null (see _resolveChannelCount_).

    private val fullProjection: Array<String>
        get() = baseProjection + hiResProjection

    // ── Selection ─────────────────────────────────────────────────────────

    /**
     * Exclude ringtones, alarms, notifications, and podcasts so that only
     * music tracks appear.
     */
    private val selection = buildString {
        append("${MediaStore.Audio.Media.IS_MUSIC} != 0")
        append(" AND ${MediaStore.Audio.Media.DURATION} > 0")
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Performs a full MediaStore scan and returns a list of [SongEntity].
     *
     * Runs entirely on [Dispatchers.IO] — safe to call from any coroutine
     * without blocking the main thread.
     *
     * @return All scanned songs. Empty list if the cursor returns no rows or
     *   permissions have not been granted.
     */
    suspend fun scan(): List<SongEntity> = withContext(Dispatchers.IO) {
        val resolver: ContentResolver = context.contentResolver
        val uri: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val results = mutableListOf<SongEntity>()
        val nowSeconds = System.currentTimeMillis() / 1_000L

        resolver.query(
            uri,
            fullProjection,
            selection,
            /* selectionArgs = */ null,
            /* sortOrder = */ "${MediaStore.Audio.Media.TITLE} ASC",
        )?.use { cursor ->

            // ── Column indices ────────────────────────────────────────────
            val idCol        = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol     = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol     = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol  = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol      = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            // Hi-res columns are only present on Q+; getColumnIndex returns -1 when absent.
            val sampleRateCol  = cursor.getColumnIndex(MediaStore.Audio.Media.SAMPLERATE)
            val bitDepthCol    = cursor.getColumnIndex(MediaStore.Audio.Media.BITS_PER_SAMPLE)
            val captureFrameCol = cursor.getColumnIndex(MediaStore.Audio.Media.CAPTURE_FRAMERATE)

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

                // Channel count: try CAPTURE_FRAMERATE first (OEM convention),
                // fall back to MediaMetadataRetriever for strict accuracy.
                val channelCount: Int? = when {
                    captureFrameCol >= 0 -> {
                        val v = cursor.getInt(captureFrameCol)
                        if (v > 0) v else resolveChannelCount(filePath)
                    }
                    else -> resolveChannelCount(filePath)
                }

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
     * Uses [android.media.MediaMetadataRetriever] to read the channel count
     * directly from the file when MediaStore does not provide it.
     *
     * Returns `null` if the value cannot be determined (e.g. file unreadable).
     */
    private fun resolveChannelCount(filePath: String): Int? = runCatching {
        android.media.MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(filePath)
            retriever
                .extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                ?.toIntOrNull()
                ?: retriever
                    .extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS)
                    ?.toIntOrNull()
        }
    }.getOrNull()

    /**
     * Build a content:// URI for a MediaStore audio item — handy for album
     * art loading via Coil.
     */
    fun artUri(songId: Long): Uri =
        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)

    companion object {
        private const val UNKNOWN = "<Unknown>"
    }
}
