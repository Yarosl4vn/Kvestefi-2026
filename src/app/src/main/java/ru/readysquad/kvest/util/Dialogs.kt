package ru.readysquad.kvest.util

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ru.readysquad.kvest.R
import ru.readysquad.kvest.data.Account
import ru.readysquad.kvest.data.AnswerRow
import ru.readysquad.kvest.data.Approval
import ru.readysquad.kvest.data.Attempt
import ru.readysquad.kvest.data.Badge
import ru.readysquad.kvest.data.Prefs
import ru.readysquad.kvest.data.Scoring
import ru.readysquad.kvest.data.Stats
import ru.readysquad.kvest.data.TimeFmt
import ru.readysquad.kvest.data.TopicStat

object Dialogs {

    private fun dp(ctx: Context, v: Int) =
        (v * ctx.resources.displayMetrics.density).toInt()

    private fun color(ctx: Context, res: Int) = ContextCompat.getColor(ctx, res)

    private fun column(ctx: Context): LinearLayout {
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        val pad = dp(ctx, 20)
        box.setPadding(pad, dp(ctx, 8), pad, dp(ctx, 4))
        return box
    }

    private fun scroll(ctx: Context, inner: View): ScrollView {
        val sv = ScrollView(ctx)
        sv.addView(inner, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        return sv
    }

    private fun title(ctx: Context, text: String, top: Int = 0): TextView =
        TextView(ctx).apply {
            this.text = text
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(color(ctx, R.color.accent))
            setPadding(0, dp(ctx, top), 0, dp(ctx, 6))
            letterSpacing = 0.06f
        }

    private fun paragraph(
        ctx: Context,
        text: String,
        size: Float = 14f,
        colorRes: Int = R.color.on_bg,
        top: Int = 0,
        bold: Boolean = false
    ): TextView =
        TextView(ctx).apply {
            this.text = text
            textSize = size
            setTextColor(color(ctx, colorRes))
            setPadding(0, dp(ctx, top), 0, dp(ctx, 4))
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun kv(ctx: Context, key: String, value: String, valueColorRes: Int = R.color.on_bg): LinearLayout {
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(0, dp(ctx, 5), 0, dp(ctx, 5))
        row.addView(
            TextView(ctx).apply {
                text = key
                textSize = 14f
                setTextColor(color(ctx, R.color.on_bg_variant))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        row.addView(
            TextView(ctx).apply {
                text = value
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(color(ctx, valueColorRes))
                gravity = Gravity.END
            }
        )
        return row
    }

    private fun bar(ctx: Context, fraction: Float, heightDp: Int = 8): View {
        val holder = FrameLayout(ctx)
        holder.setBackgroundResource(R.drawable.bg_stat)
        val h = dp(ctx, heightDp)
        holder.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h)
        val fill = View(ctx)
        fill.setBackgroundResource(R.drawable.bg_pill_primary)
        val f = fraction.coerceIn(0f, 1f)
        holder.addView(fill, FrameLayout.LayoutParams(1, ViewGroup.LayoutParams.MATCH_PARENT))
        holder.post {
            fill.layoutParams = FrameLayout.LayoutParams(
                (holder.width * f).toInt().coerceAtLeast(if (f > 0f) dp(ctx, 6) else 1),
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        val wrap = LinearLayout(ctx)
        wrap.setPadding(0, dp(ctx, 6), 0, dp(ctx, 10))
        wrap.addView(holder)
        return wrap
    }

    private fun show(ctx: Context, view: View, positive: String = "Понятно") {
        MaterialAlertDialogBuilder(ctx)
            .setView(view)
            .setPositiveButton(positive, null)
            .show()
    }

    fun level(ctx: Context, prefs: Prefs, stats: Stats, onTop: (() -> Unit)? = null) {
        val col = column(ctx)
        val level = Leveling.level(stats.totalScore)
        col.addView(paragraph(ctx, "Уровень $level · ${Leveling.title(level)}", 22f, bold = true))
        col.addView(paragraph(ctx, Leveling.hint(level), 14f, R.color.on_bg_variant, top = 4))
        col.addView(title(ctx, "ПРОГРЕСС", 16))

        val cur = Leveling.startScore(level)
        val next = Leveling.nextScore(level)
        col.addView(
            paragraph(
                ctx,
                "Очков всего: ${stats.totalScore}",
                15f
            )
        )
        col.addView(bar(ctx, (stats.totalScore - cur).toFloat() / (next - cur).toFloat()))
        col.addView(
            paragraph(
                ctx,
                if (level >= Leveling.MAX)
                    "Максимальный уровень достигнут. Дальше — только серия дней."
                else
                    "До уровня ${level + 1} осталось ${next - stats.totalScore} очков",
                14f,
                R.color.accent,
                bold = true
            )
        )
        col.addView(title(ctx, "КАК РАСТУТ ОЧКИ", 14))
        col.addView(paragraph(ctx, "10 очков за верный ответ", 14f))
        col.addView(paragraph(ctx, "+5, если решить быстрее 10 секунд", 14f))
        col.addView(paragraph(ctx, "+5 за каждый верный ответ с третьего подряд", 14f))
        col.addView(
            paragraph(
                ctx,
                "Пройдено квестов: ${stats.quests} · точность ${stats.accuracy}%",
                14f,
                R.color.on_bg_variant,
                top = 8
            )
        )
        val builder = MaterialAlertDialogBuilder(ctx)
            .setView(scroll(ctx, col))
            .setPositiveButton("Понятно", null)
        if (onTop != null) {
            builder.setNegativeButton("Топ по предметам") { _, _ -> onTop() }
        }
        builder.show()
    }

    fun goal(ctx: Context, prefs: Prefs, stats: Stats) {
        val col = column(ctx)
        val done = prefs.todayQuests
        val goal = prefs.goalPerDay.coerceAtLeast(1)
        col.addView(paragraph(ctx, "Цель на сегодня", 22f, bold = true))
        col.addView(
            paragraph(
                ctx,
                if (done >= goal) "Выполнено: $done из $goal. Отличная работа!"
                else "Пройдено $done из $goal квестов",
                15f,
                R.color.on_bg_variant,
                top = 4
            )
        )
        col.addView(bar(ctx, done.toFloat() / goal.toFloat()))
        col.addView(title(ctx, "ЧТО СЧИТАЕТСЯ КВЕСТОМ", 12))
        col.addView(paragraph(ctx, "Квест — это пять заданий по одному предмету.", 14f))
        col.addView(paragraph(ctx, "Задания открытого банка тоже идут в счёт цели.", 14f))
        col.addView(paragraph(ctx, "Дневная цель меняется в настройках.", 14f, R.color.on_bg_variant))
        col.addView(title(ctx, "СЕГОДНЯ", 14))
        col.addView(kv(ctx, "Квестов пройдено", done.toString()))
        col.addView(kv(ctx, "Дневная цель", goal.toString()))
        col.addView(kv(ctx, "Серия дней", "${prefs.streak}"))
        show(ctx, scroll(ctx, col))
    }

    fun streak(ctx: Context, prefs: Prefs) {
        val col = column(ctx)
        col.addView(paragraph(ctx, "Серия: ${prefs.streak} дней", 22f, bold = true))
        col.addView(
            paragraph(
                ctx,
                "Серия растёт, когда решаешь хотя бы один квест в день, и сбрасывается при пропуске дня.",
                14f,
                R.color.on_bg_variant,
                top = 4
            )
        )
        col.addView(title(ctx, "ПОСЛЕДНИЕ ДНИ", 14))
        val days = prefs.activeDays.sorted().takeLast(7)
        if (days.isEmpty()) {
            col.addView(paragraph(ctx, "Пока ни одного дня с квестом.", 14f))
        } else {
            days.forEach { col.addView(kv(ctx, it, "квест пройден")) }
        }
        col.addView(title(ctx, "ИТОГИ", 14))
        col.addView(kv(ctx, "Текущая серия", "${prefs.streak}"))
        col.addView(kv(ctx, "Лучшая серия", "${prefs.bestDayStreak}"))
        col.addView(kv(ctx, "Дней с занятиями", "${prefs.activeDays.size}"))
        show(ctx, scroll(ctx, col))
    }

    fun topSubjects(
        ctx: Context,
        best: Map<String, Int>,
        runs: Map<String, Int>,
        titleOf: (String) -> String
    ) {
        val col = column(ctx)
        col.addView(paragraph(ctx, "Топ по предметам", 22f, bold = true))
        col.addView(
            paragraph(
                ctx,
                "Личный зачёт: лучший результат по каждому предмету.",
                14f,
                R.color.on_bg_variant,
                top = 4
            )
        )
        val rows = best.entries.sortedByDescending { it.value }
        if (rows.isEmpty()) {
            col.addView(paragraph(ctx, "Пока нет пройденных квестов.", 14f, top = 10))
        }
        rows.forEachIndexed { i, e ->
            col.addView(title(ctx, "${i + 1}. ${titleOf(e.key)}", 12))
            col.addView(kv(ctx, "Лучший результат", "${e.value} очков"))
            col.addView(kv(ctx, "Пройдено квестов", "${runs[e.key] ?: 0}"))
        }
        show(ctx, scroll(ctx, col))
    }

    fun notifications(ctx: Context, items: List<Notifier.Item>) {
        val col = column(ctx)
        col.addView(paragraph(ctx, "Уведомления", 22f, bold = true))
        if (items.isEmpty()) {
            col.addView(
                paragraph(
                    ctx,
                    "Пока новостей нет. Пройди квест — здесь появятся достижения и подсказки.",
                    15f,
                    R.color.on_bg_variant,
                    top = 6
                )
            )
        }
        items.forEach { item ->
            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.HORIZONTAL
            row.setPadding(0, dp(ctx, 10), 0, dp(ctx, 10))
            val icon = ImageView(ctx)
            icon.setImageResource(item.iconRes)
            icon.setColorFilter(color(ctx, if (item.warn) R.color.danger else R.color.accent))
            val side = dp(ctx, 26)
            val iconBox = FrameLayout(ctx)
            iconBox.setBackgroundResource(R.drawable.bg_circle_surface)
            iconBox.addView(icon, FrameLayout.LayoutParams(dp(ctx, 16), dp(ctx, 16), Gravity.CENTER))
            row.addView(
                iconBox,
                LinearLayout.LayoutParams(side, side).apply { marginEnd = dp(ctx, 12) }
            )
            val texts = LinearLayout(ctx)
            texts.orientation = LinearLayout.VERTICAL
            texts.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            texts.addView(
                TextView(ctx).apply {
                    text = item.title
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(color(ctx, R.color.on_bg))
                }
            )
            texts.addView(
                TextView(ctx).apply {
                    text = item.text
                    textSize = 13f
                    setTextColor(color(ctx, R.color.on_bg_variant))
                    setPadding(0, dp(ctx, 3), 0, 0)
                }
            )
            row.addView(texts)
            col.addView(row)
        }
        show(ctx, scroll(ctx, col))
    }

    fun badge(ctx: Context, badge: Badge) {
        val col = column(ctx)
        val icon = ImageView(ctx).apply {
            setImageResource(BadgeIcons.of(badge.code))
            setColorFilter(color(ctx, R.color.accent))
        }
        val holder = FrameLayout(ctx)
        holder.setBackgroundResource(R.drawable.bg_circle_surface)
        val size = dp(ctx, 64)
        holder.addView(icon, FrameLayout.LayoutParams(dp(ctx, 34), dp(ctx, 34), Gravity.CENTER))
        val wrap = LinearLayout(ctx)
        wrap.gravity = Gravity.CENTER_HORIZONTAL
        wrap.orientation = LinearLayout.VERTICAL
        wrap.addView(holder, LinearLayout.LayoutParams(size, size))
        col.addView(wrap)

        col.addView(
            paragraph(ctx, badge.title, 21f, bold = true).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(ctx, 12), 0, dp(ctx, 2))
            }
        )
        col.addView(
            paragraph(
                ctx,
                if (badge.isUnlocked) "Получено " + TimeFmt.short(badge.unlockedAt ?: 0L)
                else "Пока не получено",
                14f,
                if (badge.isUnlocked) R.color.secondary else R.color.on_bg_variant
            ).apply { gravity = Gravity.CENTER_HORIZONTAL }
        )

        col.addView(title(ctx, "КАК ПОЛУЧИТЬ", 18))
        col.addView(paragraph(ctx, badge.description, 15f))

        col.addView(title(ctx, "СТАТУС", 14))
        col.addView(kv(ctx, "Достижение", badge.title))
        col.addView(
            kv(
                ctx,
                "Состояние",
                if (badge.isUnlocked) "открыто" else "закрыто",
                if (badge.isUnlocked) R.color.secondary else R.color.on_bg_variant
            )
        )
        if (badge.isUnlocked) {
            col.addView(kv(ctx, "Дата", TimeFmt.short(badge.unlockedAt ?: 0L)))
        } else {
            col.addView(
                paragraph(
                    ctx,
                    "Продолжай решать квесты — достижение откроется автоматически.",
                    14f,
                    R.color.on_bg_variant,
                    top = 8
                )
            )
        }
        show(ctx, scroll(ctx, col))
    }

    fun attempt(
        ctx: Context,
        attempt: Attempt,
        rows: List<AnswerRow>,
        subjectTitle: String,
        onStats: (() -> Unit)? = null
    ) {
        val col = column(ctx)
        col.addView(paragraph(ctx, subjectTitle, 22f, bold = true))
        col.addView(
            paragraph(
                ctx,
                TimeFmt.full(attempt.createdAt) + " · " + TimeFmt.duration(attempt.seconds),
                14f,
                R.color.on_bg_variant,
                top = 4
            )
        )
        col.addView(title(ctx, "ИТОГ", 14))
        col.addView(kv(ctx, "Верных ответов", "${attempt.correct} из ${attempt.total}"))
        col.addView(kv(ctx, "Очки", "${attempt.score}"))
        col.addView(kv(ctx, "Оценка", Scoring.gradeLabel(attempt.grade)))
        col.addView(kv(ctx, "Время", TimeFmt.duration(attempt.seconds)))

        col.addView(title(ctx, "ЗАДАНИЯ", 16))
        if (rows.isEmpty()) {
            col.addView(
                paragraph(
                    ctx,
                    "Эта попытка сделана до обновления: задания в ней не сохранялись.",
                    14f,
                    R.color.on_bg_variant
                )
            )
        }
        rows.forEachIndexed { i, r ->
            val head = TextView(ctx).apply {
                text = "${i + 1}. " + if (r.isCorrect) "верно" else "неверно"
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(color(ctx, if (r.isCorrect) R.color.secondary else R.color.danger))
                setPadding(0, dp(ctx, 12), 0, dp(ctx, 2))
            }
            col.addView(head)
            if (r.topic.isNotBlank()) {
                col.addView(paragraph(ctx, r.topic, 13f, R.color.on_bg_variant))
            }
            if (r.question.isNotBlank()) {
                col.addView(paragraph(ctx, r.question, 14f))
            }
            col.addView(kv(ctx, "Твой ответ", r.typed.ifBlank { "нет ответа" }))
            col.addView(kv(ctx, "Верный ответ", r.expected.ifBlank { "—" }, R.color.accent))
        }

        val builder = MaterialAlertDialogBuilder(ctx)
            .setView(scroll(ctx, col))
            .setPositiveButton("Закрыть", null)
        if (onStats != null) {
            builder.setNegativeButton("Статистика по предмету") { _, _ -> onStats() }
        }
        builder.show()
    }

    fun subjectStats(
        ctx: Context,
        subjectTitle: String,
        attempts: List<Attempt>,
        topics: List<TopicStat>,
        onAttempt: ((Attempt) -> Unit)? = null,
        subjectCode: String = "",
        onDrill: ((String) -> Unit)? = null
    ) {
        val col = column(ctx)
        col.addView(paragraph(ctx, subjectTitle, 22f, bold = true))
        val best = attempts.maxOfOrNull { it.score } ?: 0
        val totalCorrect = attempts.sumOf { it.correct }
        val totalAnswers = attempts.sumOf { it.total }
        val accuracy = if (totalAnswers == 0) 0 else totalCorrect * 100 / totalAnswers
        col.addView(
            paragraph(
                ctx,
                "Попыток: ${attempts.size} · точность $accuracy%",
                14f,
                R.color.on_bg_variant,
                top = 4
            )
        )
        col.addView(title(ctx, "РЕЗУЛЬТАТЫ", 14))
        col.addView(kv(ctx, "Лучший результат", "$best очков"))
        col.addView(kv(ctx, "Верных ответов", "$totalCorrect из $totalAnswers"))
        col.addView(kv(ctx, "Оценка по последней", Scoring.gradeLabel(attempts.firstOrNull()?.grade ?: 0)))
        col.addView(title(ctx, "КАК МЕНЯЛСЯ РЕЗУЛЬТАТ", 14))
        attempts.take(8).reversed().forEach { a ->
            col.addView(bar(ctx, a.score.toFloat() / 50f))
            val line = paragraph(
                ctx,
                TimeFmt.short(a.createdAt) + " · ${a.score} очков · ${a.correct} из ${a.total}",
                13f,
                R.color.on_bg_variant
            )
            if (onAttempt != null) {

                line.isClickable = true
                line.setOnClickListener { onAttempt(a) }
            }
            col.addView(line)
        }
        col.addView(title(ctx, "ТЕМЫ", 16))
        if (topics.isEmpty()) {
            col.addView(paragraph(ctx, "Пока нет данных по темам.", 14f, R.color.on_bg_variant))
        }
        topics.forEach { t ->
            col.addView(kv(ctx, t.topic, "${t.correct} из ${t.total}",
                if (t.accuracy >= 60) R.color.secondary else R.color.danger))
        }

        // Самая слабая тема: предлагаем сразу её закрыть, а не просто посмотреть цифры.
        val weakest = topics.filter { it.total >= 2 && it.accuracy < 70 }
            .minByOrNull { it.accuracy }
        if (weakest != null) {
            col.addView(title(ctx, "СЛАБАЯ ТЕМА", 16))
            col.addView(
                paragraph(
                    ctx,
                    "«${weakest.topic}» — ${weakest.accuracy}% верных ответов. " +
                        "Пять заданий по этой теме закрепят результат: если решить четыре из пяти, " +
                        "тема будет закрыта.",
                    13f,
                    R.color.on_bg_variant
                )
            )
        }

        val builder = MaterialAlertDialogBuilder(ctx)
            .setView(scroll(ctx, col))
            .setPositiveButton("Закрыть", null)
        if (weakest != null && onDrill != null) {
            builder.setNeutralButton("Пройти слабую тему") { _, _ -> onDrill(weakest.topic) }
        }
        builder.show()
    }

    fun image(ctx: Context, url: String, title: String) {
        val holder = LinearLayout(ctx)
        holder.orientation = LinearLayout.VERTICAL
        holder.setBackgroundColor(0xFF000000.toInt())
        holder.setPadding(dp(ctx, 10), dp(ctx, 10), dp(ctx, 10), dp(ctx, 10))
        val picture = ImageView(ctx)
        picture.adjustViewBounds = true
        picture.scaleType = ImageView.ScaleType.FIT_CENTER
        holder.addView(
            picture,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0).apply { weight = 1f }
        )
        holder.addView(
            TextView(ctx).apply {
                text = title
                textSize = 14f
                setTextColor(0xFFEEEEEE.toInt())
                setPadding(0, dp(ctx, 10), 0, 0)
            }
        )
        ImageLoader.load(url, picture)
        MaterialAlertDialogBuilder(ctx)
            .setView(holder)
            .setPositiveButton("Закрыть", null)
            .show()
    }

    fun approvals(
        ctx: Context,
        requests: List<Pair<Approval, Account>>,
        onDecide: (Approval, Boolean) -> Unit
    ) {
        val col = column(ctx)
        col.addView(paragraph(ctx, "Заявки на регистрацию", 21f, bold = true))
        col.addView(
            paragraph(
                ctx,
                "Подтверди тех, кто действительно учится или работает в школе.",
                14f,
                R.color.on_bg_variant,
                top = 4
            )
        )
        if (requests.isEmpty()) {
            col.addView(paragraph(ctx, "Новых заявок нет.", 15f, top = 12))
        }
        requests.forEach { (approval, user) ->
            col.addView(title(ctx, if (user.isTeacher) "УЧИТЕЛЬ" else "УЧЕНИК", 16))
            col.addView(paragraph(ctx, user.fullName, 17f, bold = true))
            col.addView(kv(ctx, "Дата рождения", user.birthDate.ifBlank { "—" }))
            col.addView(kv(ctx, "Почта", user.phone.ifBlank { "—" }))
            col.addView(kv(ctx, "Регион", user.region.ifBlank { "—" }))
            col.addView(kv(ctx, "Школа", user.school.ifBlank { "—" }))
            if (user.isStudent) {
                col.addView(kv(ctx, "Класс", ru.readysquad.kvest.data.Grades.label(user.grade, user.letter)))
            } else {
                col.addView(kv(ctx, "Должность", user.position))
                if (user.subject.isNotBlank()) col.addView(kv(ctx, "Предмет", user.subject))
                if (user.homeroom.isNotBlank()) col.addView(kv(ctx, "Класс", user.homeroom))
            }
            col.addView(kv(ctx, "Логин", user.login))
        }

        val first = requests.firstOrNull()
        val builder = MaterialAlertDialogBuilder(ctx).setView(scroll(ctx, col))
        if (first != null) {
            val (approval, user) = first
            builder.setPositiveButton("Подтвердить") { _, _ -> onDecide(approval, true) }
            builder.setNegativeButton("Отклонить") { _, _ -> onDecide(approval, false) }
            builder.setNeutralButton("Закрыть", null)
        } else {
            builder.setPositiveButton("Понятно", null)
        }
        builder.show()
    }

    fun ratingClass(ctx: Context, title: String, rows: List<Account>, myId: Long) {
        val col = column(ctx)
        col.addView(paragraph(ctx, "Рейтинг $title", 21f, bold = true))
        col.addView(
            paragraph(
                ctx,
                "Очки за все пройденные квесты. Рейтинг обновляется после каждого квеста.",
                14f,
                R.color.on_bg_variant,
                top = 4
            )
        )
        if (rows.isEmpty()) {
            col.addView(
                paragraph(
                    ctx,
                    "В классе пока нет зарегистрированных учеников. Пригласи одноклассников — " +
                        "рейтинг появится, как только они пройдут первый квест.",
                    15f,
                    top = 12
                )
            )
        }
        rows.forEachIndexed { i, a ->
            val me = a.id == myId
            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.HORIZONTAL
            row.setPadding(0, dp(ctx, 7), 0, dp(ctx, 7))
            row.addView(
                TextView(ctx).apply {
                    text = "${i + 1}"
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(color(ctx, if (me) R.color.accent else R.color.on_bg_variant))
                    minWidth = dp(ctx, 34)
                }
            )
            row.addView(
                TextView(ctx).apply {
                    text = if (me) a.fullName + "  (ты)" else a.fullName
                    textSize = 15f
                    setTextColor(color(ctx, R.color.on_bg))
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
            )
            row.addView(
                TextView(ctx).apply {
                    text = "${a.score}"
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(color(ctx, R.color.accent))
                }
            )
            col.addView(row)
        }
        show(ctx, scroll(ctx, col))
    }

    fun assignTask(
        ctx: Context,
        subjects: List<ru.readysquad.kvest.data.Subject>,
        topicsOf: (String, String) -> List<String>,
        onCreate: (ru.readysquad.kvest.data.Subject, String, String, Int) -> Unit
    ) {
        if (subjects.isEmpty()) return
        val byTitle = subjects.map { it.title }
        MaterialAlertDialogBuilder(ctx)
            .setTitle("Задание: выбери предмет")
            .setItems(byTitle.toTypedArray()) { _, which ->
                val subject = subjects[which]
                val exams = listOf(
                    ru.readysquad.kvest.data.Exam.OGE,
                    ru.readysquad.kvest.data.Exam.EGE,
                    ru.readysquad.kvest.data.Exam.VPR
                )
                MaterialAlertDialogBuilder(ctx)
                    .setTitle("${subject.title}: что сдают")
                    .setItems(exams.toTypedArray()) { _, e ->
                        val exam = exams[e]
                        val topics = topicsOf(subject.code, exam)
                        if (topics.isEmpty()) {
                            info(ctx, "По этому предмету и уровню нет тем", "Выбери другой предмет или уровень.")
                            return@setItems
                        }
                        MaterialAlertDialogBuilder(ctx)
                            .setTitle("Тема задания")
                            .setItems(topics.toTypedArray()) { _, tp ->
                                val topic = topics[tp]
                                val counts = listOf(5, 10, 15)
                                MaterialAlertDialogBuilder(ctx)
                                    .setTitle("Сколько задач")
                                    .setItems(counts.map { "$it задач" }.toTypedArray()) { _, c ->
                                        onCreate(subject, exam, topic, counts[c])
                                    }
                                    .show()
                            }
                            .show()
                    }
                    .show()
            }
            .show()
    }

    fun info(ctx: Context, title: String, text: String) {
        MaterialAlertDialogBuilder(ctx)
            .setTitle(title)
            .setMessage(text)
            .setPositiveButton("Понятно", null)
            .show()
    }

    fun studentAssignments(
        ctx: Context,
        items: List<ru.readysquad.kvest.data.Assignment>,
        passed: Set<Long>,
        onSolve: (ru.readysquad.kvest.data.Assignment) -> Unit
    ) {
        val col = column(ctx)
        col.addView(paragraph(ctx, "Задания от учителя", 21f, bold = true))
        if (items.isEmpty()) {
            col.addView(
                paragraph(
                    ctx,
                    "Пока заданий нет. Как только учитель выдаст задание классу, оно появится здесь.",
                    15f,
                    R.color.on_bg_variant,
                    top = 8
                )
            )
        }
        items.forEach { a ->
            col.addView(title(ctx, a.exam + " · " + a.topic, 14))
            col.addView(paragraph(ctx, "Задач: ${a.size} · выдал ${a.teacherName}", 14f))
            col.addView(
                paragraph(
                    ctx,
                    if (passed.contains(a.id)) "Задание уже пройдено — можно решить заново"
                    else "Ещё не пройдено",
                    13f,
                    if (passed.contains(a.id)) R.color.secondary else R.color.danger
                )
            )
        }
        val first = items.firstOrNull()
        val builder = MaterialAlertDialogBuilder(ctx).setView(scroll(ctx, col))
        if (first != null) {
            builder.setPositiveButton("Решить первое") { _, _ -> onSolve(first) }
        }
        builder.setNegativeButton("Закрыть", null)
        builder.show()
    }

    fun report(
        ctx: Context,
        assignment: ru.readysquad.kvest.data.Assignment,
        results: List<ru.readysquad.kvest.data.AssignmentResult>,
        notPassed: List<String>
    ) {
        val col = column(ctx)
        col.addView(paragraph(ctx, "Отчёт по заданию", 21f, bold = true))
        col.addView(
            paragraph(
                ctx,
                "${assignment.exam} · ${assignment.topic} · задач ${assignment.size}",
                14f,
                R.color.on_bg_variant,
                top = 4
            )
        )
        if (results.isEmpty()) {
            col.addView(
                paragraph(
                    ctx,
                    "Пока никто не прошёл задание. Напомни классу — прогресс появится здесь сразу после ответов.",
                    15f,
                    top = 10
                )
            )
        }
        results.forEach { r ->
            col.addView(title(ctx, r.studentName, 15))
            col.addView(
                kv(
                    ctx, "Оценка", Scoring.gradeLabel(r.grade),
                    if (r.grade >= 4) R.color.secondary else R.color.danger
                )
            )
            col.addView(kv(ctx, "Верных ответов", "${r.correct} из ${r.total}"))
            col.addView(kv(ctx, "Очки", "${r.score}"))
            if (r.mistakes.isEmpty()) {
                col.addView(paragraph(ctx, "Ошибок нет", 14f, R.color.secondary))
            } else {
                col.addView(paragraph(ctx, "Ошибки:", 14f, R.color.danger, bold = true))
                r.mistakes.forEach { m ->
                    col.addView(paragraph(ctx, "${m.number}. ${m.topic}", 13f, R.color.on_bg_variant))
                    col.addView(kv(ctx, "Ответил", m.typed.ifBlank { "нет ответа" }))
                    col.addView(kv(ctx, "Верно", m.expected, R.color.accent))
                }
            }
        }
        if (notPassed.isNotEmpty()) {
            col.addView(title(ctx, "ЕЩЁ НЕ ПРОШЛИ", 14))
            col.addView(paragraph(ctx, notPassed.joinToString(", "), 14f, R.color.on_bg_variant))
        }
        show(ctx, scroll(ctx, col))
    }

    fun classMode(
        ctx: Context,
        address: String,
        running: Boolean,
        pending: Int,
        onStart: () -> Unit,
        onStop: () -> Unit,
        onAssign: () -> Unit,
        onRefresh: () -> Unit
    ) {
        val col = column(ctx)
        col.addView(paragraph(ctx, "Режим класса по Wi-Fi", 21f, bold = true))
        col.addView(
            paragraph(
                ctx,
                if (running) "Сервер работает. Назови ученикам адрес — они вводят его у себя и получают задания."
                else "Включи режим класса: телефон станет сервером для учеников в этом же Wi-Fi. Интернет не нужен.",
                14f,
                R.color.on_bg_variant,
                top = 4
            )
        )
        if (running) {
            col.addView(title(ctx, "АДРЕС ДЛЯ УЧЕНИКОВ", 16))
            col.addView(paragraph(ctx, address, 22f, bold = true))
            col.addView(title(ctx, "СОСТОЯНИЕ", 14))
            col.addView(kv(ctx, "Сервер", "включён", R.color.secondary))
            col.addView(kv(ctx, "Отчётов получено", "$pending"))
        }
        col.addView(title(ctx, "КАК ЭТО РАБОТАЕТ", 16))
        col.addView(paragraph(ctx, "1. Учитель включает режим класса.", 14f))
        col.addView(paragraph(ctx, "2. Ученики вводят адрес в профиле и нажимают «Получить задания».", 14f))
        col.addView(paragraph(ctx, "3. Ученик решает — отчёт возвращается учителю сам.", 14f))
        col.addView(paragraph(ctx, "Все устройства должны быть в одной сети Wi-Fi.", 13f, R.color.on_bg_variant, top = 6))

        val builder = MaterialAlertDialogBuilder(ctx).setView(scroll(ctx, col))
        if (running) {
            builder.setPositiveButton("Обновить") { _, _ -> onRefresh() }
            builder.setNegativeButton("Остановить сервер") { _, _ -> onStop() }
            builder.setNeutralButton("Выдать задание") { _, _ -> onAssign() }
        } else {
            builder.setPositiveButton("Включить режим класса") { _, _ -> onStart() }
            builder.setNegativeButton("Закрыть", null)
            builder.setNeutralButton("Выдать задание") { _, _ -> onAssign() }
        }
        builder.show()
    }

    fun connectTeacher(
        ctx: Context,
        address: String,
        onAddress: (String) -> Unit,
        onPull: (String) -> Unit,
        onPush: (String) -> Unit,
        onDiscover: () -> Unit
    ) {
        val col = column(ctx)
        col.addView(paragraph(ctx, "Связь с учителем", 21f, bold = true))
        col.addView(
            paragraph(
                ctx,
                "Учитель называет адрес вида 192.168.1.5:8767 — введи его и получи задания.",
                14f,
                R.color.on_bg_variant,
                top = 4
            )
        )
        val input = EditText(ctx).apply {
            hint = "192.168.1.5:8767"
            setText(address)
            textSize = 16f
            setTextColor(color(ctx, R.color.on_bg))
            setBackgroundResource(R.drawable.bg_input)
            setPadding(dp(ctx, 14), dp(ctx, 14), dp(ctx, 14), dp(ctx, 14))
            minHeight = dp(ctx, 52)
        }
        col.addView(title(ctx, "АДРЕС УЧИТЕЛЯ", 14))
        col.addView(input)
        col.addView(title(ctx, "ЧТО ДЕЛАЕТ КНОПКА", 16))
        col.addView(paragraph(ctx, "«Получить задания» — забрать выданное учителем.", 14f))
        col.addView(paragraph(ctx, "«Отправить отчёты» — вернуть учителю результаты и ошибки.", 14f))
        col.addView(
            paragraph(
                ctx,
                "Не знаешь адрес? Нажми «Найти учителя в сети» — приложение само его найдёт, " +
                    "если вы в одном Wi-Fi.",
                13f,
                R.color.on_bg_variant,
                top = 8
            )
        )

        MaterialAlertDialogBuilder(ctx)
            .setView(scroll(ctx, col))
            .setPositiveButton("Получить задания") { _, _ -> onAddress(input.text.toString().trim()); onPull(input.text.toString().trim()) }
            .setNegativeButton("Отправить отчёты") { _, _ -> onAddress(input.text.toString().trim()); onPush(input.text.toString().trim()) }
            .setNeutralButton("Найти учителя в сети") { _, _ -> onDiscover() }
            .show()
    }
}

object Leveling {

    const val MAX = 6
    private val STEPS = intArrayOf(0, 100, 250, 450, 700, 1000, 1400)

    fun level(totalScore: Int): Int {
        var lvl = 1
        while (lvl < MAX && totalScore >= STEPS[lvl]) lvl++
        return lvl
    }

    fun startScore(level: Int): Int = STEPS[(level - 1).coerceIn(0, MAX - 1)]

    fun nextScore(level: Int): Int = STEPS[level.coerceIn(0, MAX)]

    fun title(level: Int): String = when (level) {
        1 -> "Новичок"
        2 -> "Ученик"
        3 -> "Практик"
        4 -> "Знаток"
        5 -> "Отличник"
        else -> "Мастер экзамена"
    }

    fun hint(level: Int): String = when (level) {
        1 -> "Первые шаги: реши несколько квестов."
        2 -> "Уже видно прогресс, продолжай в том же темпе."
        3 -> "Стабильный темп важнее рывков."
        4 -> "Половина пути до максимума."
        5 -> "Осталось совсем немного до высшего уровня."
        else -> "Высший уровень. Держи серию дней."
    }

    fun progress(totalScore: Int): Float {
        val level = level(totalScore)
        if (level >= MAX) return 1f
        val cur = startScore(level)
        val next = nextScore(level)
        return ((totalScore - cur).toFloat() / (next - cur).toFloat()).coerceIn(0f, 1f)
    }
}
