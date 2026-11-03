package io.github.stanleychy.cleantubetv.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import io.github.stanleychy.cleantubetv.net.Http
import io.github.stanleychy.cleantubetv.yt.Clients
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Small thumbnail loader with a memory cache. Replaces Glide for this app's needs. */
object ImageLoader {
    /** The largest image file that the loader reads. A YouTube thumbnail is under 100 KB. */
    private const val MAX_IMAGE_BYTES = 2 * 1024 * 1024

    /** A decoded image is at most this wide and high. The 4-column grid shows a thumbnail at about 450 px. */
    private const val MAX_SIDE = 1024

    private val pool = Executors.newFixedThreadPool(4)
    private val main = Handler(Looper.getMainLooper())
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun load(view: ImageView, url: String?) {
        view.tag = url
        view.setImageDrawable(null)
        if (url == null) return
        cache.get(url)?.let { view.setImageBitmap(it); return }
        pool.execute {
            val bmp = runCatching {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 10_000
                c.readTimeout = 15_000
                // The TV app loads the same thumbnails with this User-Agent. The platform default names the device.
                c.setRequestProperty("User-Agent", Clients.TV_UA)
                val bytes = try { c.inputStream.use { Http.readLimited(it, MAX_IMAGE_BYTES) } } finally { c.disconnect() }
                decode(bytes)
            }.getOrNull() ?: return@execute
            cache.put(url, bmp)
            main.post { if (view.tag == url) view.setImageBitmap(bmp) }
        }
    }

    /** Decodes with a sample size, so that a large image cannot use all the memory. */
    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > MAX_SIDE || bounds.outHeight / sample > MAX_SIDE) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
