package io.github.stanleychy.cleantubetv.cast

import androidx.media3.common.C
import androidx.media3.common.Player
import java.security.SecureRandom
import java.util.Locale

/**
 * One video that a phone started, and its player when the player is ready.
 * It makes the state reports for the phone. Use it on the main thread only.
 *
 * [sequence] identifies the player screen that plays this video. A new cast video gets a new number,
 * so a report from the player of an old video is not used for the new video.
 */
internal class CastPlayback(val sequence: Int, val request: CastCommand.Play) {
    /** A random ID for this playback. Lounge state reports have one. It is not the ID of the watch reports. */
    private val cpn = randomCpn()

    /** Null while the player screen loads the video. */
    var player: Player? = null
        private set

    /** Connects the player. [onChange] runs each time the state changes in a way that the phone must know. */
    fun attach(player: Player, onChange: () -> Unit) {
        this.player = player
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) = onChange()

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = onChange()

            override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) {
                // A seek moves the position. This includes a SponsorBlock skip. The phone must show the new position.
                if (reason == Player.DISCONTINUITY_REASON_SEEK) onChange()
            }
        })
        onChange()
    }

    /** True when the video played to its end. */
    val ended: Boolean get() = player?.playbackState == Player.STATE_ENDED

    /** The "nowPlaying" and "onStateChange" events for the current state. */
    fun events(): List<LoungeEvent> {
        val state = mapOf(
            "state" to state().toString(),
            "currentTime" to seconds(player?.currentPosition ?: request.startMs),
            "duration" to seconds(durationMs()),
            "loadedTime" to "0",
            "seekableStartTime" to "0",
            "seekableEndTime" to seconds(durationMs()),
            "cpn" to cpn,
        )
        val video = HashMap<String, String>(state)
        video["videoId"] = request.videoId
        request.listId?.let { video["listId"] = it }
        request.index?.let { video["currentIndex"] = it.toString() }
        return listOf(LoungeEvent("nowPlaying", video), LoungeEvent("onStateChange", state))
    }

    private fun durationMs(): Long = player?.duration?.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0

    private fun state(): Int {
        val p = player ?: return STATE_BUFFERING
        return when {
            p.playbackState == Player.STATE_ENDED -> STATE_ENDED
            // The player is idle before it starts to load, and after an error.
            p.playbackState == Player.STATE_IDLE -> if (p.playerError != null) STATE_UNSTARTED else STATE_BUFFERING
            !p.playWhenReady -> STATE_PAUSED
            p.playbackState == Player.STATE_BUFFERING -> STATE_BUFFERING
            else -> STATE_PLAYING
        }
    }

    private fun seconds(ms: Long) = "%.3f".format(Locale.US, ms / 1000.0)

    private companion object {
        // The player states of the YouTube player API. The Lounge protocol uses the same numbers.
        const val STATE_UNSTARTED = -1
        const val STATE_ENDED = 0
        const val STATE_PLAYING = 1
        const val STATE_PAUSED = 2
        const val STATE_BUFFERING = 3

        const val CPN_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

        fun randomCpn(): String {
            val r = SecureRandom()
            return (1..16).map { CPN_ALPHABET[r.nextInt(CPN_ALPHABET.length)] }.joinToString("")
        }
    }
}
