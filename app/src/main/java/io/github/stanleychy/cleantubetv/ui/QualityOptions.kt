package io.github.stanleychy.cleantubetv.ui

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import kotlin.math.min

/**
 * One row of the quality menu. It stands for all video tracks with the same short side: each codec, frame rate
 * and dynamic range. YouTube names a quality by the short side of the frame.
 *
 * @property side The short side of the frame in pixels, for example 1080.
 * @property best The track that the track selector usually selects for this resolution. Use it for the label only.
 */
@OptIn(UnstableApi::class)
class QualityOption(val side: Int, val best: Format, private val formats: List<Format>) {
    // The smallest and the largest frame of this resolution. Codecs can differ by some pixels.
    val minWidth get() = formats.minOf { it.width }
    val minHeight get() = formats.minOf { it.height }
    val maxWidth get() = formats.maxOf { it.width }
    val maxHeight get() = formats.maxOf { it.height }

    /** The name that YouTube shows for this resolution, or an empty string. */
    val hint
        get() = when {
            side >= 4320 -> "8K"
            side >= 2160 -> "4K"
            side >= 1440 -> "2K"
            side >= 1080 -> "HD"
            else -> ""
        }
}

/** Makes the rows of the quality menu from the tracks of the player. This works for DASH and for HLS. */
@OptIn(UnstableApi::class)
object QualityOptions {
    /**
     * Returns one option for each resolution that the device can play, highest first.
     * [preferredMimeTypes] is the codec order of the track selector. It finds [QualityOption.best].
     */
    fun from(tracks: Tracks, preferredMimeTypes: List<String>): List<QualityOption> {
        // The same order as the track selector: the preferred codec first, then the higher bitrate.
        // On YouTube, the HDR and the 60 fps variants have the higher bitrate.
        val preference = compareBy<Format> { mimeRank(it, preferredMimeTypes) }.thenByDescending { it.bitrate }
        return playableVideoFormats(tracks)
            .groupBy { min(it.width, it.height) }
            .map { (side, formats) -> QualityOption(side, formats.minWith(preference), formats) }
            .sortedByDescending { it.side }
    }

    /** The video tracks that have a frame size and that a decoder of the device can play. */
    private fun playableVideoFormats(tracks: Tracks): List<Format> =
        tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }.flatMap { group ->
            (0 until group.length).filter { group.isTrackSupported(it) }.map { group.getTrackFormat(it) }
        }.filter { it.width > 0 && it.height > 0 }

    private fun mimeRank(f: Format, preferredMimeTypes: List<String>): Int =
        preferredMimeTypes.indexOf(f.sampleMimeType).let { if (it < 0) preferredMimeTypes.size else it }
}
