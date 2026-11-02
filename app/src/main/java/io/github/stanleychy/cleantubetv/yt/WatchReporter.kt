package io.github.stanleychy.cleantubetv.yt

import android.util.Log
import io.github.stanleychy.cleantubetv.auth.Auth
import io.github.stanleychy.cleantubetv.net.Http
import java.security.SecureRandom
import java.util.Locale

/**
 * Reports playback to YouTube, so the video shows in Watch History with a resume point.
 *
 * The stream comes from a signed-out VISIONOS request, and its playbackTracking URLs
 * are not linked to the account. So, the same as SmartTube, this class makes one extra
 * player request as the signed-in TV client and uses the tracking URLs from it.
 * Call all methods off the main thread.
 */
class WatchReporter(private val innerTube: InnerTube, private val auth: Auth, private val info: PlayerInfo) {
    private val cpn = randomCpn()
    private var playbackUrl: String? = null
    private var watchtimeUrl: String? = null
    private var resolved = false
    private var started = false
    private var lastPosSec = 0.0

    @Synchronized
    fun onStart(positionMs: Long) {
        if (started || !resolve()) return
        started = true
        lastPosSec = positionMs / 1000.0
        val base = playbackUrl ?: return
        send("playback", "$base&ver=2&cpn=$cpn&cmt=${fmt(lastPosSec)}${lenParam(base)}")
    }

    @Synchronized
    fun onProgress(positionMs: Long) {
        if (!started) return onStart(positionMs)
        val base = watchtimeUrl ?: return
        val pos = positionMs / 1000.0
        if (pos <= lastPosSec) { lastPosSec = pos; return }
        val len = info.lengthSec.toDouble()
        val final = if (len > 0 && len - pos < len * 0.05) "&final=1" else ""
        send(
            "watchtime",
            "$base&ver=2&cpn=$cpn&cmt=${fmt(pos)}&st=${fmt(lastPosSec)}&et=${fmt(pos)}${lenParam(base)}&state=playing$final",
        )
        lastPosSec = pos
    }

    private fun resolve(): Boolean {
        if (resolved) return playbackUrl != null || watchtimeUrl != null
        resolved = true
        if (!auth.isSignedIn) return false
        try {
            val r = innerTube.player(info.videoId, Clients.TV, withAuth = true)
            val tracking = r.optJSONObject("playbackTracking")
            playbackUrl = tracking?.obj("videostatsPlaybackUrl")?.optString("baseUrl")?.ifEmpty { null }
            watchtimeUrl = tracking?.obj("videostatsWatchtimeUrl")?.optString("baseUrl")?.ifEmpty { null }
            val status = r.optJSONObject("playabilityStatus")?.optString("status")
            Log.i(TAG, "TV tracking: status=$status playback=${playbackUrl != null} watchtime=${watchtimeUrl != null}")
        } catch (e: Exception) {
            Log.w(TAG, "TV player request for tracking failed", e)
        }
        return playbackUrl != null || watchtimeUrl != null
    }

    private fun send(kind: String, url: String) {
        try {
            val headers = mutableMapOf("User-Agent" to Clients.TV_UA, "Referer" to "https://www.youtube.com/tv")
            auth.accessToken()?.let { headers["Authorization"] = "Bearer $it" }
            val r = Http.request(url, headers = headers)
            if (r.ok) Log.i(TAG, "$kind ping OK") else Log.w(TAG, "$kind ping HTTP ${r.code}")
        } catch (e: Exception) {
            Log.w(TAG, "$kind ping failed", e)
        }
    }

    /** The TV tracking URLs already contain "len". */
    private fun lenParam(base: String) = if (Regex("[?&]len=").containsMatchIn(base)) "" else "&len=${info.lengthSec}"

    private fun fmt(v: Double) = "%.3f".format(Locale.US, v)

    private companion object {
        const val TAG = "WatchReporter"
        const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        fun randomCpn(): String {
            val r = SecureRandom()
            return (1..16).map { ALPHABET[r.nextInt(64)] }.joinToString("")
        }
    }
}
