package io.github.stanleychy.cleantubetv.yt

import java.net.URI

/**
 * Checks for values that come from YouTube web pages and replies. These values are external data.
 * The app puts them in HTTP headers, JSON bodies and URLs, so each value must have the expected format.
 * Each function returns the value when it is valid, or null when it is not valid.
 * Use these checks on scraped values and on values read back from SharedPreferences.
 */
object Scraped {
    /**
     * Client version, for example 7.20261027.15.00. Only ASCII digits and dots, 3 to 5 parts,
     * at most 8 digits in each part. The format can change (major version, part count, part length),
     * but no valid value can contain a character that changes a header, JSON or URL.
     */
    private val VERSION = Regex("^\\d{1,8}(\\.\\d{1,8}){2,4}$")

    /**
     * Visitor ID: URL-safe base64, sometimes percent-encoded (for example "%3D").
     * Live values on 2026-11-01 had 452 (TV page) and 456 (VISIONOS reply) characters.
     */
    private val VISITOR = Regex("^[A-Za-z0-9_%=-]{16,1024}$")

    private val CLIENT_ID = Regex("^[A-Za-z0-9-]{1,128}\\.apps\\.googleusercontent\\.com$")
    private val CLIENT_SECRET = Regex("^[A-Za-z0-9_-]{8,128}$")

    /** The only host that the app accepts for scraped script URLs. */
    private const val HOST = "www.youtube.com"

    /**
     * The start of a URL that may get the watch-time pings with the token: https, and a host of s.youtube.com
     * (the TV app sends the pings there) or www.youtube.com. The "/" directly after the host leaves no space for
     * user info ("@"), a port or a different host. The query is not checked: YouTube can put any value in it.
     */
    private val TRACKING_URL = Regex("^https://(s|www)\\.youtube\\.com/")

    /** Video ID, for example aqz-KE-bpKQ: 11 characters of URL-safe base64. */
    private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")

    /**
     * Continuation token of a search reply: URL-safe base64, sometimes percent-encoded.
     * Live values on 2026-11-13 had 560 to 808 characters. The length increases with the query.
     */
    private val CONTINUATION = Regex("^[A-Za-z0-9_%=-]{1,8192}$")

    /** The start of a thumbnail URL: https and a host directly under ytimg.com, for example i.ytimg.com. */
    private val THUMBNAIL_URL = Regex("^https://[a-z0-9-]{1,32}\\.ytimg\\.com/")

    /** A playlist ID or a queue ID from a Lounge command. Live queue IDs on 2026-11-13 had 29 characters. */
    private val LIST_ID = Regex("^[A-Za-z0-9_-]{1,128}$")

    /** Lounge screen ID. The live value on 2026-11-13 had 26 characters (lowercase letters and digits). */
    private val SCREEN_ID = Regex("^[A-Za-z0-9_-]{16,64}$")

    /** Lounge token: URL-safe base64. The live value on 2026-11-13 had 119 characters. */
    private val LOUNGE_TOKEN = Regex("^[A-Za-z0-9_-]{32,1024}$")

    /** "Link with TV code" pairing code. The live value on 2026-11-13 had 12 digits. */
    private val PAIRING_CODE = Regex("^[0-9]{6,16}$")

    /** Lounge session IDs (SID and gsessionid). Live values on 2026-11-13 had 16 and 32 characters. */
    private val LOUNGE_SESSION_ID = Regex("^[A-Za-z0-9_-]{8,128}$")

    fun version(v: String?) = v?.takeIf { VERSION.matches(it) }

    fun videoId(v: String?) = v?.takeIf { VIDEO_ID.matches(it) }

    fun listId(v: String?) = v?.takeIf { LIST_ID.matches(it) }

    fun screenId(v: String?) = v?.takeIf { SCREEN_ID.matches(it) }

    fun loungeToken(v: String?) = v?.takeIf { LOUNGE_TOKEN.matches(it) }

    fun pairingCode(v: String?) = v?.takeIf { PAIRING_CODE.matches(it) }

    fun loungeSessionId(v: String?) = v?.takeIf { LOUNGE_SESSION_ID.matches(it) }

    fun visitor(v: String?) = v?.takeIf { VISITOR.matches(it) }

    fun clientId(v: String?) = v?.takeIf { CLIENT_ID.matches(it) }

    fun clientSecret(v: String?) = v?.takeIf { CLIENT_SECRET.matches(it) }

    fun continuation(v: String?) = v?.takeIf { CONTINUATION.matches(it) }

    /** A thumbnail URL from a search reply, or null when it does not start with [THUMBNAIL_URL]. */
    fun thumbnailUrl(url: String?) =
        url?.takeIf { THUMBNAIL_URL.containsMatchIn(it) && it.none { c -> c.isWhitespace() || c == '\\' } }

    /** The TV player uses the 8-digit form, for example 20753001. */
    fun signatureTimestamp(v: Int?) = v?.takeIf { it in 10_000_000..99_999_999 }

    /**
     * Makes an absolute https://www.youtube.com URL from a scraped path or URL.
     * Returns null for any other scheme or host, for user info ("@"), for a port, and for a URL that does not parse.
     * Example: "@evil.example/x" gives null, not "https://www.youtube.com@evil.example/x".
     */
    fun youtubeUrl(pathOrUrl: String?): String? {
        val s = pathOrUrl?.replace("\\/", "/") ?: return null
        val full = when {
            s.startsWith("https://") -> s
            s.startsWith("/") && !s.startsWith("//") -> "https://$HOST$s"
            else -> return null
        }
        val u = runCatching { URI(full) }.getOrNull() ?: return null
        val ok = u.scheme == "https" && u.host == HOST && u.rawUserInfo == null && u.port == -1 &&
            u.rawPath?.startsWith("/") == true
        return if (ok) full else null
    }

    /** A playbackTracking URL from a player reply, or null when it does not start with [TRACKING_URL]. */
    fun trackingUrl(url: String?): String? {
        val s = url?.replace("\\/", "/") ?: return null
        return s.takeIf { TRACKING_URL.containsMatchIn(it) && it.none { c -> c.isWhitespace() || c == '\\' } }
    }
}
