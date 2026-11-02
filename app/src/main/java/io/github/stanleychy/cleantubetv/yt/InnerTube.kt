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

    /**
     * Player request. VISIONOS returns direct URLs without sign-in, so playback uses it.
     * Use the TV client with [withAuth] to get the playbackTracking URLs for the account.
     */
    fun player(videoId: String, client: Client = Clients.VISIONOS, withAuth: Boolean = false): JSONObject {
        val body = JSONObject()
            .put("context", context(client, forBrowse = false))
            .put("videoId", videoId)
            .put("racyCheckOk", true)
            .put("contentCheckOk", true)
        if (client == Clients.TV) {
            TvAppInfoProvider.get(context).signatureTimestamp?.let { sts ->
                body.put(
                    "playbackContext",
                    JSONObject().put(
                        "contentPlaybackContext",
                        JSONObject().put("html5Preference", "HTML5_PREF_WANTS").put("signatureTimestamp", sts),
                    ),
                )
            }
        }
        val hadVisitor = visitorFor(client) != null
        val r = call("player", client, body, withAuth = withAuth)
        // With no visitor ID, YouTube replies LOGIN_REQUIRED but gives a new visitor ID.
        // The second request with that ID works (verified 2026-11-01). The call above kept it.
        val status = r.optJSONObject("playabilityStatus")?.optString("status")
        if (client != Clients.TV && !hadVisitor && status == "LOGIN_REQUIRED" && visitorFor(client) != null) {
            Log.i(TAG, "player: retry with the new signed-out visitor ID")
            body.getJSONObject("context").getJSONObject("client").put("visitorData", visitorFor(client))
            return call("player", client, body, withAuth = withAuth)
        }
        return r
    }

    /** The signed-in account (name, email or handle, photo). Returns null when no account item is found. */
    fun account(): AccountInfo? {
        val body = JSONObject()
            .put("context", context(Clients.TV, forBrowse = false))
            .put(
                "accountReadMask",
                JSONObject().put("returnOwner", true).put("returnBrandAccounts", true).put("returnPersonaAccounts", false),
            )
        return AccountInfo.parse(call("account/accounts_list", Clients.TV, body, withAuth = true))
    }

    /**
     * The visitor ID for a client. The TV client uses the visitor ID of the signed-in TV session.
     * Other clients are signed out and use a separate visitor ID, so that YouTube cannot link
     * their requests to the account. Null means "no visitor ID yet": YouTube gives one in the reply.
     */
    private fun visitorFor(c: Client): String? =
        if (c == Clients.TV) TvAppInfoProvider.get(context).visitorData else Scraped.visitor(prefs.getString(KEY_ANON_VISITOR, null))

    /** Keeps the visitor ID that YouTube gives to a signed-out client. */
    private fun keepAnonVisitor(c: Client, reply: JSONObject) {
        if (c == Clients.TV) return
        val v = Scraped.visitor(reply.optJSONObject("responseContext")?.optString("visitorData")) ?: return
        if (v != prefs.getString(KEY_ANON_VISITOR, null)) prefs.edit().putString(KEY_ANON_VISITOR, v).apply()
    }

    /** The TV client version comes from the YouTube TV web app. The value in [Clients] is the fallback. */
    private fun versionFor(c: Client): String =
        if (c == Clients.TV) TvAppInfoProvider.get(context).clientVersion ?: c.version else c.version

    private val prefs = context.getSharedPreferences("innertube", Context.MODE_PRIVATE)

    private fun context(c: Client, forBrowse: Boolean): JSONObject {
        val locale = Locale.getDefault()
        val client = JSONObject()
            .put("clientName", c.name)
            .put("clientVersion", versionFor(c))
            .put("hl", locale.language.ifEmpty { "en" })
            .put("gl", locale.country.ifEmpty { "US" })
            .put("utcOffsetMinutes", TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000)
        visitorFor(c)?.let { client.put("visitorData", it) }
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
        val headers = mutableMapOf(
            "Content-Type" to "application/json",
            "User-Agent" to c.userAgent,
            "X-YouTube-Client-Name" to c.nameId,
            "X-YouTube-Client-Version" to versionFor(c),
            "Origin" to "https://www.youtube.com",
        )
        visitorFor(c)?.let { headers["X-Goog-Visitor-Id"] = it }
        if (c == Clients.TV) headers["Referer"] = "https://www.youtube.com/tv"
        // Never send the account token as a signed-out client.
        if (withAuth && c == Clients.TV) auth.accessToken()?.let { headers["Authorization"] = "Bearer $it" }
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
        return JSONObject(r.body).also { keepAnonVisitor(c, it) }
    }

    private companion object {
        const val TAG = "InnerTube"
        const val KEY_ANON_VISITOR = "anon_visitor"
    }
}
