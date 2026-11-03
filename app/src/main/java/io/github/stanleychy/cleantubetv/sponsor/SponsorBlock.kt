package io.github.stanleychy.cleantubetv.sponsor

import android.util.Log
import io.github.stanleychy.cleantubetv.net.Http
import org.json.JSONArray
import java.net.URLEncoder
import java.security.MessageDigest

data class Segment(val startMs: Long, val endMs: Long, val category: String)

/**
 * SponsorBlock client (https://sponsor.ajay.app). Uses the hash-prefix endpoint:
 * the server only sees the first 4 hex characters of SHA-256(videoId), so it cannot
 * tell which video you watch. The client filters the matching video locally.
 */
object SponsorBlock {
    private const val TAG = "SponsorBlock"
    private const val API = "https://sponsor.ajay.app/api/skipSegments/"

    /** Categories that are skipped automatically. */
    val CATEGORIES = listOf("sponsor", "selfpromo", "interaction")

    /** Pauses before the second and the third attempt, in milliseconds. */
    private val RETRY_DELAYS_MS = longArrayOf(5_000, 15_000)

    /**
     * The segments to skip. The SponsorBlock server is slow at times, so the lookup tries up to three times
     * with increasing pauses. It tries again only after a network error, a timeout, HTTP 429 or a 5xx reply.
     * HTTP 404 means "no segments" and is final. [keepTrying] stops the retries, for example when the player closed.
     */
    fun segments(videoId: String, keepTrying: () -> Boolean = { true }): List<Segment> {
        val prefix = sha256Hex(videoId).take(4)
        val cats = URLEncoder.encode(JSONArray(CATEGORIES).toString(), "UTF-8")
        val actions = URLEncoder.encode("""["skip"]""", "UTF-8")
        val url = "$API$prefix?categories=$cats&actionTypes=$actions"
        for (attempt in 0..RETRY_DELAYS_MS.size) {
            if (attempt > 0) {
                if (!keepTrying()) return emptyList()
                Thread.sleep(RETRY_DELAYS_MS[attempt - 1])
            }
            try {
                val r = Http.request(url, headers = mapOf("User-Agent" to Http.APP_USER_AGENT))
                when {
                    r.ok -> return parse(videoId, r.body).also { Log.i(TAG, "${it.size} segments to skip (attempt ${attempt + 1})") }
                    r.code == 404 -> return emptyList()
                    r.code == 429 || r.code >= 500 -> Log.w(TAG, "segment lookup HTTP ${r.code} (attempt ${attempt + 1})")
                    else -> { Log.w(TAG, "segment lookup HTTP ${r.code}"); return emptyList() }
                }
            } catch (e: Exception) {
                Log.w(TAG, "segment lookup failed (attempt ${attempt + 1}): $e")
            }
        }
        return emptyList()
    }

    private fun parse(videoId: String, body: String): List<Segment> {
        val arr = JSONArray(body)
        return (0 until arr.length()).map { arr.getJSONObject(it) }
            .firstOrNull { it.optString("videoID") == videoId }
            ?.optJSONArray("segments")
            ?.let { segs ->
                (0 until segs.length()).map { segs.getJSONObject(it) }
                    .filter { it.optString("actionType", "skip") == "skip" }
                    .map {
                        val s = it.getJSONArray("segment")
                        Segment((s.getDouble(0) * 1000).toLong(), (s.getDouble(1) * 1000).toLong(), it.optString("category"))
                    }
                    .filter { it.endMs > it.startMs }
                    .sortedBy { it.startMs }
            }
            .orEmpty()
    }

    private fun sha256Hex(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
