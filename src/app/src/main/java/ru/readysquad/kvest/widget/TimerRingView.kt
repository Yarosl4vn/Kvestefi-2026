package ru.readysquad.kvest.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import ru.readysquad.kvest.R
import ru.readysquad.kvest.util.ThemeColors

class TimerRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    // Цвета берём из темы, иначе кольцо не перекрашивалось вместе с палитрой.
    private val dangerColor = ContextCompat.getColor(context, R.color.danger)
    private val warningColor = ContextCompat.getColor(context, R.color.status_review)

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f
        color = ThemeColors.withAlpha(ThemeColors.onSurfaceVariant(context), 0x33)
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
        color = ThemeColors.primary(context)
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = ThemeColors.onSurface(context)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val rect = RectF()

    var progress: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var secondsLeft: Int = 0
        set(value) {
            field = value
            ring.color = when {
                value <= 3 -> dangerColor
                value <= 6 -> warningColor
                else -> ThemeColors.primary(context)
            }
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        val size = minOf(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val pad = ring.strokeWidth / 2f + 2f
        val r = size / 2f - pad
        rect.set(cx - r, cy - r, cx + r, cy + r)

        canvas.drawCircle(cx, cy, r, track)
        canvas.drawArc(rect, -90f, 360f * progress, false, ring)

        text.textSize = size * 0.34f
        val ty = cy - (text.descent() + text.ascent()) / 2f
        canvas.drawText(secondsLeft.toString(), cx, ty, text)
    }
}
