package ru.readysquad.kvest.data

import android.content.ContentValues
import android.content.Context

data class Assignment(
    val id: Long,
    val teacherId: Long,
    val teacherName: String,
    val title: String,
    val subjectCode: String,
    val exam: String,
    val topic: String,
    val grade: String,
    val letter: String,
    val school: String = "",
    val studentId: Long,
    val taskIds: List<Long>,
    val createdAt: Long
) {
    val size: Int get() = taskIds.size
}

data class ReportRow(
    val number: Int,
    val topic: String,
    val question: String,
    val typed: String,
    val expected: String,
    val isCorrect: Boolean
)

data class AssignmentResult(
    val id: Long,
    val assignmentId: Long,
    val studentId: Long,
    val studentName: String,
    val correct: Int,
    val total: Int,
    val score: Int,
    val grade: Int,
    val rows: List<ReportRow>,
    val createdAt: Long
) {
    val accuracy: Int get() = if (total == 0) 0 else correct * 100 / total
    val mistakes: List<ReportRow> get() = rows.filter { !it.isCorrect }
}

class AssignmentStore(context: Context) {

    private val db = DbHelper(context).writableDatabase
    private val repo = Repository.get(context)

    fun tasksFor(
        subjectCode: String,
        exam: String,
        topic: String,
        limit: Int,
        excludeIds: Set<Long> = emptySet()
    ): List<Task> {
        val want = limit.coerceAtLeast(1)
        val all = repo.queryTasks(
            "SELECT id, subject_code, topic, exam, difficulty, question, opt1, opt2, opt3, opt4, " +
                "correct_index, explanation FROM tasks WHERE subject_code = ? ORDER BY RANDOM()",
            arrayOf(subjectCode)
        )
        val byExam = all.filter { it.exam.equals(exam, true) }
        val pool = byExam.ifEmpty { all }
        val exact = pool.filter { it.topic.equals(topic, true) }

        val ordered = ArrayList<Task>(all.size + pool.size)
        val seen = HashSet<Long>()
        for (t in exact) if (seen.add(t.id)) ordered.add(t)
        for (t in pool) if (seen.add(t.id)) ordered.add(t)
        for (t in all) if (seen.add(t.id)) ordered.add(t)

        val picked = ArrayList<Task>(want)

        for (t in ordered) {
            if (picked.size >= want) break
            if (t.id !in excludeIds) picked.add(t)
        }

        if (picked.size < want) {
            for (t in ordered) {
                if (picked.size >= want) break
                if (picked.none { it.id == t.id }) picked.add(t)
            }
        }
        return picked
    }

    fun availableCount(subjectCode: String, exam: String, topic: String): Int {
        val all = repo.queryTasks(
            "SELECT id, subject_code, topic, exam, difficulty, question, opt1, opt2, opt3, opt4, " +
                "correct_index, explanation FROM tasks WHERE subject_code = ?",
            arrayOf(subjectCode)
        )
        val byExam = all.filter { it.exam.equals(exam, true) }
        val pool = byExam.ifEmpty { all }
        val exact = pool.filter { it.topic.equals(topic, true) }
        return exact.ifEmpty { pool }.map { it.id }.distinct().size
    }

    fun issuedTaskIds(teacherId: Long): Set<Long> =
        byTeacher(teacherId).flatMap { it.taskIds }.toSet()

    fun topicsFor(subjectCode: String, exam: String): List<String> {
        val all = repo.queryTasks(
            "SELECT id, subject_code, topic, exam, difficulty, question, opt1, opt2, opt3, opt4, " +
                "correct_index, explanation FROM tasks WHERE subject_code = ?",
            arrayOf(subjectCode)
        )
        val byExam = all.filter { it.exam.equals(exam, true) }
        return (byExam.ifEmpty { all }).map { it.topic }.filter { it.isNotBlank() }.distinct().sorted()
    }

    fun create(
        teacher: Account,
        subjectCode: String,
        exam: String,
        topic: String,
        tasks: List<Task>,
        studentId: Long = 0L,
        grade: String = "",
        letter: String = ""
    ): Long {
        if (tasks.isEmpty()) return 0L
        // Учитель может выдать задание конкретному классу, а не только своему.
        val targetGrade = if (grade.isNotBlank()) grade
        else teacher.homeroom.filter { it.isDigit() }.ifBlank { teacher.grade }
        val targetLetter = if (grade.isNotBlank()) letter
        else teacher.homeroom.filter { it.isLetter() }
        return db.insert("assignments", null, ContentValues().apply {
            put("teacher_id", teacher.id)
            put("teacher_name", teacher.fullName)
            put("title", topic)
            put("subject_code", subjectCode)
            put("exam", exam)
            put("topic", topic)
            put("grade", targetGrade)
            put("letter", targetLetter)
            put("school", teacher.school)
            put("student_id", studentId)
            put("task_ids", tasks.joinToString(",") { it.id.toString() })
            put("created_at", System.currentTimeMillis())
        })
    }

    fun forStudent(student: Account): List<Assignment> =
        read("WHERE student_id = ? OR student_id = 0", arrayOf(student.id.toString()))
            .filter { a ->

                a.studentId == student.id || (

                    (a.school.isBlank() || a.school.equals(student.school, true)) &&
                        a.grade == student.grade &&
                        (a.letter.isBlank() || a.letter == student.letter)
                    )
            }

    fun byTeacher(teacherId: Long): List<Assignment> =
        read("WHERE teacher_id = ? ORDER BY created_at DESC", arrayOf(teacherId.toString()))

    fun byId(id: Long): Assignment? = read("WHERE id = ?", arrayOf(id.toString())).firstOrNull()

    fun tasksOf(assignment: Assignment): List<Task> =
        assignment.taskIds.mapNotNull { repo.taskById(it) }

    fun saveResult(
        assignment: Assignment,
        student: Account,
        correct: Int,
        total: Int,
        score: Int,
        grade: Int,
        answers: List<AnswerRow>
    ) {
        val details = answers.joinToString("|") { a ->
            listOf(
                if (a.isCorrect) "1" else "0",
                a.topic.replace("|", " ").replace(",", " "),
                a.question.replace("|", " ").replace(",", " ").take(160),
                a.typed.replace("|", " ").replace(",", " "),
                a.expected.replace("|", " ").replace(",", " ")
            ).joinToString(",")
        }
        db.insert("assignment_results", null, ContentValues().apply {
            put("assignment_id", assignment.id)
            put("student_id", student.id)
            put("student_name", student.fullName)
            put("correct", correct)
            put("total", total)
            put("score", score)
            put("grade", grade)
            put("details", details)
            put("created_at", System.currentTimeMillis())
        })
    }

    fun results(assignmentId: Long): List<AssignmentResult> {
        val out = ArrayList<AssignmentResult>()
        db.rawQuery(
            "SELECT id, assignment_id, student_id, student_name, correct, total, score, grade, " +
                "details, created_at FROM assignment_results WHERE assignment_id = ? " +
                "ORDER BY created_at DESC",
            arrayOf(assignmentId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    AssignmentResult(
                        id = c.getLong(0), assignmentId = c.getLong(1), studentId = c.getLong(2),
                        studentName = c.getString(3) ?: "", correct = c.getInt(4), total = c.getInt(5),
                        score = c.getInt(6), grade = c.getInt(7),
                        rows = parseDetails(c.getString(8) ?: ""), createdAt = c.getLong(9)
                    )
                )
            }
        }
        return out
    }

    fun passedStudentIds(assignmentId: Long): Set<Long> =
        results(assignmentId).map { it.studentId }.toSet()

    private fun parseDetails(raw: String): List<ReportRow> {
        if (raw.isBlank()) return emptyList()
        return raw.split("|").mapIndexedNotNull { i, part ->
            val f = part.split(",")
            if (f.size < 5) return@mapIndexedNotNull null
            ReportRow(
                number = i + 1,
                isCorrect = f[0] == "1",
                topic = f[1],
                question = f[2],
                typed = f[3],
                expected = f[4]
            )
        }
    }

    private fun read(where: String, args: Array<String>): List<Assignment> {
        val out = ArrayList<Assignment>()
        db.rawQuery(
            "SELECT id, teacher_id, teacher_name, title, subject_code, exam, topic, grade, letter, school, " +
                "student_id, task_ids, created_at FROM assignments $where",
            args
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    Assignment(
                        id = c.getLong(0), teacherId = c.getLong(1), teacherName = c.getString(2) ?: "",
                        title = c.getString(3) ?: "", subjectCode = c.getString(4) ?: "",
                        exam = c.getString(5) ?: "", topic = c.getString(6) ?: "",
                        grade = c.getString(7) ?: "", letter = c.getString(8) ?: "",
                        school = c.getString(9) ?: "",
                        studentId = c.getLong(10),
                        taskIds = (c.getString(11) ?: "").split(",").mapNotNull { it.toLongOrNull() },
                        createdAt = c.getLong(12)
                    )
                )
            }
        }
        return out
    }

    fun alreadyImported(createdAt: Long): Boolean =
        db.rawQuery("SELECT 1 FROM assignments WHERE created_at = ?", arrayOf(createdAt.toString())).use { it.moveToFirst() }

    fun importRemote(
        teacherAssignmentId: Long,
        teacherName: String,
        school: String,
        subjectCode: String,
        exam: String,
        topic: String,
        grade: String,
        letter: String,
        createdAt: Long,
        tasks: List<Task>
    ): Long {
        if (tasks.isEmpty()) return 0L
        val localIds = ArrayList<Long>()
        tasks.forEach { t ->
            val values = ContentValues().apply {
                put("subject_code", t.subjectCode.ifBlank { subjectCode })
                put("topic", t.topic.ifBlank { topic })
                put("exam", t.exam.ifBlank { exam })
                put("difficulty", t.difficulty)
                put("question", t.question)
                put("opt1", t.options.getOrElse(0) { "" })
                put("opt2", t.options.getOrElse(1) { "" })
                put("opt3", t.options.getOrElse(2) { "" })
                put("opt4", t.options.getOrElse(3) { "" })
                put("correct_index", t.correctIndex)
                put("explanation", t.explanation)
            }
            localIds.add(db.insert("tasks", null, values))
        }
        return db.insert("assignments", null, ContentValues().apply {
            put("teacher_id", 0L)
            put("teacher_name", teacherName)
            put("title", topic)
            put("subject_code", subjectCode)
            put("exam", exam)
            put("topic", topic)
            put("grade", grade)
            put("letter", letter)
            put("school", school)
            put("remote_id", teacherAssignmentId)
            put("student_id", 0L)
            put("task_ids", localIds.joinToString(","))
            put("created_at", createdAt)
        })
    }

    fun saveRemoteResult(
        assignment: Assignment,
        student: Account,
        correct: Int,
        total: Int,
        score: Int,
        grade: Int,
        details: String
    ) {
        db.insert("assignment_results", null, ContentValues().apply {
            put("assignment_id", assignment.id)
            put("student_id", student.id)
            put("student_name", student.fullName)
            put("correct", correct)
            put("total", total)
            put("score", score)
            put("grade", grade)
            put("details", details)
            put("created_at", System.currentTimeMillis())
        })
    }

    fun remoteIdOf(a: Assignment): Long =
        db.rawQuery("SELECT remote_id FROM assignments WHERE id = ?", arrayOf(a.id.toString())).use { c ->
            if (c.moveToFirst()) c.getLong(0) else 0L
        }

    fun detailsRaw(assignmentId: Long, studentId: Long): String =
        db.rawQuery(
            "SELECT details FROM assignment_results WHERE assignment_id = ? AND student_id = ? " +
                "ORDER BY created_at DESC LIMIT 1",
            arrayOf(assignmentId.toString(), studentId.toString())
        ).use { c -> if (c.moveToFirst()) c.getString(0) ?: "" else "" }

    fun detailsOf(answers: List<AnswerRow>): String = answers.joinToString("|") { a ->
        listOf(
            if (a.isCorrect) "1" else "0",
            a.topic.replace("|", " ").replace(",", " "),
            a.question.replace("|", " ").replace(",", " ").take(160),
            a.typed.replace("|", " ").replace(",", " "),
            a.expected.replace("|", " ").replace(",", " ")
        ).joinToString(",")
    }
}
