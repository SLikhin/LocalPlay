package com.localplay.app.data.model

/**
 * Immutable domain model for a single audio track.
 *
 * This is the type exposed to the UI layer. It is mapped *from* [com.localplay.app.data.db.SongEntity]
 * inside the repository so that the UI has no direct dependency on Room.
 *
 * Duration and hi-res fields use value-class wrappers for type-safety where
 * the raw types are ambiguous (e.g. milliseconds vs. seconds).
 */
data class Song(
    /** MediaStore unique identifier. */
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    /** Duration in milliseconds. */
    val durationMs: Long,
    /** Absolute path to the audio file. */
    val filePath: String,
    /** Sampling frequency in Hz, or `null` if not available. */
    val sampleRate: Int?,
    /** Bits per sample (16 / 24 / 32), or `null` if not available. */
    val bitDepth: Int?,
    /** Number of audio channels, or `null` if not available. */
    val channelCount: Int?,
) {
    /**
     * Human-readable duration string in `mm:ss` format.
     * Long-form `h:mm:ss` is used automatically for tracks ≥ 1 hour.
     */
    val durationFormatted: String
        get() {
            val totalSecs = durationMs / 1_000L
            val h = totalSecs / 3600
            val m = (totalSecs % 3600) / 60
            val s = totalSecs % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, s)
            else "%d:%02d".format(m, s)
        }

    /**
     * Returns a short label describing the audio format quality, e.g.
     * "24-bit / 96 kHz" or "Stereo" — useful for a "Hi-Res" badge.
     */
    val qualityLabel: String?
        get() {
            val parts = buildList {
                if (bitDepth != null && bitDepth > 16) add("$bitDepth-bit")
                if (sampleRate != null && sampleRate > 44100) add("${sampleRate / 1000} kHz")
            }
            return parts.joinToString(" / ").ifEmpty { null }
        }
}
