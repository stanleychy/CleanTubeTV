package io.github.stanleychy.cleantubetv.yt

import android.content.Context
import android.util.Log
import io.github.stanleychy.cleantubetv.auth.Auth
import io.github.stanleychy.cleantubetv.net.Http
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.TimeZone

/** InnerTube (youtubei/v1) requests. */
class InnerTube(private val context: Context, private val auth: Auth) {

    /** Browse as the signed-in TV client. Pass a browseId for the first page, or a continuation token. */
    fun browse(browseId: String?, continuation: String? = null): JSONObject {
        val body = JSONObject().put("context", context(Clients.TV, forBrowse = true))
        if (continuation != null) body.put("continuation", continuation) else body.put("browseId", browseId)
        return call("browse", Clients.TV, body, withAuth = true)
    }

    /** Player request for playback. VISIONOS returns direct URLs without sign-in. */
    fun player(videoId: String, client: Client = Clients.VISIONOS): JSONObject {
        val body = JSONObject()
            .put("context", context(client, forBrowse = false))
            .put("videoId", videoId)
            .put("racyCheckOk", true)
            .put("contentCheckOk", true)
        return call("player", client, body, withAuth = false)
    }

    private fun context(c: Client, forBrowse: Boolean): JSONObject {
        val app = TvAppInfoProvider.get(context)
        val locale = Locale.getDefault()
        val client = JSONObject()
            .put("clientName", c.name)
            .put("clientVersion", c.version)
            .put("hl", locale.language.ifEmpty { "en" })
            .put("gl", locale.country.ifEmpty { "US" })
            .put("utcOffsetMinutes", TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000)
            .put("visitorData", app.visitorData)
        c.extra.forEach { (k, v) -> client.put(k, v) }
        if (forBrowse && c == Clients.TV) {
            client.put("tvAppInfo", JSONObject().put("appQuality", "TV_APP_QUALITY_FULL_ANIMATION").put("zylonLeftNav", true))
            client.put("webpSupport", false)
        }
        return JSONObject()
            .put("client", client)
            .put("user", JSONObject().put("enableSafetyMode", false).put("lockedSafetyMode", false))
    }

    private fun call(endpoint: String, c: Client, body: JSONObject, withAuth: Boolean): JSONObject {
        val app = TvAppInfoProvider.get(context)
        val headers = mutableMapOf(
            "Content-Type" to "application/json",
            "User-Agent" to c.userAgent,
            "X-YouTube-Client-Name" to c.nameId,
            "X-YouTube-Client-Version" to c.version,
            "X-Goog-Visitor-Id" to app.visitorData,
            "Origin" to "https://www.youtube.com",
        )
        if (c == Clients.TV) headers["Referer"] = "https://www.youtube.com/tv"
        if (withAuth) auth.accessToken()?.let { headers["Authorization"] = "Bearer $it" }
        val r = Http.request(
            "https://www.youtube.com/youtubei/v1/$endpoint?prettyPrint=false",
            method = "POST",
            headers = headers,
            body = body.toString(),
        )
        if (!r.ok) {
            Log.w(TAG, "$endpoint HTTP ${r.code}: ${r.body.take(300)}")
            throw IOException("YouTube $endpoint request failed (HTTP ${r.code})")
        }
        return JSONObject(r.body)
    }

    private companion object { const val TAG = "InnerTube" }
}
