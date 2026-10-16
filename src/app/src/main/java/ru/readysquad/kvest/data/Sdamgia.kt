package ru.readysquad.kvest.data

object Sdamgia {

    private var app: android.content.Context? = null

    /** Приложение передаёт контекст один раз: он нужен кэшу заданий. */
    fun attach(context: android.content.Context) {
        app = context.applicationContext
    }

    private val DOMAINS: Map<String, Pair<String, String>> = mapOf(
        "math" to ("math-oge" to "math-ege"),
        "rus" to ("rus-oge" to "rus-ege"),
        "hist" to ("hist-oge" to "hist-ege"),
        "phys" to ("phys-oge" to "phys-ege"),
        "chem" to ("chem-oge" to "chem-ege"),
        "bio" to ("bio-oge" to "bio-ege"),
        "geo" to ("geo-oge" to "geo-ege"),
        "info" to ("inf-oge" to "inf-ege"),
        "eng" to ("en-oge" to "en-ege")
    )

    private const val PER_TOPIC_MAX = 2

    private const val TOPICS_MAX = 8

    private const val TIME_BUDGET_MS = 15_000L

    private const val RESERVE_EXTRA = 4

    private val reserve = java.util.Collections.synchronizedMap(HashMap<String, MutableList<Task>>())

    data class Result(val tasks: List<Task>, val error: String? = null)

    fun domainFor(subjectCode: String, exam: String): String? {
        val pair = DOMAINS[subjectCode] ?: return null
        return if (exam == Exam.EGE) pair.second else pair.first
    }

    fun hasSource(subjectCode: String): Boolean = DOMAINS.containsKey(subjectCode)

    fun quest(subjectCode: String, exam: String, limit: Int, seed: Long = System.nanoTime()): Result {
        if (!hasSource(subjectCode)) {
            return Result(emptyList(), "Для этого предмета онлайн-источник не подключён")
        }

        drain(key(subjectCode, exam), limit)?.let { return Result(it) }

        val cache = app?.let { TaskCache(it) }

        // Свежие сохранённые задания отдаём сразу, не обращаясь к сети.
        cache?.take(subjectCode, exam, limit)?.let { saved ->
            if (saved.size >= limit) return Result(saved, "Задания из сохранённых")
        }

        val first = collect(subjectCode, exam, limit + RESERVE_EXTRA, seed)
        if (first.tasks.isNotEmpty()) {
            cache?.save(first.tasks)
            return serve(subjectCode, exam, first, limit)
        }

        val other = if (exam == Exam.OGE) Exam.EGE else Exam.OGE
        val second = collect(subjectCode, other, limit + RESERVE_EXTRA, seed + 1)
        if (second.tasks.isNotEmpty()) {
            cache?.save(second.tasks)
            return serve(subjectCode, other, second, limit)
        }

        // Сети нет — отдаём то, что сохранили раньше, даже если задания уже старые.
        val fallback = when (exam) {
            Exam.OGE -> Exam.EGE
            else -> Exam.OGE
        }
        cache?.takeAny(subjectCode, exam, limit)?.let { saved ->
            if (saved.isNotEmpty()) return Result(saved, "Открытый банк недоступен: сохранённые задания")
        }
        cache?.takeAny(subjectCode, fallback, limit)?.let { saved ->
            if (saved.isNotEmpty()) return Result(saved, "Открытый банк недоступен: сохранённые задания")
        }
        return first
    }

    private fun key(subjectCode: String, exam: String) = subjectCode + "|" + exam

    private fun serve(subjectCode: String, exam: String, result: Result, limit: Int): Result {
        val now = result.tasks.take(limit)
        val rest = result.tasks.drop(limit)
        if (rest.isNotEmpty()) reserve[key(subjectCode, exam)] = ArrayList(rest)
        return Result(now)
    }

    private fun drain(key: String, limit: Int): List<Task>? {
        val pool = reserve[key] ?: return null
        if (pool.size < limit) {
            reserve.remove(key)
            return null
        }
        val take = ArrayList<Task>()
        synchronized(pool) {
            while (take.size < limit && pool.isNotEmpty()) take.add(pool.removeAt(0))
        }
        if (pool.isEmpty()) reserve.remove(key)
        return take
    }

    private fun collect(subjectCode: String, exam: String, limit: Int, seed: Long): Result {
        val domain = domainFor(subjectCode, exam)
            ?: return Result(emptyList(), "Для этого предмета онлайн-источник не подключён")

        val random = java.util.Random(seed)
        val topics = catalog(domain)
        if (topics.isEmpty()) {
            return Result(emptyList(), "Открытый банк не ответил. Проверь интернет и попробуй ещё раз")
        }

        val want = limit + 4
        val raw = ArrayList<RealTask>()
        val shuffled = topics.shuffled(random)
        val deadline = System.currentTimeMillis() + TIME_BUDGET_MS

        var scannedTopics = 0
        for (topic in shuffled) {
            if (raw.size >= want || scannedTopics >= TOPICS_MAX || System.currentTimeMillis() > deadline) break
            scannedTopics++
            var takenHere = 0
            val ids = problemIds(domain, topic.second, 6).shuffled(random)
            for (id in ids) {
                if (raw.size >= want || takenHere >= PER_TOPIC_MAX || System.currentTimeMillis() > deadline) break
                val task = problem(domain, id, topic.first) ?: continue
                if (!acceptable(task)) continue
                raw.add(task)
                takenHere++
            }
        }

        if (raw.isEmpty()) {

            return Result(emptyList(), "В этом разделе нет заданий с кратким ответом. Выбери другой предмет или уровень")
        }

        val pool = raw.map { it.answer }
        val out = ArrayList<Task>()
        for (task in raw) {
            if (out.size >= limit) break
            val options = optionsFor(task.answer, pool) ?: continue
            val correct = options.indexOfFirst { AnswerNorm.matchesSequence(it, task.answer) }
            if (correct < 0) continue
            out.add(
                Task(
                    id = task.id,
                    subjectCode = subjectCode,
                    topic = task.topic.ifBlank { "Задание" } + if (task.number.isNotBlank()) " № ${task.number}" else "",
                    exam = exam,
                    difficulty = 2,
                    question = task.condition,
                    options = options,
                    correctIndex = correct,
                    explanation = task.solution,
                    imageUrl = task.image,
                    source = task.url
                )
            )
        }

        return if (out.isEmpty()) {
            Result(emptyList(), "В этом разделе не нашлось заданий с вариантами ответа")
        } else {
            Result(out)
        }
    }

    private fun catalog(domain: String): List<Pair<String, String>> {
        val html = Http.getHtml("https://$domain.sdamgia.ru/prob_catalog", home(domain)) ?: return emptyList()
        val out = ArrayList<Pair<String, String>>()
        val seen = HashSet<String>()
        Regex("href=\"[^\"]*category_id=(\\d+)[^\"]*\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
            .findAll(html)
            .forEach { m ->
                val id = m.groupValues[1]
                val name = text(m.groupValues[2])
                if (id in seen || name.isEmpty() || name.length > 90) return@forEach
                seen.add(id)
                out.add(name to id)
            }
        return out
    }

    private fun problemIds(domain: String, categoryId: String, limit: Int): List<String> {
        val url = "https://$domain.sdamgia.ru/test?filter=all&category_id=$categoryId"
        val html = Http.getHtml(url, "https://$domain.sdamgia.ru/prob_catalog") ?: return emptyList()
        val out = ArrayList<String>()
        val seen = HashSet<String>()
        Regex("problem\\?id=(\\d+)").findAll(html).forEach { m ->
            val id = m.groupValues[1]
            if (id in seen) return@forEach
            seen.add(id)
            if (out.size < limit) out.add(id)
        }
        return out
    }

    private fun problem(domain: String, id: String, topicName: String): RealTask? {
        val url = "https://$domain.sdamgia.ru/problem?id=$id"
        val html = Http.getHtml(url, "https://$domain.sdamgia.ru/prob_catalog") ?: return null

        val start = html.indexOf("prob_maindiv")
        if (start < 0) return null
        val scope = html.substring(start)

        val condition = block(scope, "class=\"pbody\"") ?: return null
        val answer = block(scope, "class=\"answer\"")
            ?.let { text(it) }
            ?.replace(Regex("^Ответ\\s*:?\\s*"), "")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        val solution = block(scope, "class=\"solution\"")
            ?.let { text(it) }
            ?.replace(Regex("Ответ\\s*:.*$"), "")
            ?.trim()
            .orEmpty()
        val number = block(scope, "class=\"nobreak\"")
            ?.let { text(it) }
            ?.let { Regex("Тип\\s+([\\w\\d]+)").find(it)?.groupValues?.get(1) }
            .orEmpty()

        val body = text(condition)
        val image = Regex("<img[^>]+src=\"([^\"]+)\"").find(condition)?.groupValues?.get(1)
        val imageUrl = image?.let {
            if (it.startsWith("http")) it else "https://$domain.sdamgia.ru$it"
        }

        return RealTask(
            id = id.toLongOrNull() ?: 0L,
            subjectCode = "",
            exam = "",
            topic = topicName,
            number = number,
            condition = body,
            answer = answer,
            solution = solution,
            image = imageUrl,
            url = url
        )
    }

    private fun block(html: String, marker: String): String? {
        val at = html.indexOf(marker)
        if (at < 0) return null
        val openAt = html.lastIndexOf('<', at)
        if (openAt < 0) return null
        val tagEnd = html.indexOf('>', at)
        if (tagEnd < 0) return null
        val opening = html.substring(openAt, tagEnd + 1)
        val tag = Regex("^<\\s*([a-zA-Z0-9]+)").find(opening)?.groupValues?.get(1)?.lowercase() ?: "div"
        if (opening.trimEnd().endsWith("/>")) return ""
        val openRe = Regex("<\\s*" + tag + "[\\s>/]", RegexOption.IGNORE_CASE)
        val closeRe = Regex("</\\s*" + tag + "\\s*>", RegexOption.IGNORE_CASE)

        var i = tagEnd + 1
        var depth = 1
        while (i < html.length) {
            val no = openRe.find(html, i)
            val nc = closeRe.find(html, i)
            if (nc == null) return html.substring(tagEnd + 1)
            if (no != null && no.range.first < nc.range.first) {
                val gt = html.indexOf('>', no.range.first)
                val selfClosed = gt > 0 && html.substring(no.range.first, gt + 1).trimEnd().endsWith("/>")
                if (!selfClosed) depth++
                i = if (gt > 0) gt + 1 else no.range.first + 1
            } else {
                depth--
                if (depth == 0) return html.substring(tagEnd + 1, nc.range.first)
                i = nc.range.last + 1
            }
        }
        return html.substring(tagEnd + 1)
    }

    private fun stripTags(html: String): String {
        val out = StringBuilder(html.length)
        var i = 0
        while (i < html.length) {
            if (html[i] != '<') {
                out.append(html[i])
                i++
                continue
            }
            var j = i + 1
            var quote = ' '
            while (j < html.length) {
                val ch = html[j]
                if (quote != ' ') {
                    if (ch == quote) quote = ' '
                } else if (ch == '"' || ch == '\'') {
                    quote = ch
                } else if (ch == '>') {
                    break
                }
                j++
            }
            if (j >= html.length) break
            out.append(' ')
            i = j + 1
        }
        return out.toString()
    }

    private fun text(html: String): String {
        var s = Regex("<(script|style)[^>]*>.*?</\\1>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .replace(html, " ")
        s = stripTags(s)
        ENTITIES.forEach { (from, to) -> s = s.replace(from, to) }
        s = Regex("&#(\\d+);").replace(s) { it.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: " " }
        s = Regex("&#x([0-9a-fA-F]+);").replace(s) {
            it.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: " "
        }
        return s.replace("\u00ad", "")
            .replace("\u200b", "")
            .replace("\u00a0", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private val ENTITIES = listOf(
        "&nbsp;" to " ", "&quot;" to "\"", "&#039;" to "'", "&apos;" to "'",
        "&laquo;" to "«", "&raquo;" to "»", "&mdash;" to "—", "&ndash;" to "–",
        "&minus;" to "−", "&deg;" to "°", "&shy;" to "", "&amp;" to "&",
        "&lt;" to "<", "&gt;" to ">"
    )

    private fun acceptable(task: RealTask): Boolean {
        val answer = task.answer
        if (answer.isEmpty() || answer.length > 12) return false
        if (answer.any { it == '{' || it == '}' || it == '[' || it == ']' || it == '<' || it == '>' || it == '|' }) return false
        if (!AnswerNorm.isCellFriendly(answer)) return false
        val condition = task.condition

        if (condition.length < 15 || condition.length > 900) return false

        if (answer.count { it == ',' || it == '.' } > 0 && answer.length > 6) return false
        return true
    }

    fun optionsFor(answer: String, pool: List<String>): List<String>? {
        val norm = AnswerNorm.normalize(answer)
        if (norm.isEmpty()) return null

        val options = LinkedHashSet<String>()
        options.add(answer)

        val numeric = norm.toDoubleOrNull()
        if (numeric != null) {
            val decimals = decimalsOf(norm)
            val candidates = ArrayList<Double>()
            if (decimals == 0) {
                val v = numeric.toLong()
                candidates.add((v + 1).toDouble())
                candidates.add((v - 1).toDouble())
                candidates.add((v + 2).toDouble())
                candidates.add((v - 2).toDouble())
                candidates.add((v * 2).toDouble())
                candidates.add((v + 10).toDouble())
                candidates.add((v - 10).toDouble())
            } else {
                candidates.add(numeric * 10)
                candidates.add(numeric / 10)
                candidates.add(numeric + 0.1)
                candidates.add(numeric - 0.1)
                candidates.add(numeric * 2)
                candidates.add(numeric - 0.05)
            }
            candidates.shuffled().forEach { c ->
                if (options.size >= 4) return@forEach
                if (numeric > 0 && c <= 0) return@forEach
                val formatted = format(c, decimals, answer.contains(','))
                if (formatted.isEmpty()) return@forEach
                if (options.none { AnswerNorm.normalize(it) == AnswerNorm.normalize(formatted) }) {
                    options.add(formatted)
                }
            }
        } else {

            val others = pool.filter { it != answer }.shuffled()
            fill(options, others.filter { Math.abs(AnswerNorm.normalize(it).length - norm.length) <= 3 })
            if (options.size < 4) fill(options, others)
        }

        if (options.size < 4) return null
        val list = options.take(4).toMutableList()
        list.shuffle()
        return list
    }

    private fun fill(options: MutableSet<String>, candidates: List<String>) {
        for (c in candidates) {
            if (options.size >= 4) return
            if (options.none { AnswerNorm.normalize(it) == AnswerNorm.normalize(c) }) options.add(c)
        }
    }

    private fun decimalsOf(norm: String): Int =
        if (norm.contains('.')) norm.length - norm.indexOf('.') - 1 else 0

    private fun format(value: Double, decimals: Int, comma: Boolean): String {
        val raw = if (decimals == 0) {
            if (Math.abs(value) > 1e9 || value != Math.floor(value)) return ""
            Math.round(value).toString()
        } else {
            String.format(java.util.Locale.US, "%.${decimals}f", value)
        }
        return if (comma) raw.replace('.', ',') else raw
    }

    private fun home(domain: String) = "https://$domain.sdamgia.ru/"
}
