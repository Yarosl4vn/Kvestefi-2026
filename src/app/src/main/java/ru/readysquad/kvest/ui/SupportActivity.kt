package ru.readysquad.kvest.ui

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ru.readysquad.kvest.R
import ru.readysquad.kvest.data.Account
import ru.readysquad.kvest.data.AccountStore
import ru.readysquad.kvest.data.Prefs
import ru.readysquad.kvest.data.Roles
import ru.readysquad.kvest.data.SupportMessage
import ru.readysquad.kvest.data.SupportStore
import ru.readysquad.kvest.util.Chips
import ru.readysquad.kvest.util.Palettes
import ru.readysquad.kvest.util.Screens

class SupportActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var store: AccountStore
    private lateinit var support: SupportStore
    private lateinit var ui: Screens

    private var kind = SupportStore.kinds.first()

    private val me: Account? get() = prefs.currentUserId.takeIf { it > 0 }?.let { store.byId(it) }
    private val isDeveloper: Boolean get() = prefs.role == Roles.DEVELOPER || prefs.devMode

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        Palettes.apply(this, prefs.palette)
        super.onCreate(savedInstanceState)
        store = AccountStore(this)
        support = SupportStore(this)
        ui = Screens(this)
        ui.install("Помощь и поддержка") { finish() }
        render()
    }

    private fun render() {
        if (isDeveloper) renderInbox()

        ui.card("Задать вопрос") {
            addView(
                ui.text(
                    "Опиши, что случилось или чего не хватает. Обращение увидят разработчики.",
                    12f, R.color.on_bg_variant
                )
            )
            addView(ui.text("Тема", 13f, R.color.accent, bold = true, top = 12))
            addView(
                Chips.row(this@SupportActivity, SupportStore.kinds.map { it to (it == kind) }) { idx ->
                    kind = SupportStore.kinds[idx]
                    rebuild()
                }
            )
            val field = EditText(this@SupportActivity).apply {
                hint = "Что случилось?"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                minLines = 4
                background = ContextCompat.getDrawable(this@SupportActivity, R.drawable.bg_input)
                setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12))
                setTextColor(ui.color(R.color.on_bg))
                setHintTextColor(ui.color(R.color.on_bg_variant))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = ui.dp(10) }
            }
            addView(field)
            addView(
                ui.button("Отправить разработчикам") {
                    val text = field.text.toString()
                    if (text.trim().length < 10) {
                        Toast.makeText(
                            this@SupportActivity,
                            "Опиши подробнее: хотя бы 10 символов",
                            Toast.LENGTH_LONG
                        ).show()
                        return@button
                    }
                    val ok = support.send(me, kind, text)
                    Toast.makeText(
                        this@SupportActivity,
                        if (ok) "Отправлено: разработчики увидят обращение" else "Не удалось отправить",
                        Toast.LENGTH_LONG
                    ).show()
                    field.setText("")
                    rebuild()
                }
            )
        }

        val login = me?.login.orEmpty()
        if (login.isNotBlank()) {
            val mine = support.mine(login)
            ui.card("Мои обращения") {
                if (mine.isEmpty()) {
                    addView(ui.text("Ты пока ничего не отправлял.", 13f, R.color.on_bg_variant))
                } else {
                    mine.forEach { message -> addMessage(this, message, canClose = false) }
                }
            }
        } else {

            val mine = support.guest()
            if (mine.isNotEmpty()) {
                ui.card("Мои обращения") {
                    mine.forEach { message -> addMessage(this, message, canClose = false) }
                }
            }
        }
    }

    private fun renderInbox() {
        val open = support.open()
        val all = support.all()
        ui.card("Обращения") {
            addView(
                ui.text(
                    "Открытых: ${open.size} · всего: ${all.size}",
                    12f, R.color.on_bg_variant
                )
            )
            if (all.isEmpty()) {
                addView(ui.text("Обращений пока нет.", 13f, R.color.on_bg_variant, top = 8))
            } else {
                all.take(30).forEach { message -> addMessage(this, message, message.isOpen) }
            }
        }
    }

    private fun addMessage(target: LinearLayout, message: SupportMessage, canClose: Boolean) {
        target.addView(
            ui.text(
                message.kind + " · " + who(message),
                13.5f, R.color.on_bg, bold = true, top = 14
            )
        )
        target.addView(
            ui.text(
                time(message.createdAt) + " · " + message.statusLabel,
                11.5f, statusColor(message.status), bold = true
            )
        )
        target.addView(ui.text(message.text, 13f, R.color.on_bg, top = 4))

        if (message.reply.isNotBlank()) {
            target.addView(
                ui.text(
                    "Ответ" + (if (message.repliedBy.isBlank()) "" else " · ${message.repliedBy}") +
                        (if (message.repliedAt > 0L) " · " + time(message.repliedAt) else ""),
                    11.5f, R.color.on_bg_variant, top = 10
                )
            )
            target.addView(ui.text(message.reply, 13f, R.color.on_bg, top = 2))
        }

        if (canClose) {
            target.addView(ui.button("Ответить", primary = true) { askReply(message) })
            if (message.isOpen) {
                target.addView(
                    ui.button("Взять в работу", primary = false) {
                        support.setStatus(message.id, SupportStore.REVIEW)
                        rebuild()
                    }
                )
                target.addView(
                    ui.button("Закрыть обращение", primary = false) {
                        support.close(message.id)
                        rebuild()
                    }
                )
            } else {
                target.addView(
                    ui.button("Открыть заново", primary = false) {
                        support.setStatus(message.id, SupportStore.PENDING)
                        rebuild()
                    }
                )
            }
        }
    }

    private fun statusColor(status: String): Int = when (status) {
        SupportStore.REVIEW -> R.color.status_review
        SupportStore.CLOSED -> R.color.status_closed
        else -> R.color.status_waiting
    }

    private fun askReply(message: SupportMessage) {
        val pad = (20 * resources.displayMetrics.density).toInt()
        val field = EditText(this).apply {
            hint = "Ответ пользователю"
            setText(message.reply)
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        val wrap = FrameLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(field, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Ответ на обращение")
            .setView(wrap)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Отправить") { _, _ ->
                val by = me?.fullName ?: "Разработчик"
                val ok = support.reply(message.id, field.text.toString(), by)
                Toast.makeText(
                    this,
                    if (ok) "Ответ сохранён · статус: на рассмотрении" else "Напиши текст ответа",
                    Toast.LENGTH_LONG
                ).show()
                rebuild()
            }
            .show()
    }

    private fun who(message: SupportMessage): String {
        val name = message.fromName.ifBlank { "без имени" }
        val login = if (message.fromLogin.isBlank()) "" else " (${message.fromLogin})"
        val school = if (message.school.isBlank()) "" else " · ${message.school}"
        return name + login + school
    }

    private fun time(at: Long): String =
        java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(at))

    private fun rebuild() {
        ui.body.removeAllViews()
        ui.header("Помощь и поддержка") { finish() }
        render()
    }
}
