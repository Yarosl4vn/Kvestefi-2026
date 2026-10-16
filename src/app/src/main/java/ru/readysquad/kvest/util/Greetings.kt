package ru.readysquad.kvest.util

object Greetings {

    const val MALE = "m"
    const val FEMALE = "f"

    private fun isFemale(gender: String?) = gender == FEMALE

    fun ready(gender: String?): String = if (isFemale(gender)) "Готова" else "Готов"

    fun solved(gender: String?): String = if (isFemale(gender)) "решила" else "решил"

    fun helloTitle(name: String, gender: String?, quests: Int = -1): String {
        val who = name.trim().ifBlank { "друг" }
        return if (quests == 0) "Добро пожаловать, $who!" else "С возвращением, $who!"
    }

    fun helloText(gender: String?, quests: Int, streakDays: Int): String = when {
        quests == 0 -> "Первый квест ждёт. ${ready(gender)} начать?"
        streakDays > 1 -> "Серия продолжается — не разрывай её сегодня."
        else -> "${ready(gender)} решать дальше?"
    }

    fun streakLine(streakDays: Int, bestStreak: Int): String = when {
        streakDays <= 0 && bestStreak <= 0 -> "Серия пока пустая: реши одну задачу — и она начнётся."
        streakDays <= 0 -> "Серия прервалась. Лучшая была $bestStreak дней — начнём заново?"
        streakDays == 1 -> "Серия: 1 день. Лучшая: $bestStreak."
        else -> "Серия: $streakDays дней подряд. Лучшая: $bestStreak."
    }
}
