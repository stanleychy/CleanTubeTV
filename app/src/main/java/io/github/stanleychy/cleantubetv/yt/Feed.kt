package io.github.stanleychy.cleantubetv.yt

import org.json.JSONObject

data class Video(
    val id: String,
    val title: String,
    val channel: String?,
    val meta: String?,
    val thumbnail: String?,
    val duration: String?,
    val isLive: Boolean,
)

data class Shelf(val title: String?, val videos: List<Video>)

data class FeedPage(val shelves: List<Shelf>, val continuation: String?)

/**
 * Parses TV browse responses. The TV layout is:
 * tvBrowseRenderer > tvSurfaceContentRenderer > sectionListRenderer > shelfRenderer > horizontalListRenderer > tileRenderer
 * or a gridRenderer of tiles. The parser does not depend on the exact path: it finds shelves
 * and tiles anywhere in the response, so small layout changes do not break it.
 * Ads are never parsed: only video tiles become items.
 */
object FeedParser {
    fun parse(response: JSONObject): FeedPage {
        val shelves = mutableListOf<Shelf>()
        val seen = HashSet<String>()

        walk(response, { key, obj ->
            if (key == "shelfRenderer") {
                val title = obj.obj("headerRenderer", "shelfHeaderRenderer", "title").text()
                    ?: obj.optJSONObject("title").text()
                val videos = collectTiles(obj, seen)
                if (videos.isNotEmpty()) shelves += Shelf(title, videos)
                false // Tiles of this shelf are done.
            } else {
                true
            }
        })

        // Grids (Subscriptions) and any tiles outside shelves.
        val loose = collectTiles(response, seen)
        if (loose.isNotEmpty()) shelves += Shelf(null, loose)

        return FeedPage(shelves, findContinuation(response))
    }

    private fun collectTiles(root: JSONObject, seen: MutableSet<String>): List<Video> {
        val out = mutableListOf<Video>()
        walk(root, { key, obj ->
            when (key) {
                "tileRenderer" -> {
                    parseTile(obj)?.let { if (seen.add(it.id)) out += it }
                    false
                }
                "videoRenderer", "gridVideoRenderer", "compactVideoRenderer" -> {
                    parseVideoRenderer(obj)?.let { if (seen.add(it.id)) out += it }
                    false
                }
                else -> true
            }
        })
        return out
    }

    private fun parseTile(t: JSONObject): Video? {
        val type = t.optString("contentType")
        if (type.isNotEmpty() && type != "TILE_CONTENT_TYPE_VIDEO") return null
        val id = t.obj("onSelectCommand", "watchEndpoint")?.optString("videoId")?.ifEmpty { null }
            ?: t.optString("contentId").ifEmpty { null }
            ?: return null
        val meta = t.obj("metadata", "tileMetadataRenderer")
        val lines = meta?.optJSONArray("lines")?.objects().orEmpty().map { line ->
            line.obj("lineRenderer")?.optJSONArray("items")?.objects().orEmpty()
                .mapNotNull { it.obj("lineItemRenderer", "text").text() }
                .joinToString(" ")
        }.filter { it.isNotBlank() }
        val header = t.obj("header", "tileHeaderRenderer")
        val thumbs = header?.obj("thumbnail")?.optJSONArray("thumbnails")?.objects().orEmpty()
        var duration: String? = null
        var live = false
        header?.optJSONArray("thumbnailOverlays")?.objects()?.forEach { o ->
            o.obj("thumbnailOverlayTimeStatusRenderer")?.let {
                duration = it.optJSONObject("text").text()
                if (it.optString("style") == "LIVE") live = true
            }
        }
        return Video(
            id = id,
            title = meta?.optJSONObject("title").text() ?: return null,
            channel = lines.getOrNull(0),
            meta = lines.drop(1).joinToString(" · ").ifEmpty { null },
            thumbnail = pickThumb(thumbs.map { it.optString("url") to it.optInt("width") }),
            duration = duration,
            isLive = live,
        )
    }

    private fun parseVideoRenderer(v: JSONObject): Video? {
        val id = v.optString("videoId").ifEmpty { return null }
        val thumbs = v.obj("thumbnail")?.optJSONArray("thumbnails")?.objects().orEmpty()
        return Video(
            id = id,
            title = v.optJSONObject("title").text() ?: return null,
            channel = (v.optJSONObject("ownerText") ?: v.optJSONObject("shortBylineText")).text(),
            meta = listOfNotNull(v.optJSONObject("viewCountText").text(), v.optJSONObject("publishedTimeText").text())
                .joinToString(" · ").ifEmpty { null },
            thumbnail = pickThumb(thumbs.map { it.optString("url") to it.optInt("width") }),
            duration = v.optJSONObject("lengthText").text(),
            isLive = v.optJSONArray("badges")?.toString()?.contains("LIVE") == true,
        )
    }

    /** Prefer a thumbnail near 480 px wide: sharp on a 4-column TV grid, still small. */
    private fun pickThumb(list: List<Pair<String, Int>>): String? {
        val valid = list.filter { it.first.isNotEmpty() }
        val best = valid.filter { it.second in 1..640 }.maxByOrNull { it.second } ?: valid.lastOrNull()
        return best?.first?.let { if (it.startsWith("//")) "https:$it" else it }
    }

    private fun findContinuation(response: JSONObject): String? {
        // Page-level continuation of the section list or grid. Shelf (row) continuations are ignored.
        var token: String? = null
        walk(response, { key, obj ->
            when (key) {
                "horizontalListRenderer", "horizontalListContinuation", "shelfRenderer" -> false
                "nextContinuationData" -> {
                    if (token == null) token = obj.optString("continuation").ifEmpty { null }
                    false
                }
                else -> true
            }
        })
        return token
    }
}
