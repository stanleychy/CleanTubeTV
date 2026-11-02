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
        val id = intent.data?.let { videoId(it) }
        val next = if (id != null) {
            Intent(this, PlayerActivity::class.java).putExtra(PlayerActivity.EXTRA_ID, id)
        } else {
            Intent(this, MainActivity::class.java)
        }
        startActivity(next)
        finish()
    }

    companion object {
        private val ID = Regex("[A-Za-z0-9_-]{11}")
        private val PATH_PREFIXES = listOf("shorts", "live", "embed", "v", "e")

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
