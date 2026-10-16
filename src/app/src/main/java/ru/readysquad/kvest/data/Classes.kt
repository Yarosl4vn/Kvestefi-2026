package ru.readysquad.kvest.data

import android.content.ContentValues
import android.content.Context

data class SchoolClass(
    val id: Long,
    val school: String,
    val grade: String,
    val letter: String,
    val homeroomId: Long,
    val homeroomName: String
) {
    val label: String get() = Grades.label(grade, letter)
}

class ClassStore(private val context: Context) {

    private val db = DbHelper(context).writableDatabase

    fun all(school: String): List<SchoolClass> {
        val out = ArrayList<SchoolClass>()
        db.rawQuery(
            "SELECT id, school, grade, letter, homeroom_id, homeroom_name FROM classes " +
                "WHERE school = ? ORDER BY grade, letter",
            arrayOf(school)
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    SchoolClass(
                        id = c.getLong(0),
                        school = c.getString(1) ?: "",
                        grade = c.getString(2) ?: "",
                        letter = c.getString(3) ?: "",
                        homeroomId = c.getLong(4),
                        homeroomName = c.getString(5) ?: ""
                    )
                )
            }
        }
        return out
    }

    fun find(school: String, grade: String, letter: String): SchoolClass? =
        all(school).firstOrNull { it.grade == grade && it.letter == letter }

    fun create(school: String, grade: String, letter: String, homeroom: Account?): Boolean {
        if (school.isBlank() || grade.isBlank() || letter.isBlank()) return false
        if (find(school, grade, letter) != null) return false
        val id = db.insert("classes", null, ContentValues().apply {
            put("school", school)
            put("grade", grade)
            put("letter", letter)
            put("homeroom_id", homeroom?.id ?: 0L)
            put("homeroom_name", homeroom?.fullName.orEmpty())
            put("created_at", System.currentTimeMillis())
        })
        if (id > 0 && homeroom != null) assignHomeroom(school, grade, letter, homeroom)
        return id > 0
    }

    fun ensure(school: String, grade: String, letter: String) {
        if (school.isBlank() || grade.isBlank() || letter.isBlank()) return
        if (find(school, grade, letter) != null) return
        create(school, grade, letter, null)
    }

    fun remove(school: String, grade: String, letter: String, clearStudents: Boolean): Boolean {
        val cls = find(school, grade, letter) ?: return false
        if (cls.homeroomId > 0L) {
            db.execSQL(
                "UPDATE users SET homeroom = '', has_class = 0 WHERE id = ?",
                arrayOf<Any>(cls.homeroomId)
            )
        }
        if (clearStudents) {
            db.execSQL(
                "UPDATE users SET grade = '', letter = '' WHERE school = ? AND grade = ? AND letter = ?",
                arrayOf<Any>(school, grade, letter)
            )
        }
        return db.delete("classes", "id = ?", arrayOf(cls.id.toString())) > 0
    }

    fun assignHomeroom(school: String, grade: String, letter: String, teacher: Account?): Boolean {
        val cls = find(school, grade, letter) ?: return false
        if (cls.homeroomId > 0L) {
            db.execSQL(
                "UPDATE users SET homeroom = '', has_class = 0 WHERE id = ?",
                arrayOf<Any>(cls.homeroomId)
            )
        }
        val label = Grades.label(grade, letter)
        db.execSQL(
            "UPDATE classes SET homeroom_id = ?, homeroom_name = ? WHERE id = ?",
            arrayOf<Any>(teacher?.id ?: 0L, teacher?.fullName.orEmpty(), cls.id)
        )
        if (teacher != null) {
            db.execSQL(
                "UPDATE users SET homeroom = ?, has_class = 1 WHERE id = ?",
                arrayOf<Any>(label, teacher.id)
            )
        }
        return true
    }

    fun studentsCount(school: String, grade: String, letter: String): Int {
        db.rawQuery(
            "SELECT COUNT(*) FROM users WHERE role = 'student' AND school = ? AND grade = ? AND letter = ?",
            arrayOf(school, grade, letter)
        ).use { c -> if (c.moveToFirst()) return c.getInt(0) }
        return 0
    }
}
