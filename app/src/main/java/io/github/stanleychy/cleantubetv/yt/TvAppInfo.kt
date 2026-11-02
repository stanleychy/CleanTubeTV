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
 */
data class TvAppInfo(val visitorData: String, val clientId: String, val clientSecret: String, val signatureTimestamp: Int?)

object TvAppInfoProvider {
    private const val PREFS = "tv_app_info"
    private const val MAX_AGE_MS = 24L * 60 * 60 * 1000

    @Volatile private var cached: TvAppInfo? = null

    @Synchronized
    fun get(context: Context, forceRefresh: Boolean = false): TvAppInfo {
        if (!forceRefresh) cached?.let { return it }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!forceRefresh && System.currentTimeMillis() - prefs.getLong("ts", 0) < MAX_AGE_MS) {
            val v = prefs.getString("visitor", null)
            val id = prefs.getString("id", null)
            val secret = prefs.getString("secret", null)
            val sts = prefs.getInt("sts", 0).takeIf { it > 0 }
            if (v != null && id != null && secret != null && sts != null) {
                return TvAppInfo(v, id, secret, sts).also { cached = it }
            }
        }
        val info = fetch()
        prefs.edit()
            .putLong("ts", System.currentTimeMillis())
            .putString("visitor", info.visitorData)
            .putString("id", info.clientId)
            .putString("secret", info.clientSecret)
            .putInt("sts", info.signatureTimestamp ?: 0)
            .apply()
        cached = info
        return info
    }

    private fun fetch(): TvAppInfo {
        val ua = mapOf("User-Agent" to Clients.TV_UA)
        val html = Http.get("https://www.youtube.com/tv", ua)
        val visitor = Regex("\"visitorData\":\"(.*?)\"").find(html)?.groupValues?.get(1)
            ?: throw IOException("visitorData not found on /tv")
        val basePatterns = listOf(
            Regex("id=\"base-js\" src=\"(.*?)\""),
            Regex("\\.src = '(.*?m=base)'"),
            Regex("\\.src = '(.*?)'; .\\.id = 'base-js'"),
        )
        var baseUrl = basePatterns.firstNotNullOfOrNull { it.find(html)?.groupValues?.get(1) }
            ?: throw IOException("base script URL not found on /tv")
        baseUrl = baseUrl.replace("\\/", "/")
        if (baseUrl.startsWith("/")) baseUrl = "https://www.youtube.com$baseUrl"
        val js = Http.get(baseUrl, ua)
        // The first match is the TV client. Later matches belong to other clients.
        val m = Regex("clientId:\"([-\\w]+\\.apps\\.googleusercontent\\.com)\",\\n?[\$\\w]+:\"(\\w+)\"").find(js)
            ?: throw IOException("OAuth client not found in base script")
        return TvAppInfo(visitor, m.groupValues[1], m.groupValues[2], signatureTimestamp(html, ua))
    }

    private fun signatureTimestamp(html: String, ua: Map<String, String>): Int? {
        val path = Regex("\"player_url\":\"(.*?)\"").find(html)?.groupValues?.get(1)?.replace("\\/", "/") ?: return null
        val tcl = path.replace(Regex("/[^/]+\\.vflset/[^/]+\\.js$"), "/tv-player-ias-tcl.vflset/tv-player-ias-tcl.js")
        val sts = Regex("signatureTimestamp:(\\d+)")
        for (candidate in listOf(tcl, path)) {
            val js = runCatching { Http.get("https://www.youtube.com$candidate", ua) }.getOrNull() ?: continue
            val v = sts.find(js)?.groupValues?.get(1) ?: continue
            // The TV client needs the 8-digit form. The regular player has the 5-digit form.
            return (if (v.length == 5) v + "001" else v).toIntOrNull()
        }
        return null
    }
}
