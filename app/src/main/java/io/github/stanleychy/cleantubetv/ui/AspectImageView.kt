package io.github.stanleychy.cleantubetv.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView

/** ImageView with a fixed 16:9 aspect ratio. */
class AspectImageView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ImageView(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, w * 9 / 16)
    }
}
