package ru.readysquad.kvest.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import ru.readysquad.kvest.R
import ru.readysquad.kvest.data.Http
import java.util.concurrent.Executors

object ImageLoader {

    private val cache = LruCache<String, Bitmap>(24)
    private val io = Executors.newFixedThreadPool(3)
    private val ui = Handler(Looper.getMainLooper())

    fun prefetch(url: String?, referer: String? = null) {
        if (url.isNullOrBlank() || cache.get(url) != null) return
        io.execute {
            val bytes = if (referer.isNullOrBlank()) Http.getBytes(url) else Http.getImage(url, referer)
            val bitmap = bytes?.let {
                try {
                    BitmapFactory.decodeByteArray(it, 0, it.size)
                } catch (e: Exception) {
                    null
                }
            }
            if (bitmap != null) cache.put(url, bitmap)
        }
    }

    fun load(url: String, view: ImageView, referer: String? = null, onFail: (() -> Unit)? = null) {
        cache.get(url)?.let {
            view.setImageBitmap(it)
            return
        }
        view.setImageResource(R.drawable.bg_stat)

        io.execute {
            val bytes = if (referer.isNullOrBlank()) Http.getBytes(url) else Http.getImage(url, referer)
            val bitmap = bytes?.let {
                try {
                    BitmapFactory.decodeByteArray(it, 0, it.size)
                } catch (e: Exception) {
                    null
                }
            }
            if (bitmap != null) {
                cache.put(url, bitmap)
                ui.post { view.setImageBitmap(bitmap) }
            } else {
                ui.post { onFail?.invoke() }
            }
        }
    }
}
