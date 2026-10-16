package ru.readysquad.kvest.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Scoring {

    const val POINTS_PER_TASK = 10
    const val SPEED_BONUS = 5
    const val SPEED_LIMIT_SEC = 10
    const val STREAK_BONUS = 5
    const val STREAK_MIN = 3
    const val QUEST_SIZE = 5

    fun grade(accuracy: Int): Int = when {
        accuracy >= 90 -> 5
        accuracy >= 70 -> 4
        accuracy >= 40 -> 3
        else -> 2
    }

    fun gradeLabel(g: Int): String = when (g) {
        5 -> "Отлично"
        4 -> "Хорошо"
        3 -> "Удовлетворительно"
        else -> "Нужно подтянуть"
    }

    fun gradeComment(g: Int): String = when (g) {
        5 -> "Такой темп — и на экзамене будет уверенная пятёрка."
        4 -> "Очень близко к пятёрке: добьём слабые темы — и готово."
        3 -> "База есть. Ещё пара квестов по слабым темам — и оценка вырастет."
        else -> "Не расстраивайся: начнём с простых тем и пойдём вверх."
    }
}

data class QuestEvent(
    val correct: Int,
    val total: Int,
    val fastAnswers: Int,
    val grade: Int,
    val questsCount: Int,
    val totalScore: Int,
    val bestStreak: Int,
    val distinctSubjects: Int,
    val streakDays: Int,
    val hour: Int
)

object BadgeEngine {

    fun evaluate(repo: Repository, event: QuestEvent): List<Badge> {
        val hit = ArrayList<String>()
        if (event.questsCount >= 1) hit.add("first_step")
        if (event.bestStreak >= 5) hit.add("streak5")
        if (event.total > 0 && event.correct == event.total) hit.add("sniper")
        if (event.fastAnswers >= 3) hit.add("speedrun")
        if (event.streakDays >= 3) hit.add("streak3")
        if (event.streakDays >= 7) hit.add("streak7")
        if (event.totalScore >= 100) hit.add("hundred")
        if (event.totalScore >= 500) hit.add("marathoner")
        if (event.distinctSubjects >= 3) hit.add("polymath")
        if (event.questsCount >= 10) hit.add("hardworker")
        if (event.grade == 5) hit.add("genius")
        if (event.hour >= 22 || event.hour < 5) hit.add("nightowl")

        // Сложные достижения: их заметно тяжелее получить.
        if (event.bestStreak >= 10) hit.add("streak10")
        if (event.total >= 5 && event.correct == event.total) hit.add("sniper_five")
        if (event.fastAnswers >= 6) hit.add("speed_demon")
        if (event.streakDays >= 14) hit.add("two_weeks")
        if (event.totalScore >= 1000) hit.add("thousand")
        if (event.questsCount >= 50) hit.add("fifty")
        if (event.distinctSubjects >= 8) hit.add("collector")
        if (event.hour in 5..7) hit.add("earlybird")
        return unlock(repo, hit)
    }

    fun evaluateTheory(repo: Repository, learnedTopics: Int): List<Badge> {
        val hit = ArrayList<String>()
        if (learnedTopics >= 5) hit.add("theory")
        if (learnedTopics >= 15) hit.add("theory15")
        return unlock(repo, hit)
    }

    private fun unlock(repo: Repository, codes: List<String>): List<Badge> {
        if (codes.isEmpty()) return emptyList()
        val all = repo.badges()
        val newly = ArrayList<Badge>()
        codes.forEach { code ->
            if (repo.unlockBadge(code)) all.firstOrNull { it.code == code }?.let { newly.add(it) }
        }
        return newly
    }
}

object TimeFmt {
    private val dayFmt = SimpleDateFormat("dd.MM", Locale("ru"))
    private val fullFmt = SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale("ru"))

    fun short(millis: Long): String = dayFmt.format(Date(millis))
    fun full(millis: Long): String = fullFmt.format(Date(millis))

    fun duration(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return if (m == 0) "$s сек" else "$m мин $s сек"
    }
}

object AnswerNorm {

    fun normalize(raw: String): String {
        val sb = StringBuilder()
        raw.lowercase().forEach { ch ->
            when {
                ch == '−' || ch == '–' || ch == '-' -> sb.append('-')
                ch == ',' || ch == '.' -> sb.append('.')
                ch == 'ё' -> sb.append('е')
                ch.isLetterOrDigit() -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun isCellFriendly(answer: String): Boolean {
        if (answer.any { it == '<' || it == '>' || it == '=' || it == ' ' }) return false
        val n = normalize(answer)
        return n.isNotEmpty() && n.length <= 12
    }

    fun matchesSequence(typed: String, expected: String): Boolean {
        val a = normalize(typed).filter { it != '.' }
        val b = normalize(expected).filter { it != '.' }
        return a.isNotEmpty() && a == b
    }
}
