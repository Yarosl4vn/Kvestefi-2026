package ru.readysquad.kvest.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DbHelper(context: Context) : SQLiteOpenHelper(context.applicationContext, NAME, null, VERSION) {

    val appContext: Context = context.applicationContext

    override fun onCreate(db: SQLiteDatabase) {
        createSchema(db)

        ensureSchema(db)
        seedDatabase(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {

        if (oldVersion < 6) {
            dropAll(db)
            createSchema(db)
            seedDatabase(db)
            ensureSchema(db)
            return
        }

        var v = oldVersion
        if (v < 7) {

            runCatching { db.execSQL("DELETE FROM badges WHERE code = 'blank_ace'") }
            v = 7
        }
        if (v < 8) {

            runCatching { db.execSQL("ALTER TABLE answers ADD COLUMN question TEXT") }
            runCatching { db.execSQL("ALTER TABLE answers ADD COLUMN typed TEXT") }
            runCatching { db.execSQL("ALTER TABLE answers ADD COLUMN expected TEXT") }
            v = 8
        }
        if (v < 9) {

            runCatching {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS users (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, role TEXT NOT NULL, position TEXT, " +
                        "full_name TEXT NOT NULL, birth_date TEXT, gender TEXT, phone TEXT, " +
                        "region TEXT, school TEXT, grade TEXT, letter TEXT, subject TEXT, homeroom TEXT, " +
                        "has_class INTEGER NOT NULL DEFAULT 0, goal INTEGER NOT NULL DEFAULT 30, " +
                        "login TEXT UNIQUE, password TEXT, status TEXT NOT NULL, approved_by TEXT, " +
                        "avatar TEXT, about TEXT, score INTEGER NOT NULL DEFAULT 0, " +
                        "created_at INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS approvals (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, kind TEXT NOT NULL, " +
                        "addressee TEXT NOT NULL, addressee_role TEXT NOT NULL, school TEXT, " +
                        "grade TEXT, letter TEXT, status TEXT NOT NULL, created_at INTEGER NOT NULL, " +
                        "decided_at INTEGER, note TEXT)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS links (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, student_id INTEGER NOT NULL, " +
                        "teacher_id INTEGER NOT NULL)"
                )
            }
            v = 9
        }
        if (v < 10) {

            runCatching {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS assignments (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, teacher_id INTEGER NOT NULL, " +
                        "teacher_name TEXT, title TEXT, subject_code TEXT NOT NULL, exam TEXT, topic TEXT, " +
                        "grade TEXT, letter TEXT, school TEXT, remote_id INTEGER NOT NULL DEFAULT 0, " +
                "student_id INTEGER NOT NULL DEFAULT 0, " +
                        "task_ids TEXT NOT NULL, created_at INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS assignment_results (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, assignment_id INTEGER NOT NULL, " +
                        "student_id INTEGER NOT NULL, student_name TEXT, correct INTEGER NOT NULL, " +
                        "total INTEGER NOT NULL, score INTEGER NOT NULL, grade INTEGER NOT NULL, " +
                        "details TEXT, created_at INTEGER NOT NULL)"
                )
            }
            v = 10
        }
        if (v < 11) {

            runCatching { db.execSQL("ALTER TABLE assignments ADD COLUMN remote_id INTEGER NOT NULL DEFAULT 0") }
            v = 11
        }
        if (v < 12) {

            runCatching { db.execSQL("ALTER TABLE assignments ADD COLUMN school TEXT") }
            runCatching { db.execSQL("ALTER TABLE assignments ADD COLUMN remote_id INTEGER NOT NULL DEFAULT 0") }
            v = 12
        }
        if (v < 13) {

            runCatching { db.execSQL(MESSAGES_DDL) }
            v = 13
        }
        if (v < 20) {

            topUpContent(db)
            v = 20
        }
        if (v < 23) {

            // ВПР больше не используется: старые задания и шпаргалки помечаем как ОГЭ.
            runCatching { db.execSQL("UPDATE tasks SET exam = 'ОГЭ' WHERE exam = 'ВПР'") }
            runCatching { db.execSQL("UPDATE theory SET exam = 'ОГЭ' WHERE exam = 'ВПР'") }
            v = 23
        }

        ensureSchema(db)
    }

    private fun ensureSchema(db: SQLiteDatabase) {
        addColumnIfMissing(db, "assignments", "school", "TEXT")
        addColumnIfMissing(db, "assignments", "remote_id", "INTEGER NOT NULL DEFAULT 0")
        addColumnIfMissing(db, "assignments", "student_id", "INTEGER NOT NULL DEFAULT 0")
        addColumnIfMissing(db, "assignments", "teacher_name", "TEXT")
        addColumnIfMissing(db, "assignments", "title", "TEXT")
        addColumnIfMissing(db, "assignments", "exam", "TEXT")
        addColumnIfMissing(db, "assignments", "topic", "TEXT")
        addColumnIfMissing(db, "assignments", "grade", "TEXT")
        addColumnIfMissing(db, "assignments", "letter", "TEXT")
        addColumnIfMissing(db, "answers", "question", "TEXT")
        addColumnIfMissing(db, "answers", "typed", "TEXT")
        addColumnIfMissing(db, "answers", "expected", "TEXT")
        addColumnIfMissing(db, "approvals", "decided_at", "INTEGER")
        addColumnIfMissing(db, "approvals", "note", "TEXT")
        addColumnIfMissing(db, "schools", "address", "TEXT")
        addColumnIfMissing(db, "users", "has_class", "INTEGER NOT NULL DEFAULT 0")
        addColumnIfMissing(db, "users", "score", "INTEGER NOT NULL DEFAULT 0")
        addColumnIfMissing(db, "users", "nick_color", "TEXT")
        addColumnIfMissing(db, "users", "prefix", "TEXT")

        addColumnIfMissing(db, "messages", "reply_to_id", "INTEGER NOT NULL DEFAULT 0")
        addColumnIfMissing(db, "messages", "reply_text", "TEXT")
        addColumnIfMissing(db, "messages", "attachment", "TEXT")

        // Кэш заданий из открытого банка: они переживают закрытие приложения,
        // поэтому квест по реальным заданиям работает и без интернета.
        runCatching {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS task_cache (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, subject_code TEXT NOT NULL, exam TEXT, " +
                    "topic TEXT, difficulty INTEGER NOT NULL DEFAULT 2, question TEXT NOT NULL, " +
                    "opt1 TEXT, opt2 TEXT, opt3 TEXT, opt4 TEXT, " +
                    "correct_index INTEGER NOT NULL DEFAULT 0, explanation TEXT, " +
                    "fetched_at INTEGER NOT NULL)"
            )
        }
        runCatching {
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_cache_unique ON task_cache(subject_code, question)"
            )
        }

        addColumnIfMissing(db, "support_messages", "reply", "TEXT")
        addColumnIfMissing(db, "support_messages", "replied_by", "TEXT")
        addColumnIfMissing(db, "support_messages", "replied_at", "INTEGER NOT NULL DEFAULT 0")
        runCatching { db.execSQL(MESSAGES_DDL) }
        runCatching {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS links (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, student_id INTEGER NOT NULL, " +
                    "teacher_id INTEGER NOT NULL)"
            )
        }

        runCatching {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS schools (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, " +
                    "city TEXT, address TEXT, added_by TEXT, created_at INTEGER NOT NULL)"
            )
        }
        runCatching {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS school_requests (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, city TEXT, " +
                    "by_name TEXT, status TEXT NOT NULL, created_at INTEGER NOT NULL)"
            )
        }
        runCatching {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS school_codes (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, school TEXT NOT NULL, code TEXT NOT NULL, " +
                    "created_by TEXT, created_at INTEGER NOT NULL)"
            )
        }

        runCatching {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS classes (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, school TEXT NOT NULL, grade TEXT, " +
                    "letter TEXT, homeroom_id INTEGER NOT NULL DEFAULT 0, homeroom_name TEXT, " +
                    "created_at INTEGER NOT NULL)"
            )
        }

        runCatching {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS support_messages (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT, from_name TEXT, " +
                    "from_login TEXT, school TEXT, text TEXT NOT NULL, status TEXT, " +
                    "reply TEXT, replied_by TEXT, replied_at INTEGER NOT NULL DEFAULT 0, " +
                    "created_at INTEGER NOT NULL)"
            )
        }

        runCatching {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS login_events (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, " +
                    "user_name TEXT, device TEXT, place TEXT, created_at INTEGER NOT NULL)"
            )
        }
    }

    private fun addColumnIfMissing(db: SQLiteDatabase, table: String, column: String, type: String) {

        if (!hasTable(db, table)) return
        if (hasColumn(db, table, column)) return
        runCatching { db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type) }
            .onFailure { android.util.Log.e("Kvestefi", "Колонка " + table + "." + column, it) }
    }

    private fun hasTable(db: SQLiteDatabase, table: String): Boolean {
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(table)
        ).use { c -> return c.moveToFirst() }
    }

    private fun hasColumn(db: SQLiteDatabase, table: String, column: String): Boolean {
        runCatching {
            db.rawQuery("PRAGMA table_info(" + table + ")", null).use { c ->
                val nameIndex = c.getColumnIndex("name")
                while (c.moveToNext()) {
                    if (nameIndex >= 0 && column.equals(c.getString(nameIndex), ignoreCase = true)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun dropAll(db: SQLiteDatabase) {
        listOf("answers", "attempts", "tasks", "theory", "badges", "subjects").forEach {
            db.execSQL("DROP TABLE IF EXISTS " + it)
        }
    }

    private fun createSchema(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS subjects (" +
                "code TEXT PRIMARY KEY, title TEXT NOT NULL, emoji TEXT, " +
                "color_from TEXT, color_to TEXT, topics TEXT)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS tasks (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, subject_code TEXT NOT NULL, topic TEXT, " +
                "exam TEXT, difficulty INTEGER DEFAULT 1, question TEXT NOT NULL, " +
                "opt1 TEXT, opt2 TEXT, opt3 TEXT, opt4 TEXT, " +
                "correct_index INTEGER NOT NULL, explanation TEXT)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS theory (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, subject_code TEXT NOT NULL, topic TEXT, " +
                "exam TEXT, teaser TEXT, body TEXT)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS attempts (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, subject_code TEXT NOT NULL, " +
                "correct INTEGER NOT NULL, total INTEGER NOT NULL, score INTEGER NOT NULL, " +
                "grade INTEGER NOT NULL, seconds INTEGER NOT NULL, created_at INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS answers (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, attempt_id INTEGER NOT NULL, " +
                "task_id INTEGER, topic TEXT, is_correct INTEGER NOT NULL, seconds INTEGER NOT NULL, " +
                "question TEXT, typed TEXT, expected TEXT)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS badges (" +
                "code TEXT PRIMARY KEY, emoji TEXT, title TEXT, description TEXT, unlocked_at INTEGER)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS users (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, role TEXT NOT NULL, position TEXT, " +
                "full_name TEXT NOT NULL, birth_date TEXT, gender TEXT, phone TEXT, " +
                "region TEXT, school TEXT, grade TEXT, letter TEXT, subject TEXT, homeroom TEXT, " +
                "has_class INTEGER NOT NULL DEFAULT 0, goal INTEGER NOT NULL DEFAULT 30, " +
                "login TEXT UNIQUE, password TEXT, status TEXT NOT NULL, approved_by TEXT, " +
                "avatar TEXT, about TEXT, score INTEGER NOT NULL DEFAULT 0, " +
                "created_at INTEGER NOT NULL)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS approvals (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, kind TEXT NOT NULL, " +
                "addressee TEXT NOT NULL, addressee_role TEXT NOT NULL, school TEXT, " +
                "grade TEXT, letter TEXT, status TEXT NOT NULL, created_at INTEGER NOT NULL, " +
                "decided_at INTEGER, note TEXT)"
        )
        db.execSQL("CREATE TABLE IF NOT EXISTS links (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, student_id INTEGER NOT NULL, teacher_id INTEGER NOT NULL)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS assignments (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, teacher_id INTEGER NOT NULL, teacher_name TEXT, " +
                "title TEXT, subject_code TEXT NOT NULL, exam TEXT, topic TEXT, grade TEXT, letter TEXT, " +
                "school TEXT, remote_id INTEGER NOT NULL DEFAULT 0, student_id INTEGER NOT NULL DEFAULT 0, " +
                "task_ids TEXT NOT NULL, created_at INTEGER NOT NULL)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS assignment_results (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, assignment_id INTEGER NOT NULL, " +
                "student_id INTEGER NOT NULL, student_name TEXT, correct INTEGER NOT NULL, " +
                "total INTEGER NOT NULL, score INTEGER NOT NULL, grade INTEGER NOT NULL, " +
                "details TEXT, created_at INTEGER NOT NULL)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS messages (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, from_id INTEGER NOT NULL, " +
                "from_name TEXT, to_id INTEGER NOT NULL, to_name TEXT, text TEXT NOT NULL, " +
                "created_at INTEGER NOT NULL, read_at INTEGER NOT NULL DEFAULT 0, " +
                "reply_to_id INTEGER NOT NULL DEFAULT 0, reply_text TEXT, attachment TEXT)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_to ON messages(to_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_answers_attempt ON answers(attempt_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tasks_subject ON tasks(subject_code)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_theory_subject ON theory(subject_code)")
    }

    private fun seedDatabase(db: SQLiteDatabase) {
        SeedData.subjects.forEach { insertSubject(db, it) }
        SeedData.tasks.forEach { insertTask(db, it) }
        allTheory().forEach { insertTheory(db, it) }
        SeedData.badges.forEach { insertBadge(db, it) }
    }

    private fun allTheory(): List<SeedTheory.Card> =
        SeedTheory.cards + SeedTheoryExtra.cards + SeedTheoryMore.cards

    private fun insertSubject(db: SQLiteDatabase, s: Subject) {
        db.insert("subjects", null, ContentValues().apply {
            put("code", s.code)
            put("title", s.title)
            put("emoji", s.emoji)
            put("color_from", s.colorFrom)
            put("color_to", s.colorTo)
            put("topics", s.topics)
        })
    }

    private fun insertTask(db: SQLiteDatabase, t: Task) {
        db.insert("tasks", null, ContentValues().apply {
            put("subject_code", t.subjectCode)
            put("topic", t.topic)
            put("exam", t.exam)
            put("difficulty", t.difficulty)
            put("question", t.question)
            put("opt1", t.options.getOrNull(0))
            put("opt2", t.options.getOrNull(1))
            put("opt3", t.options.getOrNull(2))
            put("opt4", t.options.getOrNull(3))
            put("correct_index", t.correctIndex)
            put("explanation", t.explanation)
        })
    }

    private fun insertTheory(db: SQLiteDatabase, c: SeedTheory.Card) {
        db.insert("theory", null, ContentValues().apply {
            put("subject_code", c.subjectCode)
            put("topic", c.topic)
            put("exam", c.exam)
            put("teaser", c.teaser)
            put("body", c.body)
        })
    }

    private fun insertBadge(db: SQLiteDatabase, b: Badge) {
        db.insert("badges", null, ContentValues().apply {
            put("code", b.code)
            put("emoji", b.emoji)
            put("title", b.title)
            put("description", b.description)
            putNull("unlocked_at")
        })
    }

    /**
     * Досыпает новое содержимое (задания, шпаргалки, достижения), не трогая
     * ответы, попытки, очки и аккаунты — прогресс пользователя остаётся на месте.
     */
    private fun topUpContent(db: SQLiteDatabase) {
        val knownTasks = HashSet<String>()
        runCatching {
            db.rawQuery("SELECT subject_code, question FROM tasks", null).use { c ->
                while (c.moveToNext()) {
                    knownTasks.add((c.getString(0) ?: "") + "|" + (c.getString(1) ?: ""))
                }
            }
        }
        SeedData.tasks.forEach { t ->
            if (knownTasks.add(t.subjectCode + "|" + t.question)) insertTask(db, t)
        }

        val knownTheory = HashSet<String>()
        runCatching {
            db.rawQuery("SELECT subject_code, topic FROM theory", null).use { c ->
                while (c.moveToNext()) {
                    knownTheory.add((c.getString(0) ?: "") + "|" + (c.getString(1) ?: ""))
                }
            }
        }
        allTheory().forEach { card ->
            if (knownTheory.add(card.subjectCode + "|" + card.topic)) insertTheory(db, card)
        }

        val knownBadges = HashSet<String>()
        runCatching {
            db.rawQuery("SELECT code FROM badges", null).use { c ->
                while (c.moveToNext()) knownBadges.add(c.getString(0) ?: "")
            }
        }
        SeedData.badges.forEach { badge ->
            if (knownBadges.add(badge.code)) insertBadge(db, badge)
        }
    }

    companion object {
        const val NAME = "kvest_marathon.db"

        const val VERSION = 23

        private const val MESSAGES_DDL =
            "CREATE TABLE IF NOT EXISTS messages (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, from_id INTEGER NOT NULL, " +
                "from_name TEXT, to_id INTEGER NOT NULL, to_name TEXT, text TEXT NOT NULL, " +
                "created_at INTEGER NOT NULL, read_at INTEGER NOT NULL DEFAULT 0, " +
                "reply_to_id INTEGER NOT NULL DEFAULT 0, reply_text TEXT, attachment TEXT)"
    }
}
