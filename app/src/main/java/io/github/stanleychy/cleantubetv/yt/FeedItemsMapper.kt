package io.github.stanleychy.cleantubetv.yt

import io.github.stanleychy.cleantubetv.ui.FeedItem

object FeedItemsMapper {
    fun toItems(page: FeedPage): List<FeedItem> = page.shelves.flatMap { shelf ->
        val header = shelf.title?.let { listOf(FeedItem.Header(it)) }.orEmpty()
        header + shelf.videos.map { FeedItem.Card(it) }
    }
}
