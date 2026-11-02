package io.github.stanleychy.cleantubetv.yt

import android.content.Context
import io.github.stanleychy.cleantubetv.net.Http
import java.io.IOException

/**
 * Data scraped from the YouTube TV web app (https://www.youtube.com/tv):
 * the visitor ID and the OAuth client ID/secret that YouTube's own TV app uses.
 * YouTube only accepts TV OAuth tokens minted for this client.
 *
 * [signatureTimestamp] comes from the TV player script. The TV player request needs it,
 * else YouTube replies "The page needs to be reloaded". The TV client uses the 8-digit
 * value of the "-tcl" player variant, for example 20753001.
 *
 * [clientVersion] keeps the TV client version the same as the real TV app. A fixed old
 * version is easy for YouTube to see.
 */
data class TvAppInfo(
    val visitorData: String,
    val clientId: String,
    val clientSecret: String,
    val signatureTimestamp: Int?,
    /** The current TVHTML5 client version, for example 7.20261020.10.00. Null when not found. */
    val clientVersion: String?,
)

object TvAppInfoProvider {
    private const val PREFS = "tv_app_info"
    private const val MAX_AGE_MS = 24L * 60 * 60 * 1000

    @Volatile private var cached: TvAppInfo? = null

    @Synchronized
    fun get(context: Context, forceRefresh: Boolean = false): TvAppInfo {
        if (!forceRefresh) cached?.let { return it }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!forceRefresh && System.currentTimeMillis() - prefs.getLong("ts", 0) < MAX_AGE_MS) {
            // Check the stored values again. Values that are not valid cause a new fetch.
            val v = Scraped.visitor(prefs.getString("visitor", null))
            val id = Scraped.clientId(prefs.getString("id", null))
            val secret = Scraped.clientSecret(prefs.getString("secret", null))
            val sts = Scraped.signatureTimestamp(prefs.getInt("sts", 0))
            val version = Scraped.version(prefs.getString("version", null))
            if (v != null && id != null && secret != null && sts != null) {
                return TvAppInfo(v, id, secret, sts, version).also { cached = it }
            }
        }
        val info = fetch()
        prefs.edit()
            .putLong("ts", System.currentTimeMillis())
            .putString("visitor", info.visitorData)
            .putString("id", info.clientId)
            .putString("secret", info.clientSecret)
            .putInt("sts", info.signatureTimestamp ?: 0)
            .putString("version", info.clientVersion)
            .apply()
        cached = info
        return info
    }

    private fun fetch(): TvAppInfo {
        val ua = mapOf("User-Agent" to Clients.TV_UA)
        val html = Http.get("https://www.youtube.com/tv", ua)
        val visitor = Scraped.visitor(Regex("\"visitorData\":\"(.*?)\"").find(html)?.groupValues?.get(1))
            ?: throw IOException("valid visitorData not found on /tv")
        val basePatterns = listOf(
            Regex("id=\"base-js\" src=\"(.*?)\""),
            Regex("\\.src = '(.*?m=base)'"),
            Regex("\\.src = '(.*?)'; .\\.id = 'base-js'"),
        )
        val baseUrl = Scraped.youtubeUrl(basePatterns.firstNotNullOfOrNull { it.find(html)?.groupValues?.get(1) })
            ?: throw IOException("base script URL on /tv not found or not on www.youtube.com")
        val js = Http.get(baseUrl, ua)
        // The first match is the TV client. Later matches belong to other clients.
        val m = Regex("clientId:\"([-\\w]+\\.apps\\.googleusercontent\\.com)\",\\n?[\$\\w]+:\"(\\w+)\"").find(js)
            ?: throw IOException("OAuth client not found in base script")
        val clientId = Scraped.clientId(m.groupValues[1]) ?: throw IOException("OAuth client ID has an unexpected format")
        val secret = Scraped.clientSecret(m.groupValues[2]) ?: throw IOException("OAuth client secret has an unexpected format")
        return TvAppInfo(visitor, clientId, secret, signatureTimestamp(html, ua), clientVersion(html))
    }

    /** Reads the TVHTML5 client version from the ytcfg data of the /tv page. */
    private fun clientVersion(html: String): String? {
        val patterns = listOf(
            Regex("\"INNERTUBE_CLIENT_VERSION\":\"(.*?)\""),
            Regex("\"INNERTUBE_CONTEXT_CLIENT_VERSION\":\"(.*?)\""),
        )
        // Only the ytcfg keys: a generic "clientVersion" match can be the version of a different client.
        // A value that is not valid falls back to the value in Clients.
        return patterns.firstNotNullOfOrNull { p -> Scraped.version(p.find(html)?.groupValues?.get(1)) }
    }

    private fun signatureTimestamp(html: String, ua: Map<String, String>): Int? {
        val path = Scraped.youtubeUrl(Regex("\"player_url\":\"(.*?)\"").find(html)?.groupValues?.get(1)) ?: return null
        val tcl = path.replace(Regex("/[^/]+\\.vflset/[^/]+\\.js$"), "/tv-player-ias-tcl.vflset/tv-player-ias-tcl.js")
        val sts = Regex("signatureTimestamp:(\\d{5}|\\d{8})\\b")
        for (candidate in listOf(tcl, path)) {
            val js = runCatching { Http.get(candidate, ua) }.getOrNull() ?: continue
            val v = sts.find(js)?.groupValues?.get(1) ?: continue
            // The TV client needs the 8-digit form. The regular player has the 5-digit form.
            return Scraped.signatureTimestamp((if (v.length == 5) v + "001" else v).toIntOrNull()) ?: continue
        }
        return null
    }
}
