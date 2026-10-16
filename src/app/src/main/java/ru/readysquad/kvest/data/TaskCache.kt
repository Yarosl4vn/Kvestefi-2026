package ru.readysquad.kvest.data

import android.content.ContentValues
import android.content.Context

/**
 * Кэш заданий из открытого банка. Раньше скачанные задания жили только в памяти процесса
 * и пропадали при закрытии приложения — теперь они сохраняются и работают без интернета.
 */
class TaskCache(context: Context) {

    private val db = DbHelper(context).writableDatabase

    /** Свежие задания: им не больше [TTL_MS]. Возвращает null, если подходящих нет. */
    fun take(subjectCode: String, exam: String, limit: Int): List<Task>? =
        read(subjectCode, exam, limit, System.currentTimeMillis() - TTL_MS)

    /** Любые сохранённые задания, даже просроченные — аварийный вариант, когда сети нет. */
    fun takeAny(subjectCode: String, exam: String, limit: Int): List<Task>? =
        read(subjectCode, exam, limit, 0L)

    fun save(tasks: List<Task>) {
        if (tasks.isEmpty()) return
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            tasks.forEach { task ->
                db.insertWithOnConflict("task_cache", null, ContentValues().apply {
                    put("subject_code", task.subjectCode)
                    put("exam", task.exam)
                    put("topic", task.topic)
                    put("difficulty", task.difficulty)
                    put("question", task.question)
                    put("opt1", task.options.getOrNull(0))
                    put("opt2", task.options.getOrNull(1))
                    put("opt3", task.options.getOrNull(2))
                    put("opt4", task.options.getOrNull(3))
                    put("correct_index", task.correctIndex)
                    put("explanation", task.explanation)
                    put("fetched_at", now)
                }, android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE)
            }
            db.delete("task_cache", "fetched_at < ?", arrayOf((now - KEEP_MS).toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun count(subjectCode: String, exam: String): Int = db.rawQuery(
        "SELECT COUNT(*) FROM task_cache WHERE subject_code = ? AND exam = ?",
        arrayOf(subjectCode, exam)
    ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun oldestAgeDays(subjectCode: String, exam: String): Int {
        val at = db.rawQuery(
            "SELECT MIN(fetched_at) FROM task_cache WHERE subject_code = ? AND exam = ?",
            arrayOf(subjectCode, exam)
        ).use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else 0L }
        if (at <= 0L) return 0
        return ((System.currentTimeMillis() - at) / DAY_MS).toInt()
    }

    fun clear() {
        runCatching { db.delete("task_cache", null, null) }
    }

    private fun read(subjectCode: String, exam: String, limit: Int, newerThan: Long): List<Task>? {
        val sql = StringBuilder(
            "SELECT subject_code, topic, exam, difficulty, question, opt1, opt2, opt3, opt4, " +
                "correct_index, explanation FROM task_cache " +
                "WHERE subject_code = ? AND exam = ?"
        )
        val args = ArrayList<String>()
        args.add(subjectCode)
        args.add(exam)
        if (newerThan > 0L) {
            sql.append(" AND fetched_at > ?")
            args.add(newerThan.toString())
        }
        sql.append(" ORDER BY RANDOM() LIMIT ?")
        args.add(limit.toString())

        val out = ArrayList<Task>()
        runCatching {
            db.rawQuery(sql.toString(), args.toTypedArray()).use { c ->
                while (c.moveToNext()) {
                    val raw = (5..8).mapNotNull { c.getString(it) }
                    if (raw.size < 2) continue
                    val order = raw.indices.shuffled()
                    out.add(
                        Task(
                            id = 0L,
                            subjectCode = c.getString(0),
                            topic = c.getString(1) ?: "",
                            exam = c.getString(2) ?: exam,
                            difficulty = c.getInt(3),
                            question = c.getString(4),
                            options = order.map { raw[it] },
                            correctIndex = order.indexOf(c.getInt(9)).coerceAtLeast(0),
                            explanation = c.getString(10) ?: ""
                        )
                    )
                }
            }
        }
        return out.ifEmpty { null }
    }

    companion object {
        /** Сколько заданий считать свежими: неделя. */
        private const val TTL_MS = 7L * 24 * 60 * 60 * 1000
        private const val KEEP_MS = 30L * 24 * 60 * 60 * 1000
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
