package ru.readysquad.kvest.util

object Validate {

    fun fullName(value: String): String? {
        val trimmed = value.trim().replace(Regex("\\s+"), " ")
        if (trimmed.isEmpty()) return "Укажи ФИО"
        val words = trimmed.split(" ").filter { it.isNotBlank() }
        if (words.size < 2) return "ФИО — минимум имя и фамилия"
        if (words.size > 3) return "ФИО — не больше трёх слов"
        val bad = words.firstOrNull { !it.matches(Regex("[А-Яа-яЁёA-Za-z\\-]+")) }
        if (bad != null) return "В ФИО только буквы: «$bad» не подходит"
        if (words.any { it.length < 2 }) return "Каждое слово ФИО — минимум две буквы"
        return null
    }

    fun birthDate(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return "Укажи дату рождения"
        if (!trimmed.matches(Regex("\\d{2}\\.\\d{2}\\.\\d{2}"))) return "Дата — строго в виде дд.мм.гг"
        val day = trimmed.substring(0, 2).toInt()
        val month = trimmed.substring(3, 5).toInt()
        val year = trimmed.substring(6, 8).toInt()
        if (month !in 1..12) return "Месяца $month не существует"
        if (day !in 1..daysInMonth(month, year)) return "В этом месяце нет дня $day"

        val fullYear = if (year <= 30) 2000 + year else 1900 + year
        val age = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) - fullYear
        if (age !in 6..99) return "Проверь год: получается возраст $age"
        return null
    }

    private fun daysInMonth(month: Int, year2: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        else -> if (year2 % 4 == 0) 29 else 28
    }

    fun phone(value: String): String? {
        val digits = digitsOf(value)
        if (digits.isEmpty()) return "Укажи номер телефона"
        if (digits.length != 11) return "В номере должно быть 11 цифр, а введено ${digits.length}"
        if (!digits.startsWith("7")) return "Номер должен начинаться с +7"
        return null
    }

    /** Почта вместо телефона: простая проверка вида pupil@school.ru. */
    fun email(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return "Укажи почту"
        if (!trimmed.contains('@') || !trimmed.substringAfter('@').contains('.')) {
            return "Почта должна быть вида pupil@school.ru"
        }
        if (trimmed.length > 64) return "Слишком длинная почта"
        return null
    }

    fun formatPhone(value: String): String {
        var digits = digitsOf(value)
        if (digits.startsWith("8")) digits = "7" + digits.substring(1)
        if (digits.isNotEmpty() && !digits.startsWith("7")) digits = "7$digits"
        digits = digits.take(11)
        val body = digits.removePrefix("7")
        val out = StringBuilder("+7")
        if (body.isNotEmpty()) out.append(" ").append(body.substring(0, minOf(3, body.length)))
        if (body.length > 3) out.append(" ").append(body.substring(3, minOf(6, body.length)))
        if (body.length > 6) out.append("-").append(body.substring(6, minOf(8, body.length)))
        if (body.length > 8) out.append("-").append(body.substring(8, minOf(10, body.length)))
        return out.toString()
    }

    fun formatDate(value: String): String {
        val digits = value.filter { it.isDigit() }.take(6)
        val out = StringBuilder()
        digits.forEachIndexed { index, ch ->
            if (index == 2 || index == 4) out.append('.')
            out.append(ch)
        }
        return out.toString()
    }

    fun formatDigits(value: String, maxDigits: Int = 6): String =
        value.filter { it.isDigit() }.take(maxDigits)

    fun login(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return "Придумай логин"
        if (trimmed.length < 3) return "Логин — минимум 3 символа"
        if (trimmed.length > 24) return "Логин — не больше 24 символов"
        if (!trimmed.matches(Regex("[A-Za-zА-Яа-яЁё0-9_]+")))
            return "В логине можно: русские и английские буквы, цифры и подчёркивание"
        return null
    }

    const val PASSWORD_MIN = 8

    fun password(value: String): String? {
        if (value.isBlank()) return "Придумай пароль"
        val trimmed = value.trim()
        if (trimmed.length < PASSWORD_MIN) return "Пароль — минимум $PASSWORD_MIN символов"
        if (trimmed.length > 64) return "Пароль — не больше 64 символов"
        if (trimmed.all { it.isDigit() }) return "Пароль только из цифр легко подобрать — добавь буквы"
        if (trimmed.all { it.isLetter() }) return "Пароль только из букв легко подобрать — добавь цифры или знак"
        return null
    }

    /** Надёжность пароля: оценка 0–4, подпись и совет для интерфейса. */
    fun strength(value: String): Strength {
        val pw = value.trim()
        if (pw.isEmpty()) return Strength(0, "", "")
        val kinds = listOf(
            pw.any { it.isDigit() },
            pw.any { it.isLowerCase() },
            pw.any { it.isUpperCase() },
            pw.any { !it.isLetterOrDigit() }
        ).count { it }
        var score = 0
        if (pw.length >= PASSWORD_MIN) score++
        if (pw.length >= 12) score++
        score += when {
            kinds >= 3 -> 2
            kinds == 2 -> 1
            else -> 0
        }
        return when (score.coerceAtMost(4)) {
            0, 1 -> Strength(1, "Слабый пароль", "добавь длину и разные символы")
            2 -> Strength(2, "Средний пароль", "добавь заглавные буквы или цифры")
            3 -> Strength(3, "Хороший пароль", "почти надёжный")
            else -> Strength(4, "Надёжный пароль", "так и оставь")
        }
    }

    data class Strength(val score: Int, val label: String, val tip: String)

    fun schoolCode(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return "Введи код школы"
        if (!trimmed.matches(Regex("\\d{4}"))) return "Код школы — четыре цифры"
        return null
    }

    private fun digitsOf(value: String): String = value.filter { it.isDigit() }
}
