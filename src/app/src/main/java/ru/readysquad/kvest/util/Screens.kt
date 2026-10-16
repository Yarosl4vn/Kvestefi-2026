package ru.readysquad.kvest.util

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import ru.readysquad.kvest.R

class Screens(private val activity: Activity) {

    private val ctx: Context get() = activity

    val body = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(12), dp(18), dp(30))
    }

    private val scroll = ScrollView(activity).apply {
        overScrollMode = View.OVER_SCROLL_NEVER
        isVerticalScrollBarEnabled = false
        addView(body, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density).toInt()

    fun color(res: Int): Int = ContextCompat.getColor(activity, res)

    fun install(title: String, onBack: () -> Unit = { activity.finish() }) {
        body.setBackgroundColor(color(R.color.bg))
        val real = activity as? androidx.appcompat.app.AppCompatActivity
        real?.setContentView(scroll) ?: activity.setContentView(scroll)
        header(title, onBack)
    }

    fun header(title: String, onBack: () -> Unit) {
        val head = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(6))
        }
        head.addView(
            ImageButton(ctx).apply {
                setBackgroundResource(R.drawable.bg_circle_surface)
                setImageResource(R.drawable.ic_back)
                setPadding(dp(11), dp(11), dp(11), dp(11))
                contentDescription = "Назад"
                setColorFilter(color(R.color.on_bg_variant))
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
                setOnClickListener { onBack() }
            }
        )
        head.addView(
            TextView(ctx).apply {
                text = title
                textSize = 21f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(color(R.color.on_bg))
                setPadding(dp(12), 0, 0, 0)
            }
        )
        body.addView(head)
    }

    fun card(title: String? = null, block: LinearLayout.() -> Unit): LinearLayout {
        val inner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        if (!title.isNullOrBlank()) {
            inner.addView(
                TextView(ctx).apply {
                    text = title.uppercase()
                    textSize = 11f
                    typeface = Typeface.DEFAULT_BOLD
                    letterSpacing = 0.08f
                    setTextColor(color(R.color.accent))
                    setPadding(0, 0, 0, dp(8))
                }
            )
        }
        inner.block()
        val card = MaterialCardView(ctx).apply {
            setCardBackgroundColor(color(R.color.surface))
            radius = dp(18).toFloat()
            strokeWidth = dp(1)
            strokeColor = color(R.color.outline_variant)
            cardElevation = 0f
            addView(inner, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(12)
            layoutParams = lp
        }
        body.addView(card)
        return inner
    }

    fun row(
        iconRes: Int,
        title: String,
        subtitle: String = "",
        badge: String = "",
        titleColor: Int? = null,
        bold: Boolean = false,
        onClick: (() -> Unit)? = null
    ): LinearLayout {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(12), dp(4), dp(12))
            if (onClick != null) {
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
            }
        }
        row.addView(
            TextView(ctx).apply {
                text = " "
                setPadding(0, 0, 0, 0)
                Icons.start(this, iconRes, R.color.on_bg_variant, 18)
                layoutParams = LinearLayout.LayoutParams(dp(30), ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        )
        val texts = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        texts.addView(
            TextView(ctx).apply {
                text = title
                textSize = 16f
                setTextColor(titleColor ?: color(R.color.on_bg))
                if (bold) typeface = Typeface.DEFAULT_BOLD
            }
        )
        if (subtitle.isNotBlank()) {
            texts.addView(
                TextView(ctx).apply {
                    text = subtitle
                    textSize = 12.5f
                    setTextColor(color(R.color.on_bg_variant))
                    setPadding(0, dp(2), 0, 0)
                }
            )
        }
        row.addView(texts)
        if (badge.isNotBlank()) {
            row.addView(
                TextView(ctx).apply {
                    text = badge
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(dp(9), dp(4), dp(9), dp(4))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(11).toFloat()
                        setColor(color(R.color.danger))
                    }
                    setTextColor(color(R.color.on_bg))
                }
            )
        }
        row.addView(
            TextView(ctx).apply {
                text = " "
                Icons.start(this, R.drawable.ic_chevron, R.color.on_bg_variant, 16)
                layoutParams = LinearLayout.LayoutParams(dp(24), ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        )
        return row
    }

    fun divider() {
        body.addView(
            View(ctx).apply {
                setBackgroundColor(color(R.color.outline_variant))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
                lp.topMargin = dp(2)
                lp.bottomMargin = dp(2)
                layoutParams = lp
            }
        )
    }

    fun sectionTitle(text: String) {
        body.addView(
            TextView(ctx).apply {
                this.text = text
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(color(R.color.on_bg_variant))
                setPadding(dp(4), dp(20), 0, dp(2))
            }
        )
    }

    fun text(
        value: String,
        size: Float = 14f,
        colorRes: Int = R.color.on_bg,
        bold: Boolean = false,
        top: Int = 0
    ): TextView = TextView(ctx).apply {
        text = value
        textSize = size
        setTextColor(color(colorRes))
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(top), 0, 0)
        setLineSpacing(dp(3).toFloat(), 1f)
    }

    fun kv(key: String, value: String, top: Int = 8): LinearLayout {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(top), 0, 0)
        }
        row.addView(
            TextView(ctx).apply {
                text = key
                textSize = 13.5f
                setTextColor(color(R.color.on_bg_variant))
                layoutParams = LinearLayout.LayoutParams(dp(132), ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        )
        row.addView(
            TextView(ctx).apply {
                text = value
                textSize = 14.5f
                setTextColor(color(R.color.on_bg))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        return row
    }

    fun button(text: String, primary: Boolean = true, onClick: () -> Unit): View {
        val button = if (primary) {
            com.google.android.material.button.MaterialButton(ctx)
        } else {
            com.google.android.material.button.MaterialButton(
                ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
            )
        }
        button.text = text
        button.textSize = 15f
        button.minHeight = dp(50)
        button.insetTop = 0
        button.insetBottom = 0
        button.setOnClickListener { onClick() }
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = dp(12)
        button.layoutParams = lp
        return button
    }
}
