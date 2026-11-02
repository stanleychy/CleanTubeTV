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

    fun version(v: String?) = v?.takeIf { VERSION.matches(it) }

    fun visitor(v: String?) = v?.takeIf { VISITOR.matches(it) }

    fun clientId(v: String?) = v?.takeIf { CLIENT_ID.matches(it) }

    fun clientSecret(v: String?) = v?.takeIf { CLIENT_SECRET.matches(it) }

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
}
