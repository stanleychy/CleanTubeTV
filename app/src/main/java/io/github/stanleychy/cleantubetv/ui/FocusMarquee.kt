package io.github.stanleychy.cleantubetv.ui

import android.text.TextUtils
import android.widget.TextView

/**
 * Scrolls a text that is cut, while the card of the text has the focus.
 *
 * The Android marquee needs one line and a selected TextView. The TextView does not need the focus,
 * thus the focus stays on the card. A text that is fully visible does not change.
 *
 * [start] locks the height of the view before it changes the view to one line.
 * Thus a title of two lines keeps its height, and the cards in the grid do not move.
 */
internal class FocusMarquee(private val view: TextView) {
    /** The line limit from the layout file. [stop] sets it again. */
    private val maxLines = view.maxLines

    /** Call this when the card gets or loses the focus. */
    fun set(focused: Boolean) = if (focused) start() else stop()

    private fun start() {
        if (!isCut()) return
        view.minimumHeight = view.height
        view.setSingleLine(true)
        view.ellipsize = TextUtils.TruncateAt.MARQUEE
        view.marqueeRepeatLimit = REPEAT_FOREVER
        view.isSelected = true
    }

    /** Puts the view back to the state of the layout file. Call this also before the view gets a new text. */
    fun stop() {
        if (!view.isSelected) return
        view.isSelected = false
        view.setSingleLine(false)
        view.maxLines = maxLines
        view.ellipsize = TextUtils.TruncateAt.END
        view.minimumHeight = 0
    }

    /** True when the last line ends with an ellipsis. False before the first layout. */
    private fun isCut(): Boolean {
        val layout = view.layout ?: return false
        val lastLine = layout.lineCount - 1
        return lastLine >= 0 && layout.getEllipsisCount(lastLine) > 0
    }

    private companion object {
        /** The marquee value for "no limit". The text scrolls until the focus goes away. */
        const val REPEAT_FOREVER = -1
    }
}
