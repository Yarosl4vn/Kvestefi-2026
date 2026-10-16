package ru.readysquad.kvest.ui

import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ru.readysquad.kvest.R
import ru.readysquad.kvest.data.Account
import ru.readysquad.kvest.data.AccountStore
import ru.readysquad.kvest.data.AssignmentStore
import ru.readysquad.kvest.data.ClassStore
import ru.readysquad.kvest.data.Grades
import ru.readysquad.kvest.data.Prefs
import ru.readysquad.kvest.data.Roles
import ru.readysquad.kvest.data.Repository
import ru.readysquad.kvest.util.Chips
import ru.readysquad.kvest.util.Palettes
import ru.readysquad.kvest.util.Reports
import ru.readysquad.kvest.util.Screens

class AssignActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var store: AccountStore
    private lateinit var tasks: AssignmentStore
    private lateinit var repo: Repository
    private lateinit var ui: Screens

    private var subjectCode = ""
    private var exam = LEVELS.first()
    private var topic = ""
    private var count = 10
    private var openId = 0L
    private var targetGrade = ""
    private var targetLetter = ""

    private val me: Account? get() = prefs.currentUserId.takeIf { it > 0 }?.let { store.byId(it) }

    companion object {

        val LEVELS = listOf("ОГЭ", "ЕГЭ")

        val COUNTS = listOf(5, 10, 15, 20)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        Palettes.apply(this, prefs.palette)
        super.onCreate(savedInstanceState)
        store = AccountStore(this)
        tasks = AssignmentStore(this)
        repo = Repository.get(this)
        ui = Screens(this)
        ui.install("Выдать задание") { onBack() }
        render()
    }

    private fun onBack() {
        if (openId > 0L) {
            openId = 0L
            rebuild()
            return
        }
        finish()
    }

    private fun rebuild() {
        ui.body.removeAllViews()
        ui.header(if (openId > 0L) "Отчёт по заданию" else "Выдать задание") { onBack() }
        render()
    }

    private fun render() {
        val teacher = me
        if (teacher == null || !teacher.isTeacher) {
            ui.card("Недоступно") {
                addView(
                    ui.text(
                        "Задания выдают учителя и администрация школы. " +
                            "Войди под учительским аккаунтом.",
                        14f, R.color.on_bg_variant
                    )
                )
            }
            return
        }
        if (openId > 0L) {
            renderReport(openId)
            return
        }
        renderForm(teacher)
        renderIssued(teacher)
    }

    private fun renderForm(teacher: Account) {
        val subjects = repo.subjects()
        if (subjects.isEmpty()) {
            ui.card("Нет предметов") { addView(ui.text("В приложении нет предметов.", 14f, R.color.on_bg_variant)) }
            return
        }
        if (subjectCode.isBlank()) subjectCode = subjects.first().code
        val topics = tasks.topicsFor(subjectCode, exam)
        if (topic.isBlank() || !topics.contains(topic)) topic = topics.firstOrNull().orEmpty()

        ui.card("Что выдаём") {
            addView(ui.text("Предмет — листай влево-вправо", 13f, R.color.accent, bold = true))
            addView(
                Chips.scrollRow(this@AssignActivity, subjects.map { it.title to (it.code == subjectCode) }) { idx ->
                    subjectCode = subjects[idx].code
                    topic = ""
                    rebuild()
                }
            )
            addView(ui.text("Уровень", 13f, R.color.accent, bold = true, top = 14))
            addView(
                Chips.scrollRow(this@AssignActivity, LEVELS.map { it to (it == exam) }) { idx ->
                    exam = LEVELS[idx]
                    topic = ""
                    rebuild()
                }
            )
            addView(ui.text("Сколько задач", 13f, R.color.accent, bold = true, top = 14))
            addView(
                Chips.scrollRow(this@AssignActivity, COUNTS.map { it.toString() to (it == count) }) { idx ->
                    count = COUNTS[idx]
                    rebuild()
                }
            )
        }

        ui.card("Тема") {
            if (topics.isEmpty()) {
                addView(
                    ui.text(
                        "По этому предмету и уровню задач в приложении нет. " +
                            "Выбери другой предмет или уровень.",
                        13f, R.color.on_bg_variant
                    )
                )
            } else {
                addView(
                    ui.text(
                        "Выбери тему — задания подберутся строго по ней. " +
                            "Тем доступно: ${topics.size}, листай влево-вправо.",
                        12f, R.color.on_bg_variant
                    )
                )
                addView(
                    Chips.scrollRow(this@AssignActivity, topics.map { it to (it == topic) }) { idx ->
                        topic = topics[idx]
                        rebuild()
                    }
                )
            }
        }

        val options = classOptions(teacher)
        val current = options.firstOrNull { it.second == targetGrade && it.third == targetLetter }
            ?: options.firstOrNull()

        ui.card("Кому выдать") {
            addView(
                ui.text(
                    when {
                        current != null -> "Задание получат ученики класса ${current.first}."
                        else -> "Класс не указан: задание получат все, кому оно подходит."
                    },
                    13f, R.color.on_bg
                )
            )
            if (options.size > 1) {
                addView(
                    ui.text(
                        "Можно выдать не только своему классу — выбери другой из списка школы.",
                        12f, R.color.on_bg_variant, top = 6
                    )
                )
                addView(
                    ui.button("Сменить класс", primary = false) { pickClass(options) }
                )
            } else if (teacher.homeroom.isBlank()) {
                addView(
                    ui.text(
                        "Ты не классный руководитель, поэтому класс берётся из профиля. " +
                            "Проверь его в настройках.",
                        12f, R.color.on_bg_variant, top = 6
                    )
                )
            }
            addView(ui.button("Выдать задание") { issue(teacher) })
        }
    }

    /** Классы, которым учитель может выдать задание: свой и все классы школы. */
    private fun classOptions(teacher: Account): List<Triple<String, String, String>> {
        val out = ArrayList<Triple<String, String, String>>()
        val ownLabel = teacher.homeroom.ifBlank { Grades.label(teacher.grade, teacher.letter) }
        if (ownLabel.isNotBlank()) {
            val parts = ownLabel.trim().split(" ")
            out.add(
                Triple(
                    ownLabel,
                    parts.firstOrNull().orEmpty().filter { it.isDigit() },
                    parts.firstOrNull().orEmpty().filter { it.isLetter() }
                )
            )
        }
        if (teacher.school.isNotBlank()) {
            ClassStore(this).all(teacher.school).forEach { cls ->
                if (out.none { it.second == cls.grade && it.third == cls.letter }) {
                    out.add(Triple(cls.label, cls.grade, cls.letter))
                }
            }
        }
        return out
    }

    private fun pickClass(options: List<Triple<String, String, String>>) {
        val labels = options.map { it.first }
        MaterialAlertDialogBuilder(this)
            .setTitle("Кому выдать задание")
            .setItems(labels.toTypedArray()) { _, which ->
                targetGrade = options[which].second
                targetLetter = options[which].third
                rebuild()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun issue(teacher: Account) {
        if (topic.isBlank()) {
            Toast.makeText(this, "Выбери тему", Toast.LENGTH_LONG).show()
            return
        }

        val picked = tasks.tasksFor(subjectCode, exam, topic, count, tasks.issuedTaskIds(teacher.id))
        if (picked.isEmpty()) {
            Toast.makeText(this, "По этой теме задач не нашлось", Toast.LENGTH_LONG).show()
            return
        }
        val id = tasks.create(teacher, subjectCode, exam, topic, picked, 0L, targetGrade, targetLetter)
        if (id <= 0L) {
            Toast.makeText(this, "Не удалось выдать задание", Toast.LENGTH_LONG).show()
            return
        }
        val bank = tasks.availableCount(subjectCode, exam, topic)
        val message = if (bank < count)
            "Задание выдано: $topic, задач ${picked.size}. " +
                "В теме только $bank — остальные добавлены из других тем предмета."
        else "Задание выдано: $topic, задач ${picked.size}"
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        rebuild()
    }

    private fun renderIssued(teacher: Account) {
        val mine = tasks.byTeacher(teacher.id)
        ui.card("Выдано") {
            if (mine.isEmpty()) {
                addView(ui.text("Пока ничего не выдано.", 13f, R.color.on_bg_variant))
                return@card
            }
            addView(ui.text("Всего заданий: ${mine.size}", 12f, R.color.on_bg_variant))
            mine.forEach { assignment ->
                val done = tasks.results(assignment.id)
                val title = assignment.topic.ifBlank { assignment.title }
                addView(
                    ui.text(
                        title + " · " + assignment.exam,
                        13.5f, R.color.on_bg, bold = true, top = 14
                    )
                )
                addView(
                    ui.text(
                        "задач: ${assignment.size} · прошли: ${done.size}",
                        12f, R.color.on_bg_variant
                    )
                )
                addView(
                    ui.button("Отчёт") {
                        openId = assignment.id
                        rebuild()
                    }
                )
            }
        }
    }

    private fun renderReport(assignmentId: Long) {
        val assignment = tasks.byId(assignmentId)
        val rows = tasks.results(assignmentId)
        ui.card(assignment?.topic?.ifBlank { "Отчёт" } ?: "Отчёт") {
            addView(
                ui.text(
                    "Задач: ${assignment?.size ?: 0} · прошли: ${rows.size}",
                    12f, R.color.on_bg_variant
                )
            )
            addView(
                ui.button("Выгрузить отчёт") {
                    if (rows.isEmpty()) {
                        Toast.makeText(
                            this@AssignActivity,
                            "Пока никто не прошёл задание",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Reports.share(
                            this@AssignActivity,
                            "Отчёт по заданию",
                            Reports.assignmentCsv(
                                title = assignment?.topic?.ifBlank { "Задание" } ?: "Задание",
                                className = assignment?.let { Grades.label(it.grade, it.letter) }
                                    .orEmpty(),
                                taskCount = assignment?.size ?: 0,
                                rows = rows
                            )
                        )
                    }
                }
            )
            if (rows.isEmpty()) {
                addView(ui.text("Пока никто не прошёл.", 13f, R.color.on_bg_variant, top = 10))
                return@card
            }
            rows.sortedByDescending { it.correct }.forEachIndexed { index, result ->
                addView(
                    ui.text(
                        (index + 1).toString() + ". " + result.studentName.ifBlank { "ученик" },
                        13.5f, R.color.on_bg, bold = true, top = 14
                    )
                )
                addView(
                    ui.text(
                        "оценка ${result.grade} · верно ${result.correct} из ${result.total} " +
                            "(${result.accuracy}%) · очки ${result.score}",
                        12f, R.color.on_bg_variant
                    )
                )
                result.mistakes.take(5).forEach { row ->
                    addView(
                        ui.text(
                            "ошибка: ${row.question} — ответ «${row.typed}», верно «${row.expected}»",
                            11.5f, R.color.danger, top = 4
                        )
                    )
                }
            }
        }
    }
}
