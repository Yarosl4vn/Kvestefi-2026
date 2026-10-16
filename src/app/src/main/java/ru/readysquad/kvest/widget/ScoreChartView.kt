package ru.readysquad.kvest.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import ru.readysquad.kvest.data.ProgressPoint
import ru.readysquad.kvest.util.ThemeColors

class ScoreChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var points: List<ProgressPoint> = emptyList()
    private var fraction = 0f

    /** Индекс точки, которую пользователь выбрал пальцем: -1 — ничего не выбрано. */
    private var picked = -1
    private val coords = ArrayList<Pair<Float, Float>>()

    // Цвета берём из темы, иначе график не перекрашивался вместе с палитрой.
    private val accentColor = ThemeColors.primary(context)
    private val labelColor = ThemeColors.onSurfaceVariant(context)

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 7f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = accentColor
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor }
    private val dotCore = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ThemeColors.surfaceContainer(context)
    }
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ThemeColors.withAlpha(labelColor, 0x26)
        strokeWidth = 2f
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = labelColor
        textSize = 30f
        textAlign = Paint.Align.CENTER
    }
    private val tipBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ThemeColors.surfaceContainer(context)
    }
    private val tipText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ThemeColors.onSurface(context)
        textSize = 26f
        textAlign = Paint.Align.CENTER
    }
    private val path = Path()

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1100L
        interpolator = DecelerateInterpolator()
        addUpdateListener {
            fraction = it.animatedValue as Float
            invalidate()
        }
    }

    fun setData(data: List<ProgressPoint>) {
        points = data
        fraction = 0f
        picked = -1
        animator.cancel()
        animator.start()
        invalidate()
    }

    /** Какое число точек сейчас на графике — нужно, чтобы подсказка не выходила за края. */
    val pointCount: Int get() = points.size

    /** Ближайшая к касанию точка. */
    fun pickNear(x: Float): Int {
        if (coords.isEmpty()) return -1
        var best = 0
        var bestDistance = Float.MAX_VALUE
        coords.forEachIndexed { index, (px, _) ->
            val distance = kotlin.math.abs(px - x)
            if (distance < bestDistance) {
                bestDistance = distance
                best = index
            }
        }
        return best
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val index = pickNear(event.x)
                if (index != picked) {
                    picked = index
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                picked = -1
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val padLeft = 44f
        val padRight = 20f
        val padTop = 26f
        val padBottom = 46f
        val chartW = w - padLeft - padRight
        val chartH = h - padTop - padBottom

        for (i in 0..3) {
            val y = padTop + chartH * i / 3f
            canvas.drawLine(padLeft, y, w - padRight, y, grid)
        }

        if (points.isEmpty()) {
            label.textSize = 34f
            canvas.drawText("Пока нет попыток — пройди первый квест", w / 2f, h / 2f, label)
            return
        }

        val maxScore = maxOf(points.maxOf { it.score }, 10)
        val n = points.size
        val stepX = if (n > 1) chartW / (n - 1) else 0f

        coords.clear()
        points.forEachIndexed { i, p ->
            val x = padLeft + stepX * i
            val y = padTop + chartH - (chartH * (p.score.toFloat() / maxScore))
            coords.add(x to y)
        }

        val visible = (coords.size * fraction).toInt().coerceAtLeast(1)

        path.reset()
        val shown = coords.take(visible.coerceAtMost(coords.size))
        shown.forEachIndexed { i, (x, y) ->
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        if (shown.size >= 2) {

            val area = Path(path)
            area.lineTo(shown.last().first, padTop + chartH)
            area.lineTo(shown.first().first, padTop + chartH)
            area.close()
            fill.shader = LinearGradient(
                0f, padTop, 0f, padTop + chartH,
                ThemeColors.withAlpha(accentColor, 0x66),
                ThemeColors.withAlpha(accentColor, 0x00),
                Shader.TileMode.CLAMP
            )
            canvas.drawPath(area, fill)
        }
        canvas.drawPath(path, line)

        shown.forEach { (x, y) ->
            canvas.drawCircle(x, y, 11f, dot)
            canvas.drawCircle(x, y, 5f, dotCore)
        }

        // Нажатие на график показывает результат за конкретный день.
        if (picked in points.indices && picked < coords.size) {
            val (px, py) = coords[picked]
            val point = points[picked]
            canvas.drawCircle(px, py, 16f, dot)
            canvas.drawCircle(px, py, 9f, dotCore)

            val caption = point.label + " · " + point.score + " очков"
            val boxW = tipText.measureText(caption) + 28f
            val boxH = 46f
            val bx = (px - boxW / 2f)
                .coerceIn(padLeft, (w - padRight - boxW).coerceAtLeast(padLeft))
            val by = (py - boxH - 22f).coerceAtLeast(2f)
            canvas.drawRoundRect(bx, by, bx + boxW, by + boxH, 14f, 14f, tipBg)
            canvas.drawText(caption, bx + boxW / 2f, by + boxH / 2f + 9f, tipText)
        }

        label.textSize = 28f
        canvas.drawText(points.first().label, padLeft, h - 14f, label)
        if (n > 1) canvas.drawText(points.last().label, w - padRight, h - 14f, label)
    }
}
