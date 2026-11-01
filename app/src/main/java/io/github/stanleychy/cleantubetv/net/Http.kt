package io.github.stanleychy.cleantubetv.net

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Minimal HTTP client on top of the platform HttpURLConnection. No third-party library. */
object Http {
    class Response(val code: Int, val body: String) {
        val ok get() = code in 200..299
    }

    fun request(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
    ): Response {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..399) conn.inputStream else conn.errorStream
            val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            return Response(code, text)
        } finally {
            conn.disconnect()
        }
    }

    fun get(url: String, headers: Map<String, String> = emptyMap()): String {
        val r = request(url, headers = headers)
        if (!r.ok) throw IOException("HTTP ${r.code} for GET ${url.substringBefore('?')}")
        return r.body
    }
}
