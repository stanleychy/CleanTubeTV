package io.github.stanleychy.cleantubetv.yt

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import io.github.stanleychy.cleantubetv.auth.Auth
import io.github.stanleychy.cleantubetv.net.Http
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.TimeZone

/** A player reply and the signed-out client that gave it. The stream requests must use the User-Agent of this client. */
class Playback(val client: Client, val reply: JSONObject)

/** InnerTube (youtubei/v1) requests. */
class InnerTube(private val context: Context, private val auth: Auth) {

    /** Browse as the signed-in TV client. Pass a browseId for the first page, or a continuation token. */
    fun browse(browseId: String?, continuation: String? = null): JSONObject {
        val body = JSONObject().put("context", context(Clients.TV, forBrowse = true))
        if (continuation != null) body.put("continuation", continuation) else body.put("browseId", browseId)
        return call("browse", Clients.TV, body, withAuth = true)
    }

    /**
     * Search as the signed-in TV client, the same identity as the feeds. The official TV app does the same.
     * Thus YouTube keeps the query in the search history of the account.
     * Pass a query for the first page, or a continuation token that [Scraped.continuation] accepted.
     */
    fun search(query: String, continuation: String? = null): JSONObject {
        val body = JSONObject().put("context", context(Clients.TV, forBrowse = true))
        // The query is user input. JSONObject.put escapes it.
        if (continuation != null) body.put("continuation", continuation) else body.put("query", query)
        return call("search", Clients.TV, body, withAuth = true)
    }

    /**
     * The player reply for playback. Tries each signed-out client in [Clients.PLAYBACK] until one gives a stream.
     * When no client gives a stream, the result is that of the first client: its reason is the one to show.
     */
    fun playback(videoId: String): Playback {
        val attempts = ArrayList<Result<Playback>>()
        for (client in Clients.PLAYBACK) {
            val attempt = runCatching { Playback(client, player(videoId, client)) }
            val playback = attempt.getOrNull()
            if (playback != null && hasStream(playback.reply)) return playback
            val cause = playback?.let { "status=${statusOf(it.reply)}" } ?: attempt.exceptionOrNull()?.javaClass?.simpleName
            Log.w(TAG, "player: ${client.name} gave no stream ($cause)")
            attempts += attempt
        }
        return attempts.first().getOrThrow()
    }

    /**
     * Player request. A signed-out client returns direct URLs without sign-in, so playback uses it (see [playback]).
     * Use the TV client with [withAuth] to get the playbackTracking URLs for the account.
     */
    fun player(videoId: String, client: Client = Clients.VISIONOS, withAuth: Boolean = false): JSONObject {
        // The TV request is linked to the account. Send it one time only.
        if (client == Clients.TV) return playerCall(videoId, client, withAuth)
        return signedOutPlayer(videoId, client)
    }

    /**
     * Player request as a signed-out client, with recovery from "Sign in to confirm you're not a bot".
     * YouTube gives that LOGIN_REQUIRED reply when the request has no visitor ID or a visitor ID that it does not
     * accept. The reply to a request with no visitor ID has a new visitor ID, and the subsequent request with
     * that ID works (verified 2026-11-13). Thus this function sends a maximum of three requests:
     * with the stored ID, with no ID, and with the new ID.
     */
    private fun signedOutPlayer(videoId: String, client: Client): JSONObject {
        val hadVisitor = visitorFor(client) != null
        var reply = playerCall(videoId, client, withAuth = false)
        if (!loginRequired(reply)) return reply
        if (hadVisitor) {
            // YouTube does not accept the stored visitor ID. Discard it, and the ID from this reply: it can be the same one.
            Log.i(TAG, "player: ${client.name} LOGIN_REQUIRED, discard the signed-out visitor ID")
            forgetAnonVisitor(client)
            reply = playerCall(videoId, client, withAuth = false)
            if (!loginRequired(reply)) return reply
        }
        if (visitorFor(client) == null) return reply
        Log.i(TAG, "player: ${client.name} retry with the new signed-out visitor ID")
        return playerCall(videoId, client, withAuth = false)
    }

    /** One player request. It builds the body again for each request, so that the body has the current visitor ID. */
    private fun playerCall(videoId: String, client: Client, withAuth: Boolean): JSONObject {
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
        return call("player", client, body, withAuth = withAuth)
    }

    private fun statusOf(reply: JSONObject): String? = reply.optJSONObject("playabilityStatus")?.optString("status")

    private fun loginRequired(reply: JSONObject) = statusOf(reply) == PlayerParser.LOGIN_REQUIRED

    /** True when the reply has status OK and a direct stream URL or an HLS manifest. A reply with only SABR streams has none. */
    private fun hasStream(reply: JSONObject): Boolean {
        if (statusOf(reply) != "OK") return false
        val data = reply.optJSONObject("streamingData") ?: return false
        if (data.optString("hlsManifestUrl").isNotEmpty()) return true
        return data.optJSONArray("adaptiveFormats")?.objects().orEmpty().any { it.optString("url").isNotEmpty() }
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
     * Other clients are signed out. Each one uses its own visitor ID, so that YouTube cannot link
     * their requests to the account, and so that an ID that YouTube does not accept stays with one client.
     * Null means "no visitor ID yet": YouTube gives one in the reply.
     */
    private fun visitorFor(c: Client): String? =
        if (c == Clients.TV) TvAppInfoProvider.get(context).visitorData else Scraped.visitor(prefs.getString(anonVisitorKey(c), null))

    /** Keeps the visitor ID that YouTube gives to a signed-out client. */
    private fun keepAnonVisitor(c: Client, reply: JSONObject) {
        if (c == Clients.TV) return
        val v = Scraped.visitor(reply.optJSONObject("responseContext")?.optString("visitorData")) ?: return
        if (v != prefs.getString(anonVisitorKey(c), null)) prefs.edit().putString(anonVisitorKey(c), v).apply()
    }

    /** Discards the visitor ID of a signed-out client. The subsequent request has no visitor ID, and YouTube gives a new one. */
    private fun forgetAnonVisitor(c: Client) {
        if (c == Clients.TV) return
        prefs.edit().remove(anonVisitorKey(c)).apply()
    }

    private fun anonVisitorKey(c: Client) = "${KEY_ANON_VISITOR}_${c.name}"

    /** The TV client version comes from the YouTube TV web app. The value in [Clients] is the fallback. */
    private fun versionFor(c: Client): String =
        if (c == Clients.TV) TvAppInfoProvider.get(context).clientVersion ?: c.version else c.version

    private val prefs = context.getSharedPreferences("innertube", Context.MODE_PRIVATE).also {
        // Earlier versions kept one visitor ID for all signed-out clients in this key. It is not used now.
        if (it.contains(KEY_ANON_VISITOR)) it.edit().remove(KEY_ANON_VISITOR).apply()
    }
    private val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

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
            // A redirect (3xx) is an error too: the request never follows it. Only a debug build logs the reply body.
            Log.w(TAG, "$endpoint HTTP ${r.code}" + if (debuggable) ": ${r.body.take(300)}" else "")
            throw IOException("YouTube $endpoint request failed (HTTP ${r.code})")
        }
        return JSONObject(r.body).also { keepAnonVisitor(c, it) }
    }

    private companion object {
        const val TAG = "InnerTube"
        const val KEY_ANON_VISITOR = "anon_visitor"
    }
}
