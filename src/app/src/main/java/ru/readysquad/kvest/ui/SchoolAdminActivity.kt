package ru.readysquad.kvest.ui

import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ru.readysquad.kvest.R
import ru.readysquad.kvest.data.Account
import ru.readysquad.kvest.data.AccountStore
import ru.readysquad.kvest.data.ClassStore
import ru.readysquad.kvest.data.Grades
import ru.readysquad.kvest.data.Positions
import ru.readysquad.kvest.data.Prefs
import ru.readysquad.kvest.data.SchoolClass
import ru.readysquad.kvest.util.Chips
import ru.readysquad.kvest.util.Palettes
import ru.readysquad.kvest.util.Screens
import ru.readysquad.kvest.util.Validate

class SchoolAdminActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_CLASSES = 11
        const val MODE_TEACHERS = 12
        const val MODE_STUDENTS = 13
    }

    private lateinit var prefs: Prefs
    private lateinit var store: AccountStore
    private lateinit var classes: ClassStore
    private lateinit var ui: Screens

    private var mode = MODE_CLASSES
    private var openGrade = "9"
    private var openLetter = "А"
    private var pickedClass: SchoolClass? = null
    private var personId = 0L

    private val me: Account? get() = prefs.currentUserId.takeIf { it > 0 }?.let { store.byId(it) }
    private val school: String get() = me?.school.orEmpty()

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        Palettes.apply(this, prefs.palette)
        super.onCreate(savedInstanceState)
        store = AccountStore(this)
        classes = ClassStore(this)
        mode = intent.getIntExtra(EXTRA_MODE, MODE_CLASSES)
        ui = Screens(this)
        ui.install(title()) { onBack() }
        onBackPressedDispatcher.addCallback(
            this,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = onBack()
            }
        )
        render()
    }

    private fun admin(): Account? {
        val account = me ?: return null
        if (account == null) return null
        if (account.isDeveloper) return account
        return if (account.isTeacher && Positions.isApprover(account.position)) account else null
    }

    private fun title(): String = when {
        personId > 0L -> "Данные участника"
        pickedClass != null -> "Классный руководитель"
        mode == MODE_TEACHERS -> "Учителя школы"
        mode == MODE_STUDENTS -> "Ученики школы"
        else -> "Классы школы"
    }

    private fun onBack() {
        when {
            personId > 0L -> personId = 0L
            pickedClass != null -> pickedClass = null
            else -> {
                finish()
                return
            }
        }
        rebuild()
    }

    private fun rebuild() {
        ui.body.removeAllViews()
        ui.header(title()) { onBack() }
        render()
    }

    private fun render() {
        val account = admin()
        if (account == null) {
            ui.card("Недоступно") {
                addView(
                    ui.text(
                        "Управление школой доступно администрации школы. " +
                            "Если это твоя школа, попроси разработчика выдать должность.",
                        14f, R.color.on_bg_variant
                    )
                )
            }
            return
        }
        when {
            personId > 0L -> renderPerson()
            pickedClass != null -> renderHomeroomPicker()
            mode == MODE_TEACHERS -> renderTeachers(account)
            mode == MODE_STUDENTS -> renderStudents(account)
            else -> renderClasses(account)
        }
    }

    private fun renderClasses(account: Account) {
        val numbers = Grades.numbers
        val letters = Grades.letters

        ui.card("Завести класс") {
            addView(ui.text("Номер класса", 13f, R.color.accent, bold = true))
            addView(
                Chips.row(this@SchoolAdminActivity, numbers.map { it to (it == openGrade) }) { idx ->
                    openGrade = numbers[idx]
                    rebuild()
                }
            )
            addView(ui.text("Буква класса", 13f, R.color.accent, bold = true, top = 12))
            addView(
                Chips.row(this@SchoolAdminActivity, letters.map { it to (it == openLetter) }) { idx ->
                    openLetter = letters[idx]
                    rebuild()
                }
            )
            addView(
                ui.button("Создать класс " + Grades.label(openGrade, openLetter)) {
                    val ok = classes.create(account.school, openGrade, openLetter, null)
                    Toast.makeText(
                        this@SchoolAdminActivity,
                        if (ok) "Класс создан" else "Такой класс уже есть",
                        Toast.LENGTH_LONG
                    ).show()
                    rebuild()
                }
            )
        }

        val list = classes.all(account.school)
        ui.card("Классы школы") {
            if (list.isEmpty()) {
                addView(ui.text("Классов пока нет. Заведи первый выше.", 13f, R.color.on_bg_variant))
                return@card
            }
            addView(ui.text("Всего классов: ${list.size}", 12f, R.color.on_bg_variant))
            list.forEach { cls ->
                addView(ui.text(cls.label, 15f, R.color.on_bg, bold = true, top = 16))
                addView(ui.text("учеников: ${classes.studentsCount(cls.school, cls.grade, cls.letter)}", 12f, R.color.on_bg_variant))
                addView(
                    ui.text(
                        "классный руководитель: " + cls.homeroomName.ifBlank { "не назначен" },
                        12f, if (cls.homeroomName.isBlank()) R.color.danger else R.color.on_bg_variant
                    )
                )
                addView(
                    ui.button("Назначить руководителя") {
                        pickedClass = cls
                        rebuild()
                    }
                )
                addView(
                    ui.button("Удалить класс", primary = false) {
                        askDelete(cls)
                    }
                )
            }
        }
    }

    private fun askDelete(cls: SchoolClass) {
        val count = classes.studentsCount(cls.school, cls.grade, cls.letter)
        MaterialAlertDialogBuilder(this)
            .setTitle("Удалить класс " + cls.label + "?")
            .setMessage(
                if (count == 0) "В классе нет учеников."
                else "В классе $count учеников. Их класс будет очищен — потом нужно будет " +
                    "указать новый в карточке ученика."
            )
            .setPositiveButton("Удалить") { _, _ ->
                classes.remove(cls.school, cls.grade, cls.letter, clearStudents = true)
                Toast.makeText(this@SchoolAdminActivity, "Класс удалён", Toast.LENGTH_SHORT).show()
                rebuild()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun renderHomeroomPicker() {
        val cls = pickedClass ?: return
        val teachers = store.teachers(school)
        ui.card("Класс " + cls.label) {
            addView(ui.text("Сейчас: " + cls.homeroomName.ifBlank { "не назначен" }, 13f, R.color.on_bg))
            addView(
                ui.text(
                    "Нажми на учителя, чтобы сделать его классным руководителем этого класса. " +
                        "Прежний руководитель класса его потеряет.",
                    12f, R.color.on_bg_variant, top = 8
                )
            )
            if (teachers.isEmpty()) {
                addView(ui.text("В школе нет подтверждённых учителей.", 13f, R.color.on_bg_variant, top = 10))
            }
            teachers.forEach { teacher ->
                addView(
                    ui.button(
                        teacher.address(prefs.addressByName) + " · " + teacher.positionLine,
                        primary = false
                    ) {
                        classes.assignHomeroom(cls.school, cls.grade, cls.letter, teacher)
                        Toast.makeText(this@SchoolAdminActivity, "Назначен: " + teacher.fullName, Toast.LENGTH_LONG).show()
                        pickedClass = null
                        rebuild()
                    }
                )
            }
            addView(
                ui.button("Снять классного руководителя", primary = false) {
                    classes.assignHomeroom(cls.school, cls.grade, cls.letter, null)
                    pickedClass = null
                    rebuild()
                }
            )
        }
    }

    private fun renderTeachers(account: Account) {
        val teachers = store.everyone().filter { it.role == "teacher" && it.school == account.school }
        ui.card("Учителя школы") {
            if (teachers.isEmpty()) {
                addView(ui.text("Пока никого нет.", 13f, R.color.on_bg_variant))
                return@card
            }
            addView(ui.text("Всего: ${teachers.size}. Нажми, чтобы изменить должности.", 12f, R.color.on_bg_variant))
            teachers.forEach { teacher ->
                addView(ui.text(teacher.address(prefs.addressByName), 14.5f, R.color.on_bg, bold = true, top = 14))
                addView(ui.text(teacher.positionLine, 12f, R.color.on_bg_variant))
                addView(
                    ui.row(R.drawable.ic_settings, "Изменить", "должности и роль") {
                        personId = teacher.id
                        rebuild()
                    }
                )
            }
        }
    }

    private fun renderStudents(account: Account) {
        val students = store.everyone().filter { it.role == "student" && it.school == account.school }
        ui.card("Ученики школы") {
            if (students.isEmpty()) {
                addView(ui.text("Пока никого нет.", 13f, R.color.on_bg_variant))
                return@card
            }
            addView(ui.text("Всего: ${students.size}", 12f, R.color.on_bg_variant))
            students.groupBy { Grades.label(it.grade, it.letter).ifBlank { "класс не указан" } }
                .forEach { (label, list) ->
                    addView(ui.text(label, 13f, R.color.accent, bold = true, top = 14))
                    list.forEach { student ->
                        addView(
                            ui.row(
                                R.drawable.ic_card_person,
                                student.address(prefs.addressByName),
                                "очки: ${student.score} · логин ${student.login}"
                            ) {
                                personId = student.id
                                rebuild()
                            }
                        )
                    }
                }
        }
    }

    private fun renderPerson() {
        val person = store.byId(personId)
        if (person == null) {
            ui.card("Не найдено") { addView(ui.text("Аккаунт не найден.", 14f, R.color.on_bg_variant)) }
            return
        }
        val isTeacher = person.isTeacher
        val positions = linkedSetOf<String>().apply { addAll(person.positions) }
        val grade = linkedSetOf(person.grade)
        val letter = linkedSetOf(person.letter)

        ui.card(if (isTeacher) "Учитель" else "Ученик") {
            addView(ui.kv("Текущее ФИО", person.fullName, 8))
            addView(ui.kv("Статус", person.status))

            val nameField = EditText(this@SchoolAdminActivity).apply {
                hint = "ФИО"
                setText(person.fullName)
                inputType = InputType.TYPE_CLASS_TEXT
                background = ContextCompat.getDrawable(this@SchoolAdminActivity, R.drawable.bg_input)
                setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12))
                setTextColor(ui.color(R.color.on_bg))
                setHintTextColor(ui.color(R.color.on_bg_variant))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = ui.dp(10) }
            }
            addView(nameField)

            if (isTeacher) {
                addView(ui.text("Должности", 13f, R.color.accent, bold = true, top = 14))
                Positions.teacher.forEach { option ->
                    addView(
                        Chips.create(
                            this@SchoolAdminActivity,
                            option,
                            positions.contains(option)
                        ) {
                            if (!positions.add(option)) positions.remove(option)
                            rebuild()
                        }
                    )
                }
                if (positions.isEmpty()) positions.add(Positions.SUBJECT)
            } else {
                addView(ui.text("Класс", 13f, R.color.accent, bold = true, top = 14))
                addView(
                    Chips.row(this@SchoolAdminActivity, Grades.numbers.map { it to (it == grade.first()) }) { idx ->
                        grade.clear()
                        grade.add(Grades.numbers[idx])
                        rebuild()
                    }
                )
                addView(
                    Chips.row(this@SchoolAdminActivity, Grades.letters.map { it to (it == letter.first()) }) { idx ->
                        letter.clear()
                        letter.add(Grades.letters[idx])
                        rebuild()
                    }
                )
            }

            addView(
                ui.button("Сохранить") {
                    val name = nameField.text.toString().trim()
                    val problem = Validate.fullName(name)
                    if (problem != null) {
                        Toast.makeText(this@SchoolAdminActivity, problem, Toast.LENGTH_LONG).show()
                        return@button
                    }
                    val g = grade.firstOrNull().orEmpty()
                    val l = letter.firstOrNull().orEmpty()
                    store.adminUpdate(
                        id = person.id,
                        fullName = name,
                        school = person.school,
                        position = if (isTeacher) Positions.join(positions) else person.position,
                        role = person.role,
                        grade = g,
                        letter = l,
                        subject = person.subject,
                        homeroom = person.homeroom,
                        status = person.status
                    )
                    if (!isTeacher) classes.ensure(person.school, g, l)

                    if (isTeacher) {
                        val lead = classes.all(person.school).firstOrNull { it.homeroomId == person.id }
                        if (lead != null && !positions.contains(Positions.HOMEROOM)) {
                            classes.assignHomeroom(lead.school, lead.grade, lead.letter, null)
                        }
                    }
                    Toast.makeText(this@SchoolAdminActivity, "Сохранено", Toast.LENGTH_SHORT).show()
                    rebuild()
                }
            )
        }


    }
}
