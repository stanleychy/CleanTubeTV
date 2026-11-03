package io.github.stanleychy.cleantubetv.net

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Minimal HTTP client on top of the platform HttpURLConnection. No third-party library. */
object Http {
    /** User-Agent for hosts that are not YouTube (for example SponsorBlock). It does not name the device. */
    const val APP_USER_AGENT = "CleanTubeTV"

    /** The largest reply body that the app reads. The TV page and its scripts are a few megabytes. */
    const val MAX_BODY_BYTES = 16 * 1024 * 1024

    class Response(val code: Int, val body: String) {
        val ok get() = code in 200..299
    }

    /**
     * Sends one request and reads at most [MAX_BODY_BYTES] of the reply.
     * Redirects are not followed unless [followRedirects] is set: a 3xx reply comes back as is.
     * Thus a request with the token or a visitor ID never goes to a different URL than the one in the code.
     */
    fun request(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        followRedirects: Boolean = false,
        connectTimeoutMs: Int = 15_000,
        readTimeoutMs: Int = 30_000,
    ): Response {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = connectTimeoutMs
            conn.readTimeout = readTimeoutMs
            conn.instanceFollowRedirects = followRedirects
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..399) conn.inputStream else conn.errorStream
            val text = stream?.use { readLimited(it, MAX_BODY_BYTES).toString(Charsets.UTF_8) } ?: ""
            return Response(code, text)
        } finally {
            conn.disconnect()
        }
    }

    /** A GET with no account data (pages and scripts). It follows redirects on the same scheme. */
    fun get(url: String, headers: Map<String, String> = emptyMap()): String {
        val r = request(url, headers = headers, followRedirects = true)
        if (!r.ok) throw IOException("HTTP ${r.code} for GET ${url.substringBefore('?')}")
        return r.body
    }

    /** Reads the stream to the end. Throws when it has more than [max] bytes, so a large reply cannot use all the memory. */
    fun readLimited(stream: InputStream, max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = stream.read(buf)
            if (n < 0) return out.toByteArray()
            if (out.size() + n > max) throw IOException("reply is larger than $max bytes")
            out.write(buf, 0, n)
        }
    }
}
