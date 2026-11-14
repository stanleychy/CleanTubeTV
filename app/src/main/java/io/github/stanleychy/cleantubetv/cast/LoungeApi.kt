package io.github.stanleychy.cleantubetv.cast

import io.github.stanleychy.cleantubetv.net.Http
import io.github.stanleychy.cleantubetv.yt.Clients
import io.github.stanleychy.cleantubetv.yt.Scraped
import org.json.JSONObject
import java.io.IOException
import java.io.StringReader
import java.net.URLEncoder
import java.security.SecureRandom

/**
 * Requests to the YouTube Lounge API (https://www.youtube.com/api/lounge/). The official YouTube app on a
 * phone uses this API to control a TV screen after "Link with TV code".
 *
 * Account safety:
 * - All Lounge requests are signed out. This object has no access to the OAuth token, and it sends
 *   no visitor ID and no cookie. YouTube knows the screen only by its screen ID and device ID.
 * - The lounge token and the screen ID go in URLs and bodies. Thus an error text from this object never
 *   contains a URL, and callers must not log these values.
 * - Each value from a reply is checked with [Scraped] before the app uses it.
 */
object LoungeApi {
    /** The same User-Agent as the other TV requests. No token, no visitor ID, no cookie. */
    private val HEADERS = mapOf("User-Agent" to Clients.TV_UA)

    private const val BASE = "https://www.youtube.com/api/lounge"

    /** The name that the phone shows for this screen. It does not name the device. */
    private const val SCREEN_NAME = "CleanTube TV"

    /** The "app" and "theme" values of the YouTube TV web app. The server replied with clientName "tvhtml5". */
    private const val SCREEN_APP = "lb-v4"
    private const val THEME = "cl"

    /**
     * The server sends a "noop" message when there is no command. On 2026-11-13 two came in 40 seconds.
     * A longer silence than this timeout means that the connection is dead.
     */
    private const val POLL_READ_TIMEOUT_MS = 90_000

    private val random = SecureRandom()

    /** The IDs of one open session. [first] has the messages that came with them. */
    class Channel(val sid: String, val gsessionId: String, val first: List<LoungeMessage>)

    /** Asks for a new screen ID. The reply is the ID as plain text. */
    fun generateScreenId(): String {
        val r = Http.request("$BASE/pairing/generate_screen_id", headers = HEADERS)
        if (!r.ok) throw failed("screen ID", r.code)
        return Scraped.screenId(r.body.trim()) ?: throw IOException("Lounge screen ID has an unexpected format")
    }

    /** Gets the lounge token of a screen. The token is the key to the session of that screen. */
    fun loungeToken(screenId: String): String {
        val r = post("$BASE/pairing/get_lounge_token_batch", form("screen_ids" to screenId))
        if (!r.ok) throw failed("lounge token", r.code)
        val screen = runCatching { JSONObject(r.body).getJSONArray("screens").getJSONObject(0) }.getOrNull()
        return Scraped.loungeToken(screen?.optString("loungeToken"))
            ?: throw IOException("Lounge token has an unexpected format")
    }

    /**
     * Gets a pairing code for "Link with TV code". The reply is the code as plain text (12 digits).
     * A code is valid for a short time. Other Lounge clients get a new code after 5 minutes.
     */
    fun pairingCode(screen: LoungeScreen, token: String): String {
        val body = form(
            "access_type" to "permanent",
            "app" to SCREEN_APP,
            "lounge_token" to token,
            "screen_id" to screen.screenId,
            "screen_name" to SCREEN_NAME,
            "device_id" to screen.deviceId,
        )
        val r = post("$BASE/pairing/get_pairing_code?ctx=pair", body)
        if (!r.ok) throw failed("pairing code", r.code)
        return Scraped.pairingCode(r.body.trim()) ?: throw IOException("Lounge pairing code has an unexpected format")
    }

    /** Opens a session for the screen. The first messages are "c" with the SID and "S" with the gsessionid. */
    fun bind(screen: LoungeScreen, token: String, rid: Int): Channel {
        val url = bindUrl(screen, token, "RID" to rid.toString(), "CVER" to "1")
        val r = post(url, form("count" to "0"))
        if (!r.ok) throw failed("bind", r.code)
        val messages = LoungeChunks.all(StringReader(r.body))
        val sid = Scraped.loungeSessionId(messages.firstOrNull { it.name == "c" }?.text)
        val gsessionId = Scraped.loungeSessionId(messages.firstOrNull { it.name == "S" }?.text)
        if (sid == null || gsessionId == null) throw IOException("Lounge session IDs not found or not valid")
        return Channel(sid, gsessionId, messages)
    }

    /**
     * Opens the long poll. The server sends commands on this reply as chunks (see [LoungeChunks]).
     * [aid] is the ID of the last message that the screen got.
     */
    fun openPoll(screen: LoungeScreen, token: String, channel: Channel, aid: Int): Http.Stream {
        val url = bindUrl(
            screen, token,
            "SID" to channel.sid, "gsessionid" to channel.gsessionId,
            "RID" to "rpc", "AID" to aid.toString(), "CI" to "0", "TYPE" to "xmlhttp",
        )
        // No compression: each chunk must arrive at once, not when a compression block is full.
        return Http.openStream(url, HEADERS + ("Accept-Encoding" to "identity"), readTimeoutMs = POLL_READ_TIMEOUT_MS)
    }

    /**
     * Sends events (player state) to the phone. [rid] counts the requests of the session. [ofs] is the number
     * of events that the session sent before this request. Returns the HTTP status.
     */
    fun send(screen: LoungeScreen, token: String, channel: Channel, rid: Int, aid: Int, ofs: Int, events: List<LoungeEvent>): Int {
        val url = bindUrl(
            screen, token,
            "SID" to channel.sid, "gsessionid" to channel.gsessionId,
            "RID" to rid.toString(), "AID" to aid.toString(),
        )
        val fields = ArrayList<Pair<String, String>>()
        fields += "count" to events.size.toString()
        fields += "ofs" to ofs.toString()
        events.forEachIndexed { i, e ->
            fields += "req${i}__sc" to e.name
            e.values.forEach { (k, v) -> fields += "req${i}_$k" to v }
        }
        return post(url, form(*fields.toTypedArray())).code
    }

    /** A new request counter for a session. The web client starts with a random 5-digit number. */
    fun newRid(): Int = 10_000 + random.nextInt(80_000)

    /** The query values that each "bind" request has, then [extra]. */
    private fun bindUrl(screen: LoungeScreen, token: String, vararg extra: Pair<String, String>): String {
        val common = arrayOf(
            "device" to "LOUNGE_SCREEN",
            "id" to screen.deviceId,
            "name" to SCREEN_NAME,
            "app" to SCREEN_APP,
            "theme" to THEME,
            "mdxVersion" to "2",
            "loungeIdToken" to token,
            "VER" to "8",
            "v" to "2",
            "t" to "1",
            // A random value, so that no cache answers the request.
            "zx" to randomText(12),
        )
        return "$BASE/bc/bind?" + form(*common, *extra)
    }

    private fun post(url: String, body: String) = Http.request(
        url,
        method = "POST",
        headers = HEADERS + ("Content-Type" to "application/x-www-form-urlencoded"),
        body = body,
    )

    /** Percent-encodes names and values, so that no value can add a parameter. */
    private fun form(vararg fields: Pair<String, String>): String =
        fields.joinToString("&") { (k, v) -> URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8") }

    private fun failed(what: String, code: Int) = LoungeHttpException(what, code)

    private fun randomText(length: Int): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
    }
}

/**
 * A Lounge request got an HTTP error status. The text has only the request name and the status.
 * It must not contain the URL: the URL has the lounge token.
 */
class LoungeHttpException(val what: String, val code: Int) : IOException("Lounge $what request failed (HTTP $code)")
