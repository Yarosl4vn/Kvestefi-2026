package ru.readysquad.kvest.util

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import ru.readysquad.kvest.R

object Chips {

    private const val HEIGHT_DP = 40

    private const val PAD_H_DP = 16

    private const val MIN_WIDTH_DP = 68

    private const val GAP_DP = 8

    fun create(
        ctx: Context,
        label: String,
        selected: Boolean,
        textSize: Float = 14f,
        iconRes: Int? = null,
        onClick: (View) -> Unit
    ): TextView {
        val dp = { v: Int -> (v * ctx.resources.displayMetrics.density).toInt() }
        val chip = TextView(ctx).apply {
            text = label
            this.textSize = textSize
            gravity = Gravity.CENTER

            includeFontPadding = false
            maxLines = 1
            setPadding(dp(PAD_H_DP), 0, dp(PAD_H_DP), 0)
            minimumWidth = dp(MIN_WIDTH_DP)
            minimumHeight = dp(HEIGHT_DP)
            background = ContextCompat.getDrawable(ctx, R.drawable.bg_chip_choice)
            setTextColor(ContextCompat.getColorStateList(ctx, R.color.chip_text))
            isSelected = selected
            isClickable = true
            isFocusable = true
            if (iconRes != null) Icons.start(this, iconRes, R.color.chip_text, 15)
            setOnClickListener { view -> onClick(view) }
        }
        chip.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, dp(HEIGHT_DP)
        ).apply { marginEnd = dp(GAP_DP) }
        return chip
    }

    /**
     * Строка чипсов внутри горизонтальной прокрутки: чипсов может быть больше,
     * чем помещается по ширине экрана, и их нужно листать влево-вправо.
     */
    fun scrollRow(
        ctx: Context,
        labels: List<Pair<String, Boolean>>,
        onClick: (Int) -> Unit
    ): android.widget.HorizontalScrollView {
        val scroll = android.widget.HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        scroll.addView(row(ctx, labels, onClick))
        return scroll
    }

    fun row(ctx: Context, labels: List<Pair<String, Boolean>>, onClick: (Int) -> Unit): LinearLayout {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        labels.forEachIndexed { index, (label, selected) ->
            row.addView(create(ctx, label, selected) { onClick(index) })
        }
        return row
    }
}
