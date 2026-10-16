package ru.readysquad.kvest.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class Prefs(context: Context) {

    private val sp = context.applicationContext.getSharedPreferences("kvest_prefs", Context.MODE_PRIVATE)

    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) = sp.edit().putBoolean("onboarded", v).apply()

    var name: String
        get() = sp.getString("name", "Игрок") ?: "Игрок"
        set(v) = sp.edit().putString("name", v).apply()

    var avatar: String
        get() = sp.getString("avatar", "🧑‍🚀") ?: "🧑‍🚀"
        set(v) = sp.edit().putString("avatar", v).apply()

    var goalPerDay: Int
        get() = Goals.normalize(sp.getInt("goal_per_day", Goals.DEFAULT))
        set(v) = sp.edit().putInt("goal_per_day", Goals.normalize(v)).apply()

    var focusTopics: Set<String>
        get() = sp.getStringSet("focus", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("focus", v).apply()

    var chosenSubjects: Set<String>
        get() = sp.getStringSet("subjects", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("subjects", v).apply()

    var learnedTopics: Set<String>
        get() = sp.getStringSet("learned_topics", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("learned_topics", v).apply()

    fun markLearned(topic: String): Int {
        val set = learnedTopics.toMutableSet()
        set.add(topic)
        learnedTopics = set
        return set.size
    }

    var currentUserId: Long
        get() = sp.getLong("current_user_id", 0L)
        set(v) = sp.edit().putLong("current_user_id", v).apply()

    var role: String
        get() = sp.getString("role", "guest") ?: "guest"
        set(v) = sp.edit().putString("role", v).apply()

    var teacherAddress: String
        get() = sp.getString("teacher_address", "") ?: ""
        set(v) = sp.edit().putString("teacher_address", v).apply()

    var seenBadges: Set<String>
        get() = sp.getStringSet("seen_badges", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("seen_badges", v).apply()

    var notifSeenAt: Long
        get() = sp.getLong("notif_seen_at", 0L)
        set(v) = sp.edit().putLong("notif_seen_at", v).apply()

    var visibleInRating: Boolean
        get() = sp.getBoolean("visible_in_rating", true)
        set(value) = sp.edit().putBoolean("visible_in_rating", value).apply()

    var publicProfile: Boolean
        get() = sp.getBoolean("public_profile", true)
        set(value) = sp.edit().putBoolean("public_profile", value).apply()

    var addressByName: Boolean
        get() = sp.getBoolean("address_by_name", true)
        set(v) = sp.edit().putBoolean("address_by_name", v).apply()

    var devMode: Boolean
        get() = sp.getBoolean("dev_mode", false)
        set(v) = sp.edit().putBoolean("dev_mode", v).apply()

    var notifyImportant: Boolean
        get() = sp.getBoolean("notify_important", true)
        set(v) = sp.edit().putBoolean("notify_important", v).apply()

    var notifyStudy: Boolean
        get() = sp.getBoolean("notify_study", true)
        set(v) = sp.edit().putBoolean("notify_study", v).apply()

    var notifySystem: Boolean
        get() = sp.getBoolean("notify_system", true)
        set(v) = sp.edit().putBoolean("notify_system", v).apply()

    var shareClassResults: Boolean
        get() = sp.getBoolean("share_class_results", true)
        set(v) = sp.edit().putBoolean("share_class_results", v).apply()

    var gender: String
        get() = sp.getString("gender", "m") ?: "m"
        set(v) = sp.edit().putString("gender", v).apply()

    var learnSource: String
        get() = sp.getString("learn_source", LEARN_STEPIK) ?: LEARN_STEPIK
        set(v) = sp.edit().putString("learn_source", v).apply()

    var bestStreak: Int
        get() = sp.getInt("best_streak", 0)
        set(v) = sp.edit().putInt("best_streak", v).apply()

    var streak: Int
        get() = sp.getInt("day_streak", 0)
        set(v) = sp.edit().putInt("day_streak", v).apply()

    var bestDayStreak: Int
        get() = sp.getInt("day_streak_best", 0)
        set(v) = sp.edit().putInt("day_streak_best", v).apply()

    var lastActiveDay: String
        get() = sp.getString("last_active_day", "") ?: ""
        set(v) = sp.edit().putString("last_active_day", v).apply()

    var activeDays: Set<String>
        get() = sp.getStringSet("active_days", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("active_days", v).apply()

    fun addActiveDay(day: String) {
        val set = activeDays.toMutableSet()
        set.add(day)

        if (set.size > 60) {
            val trimmed = set.sorted().takeLast(60).toSet()
            activeDays = trimmed
        } else {
            activeDays = set
        }
    }

    var themeMode: Int
        get() = sp.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_YES)
        set(v) {
            sp.edit().putInt("theme_mode", v).apply()
            AppCompatDelegate.setDefaultNightMode(v)
        }

    fun saveThemeMode(mode: Int) {
        sp.edit().putInt("theme_mode", mode).apply()
    }

    var soundHints: Boolean
        get() = sp.getBoolean("sound_hints", true)
        set(v) = sp.edit().putBoolean("sound_hints", v).apply()

    var palette: Int
        get() = sp.getInt("palette", 0)
        set(v) = sp.edit().putInt("palette", v).apply()

    var avatarImage: String?
        get() = sp.getString("avatar_image", null)
        set(v) = sp.edit().putString("avatar_image", v).apply()

    /** Целевая сложность заданий: 1 — проще, 3 — сложнее. Меняется по результатам квестов. */
    var targetDifficulty: Int
        get() = sp.getInt("target_difficulty", 2).coerceIn(1, 3)
        set(v) = sp.edit().putInt("target_difficulty", v.coerceIn(1, 3)).apply()

    /** Темы, которые ученик закрыл: «предмет|тема». */
    var masteredTopics: Set<String>
        get() = sp.getStringSet("mastered_topics", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("mastered_topics", v).apply()

    fun isTopicMastered(subjectCode: String, topic: String): Boolean =
        masteredTopics.contains(subjectCode + "|" + topic)

    fun markTopicMastered(subjectCode: String, topic: String) {
        masteredTopics = masteredTopics + (subjectCode + "|" + topic)
    }

    /** Цель на неделю в очках. */
    var weekGoal: Int
        get() = sp.getInt("week_goal", 300).coerceAtLeast(50)
        set(v) = sp.edit().putInt("week_goal", v.coerceAtLeast(50)).apply()

    /** Отметить сегодняшний день занятий — для серии, челленджа и «задания дня». */
    fun markActiveToday() {
        val key = dateKey(Date())
        if (!activeDays.contains(key)) addActiveDay(key)
    }

    fun wasActiveToday(): Boolean = activeDays.contains(dateKey(Date()))

    /** Сколько дней из последних [days] ученик занимался. */
    fun activeDaysInLast(days: Int): Int {
        val calendar = Calendar.getInstance()
        var count = 0
        repeat(days) {
            if (activeDays.contains(dateKey(calendar.time))) count++
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }
        return count
    }

    /**
     * Число-зерно для «задания дня»: одинаковое в течение суток, поэтому у всех
     * учеников в один день набор заданий совпадает.
     */
    fun dailySeed(): Long = dateKey(Date()).hashCode().toLong()

    private fun dateKey(date: Date): String =
        SimpleDateFormat("yyyy-MM-dd", Locale("ru")).format(date)

    /** Закреплённые чаты: идентификаторы собеседников, которые идут наверху списка. */
    var pinnedChats: Set<String>
        get() = sp.getStringSet("pinned_chats", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("pinned_chats", v).apply()

    fun togglePinChat(id: Long): Boolean {
        val set = pinnedChats.toMutableSet()
        val key = id.toString()
        val pinned = if (set.contains(key)) {
            set.remove(key)
            false
        } else {
            set.add(key)
            true
        }
        pinnedChats = set
        return pinned
    }

    fun isChatPinned(id: Long): Boolean = pinnedChats.contains(id.toString())

    /** Черновик незаконченного сообщения для конкретного собеседника. */
    var draftTo: Long
        get() = sp.getLong("draft_to", 0L)
        set(v) = sp.edit().putLong("draft_to", v).apply()

    private fun today(): String {
        val c = Calendar.getInstance()
        return c.get(Calendar.YEAR).toString() + "-" + (c.get(Calendar.MONTH) + 1) + "-" + c.get(Calendar.DAY_OF_MONTH)
    }

    var todayQuests: Int
        get() = if (sp.getString("today_date", "") == today()) sp.getInt("today_quests", 0) else 0
        set(v) = sp.edit().putString("today_date", today()).putInt("today_quests", v).apply()

    fun bumpToday() {
        todayQuests = todayQuests + 1
    }

    fun usedTasks(subjectCode: String): Set<Long> =
        (sp.getStringSet("used_tasks_" + subjectCode, emptySet()) ?: emptySet())
            .mapNotNull { it.toLongOrNull() }
            .toSet()

    fun rememberUsedTasks(subjectCode: String, ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val next = usedTasks(subjectCode).toMutableSet()
        next.addAll(ids)
        sp.edit().putStringSet("used_tasks_" + subjectCode, next.map { it.toString() }.toSet()).apply()
    }

    fun clearUsedTasks(subjectCode: String) {
        sp.edit().remove("used_tasks_" + subjectCode).apply()
    }

    companion object {
        const val LEARN_STEPIK = "stepik"
        const val LEARN_WIKI = "wiki"
    }
}
