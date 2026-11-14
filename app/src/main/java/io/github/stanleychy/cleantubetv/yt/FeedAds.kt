package io.github.stanleychy.cleantubetv.yt

import org.json.JSONObject

/**
 * The rules that keep ads out of the feeds. All rules are in this file.
 *
 * The rules use only the structure of the reply (JSON keys). They do not use text such as
 * "Sponsored", because YouTube translates that text to the language of the device.
 *
 * YouTube puts an ad in the same list as the videos. The ad is a normal `tileRenderer`
 * in a wrapper:
 * adSlotRenderer > fulfillmentContent > fulfilledLayout > inFeedAdLayoutRenderer > renderingContent > tileRenderer
 * The ad tile has no `contentType`, and its `contentId` is not a video ID.
 * (Seen in a signed-out TVHTML5 reply on 2026-11-13.)
 *
 * A video with a paid promotion in it is not an ad for these rules: the feed does not mark its tile.
 */
internal object FeedAds {
    /**
     * Ad containers. The parser does not go into them, thus nothing in them becomes a card.
     * To block a new container, add its key here.
     */
    val CONTAINERS = setOf(
        "adSlotRenderer", // An ad slot in the section list or in a shelf.
        "inFeedAdLayoutRenderer", // The ad in an ad slot. Blocked also for a reply that has no slot wrapper.
        "tvMastheadRenderer", // The large ad at the top of Home.
    )

    /**
     * Ad markers. A tile that has one of these keys at any depth is an ad.
     * This is the second barrier, for an ad tile that is not in a known container.
     * To block a new marker, add its key here.
     */
    val MARKERS = setOf(
        "adBadgeViewModel", // The "Sponsored" badge.
        "adPingingEndpoint", // The report to the ad server when the tile shows.
        "adInfoDialogChoiceEndpoint", // The "Why this ad?" menu.
    )

    /** Key names that can show a new ad type. Used only for the debug report. */
    private val SUSPECT = Regex("^ad[A-Z]|[Pp]romot|[Ss]ponsor|[Mm]asthead")

    /** An enum value of YouTube, for example TILE_STYLE_YTLR_DEFAULT. The debug report shows no other values. */
    private val ENUM = Regex("[A-Z0-9_]{1,60}")

    /** True when [tile] has an ad marker. */
    fun isAd(tile: JSONObject): Boolean {
        var found = false
        walk(tile, { key, _ ->
            if (key in MARKERS) found = true
            !found
        })
        return found
    }

    /**
     * Describes the structure of a browse reply for the log of a debug build: how many times each
     * renderer key and each type of tile occurs, and which of them the parser drops.
     * The text has key names and enum values only. It has no titles, IDs, URLs or tokens.
     */
    fun describe(response: JSONObject): String {
        val counts = sortedMapOf<String, Int>()
        fun count(what: String) {
            counts[what] = (counts[what] ?: 0) + 1
        }
        walk(response, { key, obj ->
            when {
                key == null -> true
                key in CONTAINERS -> {
                    count("$key=DROP")
                    false
                }
                key == "tileRenderer" -> {
                    count(describeTile(obj))
                    false
                }
                else -> {
                    if (key.endsWith("Renderer") || key.endsWith("ViewModel") || SUSPECT.containsMatchIn(key)) count(key)
                    true
                }
            }
        })
        return counts.entries.joinToString(" ") { "${it.key}:${it.value}" }
    }

    /** For example `tile[TILE_CONTENT_TYPE_VIDEO,TILE_STYLE_YTLR_DEFAULT,watchEndpoint]`. */
    private fun describeTile(tile: JSONObject): String {
        val commands = tile.optJSONObject("onSelectCommand")?.keys()?.asSequence().orEmpty()
            .filter { it != "clickTrackingParams" }.sorted().joinToString("+")
        val suspects = sortedSetOf<String>()
        walk(tile, { key, _ ->
            if (key != null && SUSPECT.containsMatchIn(key)) suspects += key
            true
        })
        return "tile[${enum(tile, "contentType")},${enum(tile, "style")},$commands]" +
            (if (suspects.isEmpty()) "" else suspects.joinToString(",", "{", "}")) +
            (if (isAd(tile)) "=DROP" else "")
    }

    private fun enum(obj: JSONObject, name: String): String {
        val value = obj.optString(name)
        return when {
            value.isEmpty() -> "-"
            ENUM.matches(value) -> value
            else -> "?"
        }
    }
}

/** Does the same as [walk], but does not go into ad containers. */
internal fun walkWithoutAds(node: Any?, visit: (key: String?, obj: JSONObject) -> Boolean) =
    walk(node, { key, obj -> key !in FeedAds.CONTAINERS && visit(key, obj) })
