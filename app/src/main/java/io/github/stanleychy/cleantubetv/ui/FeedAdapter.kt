package io.github.stanleychy.cleantubetv.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.github.stanleychy.cleantubetv.R
import io.github.stanleychy.cleantubetv.yt.Video

sealed class FeedItem {
    data class Header(val title: String) : FeedItem()
    data class Card(val video: Video) : FeedItem()
}

class FeedAdapter(
    private val onClick: (Video) -> Unit,
    private val onNearEnd: () -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val items = mutableListOf<FeedItem>()

    fun set(newItems: List<FeedItem>) {
        items.clear(); items.addAll(newItems); notifyDataSetChanged()
    }

    fun append(more: List<FeedItem>) {
        val start = items.size
        items.addAll(more); notifyItemRangeInserted(start, more.size)
    }

    fun isHeader(pos: Int) = items.getOrNull(pos) is FeedItem.Header

    override fun getItemCount() = items.size
    override fun getItemViewType(position: Int) = if (items[position] is FeedItem.Header) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (viewType == 0) object : RecyclerView.ViewHolder(inf.inflate(R.layout.item_header, parent, false)) {}
        else CardHolder(inf.inflate(R.layout.item_video, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (position >= items.size - 8) onNearEnd()
        when (val item = items[position]) {
            is FeedItem.Header -> (holder.itemView as TextView).text = item.title
            is FeedItem.Card -> (holder as CardHolder).bind(item.video)
        }
    }

    private inner class CardHolder(v: View) : RecyclerView.ViewHolder(v) {
        private val thumb: ImageView = v.findViewById(R.id.thumb)
        private val title: TextView = v.findViewById(R.id.title)
        private val sub: TextView = v.findViewById(R.id.subtitle)
        private val duration: TextView = v.findViewById(R.id.duration)

        fun bind(video: Video) {
            title.text = video.title
            sub.text = listOfNotNull(video.channel, video.meta).joinToString(" · ")
            val badge = if (video.isLive) "LIVE" else video.duration
            duration.text = badge
            duration.visibility = if (badge.isNullOrEmpty()) View.GONE else View.VISIBLE
            ImageLoader.load(thumb, video.thumbnail)
            itemView.setOnClickListener { onClick(video) }
            itemView.setOnFocusChangeListener { view, has ->
                view.animate().scaleX(if (has) 1.06f else 1f).scaleY(if (has) 1.06f else 1f).setDuration(120).start()
                view.elevation = if (has) 8f else 0f
            }
        }
    }
}
