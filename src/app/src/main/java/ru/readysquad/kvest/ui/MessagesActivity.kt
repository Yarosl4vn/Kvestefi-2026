package ru.readysquad.kvest.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ru.readysquad.kvest.R
import ru.readysquad.kvest.data.Account
import ru.readysquad.kvest.data.AccountStore
import ru.readysquad.kvest.data.Message
import ru.readysquad.kvest.data.MessageStore
import ru.readysquad.kvest.data.Prefs
import ru.readysquad.kvest.data.StatusMarks
import ru.readysquad.kvest.util.MessagePhotos
import ru.readysquad.kvest.util.Palettes
import ru.readysquad.kvest.util.Screens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessagesActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var store: AccountStore
    private lateinit var messages: MessageStore
    private lateinit var ui: Screens

    private var otherId: Long = 0L
    private var search: String = ""
    private var draftText: String = ""
    private var replyTo: Message? = null
    private var attachment: String = ""

    private var messagesBox: LinearLayout? = null

    private val me: Account? get() = prefs.currentUserId.takeIf { it > 0L }?.let { store.byId(it) }

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        val saved = MessagePhotos.save(this, uri)
        if (saved == null) {
            Toast.makeText(this, "Не удалось прикрепить фото", Toast.LENGTH_SHORT).show()
        } else {
            attachment = saved
            rebuild()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        Palettes.apply(this, prefs.palette)
        super.onCreate(savedInstanceState)
        store = AccountStore(this)
        messages = MessageStore(this)
        ui = Screens(this)
        otherId = intent.getLongExtra(EXTRA_OTHER, 0L)
        ui.install("Сообщения") { onBack() }
        render()
    }

    private fun onBack() {
        if (otherId > 0L) {
            otherId = 0L
            search = ""
            replyTo = null
            attachment = ""
            rebuild()
        } else {
            finish()
        }
    }

    @Deprecated("Кнопка «назад» возвращает к списку чатов, а не выходит из экрана")
    override fun onBackPressed() {
        onBack()
    }

    private fun rebuild() {
        ui.body.removeAllViews()
        ui.header(if (otherId > 0L) counterpart()?.fullName ?: "Переписка" else "Сообщения") { onBack() }
        render()
    }

    private fun counterpart(): Account? = otherId.takeIf { it > 0L }?.let { store.byId(it) }

    private fun render() {
        val account = me
        if (account == null) {
            ui.card {
                addView(ui.text("Переписка доступна после регистрации.", 14f, R.color.on_bg_variant))
            }
            return
        }
        if (otherId > 0L) renderThread(account) else renderPeople(account)
    }

    private fun contacts(account: Account): List<Account> {
        val out = ArrayList<Account>()
        out.addAll(store.teachers(account.school).filter { it.id != account.id })
        if (account.isTeacher && account.homeroom.isNotBlank()) {
            val parts = account.homeroom.trim().split(" ")
            val grade = parts.firstOrNull().orEmpty().filter { it.isDigit() }
            val letter = parts.firstOrNull().orEmpty().filter { it.isLetter() }
            out.addAll(store.studentsOf(account.school, grade, letter))
        }
        return out.distinctBy { it.id }
    }

    // ── Список чатов ────────────────────────────────────────────────────

    private fun renderPeople(account: Account) {
        val people = contacts(account)
        if (people.isEmpty()) {
            ui.card {
                addView(
                    ui.text(
                        "Пока некого выбрать: в школе нет других зарегистрированных участников. " +
                            "Как только появятся учителя или одноклассники, они будут здесь.",
                        14f, R.color.on_bg_variant
                    )
                )
            }
            return
        }
        val pinned = people.filter { prefs.isChatPinned(it.id) }.sortedBy { it.fullName }
        val rest = people.filter { !prefs.isChatPinned(it.id) }
            .sortedWith(compareByDescending<Account> { messages.lastAt(account.id, it.id) }.thenBy { it.fullName })

        ui.card("Кому написать") {
            addView(
                ui.text(
                    "Закреплённые чаты всегда сверху, остальные — по времени последнего сообщения. " +
                        "Долгое нажатие на человека — закрепить чат.",
                    12f, R.color.on_bg_variant
                )
            )
            (pinned + rest).forEach { person ->
                val unread = messages.unreadFrom(account.id, person.id)
                val subtitle = buildString {
                    val status = StatusMarks.statusLine(person)
                    append(status.ifBlank { person.positionLine })
                    if (prefs.isChatPinned(person.id)) append(" · закреплён")
                }
                addView(personRow(person, subtitle, unread))
            }
        }
    }

    private fun openChat(person: Account) {
        otherId = person.id
        search = ""
        replyTo = null
        attachment = ""
        rebuild()
    }

    private fun personRow(person: Account, subtitle: String, unread: Int): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, ui.dp(10), 0, ui.dp(10))
            isClickable = true
            isFocusable = true
            setOnClickListener { openChat(person) }
            setOnLongClickListener {
                val pinnedNow = prefs.isChatPinned(person.id)
                MaterialAlertDialogBuilder(this@MessagesActivity)
                    .setTitle(StatusMarks.titleOf(person))
                    .setItems(
                        arrayOf(
                            if (pinnedNow) "Открепить чат" else "Закрепить чат",
                            "Открыть переписку"
                        )
                    ) { _, which ->
                        if (which == 0) {
                            prefs.togglePinChat(person.id)
                            rebuild()
                        } else {
                            openChat(person)
                        }
                    }
                    .setNegativeButton("Отмена", null)
                    .show()
                true
            }
        }

        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(
            TextView(this).apply {
                text = StatusMarks.titleOf(person)
                textSize = 15f
                typeface = if (person.isDeveloper) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(
                    StatusMarks.colorOf(
                        person,
                        ui.color(R.color.on_bg),
                        ui.color(R.color.danger)
                    )
                )
            }
        )
        if (subtitle.isNotBlank()) {
            column.addView(
                TextView(this).apply {
                    text = subtitle
                    textSize = 12f
                    setTextColor(ui.color(R.color.on_bg_variant))
                }
            )
        }
        row.addView(column, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        if (unread > 0) {
            row.addView(
                TextView(this).apply {
                    text = unread.toString()
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setTextColor(ui.color(R.color.on_primary))
                    background = ContextCompat.getDrawable(this@MessagesActivity, R.drawable.bg_pill_primary)
                    setPadding(ui.dp(9), ui.dp(3), ui.dp(9), ui.dp(3))
                }
            )
        }
        return row
    }

    // ── Переписка ───────────────────────────────────────────────────────

    private fun renderThread(account: Account) {
        val other = counterpart()
        if (other == null) {
            ui.card { addView(ui.text("Собеседник не найден.", 14f, R.color.on_bg_variant)) }
            return
        }
        messages.markRead(account.id, other.id)

        ui.card("Переписка") {
            addView(
                TextView(this@MessagesActivity).apply {
                    text = StatusMarks.titleOf(other)
                    textSize = 15.5f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(StatusMarks.colorOf(other, ui.color(R.color.on_bg)))
                }
            )
            addView(
                ui.text(
                    listOf(other.positionLine, StatusMarks.statusLine(other))
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    12f, R.color.on_bg_variant, top = 4
                )
            )
            addView(
                ui.text(
                    "Всего сообщений: ${messages.total(account.id, other.id)} · " +
                        "прочитано моих: ${messages.readMine(account.id, other.id)}",
                    12f, R.color.on_bg_variant, top = 6
                )
            )
        }

        ui.card("Поиск и закрепление") {
            val field = EditText(this@MessagesActivity).apply {
                hint = "Поиск по переписке"
                setText(search)
                textSize = 15f
                setTextColor(ui.color(R.color.on_bg))
                setHintTextColor(ui.color(R.color.on_bg_variant))
                background = ContextCompat.getDrawable(this@MessagesActivity, R.drawable.bg_input)
                setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12))
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                    override fun afterTextChanged(s: Editable?) {
                        search = s?.toString().orEmpty()
                        messagesBox?.let { fillMessages(it, account, other) }
                    }
                })
            }
            addView(field)
            addView(
                ui.button(
                    if (prefs.isChatPinned(other.id)) "Открепить чат" else "Закрепить чат",
                    primary = false
                ) {
                    prefs.togglePinChat(other.id)
                    rebuild()
                }
            )
        }

        val box = ui.card { }
        messagesBox = box
        fillMessages(box, account, other)

        ui.card("Новое сообщение") {
            replyTo?.let { reply ->
                addView(
                    ui.text(
                        "Ответ на: " + reply.text.replace("\n", " ").take(70).ifBlank { "фотографию" },
                        12.5f, R.color.accent
                    )
                )
                addView(ui.button("Убрать ответ", primary = false) { replyTo = null; rebuild() })
            }

            if (attachment.isNotBlank()) {
                MessagePhotos.load(this@MessagesActivity, attachment)?.let { bitmap ->
                    addView(
                        ImageView(this@MessagesActivity).apply {
                            setImageBitmap(bitmap)
                            adjustViewBounds = true
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply { topMargin = ui.dp(8) }
                        }
                    )
                }
                addView(
                    ui.button("Убрать фото", primary = false) {
                        MessagePhotos.delete(this@MessagesActivity, attachment)
                        attachment = ""
                        rebuild()
                    }
                )
            }

            addView(ui.button("Прикрепить фото", primary = false) { pickPhoto.launch("image/*") })

            val input = EditText(this@MessagesActivity).apply {
                hint = "Написать ${other.firstName.ifBlank { other.fullName }}"
                textSize = 15f
                setTextColor(ui.color(R.color.on_bg))
                setHintTextColor(ui.color(R.color.on_bg_variant))
                background = ContextCompat.getDrawable(this@MessagesActivity, R.drawable.bg_input)
                setPadding(ui.dp(14), ui.dp(14), ui.dp(14), ui.dp(14))
                minHeight = ui.dp(52)
                setText(draftText)
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                    override fun afterTextChanged(s: Editable?) {
                        draftText = s?.toString().orEmpty()
                    }
                })
            }
            addView(input)

            addView(
                ui.button("Отправить") {
                    val text = input.text.toString().trim()
                    val photo = attachment
                    if (text.isEmpty() && photo.isBlank()) return@button
                    val reply = replyTo
                    val ok = messages.send(
                        account, other, text,
                        reply?.id ?: 0L, reply?.text.orEmpty(), photo
                    )
                    if (ok) {
                        draftText = ""
                        replyTo = null
                        attachment = ""
                        rebuild()
                    } else {
                        Toast.makeText(this@MessagesActivity, "Не удалось отправить", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    private fun fillMessages(box: LinearLayout, account: Account, other: Account) {
        val found = if (search.isBlank()) {
            messages.thread(account.id, other.id)
        } else {
            messages.search(account.id, other.id, search)
        }

        box.removeAllViews()
        if (found.isEmpty()) {
            box.addView(
                ui.text(
                    if (search.isBlank()) {
                        "Переписки пока нет. Напиши первым — сообщение сохранится на устройстве."
                    } else {
                        "По запросу «$search» ничего не найдено."
                    },
                    13.5f, R.color.on_bg_variant
                )
            )
            return
        }
        if (search.isNotBlank()) {
            box.addView(ui.text("Найдено сообщений: ${found.size}", 12f, R.color.accent))
        }

        var lastDay = ""
        found.forEach { message ->
            val day = dayKeyFormat.format(Date(message.createdAt))
            if (day != lastDay) {
                lastDay = day
                box.addView(daySeparator(dayLabel(message.createdAt)))
            }
            bubble(account, message, box)
        }
    }

    private fun daySeparator(label: String): TextView = TextView(this).apply {
        text = label
        textSize = 11.5f
        gravity = Gravity.CENTER
        setTextColor(ui.color(R.color.on_bg_variant))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = ui.dp(12); bottomMargin = ui.dp(4) }
    }

    private fun bubble(account: Account, message: Message, box: LinearLayout) {
        val mine = message.fromId == account.id
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (mine) Gravity.END else Gravity.START
            setPadding(0, ui.dp(4), 0, ui.dp(4))
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(
                this@MessagesActivity,
                if (mine) R.drawable.bg_pill_primary else R.drawable.bg_pill
            )
            setPadding(ui.dp(12), ui.dp(9), ui.dp(12), ui.dp(9))
            isClickable = true
            isFocusable = true
            setOnLongClickListener {
                showActions(message)
                true
            }
        }

        if (message.replyText.isNotBlank()) {
            column.addView(
                TextView(this).apply {
                    text = "Ответ: " + message.replyText.replace("\n", " ").take(60)
                    textSize = 11.5f
                    maxLines = 2
                    alpha = 0.85f
                    setTextColor(ui.color(if (mine) R.color.on_primary else R.color.on_bg_variant))
                }
            )
        }

        if (message.hasAttachment) {
            val bitmap = MessagePhotos.load(this, message.attachment)
            if (bitmap == null) {
                column.addView(
                    TextView(this).apply {
                        text = "фото недоступно"
                        textSize = 12f
                        setTextColor(ui.color(if (mine) R.color.on_primary else R.color.on_bg_variant))
                    }
                )
            } else {
                column.addView(
                    ImageView(this).apply {
                        setImageBitmap(bitmap)
                        adjustViewBounds = true
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        layoutParams = LinearLayout.LayoutParams(ui.dp(200), ViewGroup.LayoutParams.WRAP_CONTENT)
                            .apply { bottomMargin = ui.dp(4) }
                    }
                )
            }
        }

        if (message.text.isNotBlank()) {
            column.addView(
                TextView(this).apply {
                    text = message.text
                    textSize = 14f
                    maxWidth = ui.dp(250)
                    setTextColor(ui.color(if (mine) R.color.on_primary else R.color.on_bg))
                }
            )
        }

        column.addView(
            TextView(this).apply {
                text = timeFormat.format(Date(message.createdAt)) +
                    if (mine) (if (message.isRead) "   ✓✓ прочитано" else "   ✓ отправлено") else ""
                textSize = 10.5f
                alpha = 0.85f
                setTextColor(ui.color(if (mine) R.color.on_primary else R.color.on_bg_variant))
            }
        )

        wrap.addView(column)
        box.addView(wrap)
    }

    private fun showActions(message: Message) {
        val account = me ?: return
        val mine = message.fromId == account.id
        val actions = ArrayList<String>()
        actions.add("Ответить")
        if (message.text.isNotBlank()) actions.add("Скопировать текст")
        if (mine) actions.add("Удалить сообщение")

        MaterialAlertDialogBuilder(this)
            .setTitle(if (message.text.isBlank()) "Фотография" else message.text.take(70))
            .setItems(actions.toTypedArray()) { _, which ->
                when (actions[which]) {
                    "Ответить" -> {
                        replyTo = message
                        rebuild()
                    }
                    "Скопировать текст" -> copyText(message.text)
                    "Удалить сообщение" -> {
                        if (messages.delete(message.id, account.id)) {
                            MessagePhotos.delete(this, message.attachment)
                            Toast.makeText(this, "Сообщение удалено", Toast.LENGTH_SHORT).show()
                            rebuild()
                        }
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun copyText(value: String) {
        val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("сообщение", value))
        Toast.makeText(this, "Текст скопирован", Toast.LENGTH_SHORT).show()
    }

    private fun dayLabel(at: Long): String {
        val day = dayKeyFormat.format(Date(at))
        return when (day) {
            dayKeyFormat.format(Date()) -> "Сегодня"
            dayKeyFormat.format(Date(System.currentTimeMillis() - 86_400_000L)) -> "Вчера"
            else -> dayWordFormat.format(Date(at))
        }
    }

    private val timeFormat: SimpleDateFormat get() = SimpleDateFormat("HH:mm", Locale("ru"))
    private val dayKeyFormat: SimpleDateFormat get() = SimpleDateFormat("yyyy-MM-dd", Locale("ru"))
    private val dayWordFormat: SimpleDateFormat get() = SimpleDateFormat("d MMMM", Locale("ru"))

    companion object {
        const val EXTRA_OTHER = "other_id"
    }
}
