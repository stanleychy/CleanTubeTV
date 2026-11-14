package io.github.stanleychy.cleantubetv.ui

import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import android.widget.TextView

/**
 * Scrolls a text that is cut, while the card of the text has the focus.
 *
 * The Android marquee has a fixed speed that an app cannot change. Thus this class moves the text itself:
 * it shows the text on one line and changes the horizontal scroll position at [SPEED_DP_PER_SECOND].
 * The text stays at its start, moves to its end, stays there, and then starts again.
 * The TextView does not need the focus, thus the focus stays on the card. A text that is fully visible does not change.
 *
 * [start] locks the height of the view before it changes the view to one line.
 * Thus a title of two lines keeps its height, and the cards in the grid do not move.
 */
internal class FocusMarquee(private val view: TextView) {
    /** The line limit from the layout file. [stop] sets it again. */
    private val maxLines = view.maxLines

    /** The ellipsis mode from the layout file. [stop] sets it again. */
    private val ellipsize = view.ellipsize

    /** Not null while the text scrolls. */
    private var animator: ValueAnimator? = null

    /** Call this when the card gets or loses the focus. */
    fun set(focused: Boolean) = if (focused) start() else stop()

    private fun start() {
        if (animator != null || !isCut()) return
        val distance = hiddenWidth()
        if (distance <= 0) return
        view.minimumHeight = view.height
        view.setSingleLine(true)
        // No ellipsis: the full text is in the line, and the scroll position selects the visible part.
        view.ellipsize = null
        view.setHorizontallyScrolling(true)
        animator = scroll(distance).also { it.start() }
    }

    /** Puts the view back to the state of the layout file. Call this also before the view gets a new text. */
    fun stop() {
        val running = animator ?: return
        animator = null
        running.cancel()
        view.scrollTo(0, 0)
        view.setHorizontallyScrolling(false)
        view.setSingleLine(false)
        view.maxLines = maxLines
        view.ellipsize = ellipsize
        view.minimumHeight = 0
    }

    /**
     * One cycle is: hold at the start, move [distance] pixels, hold at the end. The cycle repeats until [stop].
     * The animated value is the time in the cycle, thus the speed does not depend on the length of the text.
     */
    private fun scroll(distance: Int): ValueAnimator {
        val pixelsPerMs = SPEED_DP_PER_SECOND * view.resources.displayMetrics.density / 1000f
        val moveMs = distance / pixelsPerMs
        return ValueAnimator.ofFloat(0f, HOLD_MS + moveMs + HOLD_MS).apply {
            duration = (HOLD_MS + moveMs + HOLD_MS).toLong()
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                val movedMs = (it.animatedValue as Float - HOLD_MS).coerceIn(0f, moveMs)
                view.scrollTo((movedMs * pixelsPerMs).toInt(), 0)
            }
        }
    }

    /** The width in pixels of the part of the text that one line cannot show. */
    private fun hiddenWidth(): Int {
        val lineWidth = view.width - view.compoundPaddingLeft - view.compoundPaddingRight
        return (view.paint.measureText(view.text.toString()) - lineWidth).toInt()
    }

    /** True when the last line ends with an ellipsis. False before the first layout. */
    private fun isCut(): Boolean {
        val layout = view.layout ?: return false
        val lastLine = layout.lineCount - 1
        return lastLine >= 0 && layout.getEllipsisCount(lastLine) > 0
    }

    private companion object {
        /** 1.25 times the speed of the Android marquee, which is 30 dp in one second. */
        const val SPEED_DP_PER_SECOND = 37.5f

        /** The time that the text stays at its start and at its end, so that the user can read the first and last words. */
        const val HOLD_MS = 1200f
    }
}
