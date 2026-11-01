package io.github.stanleychy.cleantubetv.yt

import android.util.Log
import io.github.stanleychy.cleantubetv.auth.Auth
import io.github.stanleychy.cleantubetv.net.Http
import java.security.SecureRandom

/**
 * Reports playback to YouTube with the playbackTracking URLs from the player response,
 * so the video shows in Watch History. Sends the OAuth token so YouTube links the
 * watch to the account. Call all methods off the main thread.
 */
class WatchReporter(private val auth: Auth, private val info: PlayerInfo) {
    private val cpn = randomCpn()
    private val startRealMs = System.currentTimeMillis()
    private var lastPosSec = 0.0
    private var started = false

    fun onStart() {
        val base = info.statsPlaybackUrl ?: return
        if (started) return
        started = true
        send("$base&ver=2&cpn=$cpn&cmt=0&rtn=0&rt=0&len=${info.lengthSec}")
    }

    fun onProgress(positionMs: Long) {
        val base = info.statsWatchtimeUrl ?: return
        val pos = positionMs / 1000.0
        if (pos <= lastPosSec) { lastPosSec = pos; return }
        val rt = (System.currentTimeMillis() - startRealMs) / 1000.0
        send("$base&ver=2&cpn=$cpn&cmt=${fmt(pos)}&st=${fmt(lastPosSec)}&et=${fmt(pos)}&rt=${fmt(rt)}&rtn=${fmt(rt)}&len=${info.lengthSec}&state=playing")
        lastPosSec = pos
    }

    private fun send(url: String) {
        try {
            val headers = mutableMapOf("User-Agent" to Clients.VISIONOS.userAgent)
            auth.accessToken()?.let { headers["Authorization"] = "Bearer $it" }
            val r = Http.request(url, headers = headers)
            if (!r.ok) Log.w(TAG, "stats ping HTTP ${r.code}")
        } catch (e: Exception) {
            Log.w(TAG, "stats ping failed", e)
        }
    }

    private fun fmt(v: Double) = "%.3f".format(java.util.Locale.US, v)

    private companion object {
        const val TAG = "WatchReporter"
        const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        fun randomCpn(): String {
            val r = SecureRandom()
            return (1..16).map { ALPHABET[r.nextInt(64)] }.joinToString("")
        }
    }
}
