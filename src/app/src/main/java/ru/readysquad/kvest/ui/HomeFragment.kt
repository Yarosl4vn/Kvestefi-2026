package ru.readysquad.kvest.ui

import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ru.readysquad.kvest.R
import ru.readysquad.kvest.data.Account
import ru.readysquad.kvest.data.AccountStore
import ru.readysquad.kvest.data.Assignment
import ru.readysquad.kvest.data.AssignmentStore
import ru.readysquad.kvest.data.Grades
import ru.readysquad.kvest.data.Prefs
import ru.readysquad.kvest.data.Repository
import ru.readysquad.kvest.data.Roles
import ru.readysquad.kvest.data.hasTopic
import ru.readysquad.kvest.data.Sdamgia
import ru.readysquad.kvest.data.Subject
import ru.readysquad.kvest.databinding.FragmentHomeBinding
import ru.readysquad.kvest.util.Anim
import ru.readysquad.kvest.util.Avatars
import ru.readysquad.kvest.util.Streaks
import ru.readysquad.kvest.util.ThemeColors
import ru.readysquad.kvest.util.Greetings
import ru.readysquad.kvest.util.Notifier
import ru.readysquad.kvest.util.Leveling
import ru.readysquad.kvest.util.Dialogs
import ru.readysquad.kvest.util.Icons
import ru.readysquad.kvest.util.SubjectIcons

class HomeFragment : Fragment(R.layout.fragment_home), Refreshable {

    private var _b: FragmentHomeBinding? = null
    private val b get() = _b!!
    private val repo by lazy { Repository.get(requireContext()) }
    private val prefs by lazy { Prefs(requireContext()) }

    private val levels = listOf("Новичок", "Ученик", "Знаток", "Мастер", "Гуру", "Легенда")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _b = FragmentHomeBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.btnSettings.setOnClickListener { openSettings() }

        b.avatar.setOnClickListener { openSettings() }
        b.hello.setOnClickListener { openSettings() }
        b.subhello.setOnClickListener { openSettings() }
        b.ctaCard.setOnClickListener { pickSubjectAndStart() }
        b.levelCard.setOnClickListener { showLevel() }
        b.streakCard.setOnClickListener { Dialogs.streak(requireContext(), prefs) }
        b.goalCard.setOnClickListener { Dialogs.goal(requireContext(), prefs, repo.stats()) }
        b.lastCard.setOnClickListener { showLastAttempt() }
        b.btnBell.setOnClickListener { showNotifications() }
        Anim.fadeInUp(b.ctaCard, 120)
        Anim.fadeInUp(b.levelCard, 60)
    }

    private fun showNotifications() {
        startActivity(Intent(requireContext(), NotificationsActivity::class.java))
        Notifier.markSeen(prefs, repo.badges())
        _b?.bellDot?.visibility = View.GONE
    }

    private fun showLevel() {
        Dialogs.level(requireContext(), prefs, repo.stats()) {
            Dialogs.topSubjects(
                requireContext(),
                repo.bestScores(),
                repo.questsBySubject()
            ) { code -> repo.subjects().firstOrNull { it.code == code }?.title ?: code }
        }
    }

    private fun showLastAttempt() {
        val last = repo.attempts(1).firstOrNull()
        if (last == null) {
            (activity as? MainActivity)?.openTab(R.id.tab_results)
            return
        }
        Dialogs.attempt(
            requireContext(),
            last,
            repo.attemptAnswers(last.id),
            last.subjectTitle
        )
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun refresh() {
        val bind = _b ?: return
        val stats = repo.stats()

        val custom = Avatars.load(requireContext(), prefs.avatarImage)
        if (custom != null) {
            val size = (52 * resources.displayMetrics.density).toInt()
            bind.avatar.text = ""
            bind.avatar.background = BitmapDrawable(resources, Avatars.circleBitmap(custom, size))
        } else {
            bind.avatar.text = prefs.avatar
            bind.avatar.setBackgroundResource(R.drawable.bg_circle_surface)
        }

        bind.hello.text = if (prefs.name.isBlank()) "Привет!" else "Привет, ${prefs.name}!"
        bind.subhello.text = when {
            stats.quests == 0 -> "${Greetings.ready(prefs.gender)} начать первый квест?"
            prefs.streak > 1 -> "Серия ${prefs.streak} дней — не разрывай её"
            stats.accuracy >= 80 -> "Точность ${stats.accuracy}% — отличный темп"
            else -> "${Greetings.ready(prefs.gender)} продолжать подготовку?"
        }

        val level = Leveling.level(stats.totalScore)
        bind.levelTitle.text = "Уровень $level · ${Leveling.title(level)}"
        bind.totalScore.text = "${stats.totalScore} очков"
        bind.levelBar.setProgress((Leveling.progress(stats.totalScore) * 100).toInt(), true)
        bind.levelHint.text = if (level >= Leveling.MAX) "Максимальный уровень"
        else "До уровня ${level + 1} осталось ${Leveling.nextScore(level) - stats.totalScore} очков"

        Anim.countUp(0, stats.quests, 700L) { bind.statQuests.text = it.toString() }
        bind.statAccuracy.text = "${stats.accuracy}%"
        bind.statMinutes.text = "${stats.minutes} мин"

        val goal = prefs.goalPerDay.coerceAtLeast(1)
        val today = prefs.todayQuests
        bind.goalText.text = "$today из $goal"
        bind.goalBar.setProgress((today * 100 / goal).coerceAtMost(100), true)

        bind.ctaTitle.text = if (stats.quests == 0) "Начать первый квест" else "Продолжить подготовку"

        // Подпись должна описывать тот предмет, который реально откроется по нажатию.
        val next = nextSubject()
        val focus = prefs.focusTopics.firstOrNull()
            ?.takeIf { topic -> next?.hasTopic(topic) == true }
        bind.ctaSubtitle.text = when {
            next == null -> "Предметы ещё загружаются"
            prefs.role == Roles.TEACHER -> "${next.title} · пройди сам — увидишь, что решает класс"
            focus != null -> "${next.title} · тема для прокачки: $focus"
            else -> "${next.title} · 5 задач, ~4 минуты"
        }

        renderStreak(bind)
        renderLast(bind)
        renderExtras(bind)
    }

    private fun renderExtras(bind: FragmentHomeBinding) {
        val parent = bind.lastCard.parent as? LinearLayout ?: return
        parent.findViewWithTag<View>("home_extras")?.let { parent.removeView(it) }
        val box = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            tag = "home_extras"
        }
        val me = prefs.currentUserId.takeIf { it > 0L }?.let { AccountStore(requireContext()).byId(it) }

        if (me?.isStudent == true) {
            val rating = AccountStore(requireContext()).classRating(me)
            val place = rating.indexOfFirst { it.id == me.id } + 1
            if (place > 0) {
                box.addView(
                    card(
                        "Мой класс",
                        "${Grades.label(me.grade, me.letter)} · место $place из ${rating.size}",
                        "Нажми, чтобы увидеть весь список"
                    ) { showClassRating(me) }
                )
            }
        }

        if (me?.isStudent == true) {
            val items = AssignmentStore(requireContext()).forStudent(me)
            if (items.isNotEmpty()) {
                box.addView(
                    card(
                        "Задания от учителя",
                        "${items.size} шт. · ${items.first().topic}",
                        "Проверь, что задано на дом"
                    ) { showAssignments(me, items) }
                )
            }
        }

        // «Задание дня» — одинаковый набор у всех учеников в этот день.
        box.addView(
            card(
                "Задание дня",
                if (prefs.wasActiveToday()) "Задание дня · пройдено"
                else "Задание дня · 5 заданий",
                "Одинаковый набор у всего класса — сравни результат с одноклассниками"
            ) {
                val subject = nextSubject() ?: return@card
                startActivity(
                    Intent(requireContext(), QuizActivity::class.java)
                        .putExtra(QuizActivity.EXTRA_SUBJECT, subject.code)
                        .putExtra(QuizActivity.EXTRA_TITLE, subject.title)
                        .putExtra(QuizActivity.EXTRA_EMOJI, subject.emoji)
                        .putExtra(QuizActivity.EXTRA_DAILY, true)
                )
            }
        )

        // Работа над ошибками — появляется только когда ошибки есть.
        val mistakes = repo.mistakeCount()
        if (mistakes > 0) {
            box.addView(
                card(
                    "Работа над ошибками",
                    "Работа над ошибками · $mistakes",
                    "Задания, где были ошибки — пройти их заново и закрыть пробелы"
                ) {
                    val subject = nextSubject() ?: return@card
                    startActivity(
                        Intent(requireContext(), QuizActivity::class.java)
                            .putExtra(QuizActivity.EXTRA_SUBJECT, subject.code)
                            .putExtra(QuizActivity.EXTRA_TITLE, subject.title)
                            .putExtra(QuizActivity.EXTRA_EMOJI, subject.emoji)
                            .putExtra(QuizActivity.EXTRA_MISTAKES, true)
                    )
                }
            )
        }

        // Пробный экзамен — длинный вариант с общим таймером.
        box.addView(
            card(
                "Пробный экзамен",
                "Пробный экзамен · ${QuizActivity.EXAM_SIZE} заданий",
                "Проверь себя в формате экзамена, а не короткого квеста"
            ) {
                val subject = nextSubject() ?: return@card
                startActivity(
                    Intent(requireContext(), QuizActivity::class.java)
                        .putExtra(QuizActivity.EXTRA_SUBJECT, subject.code)
                        .putExtra(QuizActivity.EXTRA_TITLE, subject.title)
                        .putExtra(QuizActivity.EXTRA_EMOJI, subject.emoji)
                        .putExtra(QuizActivity.EXTRA_EXAM_MODE, true)
                )
            }
        )

        // Челлендж недели и цель по очкам.
        val weekDays = prefs.activeDaysInLast(7)
        val weekPoints = repo.pointsInLast(7)
        val goal = prefs.weekGoal
        box.addView(
            card(
                "Неделя",
                "Неделя · $weekDays из 7 дней",
                if (weekDays >= 7 && weekPoints >= goal) "Неделя закрыта полностью"
                else "$weekPoints из $goal очков · занимайся каждый день"
            ) {
                val subject = nextSubject() ?: return@card
                startQuiz(subject)
            }
        )

        if (box.childCount > 0) parent.addView(box)
    }

    private fun card(title: String, line: String, hint: String, onClick: () -> Unit): View =
        com.google.android.material.card.MaterialCardView(requireContext()).apply {
            radius = (18 * resources.displayMetrics.density)
            setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.surface))
            strokeWidth = (1 * resources.displayMetrics.density).toInt()
            strokeColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.outline_variant)
            cardElevation = 0f
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            val inner = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
                addView(TextView(requireContext()).apply {
                    text = line
                    textSize = 16f
                    setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.on_bg))
                    Icons.start(
                        this,
                        when {
                            title == "Мой класс" -> R.drawable.ic_nav_results
                            title.startsWith("Работа над ошибками") -> R.drawable.ic_badge_bulb
                            title.startsWith("Пробный экзамен") -> R.drawable.ic_card_rocket
                            title.startsWith("Неделя") -> R.drawable.ic_card_chart
                            else -> R.drawable.ic_card_book
                        },
                        R.color.accent, 16
                    )
                })
                addView(TextView(requireContext()).apply {
                    text = hint
                    textSize = 12.5f
                    setPadding(0, dp(4), 0, 0)
                    setTextColor(
                        androidx.core.content.ContextCompat.getColor(requireContext(), R.color.on_bg_variant)
                    )
                })
            }
            addView(inner)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(14)
            layoutParams = lp
        }

    private fun showClassRating(me: Account) {
        val rating = AccountStore(requireContext()).classRating(me)
        Dialogs.ratingClass(
            requireContext(),
            "Рейтинг класса · ${Grades.label(me.grade, me.letter)}",
            rating,
            me.id
        )
    }

    private fun showAssignments(me: Account, items: List<Assignment>) {
        val assignments = AssignmentStore(requireContext())
        val passed = items.map { it.id }.filter { id -> assignments.results(id).any { it.studentId == me.id } }.toSet()
        Dialogs.studentAssignments(requireContext(), items, passed) { assignment ->
            val subject = repo.subjects().firstOrNull { it.code == assignment.subjectCode }
            startActivity(
                Intent(requireContext(), QuizActivity::class.java)
                    .putExtra(QuizActivity.EXTRA_SUBJECT, assignment.subjectCode)
                    .putExtra(QuizActivity.EXTRA_TITLE, subject?.title ?: "Задание")
                    .putExtra(QuizActivity.EXTRA_EMOJI, subject?.emoji ?: "")
                    .putExtra(QuizActivity.EXTRA_ASSIGNMENT, assignment.id)
            )
        }
    }

    private fun renderStreak(bind: FragmentHomeBinding) {
        val broken = Streaks.isBroken(prefs)
        val streak = if (broken) 0 else prefs.streak

        bind.streakTitle.text = "Серия: $streak ${daysWord(streak)}"
        bind.streakHint.text = when {
            streak > 0 -> "Занимаешься каждый день — так держать"
            prefs.streak > 0 -> "Серия прервалась. Начни заново сегодня"
            else -> "Реши один квест сегодня, и серия начнётся"
        }
        bind.streakBest.text = "рекорд ${prefs.bestDayStreak}"
        bind.streakFlame.alpha = if (streak == 0) 0.4f else 1f

        val primary = ThemeColors.primary(requireContext())
        val onPrimary = ThemeColors.onPrimary(requireContext())
        val container = ThemeColors.surfaceContainer(requireContext())
        val inactive = ThemeColors.onSurfaceVariant(requireContext())

        bind.weekRow.removeAllViews()
        Streaks.week(prefs).forEach { day ->
            val cell = TextView(requireContext()).apply {
                text = day.label
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(if (day.active) onPrimary else inactive)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (day.active) primary else container)
                    if (day.isToday && !day.active) setStroke(dp(2), primary)
                }
            }
            val lp = LinearLayout.LayoutParams(0, dp(34), 1f)
            lp.marginEnd = dp(4)
            cell.layoutParams = lp
            bind.weekRow.addView(cell)
        }
    }

    private fun daysWord(n: Int): String = when {
        n % 10 == 1 && n % 100 != 11 -> "день"
        n % 10 in 2..4 && n % 100 !in 12..14 -> "дня"
        else -> "дней"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun renderLast(bind: FragmentHomeBinding) {
        val last = repo.lastAttempt()
        if (last == null) {
            bind.lastCard.visibility = View.GONE
            return
        }
        bind.lastCard.visibility = View.VISIBLE
        bind.lastLine.text = "${last.subjectTitle} · ${last.correct} из ${last.total}"
        Icons.start(bind.lastLine, SubjectIcons.of(last.subjectCode), R.color.on_bg_variant, 13)
        bind.lastSub.text = "оценка ${last.grade} · ${last.score} очков · ${last.accuracy}% точности"
    }

    /** Предмет, который откроется по нажатию на карточку «Продолжить подготовку». */
    private fun nextSubject(): Subject? {
        val subjects = repo.subjects()
        if (subjects.isEmpty()) return null
        val chosen = prefs.chosenSubjects
        val byFocus = prefs.focusTopics.firstOrNull()
            ?.let { topic -> subjects.firstOrNull { it.hasTopic(topic) } }
        val lastCode = repo.lastAttempt()?.subjectCode
        return byFocus
            ?: subjects.firstOrNull { it.code == lastCode }
            ?: subjects.firstOrNull { chosen.contains(it.code) }
            ?: subjects.first()
    }

    /** Выбор предмета прямо с главного экрана. */
    private fun pickSubjectAndStart() {
        val subjects = repo.subjects()
        if (subjects.isEmpty()) return
        val recommended = nextSubject()
        val labels = subjects.map { subject ->
            subject.emoji + "   " + subject.title +
                if (subject.code == recommended?.code) "  ·  рекомендую" else ""
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Какой предмет пройти")
            .setItems(labels.toTypedArray()) { _, which -> startQuiz(subjects[which]) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun startQuiz(subject: Subject) {
        startActivity(
            Intent(requireContext(), QuizActivity::class.java)
                .putExtra(QuizActivity.EXTRA_SUBJECT, subject.code)
                .putExtra(QuizActivity.EXTRA_TITLE, subject.title)
                .putExtra(QuizActivity.EXTRA_EMOJI, subject.emoji)
                .putExtra(QuizActivity.EXTRA_ONLINE, Sdamgia.hasSource(subject.code))
        )
    }

    private fun openSettings() {
        startActivity(Intent(requireContext(), ProfileActivity::class.java))
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }
}
