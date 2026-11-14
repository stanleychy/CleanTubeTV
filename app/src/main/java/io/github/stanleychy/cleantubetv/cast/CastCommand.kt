package io.github.stanleychy.cleantubetv.cast

import io.github.stanleychy.cleantubetv.yt.Scraped
import org.json.JSONArray

/**
 * The commands of a linked phone that this app obeys. The app plays one video at a time and has no queue,
 * so the queue commands of the Lounge protocol ("updatePlaylist", "next", "previous", "setAutoplayMode")
 * have no command here. Volume commands are also not supported.
 */
sealed class CastCommand {
    /**
     * Play one video. [listId] and [index] are not used for playback. The app sends them back in the
     * "nowPlaying" report, so that the phone knows that the report is for its queue.
     */
    data class Play(val videoId: String, val startMs: Long, val listId: String?, val index: Int?) : CastCommand()

    data object Resume : CastCommand()

    data object Pause : CastCommand()

    data class Seek(val positionMs: Long) : CastCommand()

    /** Stop the video and close the player. */
    data object Stop : CastCommand()

    /** The phone wants the player state. [remoteLinked] is true when the message shows a linked phone. */
    data class ReportState(val remoteLinked: Boolean) : CastCommand()

    companion object {
        /** Positions above this value are not valid. The limit also prevents an overflow in milliseconds. */
        private const val MAX_SECONDS = 7 * 24 * 3600.0

        /**
         * Makes a command from a Lounge message. Returns null for a message that the app ignores,
         * and for a message with a value that is not valid (for example a video ID with a wrong format).
         */
        fun from(m: LoungeMessage): CastCommand? = when (m.name) {
            // The phone sends "setPlaylist" with the selected video in "videoId". "setVideo" is the older form.
            "setPlaylist", "setVideo" -> play(m)
            "play" -> Resume
            "pause" -> Pause
            "seekTo" -> seconds(m, "newTime")?.let { Seek(it) }
            "stopVideo" -> Stop
            "getNowPlaying" -> ReportState(remoteLinked = false)
            "remoteConnected" -> ReportState(remoteLinked = true)
            "loungeStatus" -> ReportState(remoteLinked = hasRemote(m.params.optString("devices")))
            else -> null
        }

        private fun play(m: LoungeMessage): CastCommand? {
            val p = m.params
            val id = Scraped.videoId(p.optString("videoId")) ?: return null
            val index = p.optString("currentIndex").toIntOrNull()?.takeIf { it in 0..100_000 }
            return Play(id, seconds(m, "currentTime") ?: 0, Scraped.listId(p.optString("listId")), index)
        }

        /** A time in seconds (text or number, for example "42" or 42.5) as milliseconds. Null when not valid. */
        private fun seconds(m: LoungeMessage, key: String): Long? {
            val s = m.params.optString(key).toDoubleOrNull() ?: return null
            if (s.isNaN() || s < 0 || s > MAX_SECONDS) return null
            return (s * 1000).toLong()
        }

        /** "devices" is a JSON list in a text value. A phone or a computer has the type REMOTE_CONTROL. */
        private fun hasRemote(devices: String): Boolean {
            val list = runCatching { JSONArray(devices) }.getOrNull() ?: return false
            return (0 until list.length()).any { list.optJSONObject(it)?.optString("type") == "REMOTE_CONTROL" }
        }
    }
}
