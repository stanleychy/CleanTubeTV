package io.github.stanleychy.cleantubetv.yt

import org.json.JSONArray
import org.json.JSONObject

/** Small helpers to read InnerTube JSON. */
internal fun JSONObject.obj(vararg path: String): JSONObject? {
    var cur: JSONObject? = this
    for (p in path) cur = cur?.optJSONObject(p)
    return cur
}

internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

/** Text from {"simpleText": ...} or {"runs": [{"text": ...}]} or {"content": ...}. */
internal fun JSONObject?.text(): String? {
    if (this == null) return null
    optString("simpleText").takeIf { it.isNotEmpty() }?.let { return it }
    optString("content").takeIf { it.isNotEmpty() }?.let { return it }
    val runs = optJSONArray("runs") ?: return null
    return runs.objects().joinToString("") { it.optString("text") }.ifEmpty { null }
}

/** Depth-first walk over every JSONObject in a tree. */
internal fun walk(node: Any?, visit: (key: String?, obj: JSONObject) -> Boolean, key: String? = null) {
    when (node) {
        is JSONObject -> {
            if (!visit(key, node)) return
            val keys = node.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                walk(node.opt(k), visit, k)
            }
        }
        is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i), visit, key)
    }
}
