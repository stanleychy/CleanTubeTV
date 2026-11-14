package io.github.stanleychy.cleantubetv.yt

import org.json.JSONObject

/**
 * Parses TV search replies. The layout of a signed-out reply on 2026-11-13 was:
 * sectionListRenderer > shelfRenderer > horizontalListRenderer > lockupViewModel
 * and the same below continuationContents > sectionListContinuation for the next pages.
 * The parser does not depend on the exact path: it finds lockups anywhere in the reply.
 * Only videos become items. Shorts, channels and playlists are left out, because the app plays only videos.
 */
object SearchParser {
    private const val TYPE_VIDEO = "LOCKUP_CONTENT_TYPE_VIDEO"
    private const val BADGE_LIVE = "THUMBNAIL_OVERLAY_BADGE_STYLE_LIVE"

    fun parse(response: JSONObject): FeedPage {
        val seen = HashSet<String>()
        val videos = mutableListOf<Video>()
        walk(response, { key, obj ->
            if (key != "lockupViewModel") return@walk true
            parseLockup(obj)?.let { if (seen.add(it.id)) videos += it }
            false
        })
        // A reply can use the tile layout of the feeds. FeedParser reads those tiles.
        // An ad tile can have an ad layout ID (16 characters) in the place of the video ID. The ID check drops it.
        FeedParser.parse(response).shelves.flatMap { it.videos }
            .forEach { if (Scraped.videoId(it.id) != null && seen.add(it.id)) videos += it }
        val shelves = if (videos.isEmpty()) emptyList() else listOf(Shelf(null, videos))
        return FeedPage(shelves, findContinuation(response))
    }

    private fun parseLockup(lockup: JSONObject): Video? {
        if (lockup.optString("contentType") != TYPE_VIDEO) return null
        val id = Scraped.videoId(lockup.optString("contentId")) ?: return null
        val meta = lockup.obj("metadata", "lockupMetadataViewModel") ?: return null
        val rows = meta.obj("metadata", "contentMetadataViewModel")?.optJSONArray("metadataRows")?.objects().orEmpty()
            .map { row -> row.optJSONArray("metadataParts")?.objects().orEmpty().mapNotNull { it.optJSONObject("text").text() } }
            .filter { it.isNotEmpty() }
        val image = lockup.obj("contentImage", "thumbnailViewModel")
        val badges = badges(image)
        return Video(
            id = id,
            title = meta.optJSONObject("title").text() ?: return null,
            channel = rows.getOrNull(0)?.joinToString(" · "),
            meta = rows.drop(1).flatten().joinToString(" · ").ifEmpty { null },
            thumbnail = image?.obj("image")?.optJSONArray("sources")?.objects().orEmpty()
                .firstNotNullOfOrNull { Scraped.thumbnailUrl(it.optString("url")) },
            duration = badges.firstNotNullOfOrNull { it.optString("text").ifEmpty { null } },
            isLive = badges.any { it.optString("badgeStyle") == BADGE_LIVE },
        )
    }

    /** The badges on the thumbnail: the duration (for example "10:35") or the live mark. */
    private fun badges(image: JSONObject?): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        walk(image?.optJSONArray("overlays"), { key, obj ->
            if (key == "thumbnailBadgeViewModel") out += obj
            true
        })
        return out
    }

    /**
     * The token for the next page. It is in the horizontal list of the first shelf (the results),
     * so [FeedParser], which ignores the continuation of a shelf row, cannot find it.
     */
    private fun findContinuation(response: JSONObject): String? {
        var token: String? = null
        walk(response, { key, obj ->
            if (token != null) return@walk false
            if (key == "nextContinuationData") token = Scraped.continuation(obj.optString("continuation"))
            true
        })
        return token
    }
}
