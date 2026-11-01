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

    fun segments(videoId: String): List<Segment> = try {
        val prefix = sha256Hex(videoId).take(4)
        val cats = URLEncoder.encode(JSONArray(CATEGORIES).toString(), "UTF-8")
        val actions = URLEncoder.encode("""["skip"]""", "UTF-8")
        val r = Http.request("$API$prefix?categories=$cats&actionTypes=$actions")
        if (r.code == 404 || !r.ok) {
            emptyList()
        } else {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { arr.getJSONObject(it) }
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
    } catch (e: Exception) {
        Log.w(TAG, "segment lookup failed", e)
        emptyList()
    }

    private fun sha256Hex(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
