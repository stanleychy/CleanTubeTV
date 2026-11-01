package io.github.stanleychy.cleantubetv.yt

import android.content.Context
import io.github.stanleychy.cleantubetv.net.Http
import java.io.IOException

/**
 * Data scraped from the YouTube TV web app (https://www.youtube.com/tv):
 * the visitor ID and the OAuth client ID/secret that YouTube's own TV app uses.
 * YouTube only accepts TV OAuth tokens minted for this client.
 */
data class TvAppInfo(val visitorData: String, val clientId: String, val clientSecret: String)

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
            if (v != null && id != null && secret != null) {
                return TvAppInfo(v, id, secret).also { cached = it }
            }
        }
        val info = fetch()
        prefs.edit()
            .putLong("ts", System.currentTimeMillis())
            .putString("visitor", info.visitorData)
            .putString("id", info.clientId)
            .putString("secret", info.clientSecret)
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
        return TvAppInfo(visitor, m.groupValues[1], m.groupValues[2])
    }
}
