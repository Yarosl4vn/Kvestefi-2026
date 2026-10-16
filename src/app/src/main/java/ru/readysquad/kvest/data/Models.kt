package ru.readysquad.kvest.data

object Exam {
    const val OGE = "ОГЭ"
    const val EGE = "ЕГЭ"
    const val VPR = "ВПР"
    val all = listOf(OGE, EGE, VPR)
}

data class Subject(
    val code: String,
    val title: String,
    val emoji: String,
    val colorFrom: String,
    val colorTo: String,
    val topics: String
) : java.io.Serializable

data class Task(
    val id: Long,
    val subjectCode: String,
    val topic: String,
    val exam: String,
    val difficulty: Int,
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String,

    val imageUrl: String? = null,

    val source: String = ""
) {

    val answer: String get() = options.getOrElse(correctIndex) { "" }
}

data class Attempt(
    val id: Long,
    val subjectCode: String,
    val subjectTitle: String,
    val subjectEmoji: String,
    val correct: Int,
    val total: Int,
    val score: Int,
    val grade: Int,
    val seconds: Int,
    val createdAt: Long
) : java.io.Serializable {
    val accuracy: Int get() = if (total == 0) 0 else (correct * 100 / total)
}

data class Badge(
    val code: String,
    val emoji: String,
    val title: String,
    val description: String,
    val unlockedAt: Long? = null
) : java.io.Serializable {
    val isUnlocked: Boolean get() = unlockedAt != null
}

data class Stats(
    val totalScore: Int,
    val quests: Int,
    val correct: Int,
    val total: Int,
    val bestScore: Int,
    val distinctSubjects: Int,
    val minutes: Int
) {
    val accuracy: Int get() = if (total == 0) 0 else (correct * 100 / total)
}

data class AnswerRow(
    val taskId: Long,
    val topic: String,
    val isCorrect: Boolean,
    val seconds: Int,
    val question: String = "",
    val typed: String = "",
    val expected: String = ""
)

data class ProgressPoint(
    val index: Int,
    val score: Int,
    val accuracy: Int,
    val label: String
) : java.io.Serializable

data class TopicStat(
    val topic: String,
    val correct: Int,
    val total: Int
) : java.io.Serializable {
    val accuracy: Int get() = if (total == 0) 0 else (correct * 100 / total)
}

data class TheoryCard(
    val id: Long,
    val subjectCode: String,
    val subjectTitle: String,
    val subjectEmoji: String,
    val topic: String,
    val exam: String,
    val teaser: String,
    val body: String
) : java.io.Serializable

data class QuestResult(
    val subjectCode: String,
    val subjectTitle: String,
    val subjectEmoji: String,
    val correct: Int,
    val total: Int,
    val score: Int,
    val baseScore: Int,
    val speedBonus: Int,
    val streakBonus: Int,
    val grade: Int,
    val seconds: Int,
    val bestStreak: Int,
    val newBadges: List<Badge>,
    val topicsToImprove: List<TopicStat>
) : java.io.Serializable

/** Есть ли у предмета такая тема: сравнение по названию, а не по подстроке. */
fun Subject.hasTopic(topic: String): Boolean =
    topics.split(',').any { it.trim().equals(topic, ignoreCase = true) }
