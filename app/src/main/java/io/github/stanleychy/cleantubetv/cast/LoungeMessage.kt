package io.github.stanleychy.cleantubetv.cast

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.Reader

/**
 * One message from the Lounge server. On the wire it is `[id, [name, argument, ...]]`, for example
 * `[7,["seekTo",{"newTime":"42"}]]`. The server counts [id] up. The screen sends the last ID back as "AID",
 * so the server knows which messages arrived.
 */
class LoungeMessage(val id: Int, private val content: JSONArray) {
    val name: String get() = content.optString(0)

    /** The JSON object after the name, or an empty object. Most commands put their values here. */
    val params: JSONObject get() = content.optJSONObject(1) ?: JSONObject()

    /** The plain text after the name. Only the session messages "c" (SID) and "S" (gsessionid) use it. */
    val text: String get() = content.optString(1)
}

/** One message from the screen to the server: a name and its values. All values go as text in a form body. */
class LoungeEvent(val name: String, val values: Map<String, String> = emptyMap())

/**
 * Reads the reply format of the Lounge "bind" endpoint. The reply is a sequence of chunks.
 * Each chunk is a line with a length, then a JSON array of messages with that number of characters:
 *
 *     16
 *     [[5,["noop"]]
 *     ]
 *
 * A long-poll reply sends chunks for minutes, so this class reads them one at a time.
 */
object LoungeChunks {
    /** No real chunk is near this size. A larger length means that the reply is not a Lounge reply. */
    private const val MAX_CHUNK_CHARS = 1024 * 1024

    /**
     * Reads the next chunk. Returns null at the end of the reply.
     * Throws IOException when the data does not have the chunk format.
     */
    fun next(reader: Reader): List<LoungeMessage>? {
        val length = readLength(reader) ?: return null
        if (length !in 1..MAX_CHUNK_CHARS) throw IOException("Lounge chunk length is not valid")
        val chars = CharArray(length)
        var done = 0
        while (done < length) {
            val n = reader.read(chars, done, length - done)
            if (n < 0) throw IOException("Lounge reply ended in a chunk")
            done += n
        }
        return parse(String(chars))
    }

    /** All chunks of a reply that is complete (the reply to the first "bind" request). */
    fun all(reader: Reader): List<LoungeMessage> {
        val out = ArrayList<LoungeMessage>()
        while (true) out += next(reader) ?: return out
    }

    /** The decimal number on the next line that is not empty, or null at the end of the reply. */
    private fun readLength(reader: Reader): Int? {
        val digits = StringBuilder()
        while (true) {
            val c = reader.read()
            if (c < 0) break
            if (c == '\n'.code) {
                if (digits.isNotEmpty()) break
                continue
            }
            if (c == '\r'.code) continue
            if (c !in '0'.code..'9'.code || digits.length > 8) throw IOException("Lounge chunk has no length line")
            digits.append(c.toChar())
        }
        return digits.toString().toIntOrNull()
    }

    private fun parse(chunk: String): List<LoungeMessage> {
        val array = runCatching { JSONArray(chunk) }.getOrElse { throw IOException("Lounge chunk is not JSON") }
        return (0 until array.length()).mapNotNull { i ->
            val pair = array.optJSONArray(i) ?: return@mapNotNull null
            val content = pair.optJSONArray(1) ?: return@mapNotNull null
            LoungeMessage(pair.optInt(0, -1), content)
        }
    }
}
