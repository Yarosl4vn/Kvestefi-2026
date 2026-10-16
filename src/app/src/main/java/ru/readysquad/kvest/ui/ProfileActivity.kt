package ru.readysquad.kvest.ui

import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ru.readysquad.kvest.R
import ru.readysquad.kvest.data.Account
import ru.readysquad.kvest.data.AccountStore
import ru.readysquad.kvest.data.Badge
import ru.readysquad.kvest.data.DbHelper
import ru.readysquad.kvest.data.Grades
import ru.readysquad.kvest.data.MessageStore
import ru.readysquad.kvest.data.NickColors
import ru.readysquad.kvest.data.Positions
import ru.readysquad.kvest.data.Prefixes
import ru.readysquad.kvest.data.Prefs
import ru.readysquad.kvest.data.Repository
import ru.readysquad.kvest.data.Roles
import ru.readysquad.kvest.data.SeedData
import ru.readysquad.kvest.data.StatusMarks
import ru.readysquad.kvest.util.Avatars
import ru.readysquad.kvest.util.Notifier
import ru.readysquad.kvest.util.Palettes
import ru.readysquad.kvest.util.Reports
import ru.readysquad.kvest.util.Screens

class ProfileActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var store: AccountStore
    private lateinit var messages: MessageStore
    private lateinit var repo: Repository
    private lateinit var ui: Screens

    private val isDeveloper: Boolean get() = prefs.role == Roles.DEVELOPER || prefs.devMode

    private fun openPeople(mode: Int) {
        startActivity(
            Intent(this, PeopleActivity::class.java).putExtra(PeopleActivity.EXTRA_MODE, mode)
        )
    }

    /** Отчёт о прогрессе для родителей: статистика собирается и отдаётся через «Поделиться». */
    private fun shareProgress() {
        val stats = repo.stats()
        if (stats.quests == 0) {
            android.widget.Toast.makeText(
                this,
                "Пока нечего показывать: пройди хотя бы один квест",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }
        val weak = repo.topicStats().filter { it.total >= 2 && it.accuracy < 70 }.take(5)
        Reports.share(
            this,
            "Прогресс в Квестефи",
            Reports.progressText(
                prefs = prefs,
                stats = stats,
                attempts = repo.attempts(5),
                weak = weak,
                weekPoints = repo.pointsInLast(7),
                weekDays = prefs.activeDaysInLast(7)
            )
        )
    }

    /** Разработчик выбирает себе префикс и цвет ника — они видны во всех списках. */
    private fun askMyPrefix() {
        val account = me ?: return
        val options = Prefixes.all + "Убрать префикс"
        MaterialAlertDialogBuilder(this)
            .setTitle("Мой префикс")
            .setItems(options.toTypedArray()) { _, which ->
                store.setPrefix(account.id, if (which == options.lastIndex) "" else options[which])
                rebuild()
            }
            .setNeutralButton("Свой вариант") { _, _ -> askMyCustomPrefix(account) }
            .show()
    }

    private fun askMyCustomPrefix(account: Account) {
        val field = EditText(this).apply {
            hint = "Например: Основатель проекта"
            setText(account.prefix)
            setTextColor(ui.color(R.color.on_bg))
            setHintTextColor(ui.color(R.color.on_bg_variant))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Свой префикс")
            .setView(field)
            .setPositiveButton("Сохранить") { _, _ ->
                store.setPrefix(account.id, field.text.toString())
                rebuild()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun askMyColor() {
        val account = me ?: return
        val labels = NickColors.all.map { it.second }.toTypedArray()
        val current = NickColors.all.indexOfFirst { it.first.equals(account.nickColor, true) }
            .coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle("Цвет ника")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                store.setNickColor(account.id, NickColors.all[which].first)
                dialog.dismiss()
                rebuild()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private var page = 0

    private val me: Account? get() = prefs.currentUserId.takeIf { it > 0L }?.let { store.byId(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        Palettes.apply(this, prefs.palette)
        super.onCreate(savedInstanceState)
        store = AccountStore(this)
        messages = MessageStore(this)
        repo = Repository.get(this)
        ui = Screens(this)
        ui.install("Профиль") { onBack() }
        onBackPressedDispatcher.addCallback(
            this,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = onBack()
            }
        )
        render()
    }

    override fun onResume() {
        super.onResume()
        rebuild()
    }

    private fun rebuild() {
        ui.body.removeAllViews()
        ui.header(pageTitle()) { onBack() }
        render()
    }

    private fun render() {

        if (page != 0) {
            renderPage()
            return
        }
        buildIdentity()
        buildRows()
        if (me?.isDeveloper == true || prefs.devMode) buildDevCard()
        buildTail()
    }

    private fun buildIdentity() {
        val account = me
        ui.card {
            val head = LinearLayout(this@ProfileActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            head.addView(avatarView(), LinearLayout.LayoutParams(ui.dp(58), ui.dp(58)))
            val titles = LinearLayout(this@ProfileActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(ui.dp(14), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            titles.addView(
                TextView(this@ProfileActivity).apply {
                    val name = account?.address(prefs.addressByName)?.ifBlank { prefs.name } ?: prefs.name
                    val prefix = account?.let { StatusMarks.prefixOf(it) }.orEmpty()
                    text = if (prefix.isBlank()) name else "$prefix · $name"
                    textSize = 21f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(
                        account?.let {
                            StatusMarks.colorOf(
                                it, ui.color(R.color.on_bg), ui.color(R.color.danger)
                            )
                        } ?: ui.color(R.color.on_bg)
                    )
                }
            )
            titles.addView(
                TextView(this@ProfileActivity).apply {
                    text = Roles.title(account?.role ?: prefs.role)
                    textSize = 13f
                    setTextColor(ui.color(R.color.accent))
                    setPadding(0, ui.dp(2), 0, 0)
                }
            )
            head.addView(titles)
            addView(head)

            if (isDeveloper) {
                addView(ui.kv("Роль", "Разработчик", 14))
                addView(
                    ui.text(
                        "Служебный доступ: школы, учителя и ученики.",
                        13f, R.color.on_bg_variant, top = 10
                    )
                )
                addView(
                    ui.row(
                        R.drawable.ic_card_person,
                        "Связь со всеми учителями",
                        "список всех учителей, их школы и должности"
                    ) { openPeople(PeopleActivity.MODE_TEACHERS) }
                )
                addView(
                    ui.row(
                        R.drawable.ic_nav_results,
                        "Список всех учеников",
                        "кто из какой школы и в каком классе"
                    ) { openPeople(PeopleActivity.MODE_STUDENTS) }
                )
                addView(
                    ui.row(
                        R.drawable.ic_subj_info,
                        "Справочник школ",
                        "добавить школу и классы"
                    ) { openPeople(PeopleActivity.MODE_SCHOOLS) }
                )
                addView(
                    ui.row(
                        R.drawable.ic_subj_info,
                        "Обращения в поддержку",
                        "вопросы и сообщения об ошибках"
                    ) { startActivity(Intent(this@ProfileActivity, SupportActivity::class.java)) }
                )
                return@card
            }

            if (account == null) {
                addView(
                    ui.text(
                        "Гостевой профиль: данные хранятся только на устройстве. " +
                            "Зарегистрируйся, чтобы попасть в рейтинг класса и получать задания учителя.",
                        13f, R.color.on_bg_variant, top = 12
                    )
                )
                return@card
            }

            addView(ui.kv("ФИО", account.fullName.ifBlank { "не указано" }, 14))
            addView(ui.kv("Школа", account.school.ifBlank { "не указана" }))
            addView(ui.kv("Должность", account.positionLine))
            if (account.isTeacher) {
                addView(ui.kv("Предмет", account.subject.ifBlank { "не указан" }))
                addView(
                    ui.kv(
                        "Мой класс",
                        if (account.homeroom.isBlank()) "не назначен" else account.homeroom
                    )
                )
            } else {
                if (!account.isDeveloper) addView(ui.kv("Мой класс", Grades.label(account.grade, account.letter)))
                addView(
                    ui.kv(
                        "Классный руководитель",
                        homeroomTeacherName(account) ?: "ещё не назначен"
                    )
                )
            }
            addView(ui.kv("Очки", "${account.score} · ${levelLine(account.score)}"))
            addView(ui.kv("Логин", account.login))

            if (account.isStudent) {
                addView(
                    ui.row(
                        R.drawable.ic_card_chart,
                        "Рейтинг класса",
                        "место в классе и результаты одноклассников"
                    ) { openPage(PAGE_CLASS) }
                )
            }
            addView(
                ui.row(
                    R.drawable.ic_card_person,
                    "Связь с учителями",
                    teacherContactLine(account)
                ) { openPage(PAGE_TEACHERS) }
            )
        }
    }

    private fun avatarView(): TextView {
        val size = ui.dp(58)
        val bitmap = Avatars.load(this, prefs.avatarImage)
        return TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 26f
            if (bitmap != null) {
                text = ""
                background = android.graphics.drawable.BitmapDrawable(resources, Avatars.circleBitmap(bitmap, size))
            } else {
                text = prefs.avatar
                background = androidx.core.content.ContextCompat.getDrawable(
                    this@ProfileActivity, R.drawable.bg_circle_surface
                )
            }
        }
    }

    private fun homeroomTeacherName(account: Account): String? =
        store.homeroomOf(account.school, account.grade, account.letter)?.fullName

    private fun teacherContactLine(account: Account): String {
        val count = store.teachers(account.school).size
        return when {
            account.school.isBlank() -> "школа не указана"
            count == 0 -> "учителя пока не зарегистрированы"
            else -> "учителей в школе: $count"
        }
    }

    private fun levelLine(score: Int): String = "уровень ${ru.readysquad.kvest.util.Leveling.level(score)}"

    private fun buildRows() {
        val account = me
        ui.card("Разделы") {
            val unread = account?.let { messages.unreadCount(it.id) } ?: 0

            if (account != null) {
                addView(
                    ui.row(
                        R.drawable.ic_card_book,
                        "Сообщения",
                        "личная переписка с учителями и классом",
                        badge = if (unread > 0) unread.toString() else ""
                    ) { openMessages(null) }
                )
            }
            addView(
                ui.row(
                    R.drawable.ic_card_bell,
                    "Уведомления",
                    "важные, учебные и системные"
                ) { openNotifications() }
            )
            addView(
                ui.row(
                    R.drawable.ic_settings,
                    "Настройки профиля",
                    "имя, аватар, класс, предметы, конфиденциальность"
                ) { openSettings() }
            )
            addView(
                ui.row(
                    R.drawable.ic_subj_info,
                    "Настройки приложения",
                    "цвет, тема, уведомления, данные, о приложении"
                ) { openAppSettings() }
            )
            addView(
                ui.row(
                    R.drawable.ic_badge_shield,
                    "Разработки проекта",
                    "команда приложения: префиксы и профили"
                ) { openPeople(PeopleActivity.MODE_DEVELOPERS) }
            )
            addView(
                ui.row(
                    R.drawable.ic_share,
                    "Поделиться прогрессом",
                    "отчёт для родителей: точность, серия, слабые темы"
                ) { shareProgress() }
            )
            addView(
                ui.row(
                    R.drawable.ic_badge_cup,
                    "Достижения",
                    "открыто ${repo.unlockedCount()} из ${repo.badges().size}"
                ) { openPage(PAGE_ACHIEVEMENTS) }
            )

            if (account != null && account.isTeacher) {
                addView(
                    ui.row(
                        R.drawable.ic_card_check,
                        "Выдать задание",
                        "классу или своему классу: предмет, уровень, тема"
                    ) { startActivity(Intent(this@ProfileActivity, AssignActivity::class.java)) }
                )
            }

            if (account != null && account.isTeacher &&
                account.positions.contains(Positions.ADMIN)
            ) {
                addView(
                    ui.row(
                        R.drawable.ic_settings,
                        "Управление школой",
                        "классы, классные руководители, должности и данные учеников"
                    ) { startActivity(Intent(this@ProfileActivity, SchoolAdminActivity::class.java)) }
                )
            }
            // «Список класса» нужен ученику и классному руководителю. Обычному учителю — нет.
            if (account != null && (account.isStudent || account.hasClass)) {
                addView(
                    ui.row(
                        R.drawable.ic_nav_results,
                        "Список класса",
                        if (account.isStudent) "кто в каком месте" else "ученики твоего класса"
                    ) { openPage(PAGE_CLASS) }
                )
            }
            addView(
                ui.row(
                    R.drawable.ic_subj_math,
                    "Список предметов",
                    "что сдаёшь и что готовишь"
                ) { openPage(PAGE_SUBJECTS) }
            )
            addView(
                ui.row(
                    R.drawable.ic_card_check,
                    "Оценки",
                    "прогноз по последним работам"
                ) { openPage(PAGE_GRADES) }
            )
            addView(
                ui.row(
                    R.drawable.ic_card_chart,
                    "Статистика",
                    "точность, время, темы"
                ) { openPage(PAGE_STATS) }
            )
            if (account?.isTeacher == true) {
                addView(
                    ui.row(
                        R.drawable.ic_nav_learn,
                        "Список классов",
                        "ученики и их классные руководители"
                    ) { openPage(PAGE_CLASSES) }
                )
                addView(
                    ui.row(
                        R.drawable.ic_card_person,
                        "Список учителей",
                        "все учителя школы"
                    ) { openPage(PAGE_TEACHERS) }
                )
            }
        }
    }

    private fun buildDevCard() {
        ui.card("Служебный режим") {
            addView(ui.text("Доступ разработчика: видны внутренние данные и быстрые действия.", 13f, R.color.on_bg_variant))
            val users = store.all(500)
            addView(ui.kv("Аккаунтов", users.size.toString(), 10))
            addView(ui.kv("Школ", store.knownSchools().size.toString()))
            addView(ui.kv("Ждут подтверждения", store.pendingApprovals().size.toString()))
            addView(ui.kv("Версия базы", DbHelper.VERSION.toString()))
            addView(ui.kv("Режим", if (me?.isDeveloper == true) "аккаунт разработчика" else "сессия разработчика"))
            addView(
                ui.button("Список всех аккаунтов", primary = false) {
                    val rows = users.joinToString("\n") {
                        "${it.fullName} · ${Roles.title(it.role)} · ${it.school} · ${it.status}"
                    }.ifBlank { "Пусто" }
                    openTextPage("Аккаунты", rows, PAGE_ACCOUNTS)
                }
            )
            addView(ui.button("Мой префикс", primary = false) { askMyPrefix() })
            addView(ui.button("Мой цвет ника", primary = false) { askMyColor() })
            addView(
                ui.button("Выйти из служебного режима", primary = false) {
                    prefs.devMode = false
                    prefs.role = Roles.GUEST
                    prefs.currentUserId = 0L
                    prefs.onboarded = false
                    startActivity(Intent(this@ProfileActivity, AuthActivity::class.java))
                    finishAffinity()
                }
            )
        }
    }

    private fun buildTail() {
        ui.card {
            addView(ui.row(R.drawable.ic_card_book, "Помощь", "частые вопросы и как всё устроено") { openPage(PAGE_HELP) })
            addView(ui.row(R.drawable.ic_subj_info, "О приложении", "версия, источники заданий") { openPage(PAGE_ABOUT) })
        }
        ui.body.addView(
            ui.text(
                "Квестефи · подготовка к ОГЭ и ЕГЭ · работает без интернета",
                11.5f, R.color.on_bg_variant, top = 14
            )
        )
    }

    private fun openMessages(other: Long?) {
        val account = me
        if (account == null) {
            openTextPage(
                "Сообщения",
                "Переписка доступна после регистрации: нужно знать, кому писать.",
                PAGE_NOTE
            )
            return
        }
        startActivity(
            Intent(this, MessagesActivity::class.java)
                .putExtra(MessagesActivity.EXTRA_OTHER, other ?: 0L)
        )
    }

    private fun openNotifications() {
        startActivity(Intent(this, NotificationsActivity::class.java))
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun openAppSettings() {
        startActivity(Intent(this, AppSettingsActivity::class.java))
    }

    private var pageBody = ""

    private fun openTextPage(title: String, body: String, target: Int) {
        pageBody = body
        pageTitleCache = title
        openPage(target)
    }

    private var pageTitleCache = ""

    private fun openPage(target: Int) {
        page = target
        rebuild()
    }

    private fun onBack() {
        if (page != 0) {
            page = 0
            rebuild()
            return
        }
        finish()
    }

    private fun pageTitle(): String = when (page) {
        PAGE_ACHIEVEMENTS -> "Достижения"
        PAGE_CLASS -> "Список класса"
        PAGE_SUBJECTS -> "Список предметов"
        PAGE_GRADES -> "Оценки"
        PAGE_STATS -> "Статистика"
        PAGE_TEACHERS -> "Связь с учителями"
        PAGE_DEVICES -> "Устройства"
        PAGE_CLASSES -> "Список классов"
        PAGE_ACCOUNTS, PAGE_ROSTER, PAGE_NOTE ->
            pageTitleCache.ifBlank { "Подробнее" }
            PAGE_HELP -> "Помощь"
            PAGE_QUESTIONS -> "Вопросы о Квестефи"
            PAGE_FEATURES -> "Возможности Квестефи"
            PAGE_PRIVACY -> "Политика конфиденциальности"
            PAGE_FAQ -> "Частые вопросы"
            PAGE_ABOUT -> "О приложении"
            else -> "Профиль"
    }

    private fun renderPage() {
        val account = me
        when (page) {
            PAGE_ACHIEVEMENTS -> {
                val badges = repo.badges()
                ui.card("Достижения") {
                    addView(
                        ui.text(
                            "Открыто ${badges.count { it.isUnlocked }} из ${badges.size}",
                            12f, R.color.on_bg_variant, top = 8
                        )
                    )
                    badges.forEach { badge ->
                        addView(
                            ui.text(
                                badge.emoji + "  " + badge.title,
                                14.5f,
                                if (badge.isUnlocked) R.color.on_bg else R.color.on_bg_variant,
                                bold = true, top = 14
                            )
                        )
                        addView(ui.text(badge.description, 12f, R.color.on_bg_variant))
                        addView(
                            ui.text(
                                if (badge.isUnlocked) "открыто" else "ещё не открыто",
                                11.5f,
                                if (badge.isUnlocked) R.color.success else R.color.on_bg_variant
                            )
                        )
                    }
                }
            }

            PAGE_CLASS -> {
                ui.card("Список класса") {
                    if (account == null) {
                        addView(ui.text("Нужен аккаунт.", 13f, R.color.on_bg_variant))
                        return@card
                    }
                    if (account.isStudent) {
                        val rows = store.classRating(account)
                        addView(
                            ui.text(
                                Grades.label(account.grade, account.letter) + " · участников: ${rows.size}",
                                12f, R.color.on_bg_variant
                            )
                        )
                        rows.forEachIndexed { index, person ->
                            addView(
                                ui.text(
                                    (index + 1).toString() + ". " + person.address(prefs.addressByName),
                                    14f, R.color.on_bg, bold = index == 0, top = 12
                                )
                            )
                            addView(ui.text("очков: ${person.score}", 12f, R.color.on_bg_variant))
                        }
                        return@card
                    }
                    if (!account.hasClass) {
                        addView(
                            ui.text(
                                "Список класса есть у ученика и у классного руководителя. " +
                                    "Если ты ведёшь класс, попроси администрацию школы назначить тебя.",
                                13f, R.color.on_bg_variant
                            )
                        )
                        return@card
                    }
                    val label = account.homeroom.ifBlank { Grades.label(account.grade, account.letter) }
                    val parts = label.trim().split(" ")
                    val grade = parts.firstOrNull().orEmpty().filter { it.isDigit() }
                    val letter = parts.firstOrNull().orEmpty().filter { it.isLetter() }
                    val students = store.studentsOf(account.school, grade, letter)
                        .sortedByDescending { it.score }
                    addView(ui.text("$label · учеников: ${students.size}", 12f, R.color.on_bg_variant))
                    if (students.isEmpty()) {
                        addView(ui.text("В классе пока нет учеников.", 13f, R.color.on_bg_variant, top = 10))
                    }
                    students.forEachIndexed { index, person ->
                        addView(
                            ui.text(
                                (index + 1).toString() + ". " + person.address(prefs.addressByName),
                                14f, R.color.on_bg, bold = index == 0, top = 12
                            )
                        )
                        addView(ui.text("очков: ${person.score}", 12f, R.color.on_bg_variant))
                    }
                }
            }

            PAGE_CLASSES -> {
                ui.card("Классы школы") {
                    if (account == null || account.school.isBlank()) {
                        addView(ui.text("Школа не указана.", 13f, R.color.on_bg_variant))
                        return@card
                    }
                    val classes = store.classesOf(account.school)
                    if (classes.isEmpty()) {
                        addView(ui.text("В школе пока нет классов с учениками.", 13f, R.color.on_bg_variant))
                    }
                    classes.forEach { (label, count, homeroom) ->
                        addView(ui.text(label, 14.5f, R.color.on_bg, bold = true, top = 14))
                        addView(ui.text("учеников: $count", 12f, R.color.on_bg_variant))
                        addView(
                            ui.text(
                                "классный руководитель: " + (homeroom?.fullName ?: "не назначен"),
                                12f, R.color.on_bg_variant
                            )
                        )
                    }
                }
            }

            PAGE_SUBJECTS -> {
                ui.card("Предметы") {
                    val scores = repo.bestScores()
                    val chosen = prefs.chosenSubjects
                    addView(
                        ui.text(
                            if (chosen.isEmpty()) "Выбранные для сдачи предметы в настройках."
                            else "Отмечено как сдающие: ${chosen.size}",
                            12f, R.color.on_bg_variant, top = 8
                        )
                    )
                    repo.subjects().forEach { subject ->
                        val best = scores[subject.code] ?: 0
                        addView(
                            ui.text(
                                subject.title,
                                14.5f, R.color.on_bg, bold = chosen.contains(subject.code), top = 12
                            )
                        )
                        addView(
                            ui.text(
                                if (best > 0) "лучший результат: $best из 100" else "ещё не проходил",
                                12f,
                                if (best > 0) R.color.on_bg_variant else R.color.on_bg_variant
                            )
                        )
                    }
                }
            }

            PAGE_GRADES -> {
                ui.card("Оценки") {
                    val attempts = repo.attempts(40)
                    if (attempts.isEmpty()) {
                        addView(
                            ui.text(
                                "Оценок пока нет: пройди первый квест, и здесь появится оценка.",
                                13f, R.color.on_bg_variant
                            )
                        )
                        return@card
                    }
                    var sum = 0.0
                    attempts.forEach { attempt ->
                        sum += attempt.grade
                        addView(
                            ui.text(
                                attempt.subjectEmoji + "  " + attempt.subjectTitle,
                                14f, R.color.on_bg, bold = true, top = 14
                            )
                        )
                        addView(
                            ui.text(
                                when (attempt.grade) {
                                    5 -> "оценка 5"
                                    4 -> "оценка 4"
                                    3 -> "оценка 3"
                                    else -> "оценка 2"
                                } + " · верно ${attempt.correct} из ${attempt.total} · очки ${attempt.score}",
                                12f, R.color.on_bg_variant
                            )
                        )
                        addView(ui.text(dateOf(attempt.createdAt), 11.5f, R.color.on_bg_variant))
                    }
                    addView(
                        ui.text(
                            "Средняя оценка: " + String.format("%.2f", sum / attempts.size),
                            13f, R.color.accent, bold = true, top = 16
                        )
                    )
                }
            }

            PAGE_STATS -> {
                val stats = repo.stats()
                ui.card("Статистика") {
                    addView(ui.kv("Пройдено квестов", stats.quests.toString(), 8))
                    addView(ui.kv("Верных ответов", stats.correct.toString() + " из " + stats.total))
                    addView(ui.kv("Точность", stats.accuracy.toString() + "%"))
                    addView(ui.kv("Очки", stats.totalScore.toString()))
                    addView(ui.kv("Лучший результат", stats.bestScore.toString() + " из 100"))
                    addView(ui.kv("Предметов затронуто", stats.distinctSubjects.toString()))
                    addView(ui.kv("Времени за заданиями", stats.minutes.toString() + " мин"))
                }
                val weak = repo.topicStats().sortedBy { it.accuracy }.take(6)
                ui.card("Слабые темы") {
                    if (weak.isEmpty()) {
                        addView(ui.text("Пока нет данных по темам.", 13f, R.color.on_bg_variant))
                    }
                    weak.forEach { stat ->
                        addView(ui.text(stat.topic, 14f, R.color.on_bg, bold = true, top = 12))
                        addView(
                            ui.text(
                                "верно ${stat.correct} из ${stat.total} · ${stat.accuracy}%",
                                12f,
                                if (stat.accuracy < 50) R.color.danger else R.color.on_bg_variant
                            )
                        )
                    }
                }
            }

            PAGE_TEACHERS -> {
                ui.card("Учителя школы") {
                    if (account == null || account.school.isBlank()) {
                        addView(ui.text("Школа не указана.", 13f, R.color.on_bg_variant))
                        return@card
                    }
                    val teachers = store.teachers(account.school)
                    if (teachers.isEmpty()) {
                        addView(ui.text("В школе пока нет подтверждённых учителей.", 13f, R.color.on_bg_variant))
                    }
                    addView(
                        ui.text(
                            "Писать можно в разделе «Сообщения» — там учителя выбираются списком.",
                            12f, R.color.on_bg_variant, top = 8
                        )
                    )
                    teachers.forEach { teacher ->
                        addView(
                            ui.text(
                                teacher.address(prefs.addressByName).ifBlank { teacher.fullName },
                                14.5f, R.color.on_bg, bold = true, top = 14
                            )
                        )
                        addView(ui.text(teacher.positionLine, 12f, R.color.on_bg_variant))
                        if (teacher.subject.isNotBlank()) {
                            addView(ui.text("предмет: " + teacher.subject, 12f, R.color.on_bg_variant))
                        }
                        if (teacher.homeroom.isNotBlank()) {
                            addView(ui.text("класс: " + teacher.homeroom, 12f, R.color.on_bg_variant))
                        }
                    }
                }
            }

            PAGE_DEVICES -> {
                ui.card("Устройства") {
                    addView(
                        ui.text(
                            "Входы в аккаунт видны в разделе «Устройства» профиля. " +
                                "Здесь можно посмотреть устройство и время последнего входа.",
                            13f, R.color.on_bg
                        )
                    )
                }
            }

            PAGE_ACCOUNTS, PAGE_ROSTER, PAGE_NOTE -> {
                ui.card(pageTitleCache.ifBlank { "Подробнее" }) {
                    addView(ui.text(pageBody, 13.5f, R.color.on_bg, top = 8))
                }
            }

            PAGE_HELP -> {
                ui.card("Справка") {
                    addView(
                        ui.text(
                            "Разделы справки: о приложении, его возможностях, конфиденциальности " +
                                "и связь с разработчиками.",
                            12f, R.color.on_bg_variant, top = 4
                        )
                    )
                    addView(
                        ui.row(
                            R.drawable.ic_card_book,
                            "Вопросы о Квестефи",
                            "что это за приложение и для кого"
                        ) { openPage(PAGE_QUESTIONS) }
                    )
                    addView(
                        ui.row(
                            R.drawable.ic_card_rocket,
                            "Возможности Квестефи",
                            "что умеет приложение"
                        ) { openPage(PAGE_FEATURES) }
                    )
                    addView(
                        ui.row(
                            R.drawable.ic_badge_shield,
                            "Политика конфиденциальности",
                            "какие данные и где хранятся"
                        ) { openPage(PAGE_PRIVACY) }
                    )
                    addView(
                        ui.row(
                            R.drawable.ic_badge_bulb,
                            "Часто задаваемые вопросы",
                            "короткие ответы на главное"
                        ) { openPage(PAGE_FAQ) }
                    )
                    addView(
                        ui.row(
                            R.drawable.ic_send,
                            "Поддержка",
                            "вопросы, жалобы и предложения разработчикам"
                        ) { startActivity(Intent(this@ProfileActivity, SupportActivity::class.java)) }
                    )
                }
            }

            PAGE_QUESTIONS -> {
                ui.card("Вопросы о Квестефи") {
                    addView(
                        ui.text(
                            "Квестефи — мобильное приложение для подготовки к ОГЭ и ЕГЭ. " +
                                "Ты решаешь задачи в формате экзамена, держишь серию дней и видишь, " +
                                "какие темы проседают.",
                            13f, R.color.on_bg, top = 8
                        )
                    )
                    addView(
                        ui.text(
                            "Для кого: школьники 5–11 классов готовятся сами, учителя выдают задания " +
                                "классу и смотрят отчёты, администрация школы ведёт классы и учителей.",
                            13f, R.color.on_bg, top = 10
                        )
                    )
                    addView(
                        ui.text(
                            "Чем отличается от каталога задач: здесь есть игровая механика — очки за " +
                                "скорость, серия дней, 13 достижений, рейтинг класса и понятный прогноз оценки.",
                            13f, R.color.on_bg, top = 10
                        )
                    )
                    addView(
                        ui.text(
                            "Приложение работает и без интернета: встроенный банк заданий, теория, " +
                                "генератор тренировки и вся статистика доступны офлайн.",
                            13f, R.color.on_bg, top = 10
                        )
                    )
                }
            }

            PAGE_FEATURES -> {
                ui.card("Возможности Квестефи") {
                    addView(ui.text("Квесты", 14.5f, R.color.on_bg, bold = true, top = 8))
                    addView(
                        ui.text(
                            "11 предметов с метками ОГЭ и ЕГЭ. За квест — 5 заданий, очки за " +
                                "верный ответ, бонусы за скорость и серию, разбор после каждой задачи.",
                            13f, R.color.on_bg_variant, top = 4
                        )
                    )
                    addView(ui.text("Реальные задания", 14.5f, R.color.on_bg, bold = true, top = 12))
                    addView(
                        ui.text(
                            "Задания приходят из открытого банка в момент решения: настоящие " +
                                "формулировки, рисунок, разбор и ссылка на источник. Ответ вписывается " +
                                "в клетки, как в бланке ответов.",
                            13f, R.color.on_bg_variant, top = 4
                        )
                    )
                    addView(ui.text("Обучение", 14.5f, R.color.on_bg, bold = true, top = 12))
                    addView(
                        ui.text(
                            "Шпаргалки по темам, статьи из свободных источников, короткие выжимки " +
                                "по запросу и подборка курсов из открытого каталога Stepik.",
                            13f, R.color.on_bg_variant, top = 4
                        )
                    )
                    addView(ui.text("Результаты и мотивация", 14.5f, R.color.on_bg, bold = true, top = 12))
                    addView(
                        ui.text(
                            "График прогресса, история попыток, слабые темы, прогноз оценки, " +
                                "серия дней и 13 достижений.",
                            13f, R.color.on_bg_variant, top = 4
                        )
                    )
                    addView(ui.text("Класс и учитель", 14.5f, R.color.on_bg, bold = true, top = 12))
                    addView(
                        ui.text(
                            "Учитель выдаёт задания классу и смотрит отчёты, работает рейтинг класса " +
                                "и переписка внутри школы.",
                            13f, R.color.on_bg_variant, top = 4
                        )
                    )
                }
            }

            PAGE_PRIVACY -> {
                ui.card("Политика конфиденциальности") {
                    addView(
                        ui.text(
                            "Что хранится на устройстве",
                            14.5f, R.color.on_bg, bold = true, top = 8
                        )
                    )
                    addView(
                        ui.text(
                            "Профиль, класс, школа, результаты попыток, шпаргалки и достижения — " +
                                "в локальной базе SQLite и настройках приложения. Эти данные не " +
                                "отправляются на сторонние серверы.",
                            13f, R.color.on_bg_variant, top = 4
                        )
                    )
                    addView(ui.text("Что уходит в интернет", 14.5f, R.color.on_bg, bold = true, top = 12))
                    addView(
                        ui.text(
                            "Только то, что нужно для работы: реальные задания запрашиваются у " +
                                "открытого образовательного портала, статьи и картинки — у Википедии, " +
                                "Викисловаря и Wikimedia Commons, курсы — у каталога Stepik. " +
                                "Полученные задания не сохраняются в приложении.",
                            13f, R.color.on_bg_variant, top = 4
                        )
                    )
                    addView(ui.text("Обмен внутри школы", 14.5f, R.color.on_bg, bold = true, top = 12))
                    addView(
                        ui.text(
                            "Задания и результаты передаются между устройствами только по локальной " +
                                "сети Wi-Fi школы. Интернет для этого не нужен.",
                            13f, R.color.on_bg_variant, top = 4
                        )
                    )
                    addView(ui.text("Сторонние материалы", 14.5f, R.color.on_bg, bold = true, top = 12))
                    addView(
                        ui.text(
                            "Тексты Википедии и Викисловаря используются по лицензии CC BY-SA, " +
                                "картинки — из Wikimedia Commons. У каждого задания видна ссылка на источник.",
                            13f, R.color.on_bg_variant, top = 4
                        )
                    )
                    addView(ui.text("Реклама и сбор данных", 14.5f, R.color.on_bg, bold = true, top = 12))
                    addView(
                        ui.text(
                            "Рекламы нет, данные не продаются и не передаются третьим лицам. " +
                                "В настройках можно скрыть себя из рейтинга класса и закрыть профиль " +
                                "от других пользователей.",
                            13f, R.color.on_bg_variant, top = 4
                        )
                    )
                }
            }

            PAGE_FAQ -> {
                ui.card("Часто задаваемые вопросы") {
                    addView(faqItem("Нужен ли интернет?", "Для квестов по реальным заданиям, библиотеки и курсов — да. Встроенные задания, теория и статистика работают офлайн."))
                    addView(faqItem("Как получить логин и пароль?", "Логин и пароль выдаёт школа: классный руководитель или администрация. При самостоятельной регистрации ты придумываешь их сам."))
                    addView(faqItem("Забыл пароль, что делать?", "Пароль меняет школа. Обратись к классному руководителю или в администрацию — они восстановят доступ."))
                    addView(faqItem("Почему ученик видит 5 заданий вместо 10?", "Так бывает, если в выбранной теме меньше задач. Приложение добирает остальные из других тем предмета и сообщает об этом учителю."))
                    addView(faqItem("Как попасть в рейтинг класса?", "Нужно учиться в том же классе и не выключать показ себя в рейтинге. Проверить можно в настройках профиля."))
                    addView(faqItem("Как сообщить об ошибке?", "Раздел «Поддержка» в помощи: опиши проблему, разработчики увидят обращение и ответят в этом же разделе."))
                }
            }

            PAGE_ABOUT -> {
                ui.card("О приложении") {
                    addView(ui.kv("Название", "Квестефи", 8))
                    addView(ui.kv("Версия", ru.readysquad.kvest.CrashLog.appVersion(this@ProfileActivity)))
                    addView(
                        ui.text(
                            "Подготовка к ОГЭ и ЕГЭ в игровой форме. Работает без интернета: " +
                                "задания, шпаргалки и статистика хранятся на устройстве.",
                            13f, R.color.on_bg, top = 12
                        )
                    )
                    addView(
                        ui.text(
                            "Задания по русскому языку и математике берутся из открытого банка " +
                                "Сдам ГИА. Разметка и оформление — ReadySquad.",
                            13f, R.color.on_bg_variant, top = 10
                        )
                    )
                }
            }
        }
    }

    private fun dateOf(at: Long): String =
        java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(at))

    private fun faqItem(question: String, answer: String): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, ui.dp(12), 0, 0)
        }
        box.addView(
            TextView(this).apply {
                text = question
                textSize = 14.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ui.color(R.color.on_bg))
            }
        )
        box.addView(ui.text(answer, 13f, R.color.on_bg_variant, top = 4))
        return box
    }

    private fun badgeRow(badge: Badge): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, ui.dp(8), 0, ui.dp(8))
        }
        row.addView(
            TextView(this).apply {
                text = if (badge.isUnlocked) badge.title else "${badge.title} (закрыто)"
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ui.color(if (badge.isUnlocked) R.color.accent else R.color.on_bg_variant))
            }
        )
        row.addView(ui.text(badge.description, 12.5f, R.color.on_bg_variant, top = 2))
        return row
    }

    private fun showClassStudents(account: Account, label: String) {
        val parts = label.trim().split(" ")
        val grade = parts.firstOrNull().orEmpty().filter { it.isDigit() }
        val letter = parts.firstOrNull().orEmpty().filter { it.isLetter() }
        val students = store.studentsOf(account.school, grade, letter)
        val text = if (students.isEmpty()) "Пусто" else students.joinToString("\n") { s ->
            "${s.fullName} · ${s.score} очков · ${s.status}"
        }
        openTextPage("Класс $label", text, PAGE_ROSTER)
    }

    private fun dialog(title: String, text: String) {
        val view = TextView(this).apply {
            this.text = text
            textSize = 13.5f
            setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(4))
            setTextColor(ui.color(R.color.on_bg))
            setLineSpacing(ui.dp(4).toFloat(), 1f)
        }
        val scroll = ScrollView(this).apply { addView(view) }
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton("Понятно", null)
            .show()
    }

    private fun unreadNotifications(): Int {
        val items = Notifier.alerts(this, prefs) +
            Notifier.build(prefs, repo.stats(), repo.badges(), repo.topicStats(null))
        return items.size
    }
}

private const val PAGE_ACHIEVEMENTS = 1
private const val PAGE_CLASS = 2
private const val PAGE_SUBJECTS = 3
private const val PAGE_GRADES = 4
private const val PAGE_STATS = 5
private const val PAGE_TEACHERS = 6
private const val PAGE_HELP = 7
private const val PAGE_ABOUT = 8
private const val PAGE_DEVICES = 9
private const val PAGE_CLASSES = 10
private const val PAGE_ACCOUNTS = 11
private const val PAGE_ROSTER = 12
private const val PAGE_NOTE = 13
private const val PAGE_QUESTIONS = 14
private const val PAGE_FEATURES = 15
private const val PAGE_PRIVACY = 16
private const val PAGE_FAQ = 17
