package ru.readysquad.kvest.data

import android.graphics.Color

/** Служебные префиксы. Придумать свой может только разработчик. */
object Prefixes {

    const val DEVELOPER = "Разработчик"
    const val SUPPORT = "Агент Поддержки"

    val all = listOf(DEVELOPER, SUPPORT)
}

/** Цвета ника, которые разработчик может выбрать себе и назначить другим. */
object NickColors {

    /** Пустая строка — обычный цвет из темы приложения. */
    val all: List<Pair<String, String>> = listOf(
        "" to "Обычный",
        "#E5484D" to "Красный",
        "#F2820A" to "Оранжевый",
        "#2E9E5B" to "Зелёный",
        "#3B82F6" to "Синий",
        "#8B5CF6" to "Фиолетовый",
"#E0669B" to "Розовый",
        "#B3121B" to "Кроваво-красный",
        "#00E676" to "Ярко-зелёный",
        "#00B0FF" to "Ярко-голубой",
        "#FFD600" to "Золотой",
        "#FF6D00" to "Апельсиновый",
        "#8E24AA" to "Аметистовый",
        "#00BFA5" to "Бирюзовый"
    )

    fun titleOf(hex: String): String =
        all.firstOrNull { it.first.equals(hex, true) }?.second ?: "Обычный"

    fun colorOf(hex: String, fallback: Int): Int =
        if (hex.isBlank()) fallback
        else runCatching { Color.parseColor(hex) }.getOrDefault(fallback)
}

/** Префиксы и статусы, которые показываются рядом с именем пользователя. */
object StatusMarks {

    const val EXCELLENT = "Отличник"
    const val ACTIVE = "Активист"
    const val ON_STREAK = "Серия дней"

    /** Порог очков, с которого выдаётся школьный статус. */
    private const val ACTIVE_FROM = 300
    private const val EXCELLENT_FROM = 1000

    /** Служебный префикс: у разработчика он есть всегда, остальным его ставит разработчик. */
    fun prefixOf(account: Account): String = when {
        account.prefix.isNotBlank() -> account.prefix
        account.isDeveloper -> Prefixes.DEVELOPER
        else -> ""
    }

    /** Статусы, которые видит любой пользователь: считаются по очкам. */
    fun statusesOf(account: Account): List<String> {
        val out = ArrayList<String>(2)
        if (account.score >= EXCELLENT_FROM) out.add(EXCELLENT)
        if (account.score >= ACTIVE_FROM) out.add(ACTIVE)
        return out
    }

    /** Имя с префиксом — так оно выводится в списках и переписке. */
    fun titleOf(account: Account): String {
        val prefix = prefixOf(account)
        return if (prefix.isBlank()) account.fullName else "$prefix · ${account.fullName}"
    }

    fun statusLine(account: Account): String = statusesOf(account).joinToString(" · ")

    /**
     * Цвет ника. У разработчиков и носителей префикса он свой: если цвет не выбран,
     * используется [markDefault] — по умолчанию красный.
     */
    fun colorOf(account: Account, fallback: Int, markDefault: Int = fallback): Int =
        if (account.isDeveloper || account.prefix.isNotBlank()) {
            if (account.nickColor.isBlank()) markDefault
            else NickColors.colorOf(account.nickColor, markDefault)
        } else {
            fallback
        }
}
