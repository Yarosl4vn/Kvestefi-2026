package ru.readysquad.kvest.ui

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ru.readysquad.kvest.R
import ru.readysquad.kvest.data.AccountStore
import ru.readysquad.kvest.data.AssignmentStore
import ru.readysquad.kvest.data.AnswerRow
import ru.readysquad.kvest.data.BadgeEngine
import ru.readysquad.kvest.data.Exam
import ru.readysquad.kvest.data.Prefs
import ru.readysquad.kvest.data.Sdamgia
import ru.readysquad.kvest.data.QuestEvent
import ru.readysquad.kvest.data.QuestResult
import ru.readysquad.kvest.data.Repository
import ru.readysquad.kvest.data.Scoring
import ru.readysquad.kvest.data.Task
import ru.readysquad.kvest.data.TaskGenerator
import ru.readysquad.kvest.databinding.ActivityQuizBinding
import ru.readysquad.kvest.databinding.ItemOptionBinding
import ru.readysquad.kvest.util.Anim
import ru.readysquad.kvest.util.ImageLoader
import ru.readysquad.kvest.util.Palettes
import ru.readysquad.kvest.util.Streaks
import java.util.Calendar
import ru.readysquad.kvest.util.Icons
import ru.readysquad.kvest.util.SubjectIcons

class QuizActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SUBJECT = "subject_code"
        const val EXTRA_TITLE = "subject_title"
        const val EXTRA_EMOJI = "subject_emoji"
        const val EXTRA_TRAIN = "train"

        const val EXTRA_ONLINE = "online"

        const val EXTRA_EXAM = "exam"

        const val EXTRA_ASSIGNMENT = "assignment_id"

        /** Работа над слабой темой, «задание дня» и работа над ошибками. */
        const val EXTRA_TOPIC = "topic"
        const val EXTRA_TOPIC_DRILL = "topic_drill"
        const val EXTRA_DAILY = "daily"
        const val EXTRA_MISTAKES = "mistakes"

        /** Пробный экзамен: вариант длиннее обычного квеста. */
        const val EXTRA_EXAM_MODE = "exam_mode"
        const val EXAM_SIZE = 20

        /** Где нужно решать и считать — времени больше, где только выбрать ответ — 30 секунд. */
        private const val QUESTION_MS = 30_000L
        private const val QUESTION_MS_THINKING = 120_000L
        private val THINKING_SUBJECTS = setOf("math", "phys", "chem", "info")
        private val LETTERS = listOf("А", "Б", "В", "Г")
    }

    private lateinit var b: ActivityQuizBinding
    private val prefs by lazy { Prefs(this) }
    private val repo by lazy { Repository.get(this) }

    private lateinit var subjectCode: String
    private lateinit var subjectTitle: String
    private lateinit var subjectEmoji: String
    private var train = false
    private var online = false
    private var exam = Exam.OGE
    private var assignmentId = 0L

    /** Работа над слабой темой: квест идёт строго по одной теме. */
    private var topic = ""
    private var topicDrill = false

    /** «Задание дня»: набор заданий одинаковый у всех и не меняется в течение суток. */
    private var daily = false

    /** Пробный экзамен: заданий больше, общий таймер на весь вариант. */
    private var examMode = false

    /** Адаптивная сложность: целевая сложность следующего задания. */
    private var targetDifficulty = 2
    private var pool: List<Task> = emptyList()
    private val takenIds = HashSet<Long>()

    private var requestId = 0

    private var tasks: List<Task> = emptyList()
    private var index = 0
    private var selected = -1
    private var revealed = false

    private var score = 0
    private var baseScore = 0
    private var speedBonus = 0
    private var streakBonus = 0
    private var streak = 0
    private var bestStreak = 0
    private var fastAnswers = 0

    private var questionStartMs = 0L
    private var questStartMs = 0L
    private var timer: CountDownTimer? = null
    private var questionMs = QUESTION_MS
    private val answers = ArrayList<AnswerRow>()
    private val optionRows = ArrayList<ItemOptionBinding>()
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Palettes.apply(this, Prefs(this).palette)
        b = ActivityQuizBinding.inflate(layoutInflater)
        setContentView(b.root)

        subjectCode = intent.getStringExtra(EXTRA_SUBJECT) ?: "math"
        questionMs = if (THINKING_SUBJECTS.contains(subjectCode)) QUESTION_MS_THINKING else QUESTION_MS
        subjectTitle = intent.getStringExtra(EXTRA_TITLE) ?: "Математика"
        subjectEmoji = intent.getStringExtra(EXTRA_EMOJI) ?: "📐"
        train = intent.getBooleanExtra(EXTRA_TRAIN, false)
        online = intent.getBooleanExtra(EXTRA_ONLINE, false)
        exam = intent.getStringExtra(EXTRA_EXAM) ?: examForGrade()

        b.quizSubject.text = subjectTitle
        Icons.start(b.quizSubject, SubjectIcons.of(subjectCode), R.color.on_bg, 16)
        b.btnClose.setOnClickListener { confirmExit() }
        b.btnAction.setOnClickListener { onAction() }
        b.btnAction.isEnabled = false
        b.btnRetry.setOnClickListener { loadOnline() }
        b.btnRetry.visibility = View.GONE
        b.loadingBox.visibility = View.GONE

        assignmentId = intent.getLongExtra(EXTRA_ASSIGNMENT, 0L)
        if (assignmentId > 0L) {

            val store = AssignmentStore(this)
            val assignment = store.byId(assignmentId)
            if (assignment == null) {
                Toast.makeText(this, "Задание не найдено", Toast.LENGTH_LONG).show()
                finish()
                return
            }
            tasks = store.tasksOf(assignment)
            if (tasks.isEmpty()) {
                Toast.makeText(this, "В задании нет доступных задач", Toast.LENGTH_LONG).show()
                finish()
                return
            }
            b.quizSubject.text = "$subjectTitle · задание учителя"
        }

        if (online) {
            loadOnline()
            return
        }

        topic = intent.getStringExtra(EXTRA_TOPIC).orEmpty()
        topicDrill = intent.getBooleanExtra(EXTRA_TOPIC_DRILL, false)
        examMode = intent.getBooleanExtra(EXTRA_EXAM_MODE, false)
        daily = intent.getBooleanExtra(EXTRA_DAILY, false)

        if (intent.getBooleanExtra(EXTRA_MISTAKES, false)) {
            // Работа над ошибками: собраны задания, в которых ученик ошибался.
            tasks = repo.mistakeTasks(Scoring.QUEST_SIZE)
            if (tasks.isNotEmpty()) {
                subjectCode = tasks.first().subjectCode
                subjectTitle = repo.subject(subjectCode)?.title ?: subjectTitle
                b.quizSubject.text = "Работа над ошибками · $subjectTitle"
            } else {
                Toast.makeText(this, "Ошибок пока нет — так держать!", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            startQuest()
            return
        }

        if (daily) {
            // «Задание дня»: набор одинаковый у всех и не меняется в течение суток.
            b.quizSubject.text = "Задание дня · $subjectTitle"
            tasks = repo.dailyTasks(subjectCode, Scoring.QUEST_SIZE, prefs.dailySeed())
            startQuest()
            return
        }

        if (topic.isNotBlank()) {
            // Работа над слабой темой: задания строго по этой теме.
            tasks = repo.tasksByTopic(subjectCode, topic, questSize())
            val hasFew = tasks.size < questSize()
            b.quizSubject.text = "$subjectTitle · $topic"
            if (hasFew && tasks.isEmpty()) {
                Toast.makeText(this, "По теме «$topic» пока нет заданий", Toast.LENGTH_LONG).show()
            } else if (hasFew) {
                Toast.makeText(
                    this,
                    "По теме «$topic» всего ${tasks.size} заданий — берём все",
                    Toast.LENGTH_SHORT
                ).show()
            }
            startQuest()
            return
        }

        tasks = if (train) {
            TaskGenerator.generate(subjectCode, questSize())
        } else {
            repo.shuffledTasks(subjectCode, questSize())
        }
        startQuest()
    }

    private fun loadOnline() {
        requestId++
        val current = requestId
        b.quizSubject.text = "$subjectTitle · онлайн"
        Icons.start(b.quizSubject, SubjectIcons.of(subjectCode), R.color.on_bg, 16)
        b.loadingBox.visibility = View.VISIBLE
        b.questionCard.visibility = View.GONE
        b.optionsBox.visibility = View.GONE
        b.btnAction.visibility = View.GONE

        Thread {
            val result = Sdamgia.quest(subjectCode, exam, Scoring.QUEST_SIZE, System.nanoTime())
            handler.post {
                if (current != requestId) return@post
                b.loadingBox.visibility = View.GONE
                b.questionCard.visibility = View.VISIBLE
                b.optionsBox.visibility = View.VISIBLE
                b.btnAction.visibility = View.VISIBLE
                if (result.tasks.isEmpty()) {

                    val fallback = repo.shuffledTasks(subjectCode, Scoring.QUEST_SIZE)
                    if (fallback.isNotEmpty()) {
                        Toast.makeText(
                            this,
                            result.error ?: "Открытый банк недоступен — встроенные задания",
                            Toast.LENGTH_LONG
                        ).show()
                        b.quizSubject.text = "$subjectTitle · встроенный набор"
                        Icons.start(b.quizSubject, SubjectIcons.of(subjectCode), R.color.on_bg, 16)
                        tasks = fallback
                        startQuest()
                        return@post
                    }
                    b.loadingText.text = result.error ?: "Не удалось загрузить задания"
                    b.loadingBox.visibility = View.VISIBLE
                    b.questionCard.visibility = View.GONE
                    b.optionsBox.visibility = View.GONE
                    b.btnAction.visibility = View.GONE
                    b.quizProgress.text = "Квест по реальным заданиям работает онлайн"
                    b.btnRetry.visibility = View.VISIBLE
                } else {
                    tasks = result.tasks
                    if (result.error != null) {
                        // Задания пришли из кэша: говорим об этом честно.
                        Toast.makeText(this, result.error, Toast.LENGTH_SHORT).show()
                        b.quizSubject.text = "$subjectTitle · сохранённые задания"
                        Icons.start(b.quizSubject, SubjectIcons.of(subjectCode), R.color.on_bg, 16)
                    }
                    startQuest()
                }
            }
        }.start()
    }

    private fun startQuest() {
        if (tasks.isEmpty()) {
            Toast.makeText(this, "Для этого предмета пока нет заданий", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        questStartMs = System.currentTimeMillis()
        renderQuestion()
    }

    private fun renderQuestion() {
        val task = tasks[index]
        selected = -1
        revealed = false

        b.quizProgress.text = "Задача ${index + 1} из ${tasks.size}"
        b.topicLabel.text = "${task.exam.uppercase()} · ${task.topic.uppercase()}"
        b.questionText.text = task.question
        b.explanationCard.visibility = View.GONE
        b.btnAction.text = "Ответить"
        b.btnAction.isEnabled = false
        b.loadingBox.visibility = View.GONE
        b.btnRetry.visibility = View.GONE

        val image = task.imageUrl
        if (!image.isNullOrBlank()) {
            b.questionImage.visibility = View.VISIBLE
            ImageLoader.load(image, b.questionImage, referer = task.source) {
                b.questionImage.visibility = View.GONE
            }
        } else {
            b.questionImage.setImageDrawable(null)
            b.questionImage.visibility = View.GONE
        }
        if (task.source.isNotBlank()) {
            b.sourceLabel.text = "Источник: ${task.source}"
            b.sourceLabel.visibility = View.VISIBLE
        } else {
            b.sourceLabel.visibility = View.GONE
        }

        tasks.getOrNull(index + 1)?.let { next -> ImageLoader.prefetch(next.imageUrl, next.source) }

        setChips()

        buildOptions(task)

        b.questionCard.alpha = 0f
        b.questionCard.translationY = 34f
        b.questionCard.animate().alpha(1f).translationY(0f).setDuration(320).start()
        optionRows.forEachIndexed { i, row ->
            Anim.fadeInUp(row.root, 90L + i * 70L, 24f)
        }

        b.questBar.setProgress(index * 100 / tasks.size, true)
        startTimer()
    }

    private fun buildOptions(task: Task) {
        b.optionsBox.removeAllViews()
        optionRows.clear()
        task.options.forEachIndexed { i, text ->
            val row = ItemOptionBinding.inflate(layoutInflater, b.optionsBox, false)
            row.optionBadge.text = LETTERS.getOrElse(i) { "?" }
            row.optionText.text = text
            row.optionMark.visibility = View.GONE
            row.root.setOnClickListener { if (!revealed) pick(i) }
            b.optionsBox.addView(row.root)
            optionRows.add(row)
        }
    }

    private fun pick(i: Int) {
        selected = i
        optionRows.forEachIndexed { idx, row ->
            row.root.setBackgroundResource(
                if (idx == i) R.drawable.bg_option_selected else R.drawable.bg_option_normal
            )
        }
        b.btnAction.isEnabled = true
        Anim.release(optionRows[i].root)
    }

    private fun startTimer() {
        timer?.cancel()
        questionStartMs = System.currentTimeMillis()
        b.timerRing.secondsLeft = (questionMs / 1000).toInt()
        b.timerRing.progress = 1f

        timer = object : CountDownTimer(questionMs, 200) {
            override fun onTick(millisUntilFinished: Long) {
                b.timerRing.secondsLeft = (millisUntilFinished / 1000).toInt()
                b.timerRing.progress = millisUntilFinished.toFloat() / questionMs
            }

            override fun onFinish() {
                b.timerRing.secondsLeft = 0
                b.timerRing.progress = 0f
                if (!revealed) reveal()
            }
        }.start()
    }

    private fun onAction() {
        if (!revealed) {
            reveal()
        } else {
            if (index < tasks.size - 1) {
                index++
                renderQuestion()
            } else {
                finishQuest()
            }
        }
    }

    private fun reveal() {
        if (revealed || tasks.isEmpty()) return
        revealed = true
        timer?.cancel()
        val task = tasks[index]
        val seconds = ((System.currentTimeMillis() - questionStartMs) / 1000).toInt()
        val correct = selected == task.correctIndex

        optionRows.forEachIndexed { idx, row ->
            when {
                idx == task.correctIndex -> {
                    row.root.setBackgroundResource(R.drawable.bg_option_correct)
                    row.optionMark.text = ""
                    Icons.start(row.optionMark, R.drawable.ic_card_check, R.color.success, 20)
                    row.optionMark.visibility = View.VISIBLE
                }
                idx == selected -> {
                    row.root.setBackgroundResource(R.drawable.bg_option_wrong)
                    row.optionMark.text = ""
                    Icons.start(row.optionMark, R.drawable.ic_card_cross, R.color.danger, 20)
                    row.optionMark.visibility = View.VISIBLE
                }
                else -> row.root.setBackgroundResource(R.drawable.bg_option_normal)
            }
        }
        if (correct && selected >= 0) Anim.pop(optionRows[selected].root, 0)
        else if (selected >= 0) Anim.shake(optionRows[selected].root)

        if (correct) {
            baseScore += Scoring.POINTS_PER_TASK
            streak++
            bestStreak = maxOf(bestStreak, streak)
            if (seconds <= Scoring.SPEED_LIMIT_SEC) {
                speedBonus += Scoring.SPEED_BONUS
                fastAnswers++
                flashSpeed(true)
            } else {
                flashSpeed(false)
            }
            if (streak >= Scoring.STREAK_MIN) streakBonus += Scoring.STREAK_BONUS
        } else {
            streak = 0
            Anim.shake(b.timerRing)
        }
        score = baseScore + speedBonus + streakBonus

        answers.add(
            AnswerRow(
                taskId = task.id,
                topic = task.topic,
                isCorrect = correct,
                seconds = seconds,
                question = task.question,
                typed = task.options.getOrNull(selected).orEmpty(),
                expected = task.answer
            )
        )
        setChips()

        if (prefs.soundHints && task.explanation.isNotBlank()) {
            b.explanationText.text = (if (correct) "Верно. " else "Правильный ответ: ") +
                task.options.getOrNull(task.correctIndex).orEmpty() + "\n" + task.explanation
            b.explanationCard.visibility = View.VISIBLE
            Anim.fadeInUp(b.explanationCard, 0, 18f)
        }

        b.btnAction.isEnabled = true
        b.btnAction.text = if (index < tasks.size - 1) "Следующая задача" else "Показать результат"
    }

    private fun setChips() {

        b.scoreChip.text = "$score очков"
        Icons.start(b.scoreChip, R.drawable.ic_badge_gem, R.color.on_bg_variant, 13)
        b.streakChip.text = "серия $streak"
        Icons.start(b.streakChip, R.drawable.ic_badge_flame, R.color.on_bg_variant, 13)
        b.speedChip.text = if (fastAnswers > 0) "быстрых: $fastAnswers" else "+5 за скорость"
        Icons.start(b.speedChip, R.drawable.ic_badge_bolt, R.color.on_bg_variant, 13)
    }

    private fun flashSpeed(fast: Boolean) {
        b.speedChip.animate().scaleX(1.14f).scaleY(1.14f).setDuration(140).withEndAction {
            b.speedChip.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
        }.start()
        b.speedChip.setTextColor(
            ContextCompat.getColor(this, if (fast) R.color.accent else R.color.on_bg_variant)
        )
    }

    /** Экзамен по классу ученика: 10–11 классы готовятся к ЕГЭ, остальные к ОГЭ. */
    private fun examForGrade(): String {
        val grade = AccountStore(this).byId(prefs.currentUserId)?.grade.orEmpty()
        return if (grade.startsWith("10") || grade.startsWith("11")) Exam.EGE else Exam.OGE
    }

    /** Сколько заданий в этом квесте: в пробном экзамене их больше. */
    private fun questSize(): Int = if (examMode) EXAM_SIZE else Scoring.QUEST_SIZE

    private fun finishQuest() {
        timer?.cancel()
        b.questBar.setProgress(100, true)

        val total = tasks.size
        val correctCount = answers.count { it.isCorrect }
        val accuracy = if (total == 0) 0 else correctCount * 100 / total
        val grade = Scoring.grade(accuracy)
        val seconds = ((System.currentTimeMillis() - questStartMs) / 1000).toInt()

        repo.saveAttempt(subjectCode, correctCount, total, score, grade, seconds, answers)

        AccountStore(this).addScore(prefs.currentUserId, score)

        // Отмечаем день занятий, подстраиваем сложность и закрываем тему, если получилось.
        prefs.markActiveToday()
        val level = prefs.targetDifficulty
        when {
            accuracy >= 80 && level < 3 -> prefs.targetDifficulty = level + 1
            accuracy <= 40 && level > 1 -> prefs.targetDifficulty = level - 1
        }
        if (topicDrill && topic.isNotBlank() && total > 0 && correctCount * 5 >= total * 4) {
            prefs.markTopicMastered(subjectCode, topic)
        }

        if (assignmentId > 0L && prefs.currentUserId > 0L) {
            val stores = AssignmentStore(this)
            val assignment = stores.byId(assignmentId)
            val me = AccountStore(this).byId(prefs.currentUserId)
            if (assignment != null && me != null) {
                stores.saveResult(assignment, me, correctCount, total, score, grade, answers)
            }
        }

        val streakDays = Streaks.touch(prefs)
        if (streakDays > prefs.bestDayStreak) prefs.bestDayStreak = streakDays

        val stats = repo.stats()
        val streakToRemember = maxOf(prefs.bestStreak, bestStreak)
        prefs.bestStreak = streakToRemember
        prefs.bumpToday()

        val event = QuestEvent(
            correct = correctCount,
            total = total,
            fastAnswers = fastAnswers,
            grade = grade,
            questsCount = stats.quests,
            totalScore = stats.totalScore,
            bestStreak = streakToRemember,
            distinctSubjects = stats.distinctSubjects,
            streakDays = streakDays,
            hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        )
        val newBadges = BadgeEngine.evaluate(repo, event)
        val weak = repo.topicStats(subjectCode).filter { it.total > 0 && it.accuracy < 70 }.take(3)

        val result = QuestResult(
            subjectCode = subjectCode,
            subjectTitle = subjectTitle,
            subjectEmoji = subjectEmoji,
            correct = correctCount,
            total = total,
            score = score,
            baseScore = baseScore,
            speedBonus = speedBonus,
            streakBonus = streakBonus,
            grade = grade,
            seconds = seconds,
            bestStreak = bestStreak,
            newBadges = newBadges,
            topicsToImprove = weak
        )

        startActivity(
            Intent(this, ResultActivity::class.java).putExtra(ResultActivity.EXTRA_RESULT, result)
        )
        finish()
    }

    private fun confirmExit() {
        if (index == 0 && !revealed && answers.isEmpty()) {
            finish()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Прервать квест?")
            .setMessage("Очки этой попытки не сохранятся.")
            .setNegativeButton("Продолжить", null)
            .setPositiveButton("Выйти") { _, _ -> finish() }
            .show()
    }

    override fun onBackPressed() {
        confirmExit()
    }

    override fun onDestroy() {
        timer?.cancel()
        requestId++
        super.onDestroy()
    }
}
