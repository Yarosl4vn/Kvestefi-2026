package ru.readysquad.kvest.ui

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import ru.readysquad.kvest.R
import ru.readysquad.kvest.data.Prefs
import ru.readysquad.kvest.data.Repository
import ru.readysquad.kvest.util.Icons
import ru.readysquad.kvest.util.Notifier
import ru.readysquad.kvest.util.Palettes
import ru.readysquad.kvest.util.Screens

class NotificationsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var repo: Repository
    private lateinit var ui: Screens

    private var section = Notifier.KIND_IMPORTANT
    private var openIndex = -1
    private var openList: List<Notifier.Item> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        Palettes.apply(this, prefs.palette)
        super.onCreate(savedInstanceState)
        repo = Repository.get(this)
        ui = Screens(this)
        ui.install("Уведомления") { onBack() }
        render()
    }

    private fun onBack() {
        if (openIndex in openList.indices) {
            openIndex = -1
            rebuild()
            return
        }
        finish()
    }

    @Deprecated("Кнопка «назад» должна закрывать подробности, а не выходить из экрана")
    override fun onBackPressed() {
        onBack()
    }

    private fun items(kind: String) =
        Notifier.of(prefs, repo.stats(), repo.badges(), repo.topicStats(null), kind)

    private fun all() =
        Notifier.alerts(this, prefs) +
            Notifier.build(prefs, repo.stats(), repo.badges(), repo.topicStats(null))

    private fun rebuild() {
        ui.body.removeAllViews()
        ui.header(if (openIndex in openList.indices) "Подробности" else "Уведомления") { onBack() }
        render()
    }

    private fun render() {
        if (openIndex in openList.indices) {
            renderDetail(openList[openIndex])
            return
        }
        buildSectionChips()
        val list = visibleItems()
        if (list.isEmpty()) {
            ui.card {
                addView(
                    ui.text(
                        "В этом разделе пока пусто. Занимайся — здесь появятся достижения, " +
                            "напоминания о цели дня и подсказки по слабым темам.",
                        14f, R.color.on_bg_variant
                    )
                )
            }
            return
        }
        list.forEachIndexed { index, item ->
            val card = ui.card {
                val head = LinearLayout(this@NotificationsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                head.addView(
                    TextView(this@NotificationsActivity).apply {
                        text = " "
                        Icons.start(
                            this, item.iconRes,
                            if (item.warn) R.color.danger else R.color.accent, 17
                        )
                        layoutParams = LinearLayout.LayoutParams(ui.dp(28), ViewGroup.LayoutParams.WRAP_CONTENT)
                    }
                )
                head.addView(
                    TextView(this@NotificationsActivity).apply {
                        text = item.title
                        textSize = 15.5f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(ui.color(if (item.warn) R.color.danger else R.color.on_bg))
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    }
                )
                addView(head)
                addView(ui.text(item.text, 13.5f, R.color.on_bg_variant, top = 6))
                addView(ui.text("Нажми, чтобы открыть подробности", 12f, R.color.accent, top = 8))
            }
            card.isClickable = true
            card.isFocusable = true
            card.setOnClickListener {
                openIndex = index
                openList = list
                rebuild()
            }
        }
    }

    /** Подробности одного уведомления: полный текст и что с ним делать. */
    private fun renderDetail(item: Notifier.Item) {
        ui.card(Notifier.kindTitle(item.kind)) {
            val head = LinearLayout(this@NotificationsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            head.addView(
                TextView(this@NotificationsActivity).apply {
                    text = " "
                    Icons.start(
                        this, item.iconRes,
                        if (item.warn) R.color.danger else R.color.accent, 19
                    )
                    layoutParams = LinearLayout.LayoutParams(ui.dp(30), ViewGroup.LayoutParams.WRAP_CONTENT)
                }
            )
            head.addView(
                TextView(this@NotificationsActivity).apply {
                    text = item.title
                    textSize = 17f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(ui.color(if (item.warn) R.color.danger else R.color.on_bg))
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
            )
            addView(head)
            addView(ui.text(item.text, 14.5f, R.color.on_bg, top = 12))
            addView(
                ui.text(
                    when (item.kind) {
                        Notifier.KIND_IMPORTANT ->
                            "Важное уведомление: на него стоит обратить внимание в первую очередь."
                        Notifier.KIND_STUDY ->
                            "Учебное уведомление: связано с заданиями, темами и учёбой."
                        else ->
                            "Системное уведомление: подсказка о работе приложения."
                    },
                    12f, R.color.on_bg_variant, top = 12
                )
            )
            addView(ui.button("К списку уведомлений") {
                openIndex = -1
                rebuild()
            })
        }
    }

    private fun buildSectionChips() {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, ui.dp(12), 0, 0)
        }
        listOf(Notifier.KIND_IMPORTANT, Notifier.KIND_STUDY, Notifier.KIND_SYSTEM).forEach { kind ->
            val count = visibleItems(kind).size
            val chip = TextView(this).apply {
                text = if (count > 0) "${Notifier.kindTitle(kind)} · $count" else Notifier.kindTitle(kind)
                textSize = 13.5f
                gravity = Gravity.CENTER
                setPadding(ui.dp(14), ui.dp(10), ui.dp(14), ui.dp(10))
                background = ContextCompat.getDrawable(this@NotificationsActivity, R.drawable.bg_chip_choice)
                setTextColor(ContextCompat.getColorStateList(this@NotificationsActivity, R.color.chip_text))
                isSelected = section == kind
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginEnd = ui.dp(8) }
                setOnClickListener {
                    if (section == kind) return@setOnClickListener
                    section = kind
                    rebuild()
                }
            }
            row.addView(chip)
        }
        ui.body.addView(row)

        ui.card("Разделы уведомлений") {
            addView(switchRow("Важные", prefs.notifyImportant) { prefs.notifyImportant = it })
            addView(switchRow("Учебные", prefs.notifyStudy) { prefs.notifyStudy = it })
            addView(switchRow("Системные", prefs.notifySystem) { prefs.notifySystem = it })
        }
    }

    private fun visibleItems(kind: String? = null): List<Notifier.Item> {
        val all = all()
        return all.filter { item ->
            val enabled = when (item.kind) {
                Notifier.KIND_IMPORTANT -> prefs.notifyImportant
                Notifier.KIND_STUDY -> prefs.notifyStudy
                else -> prefs.notifySystem
            }
            enabled && (kind == null || item.kind == kind)
        }
    }

    private fun switchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, ui.dp(8), 0, ui.dp(8))
        }
        row.addView(
            TextView(this).apply {
                text = label
                textSize = 15f
                setTextColor(ui.color(R.color.on_bg))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        val toggle = TextView(this).apply {
            text = if (checked) "включены" else "выключены"
            textSize = 13f
            setPadding(ui.dp(12), ui.dp(7), ui.dp(12), ui.dp(7))
            background = ContextCompat.getDrawable(
                this@NotificationsActivity,
                if (checked) R.drawable.bg_pill_primary else R.drawable.bg_pill
            )
            setTextColor(ui.color(if (checked) R.color.on_primary else R.color.on_bg_variant))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                onChange(!checked)
                rebuild()
            }
        }
        row.addView(toggle)
        return row
    }
}
