package io.github.stanleychy.cleantubetv.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle

/**
 * Opens youtube.com, youtu.be and vnd.youtube links. A video link starts the player.
 * Other links open the main screen. This activity has no UI.
 */
class LinkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.data
        val id = uri?.let { videoId(it) }
        val next = if (id != null) {
            Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_ID, id)
                .putExtra(PlayerActivity.EXTRA_START_MS, startMs(uri))
        } else {
            Intent(this, MainActivity::class.java)
        }
        startActivity(next)
        finish()
    }

    companion object {
        private val ID = Regex("[A-Za-z0-9_-]{11}")
        private val PATH_PREFIXES = listOf("shorts", "live", "embed", "v", "e")

        /** "90", "90s", "1m30s" or "1h2m3s". At most 10 characters, so the value cannot overflow. */
        private val TIME = Regex("^(?:(\\d{1,3})h)?(?:(\\d{1,4})m)?(?:(\\d{1,6})s?)?$")

        /**
         * The start time of a link ("t" or "start" parameter, also in the "#t=" fragment), in milliseconds.
         * Returns 0 when there is no start time or when the value is not valid.
         */
        fun startMs(uri: Uri): Long {
            val raw = runCatching {
                // vnd.youtube:ID?t=90 is an opaque URI, so parse its query from the scheme-specific part.
                val q = if (uri.isOpaque) Uri.parse("x://x/?" + uri.schemeSpecificPart.substringAfter('?', "")) else uri
                q.getQueryParameter("t") ?: q.getQueryParameter("start")
                    ?: uri.fragment?.takeIf { it.startsWith("t=") }?.removePrefix("t=")
            }.getOrNull() ?: return 0
            if (raw.isEmpty() || raw.length > 10) return 0
            val m = TIME.matchEntire(raw) ?: return 0
            val (h, min, s) = m.destructured
            return ((h.toLongOrNull() ?: 0) * 3600 + (min.toLongOrNull() ?: 0) * 60 + (s.toLongOrNull() ?: 0)) * 1000
        }

        /** The video ID in a YouTube link, or null if the link is not for one video. */
        fun videoId(uri: Uri): String? {
            val segments = uri.pathSegments.orEmpty()
            val candidate = when {
                uri.scheme == "vnd.youtube" -> uri.schemeSpecificPart?.removePrefix("//")?.substringBefore('?')
                uri.host.equals("youtu.be", ignoreCase = true) -> segments.firstOrNull()
                segments.firstOrNull() == "watch" -> uri.getQueryParameter("v")
                segments.size >= 2 && segments[0] in PATH_PREFIXES -> segments[1]
                else -> null
            }
            return candidate?.takeIf { ID.matches(it) }
        }
    }
}
