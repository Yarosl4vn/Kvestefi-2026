package ru.readysquad.kvest.util

import android.widget.TextView
import androidx.core.content.ContextCompat
import ru.readysquad.kvest.R

object Icons {

    fun start(view: TextView, iconRes: Int, tintRes: Int = R.color.on_bg_variant, sizeDp: Int = 15) {
        apply(view, iconRes, tintRes, sizeDp, start = true)
    }

    fun end(view: TextView, iconRes: Int, tintRes: Int = R.color.on_bg_variant, sizeDp: Int = 15) {
        apply(view, iconRes, tintRes, sizeDp, start = false)
    }

    private fun apply(view: TextView, iconRes: Int, tintRes: Int, sizeDp: Int, start: Boolean) {
        val ctx = view.context
        val drawable = ContextCompat.getDrawable(ctx, iconRes) ?: return
        val size = (sizeDp * ctx.resources.displayMetrics.density).toInt().coerceAtLeast(1)
        drawable.setBounds(0, 0, size, size)

        val tint = ContextCompat.getColorStateList(ctx, tintRes)
        if (tint != null) drawable.setTintList(tint) else drawable.setTint(ContextCompat.getColor(ctx, tintRes))
        if (start) {
            view.setCompoundDrawables(drawable, null, null, null)
        } else {
            view.setCompoundDrawables(null, null, drawable, null)
        }
        view.compoundDrawablePadding = (6 * ctx.resources.displayMetrics.density).toInt()
    }
}
