package ru.readysquad.kvest.ui

import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ru.readysquad.kvest.R
import ru.readysquad.kvest.data.Account
import ru.readysquad.kvest.data.AccountStore
import ru.readysquad.kvest.data.Grades

import ru.readysquad.kvest.data.ClassStore
import ru.readysquad.kvest.data.NickColors
import ru.readysquad.kvest.data.Positions
import ru.readysquad.kvest.data.Prefs
import ru.readysquad.kvest.data.Prefixes
import ru.readysquad.kvest.data.StatusMarks
import ru.readysquad.kvest.util.Palettes
import ru.readysquad.kvest.data.Roles
import ru.readysquad.kvest.data.SchoolSeed
import ru.readysquad.kvest.data.SchoolStore
import ru.readysquad.kvest.data.SessionStore
import ru.readysquad.kvest.util.Screens
import ru.readysquad.kvest.util.Validate

class PeopleActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_TEACHERS = 1
        const val MODE_STUDENTS = 2
        const val MODE_SCHOOLS = 3
        const val MODE_APPROVALS = 4
        const val MODE_DEVICES = 6
        const val MODE_DEVELOPERS = 7
    }

    private lateinit var prefs: Prefs
    private lateinit var store: AccountStore
    private lateinit var schools: SchoolStore
    private lateinit var ui: Screens

    private var mode = MODE_TEACHERS
    private var personId = 0L
    private var editing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        Palettes.apply(this, prefs.palette)
        super.onCreate(savedInstanceState)
        store = AccountStore(this)
        schools = SchoolStore(this)
        mode = intent.getIntExtra(EXTRA_MODE, MODE_TEACHERS)
        ui = Screens(this)
        ui.install(title()) { onBack() }
        render()
    }

    private fun onBack() {

        if (personId > 0L || editing) {
            personId = 0L
            editing = false
            rebuild()
            return
        }
        finish()
    }

    private fun title(): String = when {
        personId > 0L -> if (editing) "Правка данных" else "Карточка"
        mode == MODE_TEACHERS -> "Все учителя"
        mode == MODE_STUDENTS -> "Все ученики"
        mode == MODE_SCHOOLS -> "Справочник школ"
        mode == MODE_DEVICES -> "Устройства"
        mode == MODE_DEVELOPERS -> "Разработки проекта"
        else -> "Заявки"
    }

    private fun rebuild() {
        ui.body.removeAllViews()
        ui.header(title()) { onBack() }
        render()
    }

    private fun render() {
        when {
            personId > 0L -> renderPerson()
            mode == MODE_TEACHERS -> renderPeople(Roles.TEACHER)
            mode == MODE_STUDENTS -> renderPeople(Roles.STUDENT)
            mode == MODE_SCHOOLS -> renderSchools()
            mode == MODE_DEVICES -> renderDevices()
            mode == MODE_DEVELOPERS -> renderPeople(Roles.DEVELOPER)
            else -> renderApprovals()
        }
    }

    private fun renderPeople(role: String) {
        // Учителя и администрация видят только свою школу: чужие аккаунты недоступны.
        val viewer = store.byId(prefs.currentUserId)
        val everywhere = prefs.role == Roles.DEVELOPER || prefs.devMode
        val mySchool = viewer?.school.orEmpty()
        val all = store.everyone().filter { it.role == role && (everywhere || mySchool.isBlank() || it.school == mySchool) }
        if (all.isEmpty()) {
            val developerList = role == Roles.DEVELOPER
            ui.card(if (developerList) "Команда проекта" else "Пусто") {
                addView(
                    ui.text(
                        if (developerList) {
                            "Участников команды пока нет. Когда разработчик зарегистрируется " +
                                "в приложении, он появится здесь: имя будет выделено красным, " +
                                "а рядом — префикс «Разработчик» или «Агент Поддержки». " +
                                "Открыть профиль разработчика сможет любой пользователь."
                        } else {
                            "Пока никого нет."
                        },
                        14f, R.color.on_bg_variant
                    )
                )
            }
            return
        }
        val listTitle = when (role) {
            Roles.TEACHER -> "Учителя"
            Roles.DEVELOPER -> "Команда проекта"
            else -> "Ученики"
        }
        ui.card(listTitle) {
            addView(ui.text("Всего: ${all.size}. Нажми строку, чтобы открыть данные.", 12f, R.color.on_bg_variant))
            all.groupBy { it.school.ifBlank { "школа не указана" } }.forEach { (school, list) ->
                addView(ui.text(school, 13f, R.color.accent, bold = true, top = 14))
                list.forEach { person -> addView(personRow(person)) }
            }
        }
    }

    private fun personRow(person: Account): LinearLayout {
        val subtitle = buildList {
            if (person.isTeacher) add(person.positionLine) else add(Grades.label(person.grade, person.letter))
            val status = StatusMarks.statusLine(person)
            if (status.isNotBlank()) add(status)
            if (person.status != "approved") add("заявка: " + statusWord(person.status))
            add("логин " + person.login)
        }.joinToString(" · ")
        val name = person.address(prefs.addressByName).ifBlank { person.fullName }
        val prefix = StatusMarks.prefixOf(person)
        return ui.row(
            if (person.isTeacher) R.drawable.ic_card_person else R.drawable.ic_nav_results,
            if (prefix.isBlank()) name else "$prefix · $name",
            subtitle,
            titleColor = StatusMarks.colorOf(
                person, ui.color(R.color.on_bg), ui.color(R.color.danger)
            ),
            bold = person.isDeveloper
        ) {
            personId = person.id
            editing = false
            rebuild()
        }
    }

    private fun statusWord(status: String): String = when (status) {
        "approved" -> "подтверждён"
        "pending" -> "ждёт подтверждения"
        else -> "отклонён"
    }

    private fun renderPerson() {
        val person = store.byId(personId)
        if (person == null) {
            ui.card("Не найдено") { addView(ui.text("Аккаунт не найден.", 14f, R.color.on_bg_variant)) }
            return
        }
        // Профиль как в мессенджере: аватар, имя с префиксом, очки и ключевые факты.
        val classInfo = if (person.isStudent) {
            ClassStore(this).find(person.school, person.grade, person.letter)
        } else {
            null
        }
        ui.card {
            val head = LinearLayout(this@PeopleActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            head.addView(
                TextView(this@PeopleActivity).apply {
                    text = person.fullName.trim().take(1).uppercase().ifBlank { "?" }
                    textSize = 26f
                    gravity = Gravity.CENTER
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(ui.color(R.color.on_primary))
                    background = ContextCompat.getDrawable(this@PeopleActivity, R.drawable.bg_pill_primary)
                    layoutParams = LinearLayout.LayoutParams(ui.dp(68), ui.dp(68))
                }
            )
            val titles = LinearLayout(this@PeopleActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(ui.dp(14), 0, 0, 0)
            }
            titles.addView(
                TextView(this@PeopleActivity).apply {
                    val prefix = StatusMarks.prefixOf(person)
                    text = if (prefix.isBlank()) person.fullName else prefix + " · " + person.fullName
                    textSize = 19f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(
                        StatusMarks.colorOf(person, ui.color(R.color.on_bg), ui.color(R.color.danger))
                    )
                }
            )
            titles.addView(
                TextView(this@PeopleActivity).apply {
                    text = listOf(Roles.title(person.role), StatusMarks.statusLine(person))
                        .filter { it.isNotBlank() }.joinToString(" · ")
                    textSize = 12.5f
                    setTextColor(ui.color(R.color.on_bg_variant))
                }
            )
            head.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(head)
        }

        ui.card("О пользователе") {
            addView(ui.kv("Очки", person.score.toString(), 12))
            addView(ui.kv("Школа", person.school.ifBlank { "не указана" }))
            if (person.isStudent) {
                addView(ui.kv("Класс", Grades.label(person.grade, person.letter)))
                addView(
                    ui.kv(
                        "Классный руководитель",
                        classInfo?.homeroomName?.ifBlank { "не назначен" } ?: "не назначен"
                    )
                )
            } else if (!person.isDeveloper) {
                addView(ui.kv("Должность", person.positionLine))
                addView(ui.kv("Предметы", person.subject.ifBlank { "не указаны" }))
                addView(ui.kv("Классное руководство", person.homeroom.ifBlank { "нет" }))
            }
        }

        val viewer = store.byId(prefs.currentUserId)
        val canSeePrivate = prefs.role == Roles.DEVELOPER || prefs.devMode ||
            person.id == prefs.currentUserId || Positions.isApprover(viewer?.position)

        ui.card("Данные") {
            if (!editing) {
                addView(ui.kv("ФИО", person.fullName.ifBlank { "не указано" }, 12))
                addView(ui.kv("Роль", Roles.title(person.role)))
                if (!person.isDeveloper) addView(ui.kv("Должность", person.positionLine))
                addView(ui.kv("Префикс", StatusMarks.prefixOf(person).ifBlank { "нет" }))
                addView(ui.kv("Школа", person.school.ifBlank { "не указана" }))
                if (!person.isDeveloper) addView(ui.kv("Класс", Grades.label(person.grade, person.letter)))
                addView(ui.kv("Очки", person.score.toString()))
                val statuses = StatusMarks.statusLine(person)
                if (statuses.isNotBlank()) addView(ui.kv("Статусы", statuses))

                if (canSeePrivate) {
                    addView(ui.kv("Предмет", person.subject.ifBlank { "не указан" }))
                    addView(ui.kv("Классное руководство", person.homeroom.ifBlank { "нет" }))
                    addView(ui.kv("Логин", person.login))
                    addView(ui.kv("Статус", statusWord(person.status)))
                    addView(ui.kv("Почта", person.phone.ifBlank { "не указана" }))
                    addView(ui.kv("Дата рождения", person.birthDate.ifBlank { "не указана" }))
                    addView(ui.kv("Регион", person.region.ifBlank { "не указан" }))
                    addView(ui.kv("Заявка от", person.approvedBy.ifBlank { "—" }))
                    addView(ui.button("Изменить данные") {
                        editing = true
                        rebuild()
                    })
                } else {
                    addView(
                        ui.text(
                            "Это публичная часть профиля. Логин, почта и дата рождения видны " +
                                "только самому пользователю и администрации школы.",
                            12f, R.color.on_bg_variant, top = 10
                        )
                    )
                }
            } else {
                addView(ui.text("Меняй только то, что нужно: остальное останется как было.", 12f, R.color.on_bg_variant))
                val nameField = input("ФИО", person.fullName)
                val schoolField = input("Школа", person.school)
                val positionField = input("Должности через запятую", person.position)
                val subjectField = input("Предмет", person.subject)
                val gradeField = input("Класс (например 9)", person.grade)
                val letterField = input("Буква класса", person.letter)
                val homeroomField = input("Классное руководство (например 9А)", person.homeroom)
                addView(nameField)
                addView(schoolField)
                addView(positionField)
                addView(subjectField)
                addView(gradeField)
                addView(letterField)
                addView(homeroomField)
                addView(ui.button("Сохранить") {
                    val name = nameField.text.toString().trim()
                    val problem = Validate.fullName(name)
                    if (problem != null) {
                        Toast.makeText(this@PeopleActivity, problem, Toast.LENGTH_LONG).show()
                        return@button
                    }
                    store.adminUpdate(
                        id = person.id,
                        fullName = name,
                        school = schoolField.text.toString().trim(),
                        position = positionField.text.toString().trim(),
                        role = person.role,
                        grade = gradeField.text.toString().trim(),
                        letter = letterField.text.toString().trim(),
                        subject = subjectField.text.toString().trim(),
                        homeroom = homeroomField.text.toString().trim(),
                        status = person.status
                    )
                    editing = false
                    Toast.makeText(this@PeopleActivity, "Сохранено", Toast.LENGTH_SHORT).show()
                    rebuild()
                })
                addView(ui.button("Отмена", primary = false) {
                    editing = false
                    rebuild()
                })
            }
        }

        ui.card("Статус заявки") {
            addView(ui.text("Текущий статус: " + statusWord(person.status), 13f, R.color.on_bg))
            addView(ui.button("Подтвердить", primary = false) {
                store.changeStatus(person.id, "approved")
                rebuild()
            })
            addView(ui.button("Отклонить", primary = false) {
                store.changeStatus(person.id, "rejected")
                rebuild()
            })
        }

        if (prefs.role == Roles.DEVELOPER || prefs.devMode) {
            ui.card("Служебное: префикс и роль") {
                addView(
                    ui.text(
                        "Префиксы и цвета ник ставит только разработчик. Можно выбрать готовый " +
                            "или придумать свой — он сохранится у пользователя и будет виден в списках.",
                        12f, R.color.on_bg_variant
                    )
                )
                addView(ui.button("Назначить префикс") { askPrefix(person) })
                addView(ui.button("Роль и должность", primary = false) { askRole(person) })
                addView(ui.button("Цвет ника", primary = false) { askColor(person) })
                addView(
                    ui.button(
                        if (person.isDeveloper) "Снять роль разработчика" else "Сделать разработчиком",
                        primary = false
                    ) {
                        store.adminUpdate(
                            id = person.id,
                            fullName = person.fullName,
                            school = person.school,
                            position = person.position,
                            role = if (person.isDeveloper) Roles.STUDENT else Roles.DEVELOPER,
                            grade = person.grade,
                            letter = person.letter,
                            subject = person.subject,
                            homeroom = person.homeroom,
                            status = person.status
                        )
                        Toast.makeText(this@PeopleActivity, "Роль изменена", Toast.LENGTH_SHORT).show()
                        rebuild()
                    }
                )
            }
        }
    }

    /** Разработчик меняет роль и должность: ученик, учитель, администрация школы, разработчик. */
    private fun askRole(person: Account) {
        val options = listOf(
            "Ученик" to (Roles.STUDENT to "Ученик"),
            "Учитель-предметник" to (Roles.TEACHER to Positions.SUBJECT),
            "Классный руководитель" to (Roles.TEACHER to Positions.HOMEROOM),
            "Администрация школы" to (Roles.TEACHER to Positions.ADMIN),
            "Разработчик" to (Roles.DEVELOPER to person.position)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("Роль: " + person.fullName)
            .setItems(options.map { it.first }.toTypedArray()) { _, which ->
                val chosen = options[which].second
                store.adminUpdate(
                    id = person.id,
                    fullName = person.fullName,
                    school = person.school,
                    position = chosen.second,
                    role = chosen.first,
                    grade = person.grade,
                    letter = person.letter,
                    subject = person.subject,
                    homeroom = person.homeroom,
                    status = person.status
                )
                Toast.makeText(this, "Роль обновлена", Toast.LENGTH_SHORT).show()
                rebuild()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun askPrefix(person: Account) {
        val options = Prefixes.all + "Убрать префикс"
        MaterialAlertDialogBuilder(this)
            .setTitle("Префикс для " + person.fullName)
            .setItems(options.toTypedArray()) { _, which ->
                store.setPrefix(person.id, if (which == options.lastIndex) "" else options[which])
                rebuild()
            }
            .setNeutralButton("Свой вариант") { _, _ -> askCustomPrefix(person) }
            .show()
    }

    private fun askCustomPrefix(person: Account) {
        val field = EditText(this).apply {
            hint = "Например: Куратор проекта"
            setText(person.prefix)
            setTextColor(ui.color(R.color.on_bg))
            setHintTextColor(ui.color(R.color.on_bg_variant))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Свой префикс")
            .setView(field)
            .setPositiveButton("Сохранить") { _, _ ->
                store.setPrefix(person.id, field.text.toString())
                rebuild()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun askColor(person: Account) {
        val labels = NickColors.all.map { it.second }.toTypedArray()
        val current = NickColors.all.indexOfFirst { it.first.equals(person.nickColor, true) }.coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle("Цвет ника")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                store.setNickColor(person.id, NickColors.all[which].first)
                dialog.dismiss()
                rebuild()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun input(label: String, value: String): EditText {
        val ctx = this
        return EditText(ctx).apply {
            hint = label
            setText(value)
            inputType = InputType.TYPE_CLASS_TEXT
            background = ContextCompat.getDrawable(ctx, R.drawable.bg_input)
            setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12))
            setTextColor(ui.color(R.color.on_bg))
            setHintTextColor(ui.color(R.color.on_bg_variant))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = ui.dp(10) }
        }
    }

    private fun renderSchools() {
        ui.card("Добавить школу") {
            addView(
                ui.text(
                    "Школы в приложении добавляют только разработчики: остальные выбирают " +
                        "из этого списка.",
                    12f, R.color.on_bg_variant
                )
            )
            val name = input("Название школы", "")
            val city = input("Город", SchoolSeed.cities.first())
            val address = input("Адрес", "")
            addView(name)
            addView(city)
            addView(address)
            addView(ui.button("Добавить в справочник") {
                val ok = schools.addByDeveloper(
                    name.text.toString(), city.text.toString(), address.text.toString(), "разработчик"
                )
                Toast.makeText(
                    this@PeopleActivity,
                    if (ok) "Школа добавлена" else "Не добавлено: пустое или уже есть",
                    Toast.LENGTH_LONG
                ).show()
                rebuild()
            })
        }

        ui.card("Заявки на школы") {
            val requests = schools.requests()
            if (requests.isEmpty()) {
                addView(ui.text("Новых заявок нет.", 13f, R.color.on_bg_variant))
            } else {
                requests.forEach { (name, city, by) ->
                    addView(ui.text(name, 13.5f, R.color.on_bg, bold = true, top = 12))
                    addView(ui.text("город: $city · просил: $by", 12f, R.color.on_bg_variant))
                    addView(ui.button("Добавить «$name»") {
                        schools.decideRequest(name, true)
                        rebuild()
                    })
                    addView(ui.button("Отклонить", primary = false) {
                        schools.decideRequest(name, false)
                        rebuild()
                    })
                }
            }
        }

        ui.card("Добавленные разработчиком") {
            val added = schools.all().filter { it.addedBy.isNotBlank() }
            if (added.isEmpty()) {
                addView(ui.text("Добавленных школ пока нет.", 13f, R.color.on_bg_variant))
            } else {
                added.forEach { entry ->
                    addView(ui.text(entry.caption, 13f, R.color.on_bg, top = 10))
                    addView(ui.button("Удалить", primary = false) {
                        schools.removeByDeveloper(entry.name)
                        rebuild()
                    })
                }
            }
        }

        ui.card("Справочник сейчас") {
            addView(ui.text("Школ в справочнике: ${schools.all().size}", 13f, R.color.on_bg))
        }
    }

    private fun renderDevices() {
        val sessions = SessionStore(this)
        val dev = prefs.role == Roles.DEVELOPER || prefs.devMode
        val events = if (dev) sessions.all() else sessions.of(prefs.currentUserId)
        ui.card("Устройства") {
            if (events.isEmpty()) {
                addView(ui.text("Входов пока не было.", 13f, R.color.on_bg_variant))
                return@card
            }
            addView(
                ui.text(
                    if (dev) "Всего входов: ${events.size}" else "Входов: ${events.size}",
                    12f, R.color.on_bg_variant
                )
            )
            val week = 1000L * 60 * 60 * 24 * 7
            val now = System.currentTimeMillis()
            events.distinctBy { it.userId.toString() + it.device }.forEach { event ->
                addView(ui.text(event.device, 13.5f, R.color.on_bg, bold = true, top = 14))
                addView(ui.text(event.place, 12f, R.color.on_bg_variant))
                addView(
                    ui.text(
                        if (dev) event.userName + " · вход " + time(event.createdAt)
                        else "вход " + time(event.createdAt),
                        11.5f, R.color.on_bg_variant
                    )
                )
                addView(
                    ui.text(
                        if (now - event.createdAt < week) "заходил на этой неделе" else "давно не заходил",
                        11.5f, R.color.accent
                    )
                )
            }
        }
    }

    private fun time(at: Long): String =
        java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(at))

    private fun renderApprovals() {
        val pending = store.pendingApprovals()
        ui.card("Заявки") {
            if (pending.isEmpty()) {
                addView(ui.text("Заявок нет.", 13f, R.color.on_bg_variant))
            } else {
                addView(ui.text("Всего: ${pending.size}", 12f, R.color.on_bg_variant))
                pending.forEach { (approval, user) ->
                    addView(
                        ui.text(
                            user.fullName + " · " + Roles.title(user.role),
                            13.5f, R.color.on_bg, bold = true, top = 14
                        )
                    )
                    addView(ui.text(user.school.ifBlank { "школа не указана" }, 12f, R.color.on_bg_variant))
                    addView(
                        ui.text(
                            "должность: " + user.positionLine + " · кому: " + approval.addressee,
                            12f, R.color.on_bg_variant
                        )
                    )
                    addView(ui.button("Подтвердить") {
                        store.decide(approval.id, true, "разработчик")
                        rebuild()
                    })
                    addView(ui.button("Отклонить", primary = false) {
                        store.decide(approval.id, false, "разработчик")
                        rebuild()
                    })
                }
            }
        }
    }
}
