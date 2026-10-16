package ru.readysquad.kvest.util

import android.content.Context
import ru.readysquad.kvest.R
import ru.readysquad.kvest.data.AccountStore
import ru.readysquad.kvest.data.AssignmentStore
import ru.readysquad.kvest.data.Badge
import ru.readysquad.kvest.data.MessageStore
import ru.readysquad.kvest.data.Prefs
import ru.readysquad.kvest.data.Stats
import ru.readysquad.kvest.data.SupportStore
import ru.readysquad.kvest.data.TopicStat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Notifier {

    const val KIND_IMPORTANT = "important"
    const val KIND_STUDY = "study"
    const val KIND_SYSTEM = "system"

    fun kindTitle(kind: String): String = when (kind) {
        KIND_IMPORTANT -> "Важные"
        KIND_STUDY -> "Учебные"
        else -> "Системные"
    }

    data class Item(
        val iconRes: Int,
        val title: String,
        val text: String,
        val warn: Boolean,
        val kind: String = KIND_STUDY
    )

    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale("ru"))

    fun todayKey(): String = dayFmt.format(Date())

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun newBadges(prefs: Prefs, badges: List<Badge>): List<Badge> {
        val seen = prefs.seenBadges
        return badges.filter { it.isUnlocked && !seen.contains(it.code) }
    }

    fun build(
        prefs: Prefs,
        stats: Stats,
        badges: List<Badge>,
        topics: List<TopicStat>
    ): List<Item> {
        val out = ArrayList<Item>()

        newBadges(prefs, badges).take(3).forEach { b ->
            out.add(
                Item(
                    BadgeIcons.of(b.code),
                    "Новое достижение: ${b.title}",
                    b.description,
                    warn = false,
                    kind = KIND_IMPORTANT
                )
            )
        }

        if (stats.quests == 0) {
            out.add(
                Item(
                    R.drawable.ic_card_rocket,
                    "Первый квест ждёт",
                    "Пять заданий по одному предмету — это займёт около четырёх минут.",
                    warn = false,
                    kind = KIND_SYSTEM
                )
            )
        }

        val today = prefs.todayQuests
        val goal = prefs.goalPerDay.coerceAtLeast(1)
        out.add(
            Item(
                R.drawable.ic_badge_target,
                if (today >= goal) "Цель на сегодня выполнена" else "Цель на сегодня: $today из $goal",
                if (today >= goal) "Отличный день. Можно пройти ещё один квест для серии."
                else run {

                val left = goal - today
                val word = when {
                    left % 100 in 11..14 -> "квестов"
                    left % 10 == 1 -> "квест"
                    left % 10 in 2..4 -> "квеста"
                    else -> "квестов"
                }
                "Пройди ещё $left $word — и цель дня закрыта."
            },
                warn = false
            )
        )

        val active = prefs.activeDays.contains(todayKey())
        if (prefs.streak > 0 || active) {
            out.add(
                Item(
                    R.drawable.ic_badge_flame,
                    if (active) "Серия ${prefs.streak} дней продолжается"
                    else "Серия ${prefs.streak} дней под угрозой",
                    if (active) "Ты уже занимался сегодня — серия в безопасности."
                    else "Реши хотя бы один квест сегодня, иначе серия сбросится.",
                    warn = !active,
                    kind = KIND_IMPORTANT
                )
            )
        }

        val weak = topics.filter { it.total >= 2 && it.accuracy < 60 }.take(2)
        weak.forEach { t ->
            out.add(
                Item(
                    R.drawable.ic_card_chart,
                    "Слабая тема: ${t.topic}",
                    "Верных ответов ${t.correct} из ${t.total}. Загляни в шпаргалки по этой теме.",
                    warn = true,
                    kind = KIND_IMPORTANT
                )
            )
        }

        if (stats.total > 0 && stats.accuracy >= 80) {
            out.add(
                Item(
                    R.drawable.ic_badge_check,
                    "Точность ${stats.accuracy}%",
                    "Такой темп близок к пятёрке на экзамене. Продолжай.",
                    warn = false,
                    kind = KIND_SYSTEM
                )
            )
        }

        return out
    }

    fun alerts(context: Context, prefs: Prefs): List<Item> {
        val out = ArrayList<Item>()
        val me = runCatching { AccountStore(context).byId(prefs.currentUserId) }.getOrNull()

        runCatching {
            if (me != null) {
                AssignmentStore(context).forStudent(me).take(2).forEach { a ->
                    out.add(
                        Item(
                            R.drawable.ic_card_book,
                            "Задание от учителя: " + a.topic,
                            "Задач: " + a.taskIds.size + ". Открой «Задания» в профиле и пройди его.",
                            warn = false,
                            kind = KIND_STUDY
                        )
                    )
                }
            }
        }

        runCatching {
            val support = SupportStore(context)
            val mine = if (me != null && me.login.isNotBlank()) support.mine(me.login)
            else support.guest()
            mine.filter { it.reply.isNotBlank() && it.status == SupportStore.REVIEW }
                .take(2)
                .forEach { m ->
                    out.add(
                        Item(
                            R.drawable.ic_card_chart,
                            "Ответ на обращение",
                            m.reply,
                            warn = false,
                            kind = KIND_IMPORTANT
                        )
                    )
                }
        }

        runCatching {
            if (me != null) {
                val unread = MessageStore(context).unreadCount(me.id)
                if (unread > 0) {
                    out.add(
                        Item(
                            R.drawable.ic_send,
                            "Непрочитанных сообщений: " + unread,
                            "Открой «Сообщения» в профиле, чтобы ответить.",
                            warn = true,
                            kind = KIND_STUDY
                        )
                    )
                }
            }
        }

        return out
    }

    fun of(
        prefs: Prefs,
        stats: Stats,
        badges: List<Badge>,
        topics: List<TopicStat>,
        kind: String
    ): List<Item> = build(prefs, stats, badges, topics).filter { it.kind == kind }

    fun countOf(
        prefs: Prefs,
        stats: Stats,
        badges: List<Badge>,
        topics: List<TopicStat>,
        kind: String
    ): Int = of(prefs, stats, badges, topics, kind).size

    fun hasUnread(prefs: Prefs, items: List<Item>): Boolean =
        items.isNotEmpty() && prefs.notifSeenAt < startOfToday()

    fun markSeen(prefs: Prefs, badges: List<Badge>) {
        prefs.notifSeenAt = System.currentTimeMillis()
        markBadgesSeen(prefs, badges)
    }

    fun markBadgesSeen(prefs: Prefs, badges: List<Badge>) {
        prefs.seenBadges = prefs.seenBadges + badges.filter { it.isUnlocked }.map { it.code }
    }
}
