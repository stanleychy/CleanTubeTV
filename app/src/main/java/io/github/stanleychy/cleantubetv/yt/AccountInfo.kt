package io.github.stanleychy.cleantubetv.yt

import org.json.JSONObject

/** One entry of account/accounts_list. [byline] is usually the e-mail address. */
data class AccountInfo(val name: String, val byline: String?, val handle: String?, val photoUrl: String?) {
    companion object {
        fun parse(r: JSONObject): AccountInfo? {
            val items = ArrayList<JSONObject>()
            walk(r, { key, o -> if (key == "accountItem") { items += o; false } else true })
            val item = items.firstOrNull { it.optBoolean("isSelected") } ?: items.firstOrNull() ?: return null
            val photo = item.obj("accountPhoto")?.optJSONArray("thumbnails")?.objects()?.lastOrNull()?.optString("url")
            return AccountInfo(
                name = item.optJSONObject("accountName").text() ?: return null,
                byline = item.optJSONObject("accountByline").text(),
                handle = item.optJSONObject("channelHandle").text(),
                photoUrl = photo?.let { if (it.startsWith("//")) "https:$it" else it },
            )
        }
    }
}
