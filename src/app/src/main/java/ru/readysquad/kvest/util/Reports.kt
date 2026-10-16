package ru.readysquad.kvest.util

import android.content.Context
import android.content.Intent
import ru.readysquad.kvest.data.AssignmentResult
import ru.readysquad.kvest.data.Attempt
import ru.readysquad.kvest.data.Prefs
import ru.readysquad.kvest.data.Stats
import ru.readysquad.kvest.data.TopicStat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Выгрузка результатов: отчёт учителя по заданию и отчёт о прогрессе ученика.
 * Отдаём обычным текстом через системное «Поделиться» — его можно вставить
 * в письмо, чат класса или таблицу (разделитель — точка с запятой).
 */
object Reports {

    fun share(context: Context, subject: String, body: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, "Поделиться отчётом"))
        }
    }

    /** Отчёт учителя по заданию: кто прошёл, с каким результатом и какие темы просел. */
    fun assignmentCsv(
        title: String,
        className: String,
        taskCount: Int,
        rows: List<AssignmentResult>
    ): String {
        val builder = StringBuilder()
        builder.append("Отчёт по заданию: ").append(title).append('\n')
        if (className.isNotBlank()) builder.append("Класс: ").append(className).append('\n')
        builder.append("Задач в задании: ").append(taskCount)
            .append(" · прошли: ").append(rows.size).append('\n')
        if (rows.isNotEmpty()) {
            val correct = rows.sumOf { it.correct }
            val total = rows.sumOf { it.total }
            builder.append("Средняя точность: ")
                .append(if (total == 0) 0 else correct * 100 / total)
                .append("%\n")
        }
        builder.append('\n')
        builder.append("Ученик;Верных;Всего;Оценка;Очки;Сдано\n")
        rows.sortedByDescending { it.correct }.forEach { row ->
            builder.append(clean(row.studentName)).append(';')
                .append(row.correct).append(';')
                .append(row.total).append(';')
                .append(row.grade).append(';')
                .append(row.score).append(';')
                .append(SimpleDateFormat("dd.MM.yyyy", Locale("ru")).format(Date(row.createdAt)))
                .append('\n')
        }
        if (rows.isEmpty()) {
            builder.append("Пока никто не прошёл задание.\n")
        }
        return builder.toString()
    }

    /** Отчёт о прогрессе ученика — для родителей. */
    fun progressText(
        prefs: Prefs,
        stats: Stats,
        attempts: List<Attempt>,
        weak: List<TopicStat>,
        weekPoints: Int,
        weekDays: Int
    ): String {
        val builder = StringBuilder()
        builder.append("Квестефи · прогресс\n")
        if (prefs.name.isNotBlank()) builder.append(prefs.name).append('\n')
        builder.append('\n')
        builder.append("Квестов всего: ").append(stats.quests).append('\n')
        builder.append("Точность ответов: ").append(stats.accuracy).append("%\n")
        builder.append("Очков всего: ").append(stats.totalScore).append('\n')
        builder.append("Серия дней: ").append(prefs.streak)
            .append(" (рекорд ").append(prefs.bestDayStreak).append(")\n")
        builder.append("За неделю: ").append(weekDays).append(" дней, ")
            .append(weekPoints).append(" очков из ").append(prefs.weekGoal).append('\n')
        builder.append("Минут за квестами: ").append(stats.minutes).append('\n')

        val last = attempts.firstOrNull()
        if (last != null) {
            builder.append('\n')
            builder.append("Последний квест: ").append(last.subjectTitle)
                .append(" — ").append(last.correct).append(" из ").append(last.total)
                .append(", оценка ").append(last.grade).append('\n')
        }

        if (weak.isNotEmpty()) {
            builder.append('\n')
            builder.append("Темы, которые стоит подтянуть:\n")
            weak.forEach { topic ->
                builder.append("· ").append(topic.topic)
                    .append(" — ").append(topic.accuracy).append("% верных\n")
            }
        }
        builder.append('\n')
        builder.append("Собрано в приложении «Квестефи» · команда ReadySquad\n")
        return builder.toString()
    }

    private fun clean(value: String): String =
        value.replace(';', ',').replace('\n', ' ').trim()
}
