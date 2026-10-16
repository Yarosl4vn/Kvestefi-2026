package ru.readysquad.kvest.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import ru.readysquad.kvest.R
import ru.readysquad.kvest.data.Prefs
import ru.readysquad.kvest.data.Repository
import ru.readysquad.kvest.ui.MainActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Виджет на главном экране телефона: зовёт пройти квест, показывает серию
 * и сколько дней пользователь не занимался, а текст меняется каждый день.
 */
class KvestWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> manager.updateAppWidget(id, build(context)) }
    }

    private fun build(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_kvest)
        val prefs = Prefs(context)

        val today = dayKey(System.currentTimeMillis())
        val activeToday = prefs.lastActiveDay.isNotBlank() && prefs.lastActiveDay == today
        val missed = daysMissed(prefs.lastActiveDay, today)
        val streak = prefs.streak
        val name = prefs.name.ifBlank { "друг" }

        views.setTextViewText(R.id.widget_title, "Квестефи · привет, $name")

        val dayIndex = dayOfYear()
        val subject = subjectTitle(context, prefs)

        val text = when {
            activeToday -> when (dayIndex % 3) {
                0 -> "Серия в безопасности: сегодня уже занимался. Хочешь ещё квест?"
                1 -> "Темп дня взят. Ещё один квест — и серия станет крепче."
                else -> "Сегодня засчитано. Закрепи результат ещё одним квестом."
            }
            missed == 0 -> when (dayIndex % 3) {
                0 -> "Первый квест ждёт: 5 заданий, около четырёх минут."
                1 -> "Начни с короткого квеста — он занимает меньше пяти минут."
                else -> "Сегодня квеста ещё не было. Пять заданий — и день закрыт."
            }
            else -> when (dayIndex % 3) {
                0 -> "Без квеста $missed дн. — серия $streak прервётся, если не пройти сегодня."
                1 -> "Ты не проходил квест $missed дн. Вернись, пока серия жива."
                else -> "$missed дн. без квестов. Один квест сегодня — и серия продолжится."
            }
        }
        views.setTextViewText(R.id.widget_text, text)
        views.setTextViewText(R.id.widget_subject, "Сегодня советуем: $subject")

        views.setTextViewText(
            R.id.widget_streak,
            when {
                streak > 0 -> "Серия: $streak дн."
                else -> "Серия пока не начата"
            }
        )

        views.setTextViewText(
            R.id.widget_cta,
            if (activeToday) "Пройти ещё квест" else "Пройти квест"
        )

        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pending)
        return views
    }

    /** Предмет, который приложение предложит первым: из выбранных или по последней попытке. */
    private fun subjectTitle(context: Context, prefs: Prefs): String = runCatching {
        val repo = Repository.get(context)
        val subjects = repo.subjects()
        val lastCode = repo.lastAttempt()?.subjectCode
        val subject = subjects.firstOrNull { it.code == lastCode }
            ?: subjects.firstOrNull { prefs.chosenSubjects.contains(it.code) }
            ?: subjects.firstOrNull()
        subject?.title ?: "любой предмет"
    }.getOrDefault("любой предмет")

    private fun dayKey(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale("ru")).format(Date(millis))

    private fun dayOfYear(): Int = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)

    /** Сколько дней прошло с последнего занятия. 0 — занимался сегодня или данных нет. */
    private fun daysMissed(lastDay: String, today: String): Int {
        if (lastDay.isBlank()) return 0
        val format = SimpleDateFormat("yyyy-MM-dd", Locale("ru"))
        val last = runCatching { format.parse(lastDay) }.getOrNull() ?: return 0
        val now = runCatching { format.parse(today) }.getOrNull() ?: return 0
        val diff = now.time - last.time
        return (diff / 86_400_000L).toInt().coerceAtLeast(0)
    }
}
