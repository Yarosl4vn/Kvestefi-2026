package ru.readysquad.kvest.data

import android.content.ContentValues
import android.content.Context
import ru.readysquad.kvest.util.Greetings

object Roles {
    const val GUEST = "guest"
    const val STUDENT = "student"
    const val TEACHER = "teacher"

    const val DEVELOPER = "developer"

    fun title(role: String): String = when (role) {
        STUDENT -> "Ученик"
        TEACHER -> "Учитель"
        DEVELOPER -> "Разработчик"
        else -> "Гость"
    }
}

object Positions {

    const val SUBJECT = "Учитель-предметник"
    const val HOMEROOM = "Классный руководитель"
    const val ADMIN = "Администрация школы"

    val student = listOf("Ученик", "Староста", "Заместитель старосты")

    val teacher = listOf(SUBJECT, HOMEROOM, ADMIN)

    fun of(role: String): List<String> = if (role == Roles.TEACHER) teacher else student

    fun join(items: Collection<String>): String =
        items.filter { it.isNotBlank() }.distinct().joinToString(", ")

    fun parse(raw: String?): List<String> =
        raw.orEmpty().split(",").map { it.trim() }.filter { it.isNotBlank() }

    fun isApprover(position: String?): Boolean {
        val positions = parse(position)
        return positions.contains(ADMIN) || positions.any { it.contains("администра", true) }
    }
}

object Regions {
    const val MOSCOW_REGION = "Московская область"

    val list = listOf(MOSCOW_REGION)

    val single: Boolean get() = list.size == 1
}

object Grades {
    val numbers = listOf("5", "6", "7", "8", "9", "10", "11")
    val letters = listOf("А", "Б", "В", "Г", "Д", "Е", "Ж", "З", "И", "К")

    fun label(grade: String?, letter: String?): String =
        if (grade.isNullOrBlank()) "класс не указан"
        else (grade + (letter ?: "")).trim()
}

object Goals {

    val options = listOf(0, 15, 30, 50, 100)

    val studentOptions = options.filter { it > 0 }

    const val DEFAULT = 30

    const val NONE = 0

    fun normalize(value: Int): Int =
        if (value <= 0) NONE else studentOptions.minByOrNull { kotlin.math.abs(it - value) } ?: DEFAULT

    fun title(value: Int): String = when (normalize(value)) {
        0 -> "Не занимаюсь квестами"
        15 -> "Спокойный темп · 15 квестов в день"
        30 -> "Обычный темп · 30 квестов в день"
        50 -> "Интенсивный темп · 50 квестов в день"
        else -> "Максимум · 100 квестов в день"
    }

    fun label(value: Int): String = if (normalize(value) == NONE) "не занимаюсь"
        else normalize(value).toString()
}

data class Account(
    val id: Long = 0L,
    val role: String,
    val position: String,
    val fullName: String,
    val birthDate: String = "",
    val gender: String = Greetings.MALE,
    val phone: String = "",
    val region: String = "",
    val school: String = "",
    val grade: String = "",
    val letter: String = "",
    val subject: String = "",
    val homeroom: String = "",
    val hasClass: Boolean = false,
    val goal: Int = 30,
    val login: String = "",
    val password: String = "",
    val status: String = "pending",
    val approvedBy: String = "",
    val avatar: String = "",
    val about: String = "",
    val score: Int = 0,
    val nickColor: String = "",
    val prefix: String = ""
) {
    val isTeacher: Boolean get() = role == Roles.TEACHER
    val isStudent: Boolean get() = role == Roles.STUDENT
    val isApproved: Boolean get() = status == "approved"
    val isDeveloper: Boolean get() = role == Roles.DEVELOPER

    val positions: List<String> get() = Positions.parse(position)

    val positionLine: String get() = positions.joinToString(", ").ifBlank { Roles.title(role) }

    val firstName: String
        get() {
            val parts = fullName.split(" ").filter { it.isNotBlank() }
            return when {
                parts.isEmpty() -> ""
                parts.size == 1 -> parts[0]
                else -> parts[1]
            }
        }

    val lastName: String
        get() = fullName.split(" ").filter { it.isNotBlank() }.firstOrNull() ?: ""

    fun address(byName: Boolean): String =
        if (byName) firstName.ifBlank { fullName } else fullName

    fun subtitle(): String = if (isStudent)
        listOf(school, Grades.label(grade, letter)).filter { it.isNotBlank() }.joinToString(" · ")
    else
        listOf(
            subject,
            homeroom.takeIf { it.isNotBlank() }?.let { "класс $it" } ?: "",
            school
        ).filter { it.isNotBlank() }.joinToString(" · ")
}

data class Approval(
    val id: Long,
    val userId: Long,
    val kind: String,
    val addressee: String,
    val addresseeRole: String,
    val school: String,
    val grade: String,
    val letter: String,
    val status: String,
    val createdAt: Long,
    val note: String = ""
)

class AccountStore(private val context: Context) {

    private val db = DbHelper(context).writableDatabase

    fun register(account: Account): Pair<Long, String> {
        if (account.login.isBlank() || account.password.isBlank()) {
            return 0L to "Укажи логин и пароль"
        }
        if (loginExists(account.login)) return 0L to "Такой логин уже занят"

        val isAdmin = account.isTeacher && account.positions.contains(Positions.ADMIN)
        val nobodyInSchool = !hasApprover(account.school)
        val toDevelopers = isAdmin || nobodyInSchool
        val addresseeRole = when {
            toDevelopers -> "developer"
            account.isTeacher -> "admin"
            else -> "homeroom"
        }
        val addressee = when {
            toDevelopers -> "Разработчикам"
            account.isTeacher -> "Администрация школы ${account.school}"
            else -> "Классный руководитель ${Grades.label(account.grade, account.letter)}"
        }

        val id = db.insert("users", null, ContentValues().apply {
            put("role", account.role)
            put("position", account.position)
            put("full_name", account.fullName)
            put("birth_date", account.birthDate)
            put("gender", account.gender)
            put("phone", account.phone)
            put("region", account.region)
            put("school", account.school)
            put("grade", account.grade)
            put("letter", account.letter)
            put("subject", account.subject)
            put("homeroom", account.homeroom)
            put("has_class", if (account.hasClass) 1 else 0)
            put("goal", Goals.normalize(account.goal))
            put("login", account.login)
            put("password", account.password)
            put("status", "approved")
            put("approved_by", "")
            put("avatar", account.avatar)
            put("about", account.about)
            put("created_at", System.currentTimeMillis())
        })
        if (id <= 0) return 0L to "Не удалось создать аккаунт"

        db.insert("approvals", null, ContentValues().apply {
            put("user_id", id)
            put("kind", account.role)
            put("addressee", addressee)
            put("addressee_role", addresseeRole)
            put("school", account.school)
            put("grade", account.grade)
            put("letter", account.letter)
            put("status", "approved")
            put("created_at", System.currentTimeMillis())
        })
        return id to ""
    }

    private fun teachersCount(school: String): Int =
        db.rawQuery(
            "SELECT COUNT(*) FROM users WHERE role = 'teacher' AND school = ?",
            arrayOf(school)
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    private fun loginExists(login: String): Boolean =
        db.rawQuery("SELECT 1 FROM users WHERE lower(login) = lower(?)", arrayOf(login)).use { it.moveToFirst() }

    fun login(login: String, password: String): Pair<Account?, String> {
        val found = byLogin(login) ?: return null to "Аккаунт с таким логином не найден"

        if (found.first.password != password) return null to "Неверный пароль"
        return when (found.first.status) {
            "approved" -> found.first to ""
            "pending" -> null to "Заявка ещё не подтверждена классным руководителем или школой"
            else -> null to "Заявка отклонена. Обратись в школу"
        }
    }

    fun byLogin(login: String): Pair<Account, String>? {
        val trimmed = login.trim()
        if (trimmed.isEmpty()) return null
        query("WHERE login = ?", arrayOf(trimmed)).firstOrNull()?.let { return it to "" }
        query("WHERE lower(login) = lower(?)", arrayOf(trimmed)).firstOrNull()?.let { return it to "" }
        return query("", emptyArray()).firstOrNull { it.login.trim().equals(trimmed, true) }?.let { it to "" }
    }

    fun byId(id: Long): Account? = query("WHERE id = ?", arrayOf(id.toString())).firstOrNull()

    fun pendingFor(account: Account): List<Pair<Approval, Account>> {
        val all = pendingApprovals()
        return all.filter { (a, user) ->
            val sameSchool = a.school.equals(account.school, true)
            when {

                Positions.isApprover(account.position) -> sameSchool

                account.isTeacher && a.addresseeRole == "homeroom" -> {
                    val ownClass = account.homeroom.isBlank() ||
                        a.grade == account.homeroom.filter { it.isDigit() }.ifBlank { a.grade }
                    sameSchool && ownClass
                }
                else -> false
            }
        }
    }

    fun pendingApprovals(): List<Pair<Approval, Account>> {
        val out = ArrayList<Pair<Approval, Account>>()
        db.rawQuery(
            "SELECT a.id, a.user_id, a.kind, a.addressee, a.addressee_role, a.school, a.grade, " +
                "a.letter, a.status, a.created_at, a.note FROM approvals a " +
                "WHERE a.status = 'pending' ORDER BY a.created_at DESC",
            null
        ).use { c ->
            while (c.moveToNext()) {
                val approval = Approval(
                    id = c.getLong(0), userId = c.getLong(1), kind = c.getString(2) ?: "",
                    addressee = c.getString(3) ?: "", addresseeRole = c.getString(4) ?: "",
                    school = c.getString(5) ?: "", grade = c.getString(6) ?: "",
                    letter = c.getString(7) ?: "", status = c.getString(8) ?: "",
                    createdAt = c.getLong(9), note = c.getString(10) ?: ""
                )
                byId(approval.userId)?.let { out.add(approval to it) }
            }
        }
        return out
    }

    fun everyone(limit: Int = 500): List<Account> =
        query("WHERE 1 = 1 ORDER BY school, full_name LIMIT ?", arrayOf(limit.toString()))

    fun adminUpdate(
        id: Long,
        fullName: String,
        school: String,
        position: String,
        role: String,
        grade: String,
        letter: String,
        subject: String,
        homeroom: String,
        status: String
    ): Boolean {
        if (id <= 0L) return false
        db.execSQL(
            "UPDATE users SET full_name = ?, school = ?, position = ?, role = ?, grade = ?, " +
                "letter = ?, subject = ?, homeroom = ?, has_class = ?, status = ? WHERE id = ?",
            arrayOf<Any>(
                fullName, school, position, role, grade, letter, subject, homeroom,
                if (homeroom.isBlank()) 0 else 1, status, id
            )
        )
        return true
    }

    /** Цвет ника: пустая строка — обычный цвет из темы. Меняет только сам пользователь или разработчик. */
    fun setNickColor(id: Long, color: String): Boolean {
        if (id <= 0L) return false
        db.execSQL("UPDATE users SET nick_color = ? WHERE id = ?", arrayOf<Any>(color.trim(), id))
        return true
    }

    /** Префикс перед именем: «Разработчик», «Агент Поддержки» или свой придуманный. */
    fun setPrefix(id: Long, prefix: String): Boolean {
        if (id <= 0L) return false
        db.execSQL("UPDATE users SET prefix = ? WHERE id = ?", arrayOf<Any>(prefix.trim(), id))
        return true
    }

    fun developers(): List<Account> = query("WHERE role = 'developer' ORDER BY id", emptyArray())

    fun setPassword(id: Long, password: String): Boolean {
        if (id <= 0L || password.trim().length < ru.readysquad.kvest.util.Validate.PASSWORD_MIN) return false
        db.execSQL("UPDATE users SET password = ? WHERE id = ?", arrayOf<Any>(password, id))
        return true
    }

    fun changeStatus(id: Long, status: String): Boolean {
        if (id <= 0L) return false
        db.execSQL("UPDATE users SET status = ? WHERE id = ?", arrayOf<Any>(status, id))
        return true
    }

    fun decide(approvalId: Long, approve: Boolean, byWhom: String) {
        val status = if (approve) "approved" else "rejected"
        db.rawQuery("SELECT user_id FROM approvals WHERE id = ?", arrayOf(approvalId.toString())).use { c ->
            if (c.moveToFirst()) {
                val userId = c.getLong(0)
                db.execSQL(
                    "UPDATE users SET status = ?, approved_by = ? WHERE id = ?",
                    arrayOf(status, byWhom, userId.toString())
                )
            }
        }
        db.execSQL(
            "UPDATE approvals SET status = ?, decided_at = ? WHERE id = ?",
            arrayOf(status, System.currentTimeMillis().toString(), approvalId.toString())
        )
    }

    fun byRole(role: String): List<Account> = query("WHERE role = ? AND status = 'approved'", arrayOf(role))

    fun classmates(account: Account): List<Account> = if (!account.isStudent) emptyList() else query(
        "WHERE role = 'student' AND status = 'approved' AND school = ? AND grade = ? AND letter = ?",
        arrayOf(account.school, account.grade, account.letter)
    )

    fun classNames(school: String): List<String> {
        val out = ArrayList<String>()
        db.rawQuery(
            "SELECT DISTINCT grade, letter FROM users WHERE role = 'student' AND school = ? " +
                "AND status = 'approved' ORDER BY grade, letter",
            arrayOf(school)
        ).use { c ->
            while (c.moveToNext()) out.add(Grades.label(c.getString(0), c.getString(1)))
        }
        return out
    }

    fun updateProfile(
        id: Long,
        fullName: String,
        avatar: String,
        about: String,
        goal: Int,
        gender: String
    ) {
        if (id <= 0L) return
        db.execSQL(
            "UPDATE users SET full_name = ?, avatar = ?, about = ?, goal = ?, gender = ? WHERE id = ?",
            arrayOf(fullName, avatar, about, Goals.normalize(goal).toString(), gender, id.toString())
        )
    }

    fun knownSchools(): List<String> {
        val out = ArrayList<String>()
        db.rawQuery(
            "SELECT DISTINCT school FROM users WHERE school IS NOT NULL AND school <> '' ORDER BY school",
            null
        ).use { c -> while (c.moveToNext()) out.add(c.getString(0) ?: "") }
        return out.filter { it.isNotBlank() }
    }

    fun schoolExists(name: String): Boolean {
        val q = name.trim()
        if (q.isEmpty()) return false
        db.rawQuery("SELECT 1 FROM users WHERE lower(school) = lower(?) LIMIT 1", arrayOf(q)).use {
            return it.moveToFirst()
        }
    }

    fun schoolSuggestions(query: String, limit: Int = 6): List<Pair<String, Boolean>> {
        val q = query.trim()
        val mine = knownSchools()
        val out = ArrayList<Pair<String, Boolean>>()
        (mine + SeedSchools.typical).distinct().forEach { name ->
            if (out.size >= limit) return@forEach
            if (q.isEmpty() || name.contains(q, ignoreCase = true)) out.add(name to mine.contains(name))
        }
        return out
    }

    fun hasApprover(school: String): Boolean {
        val rows = query("WHERE school = ? AND status = 'approved' AND role = 'teacher'", arrayOf(school))
        return rows.any {
            it.positions.contains(Positions.ADMIN) || it.positions.contains(Positions.HOMEROOM)
        }
    }

    fun teachers(school: String): List<Account> = query(
        "WHERE role = 'teacher' AND status = 'approved' AND school = ? ORDER BY full_name",
        arrayOf(school)
    )

    fun homeroomTeachers(school: String): List<Account> =
        teachers(school).filter { it.positions.contains(Positions.HOMEROOM) }

    fun homeroomOf(school: String, grade: String, letter: String): Account? {
        val cls = Grades.label(grade, letter)
        return homeroomTeachers(school).firstOrNull { it.homeroom.equals(cls, ignoreCase = true) }
    }

    fun studentsOf(school: String, grade: String, letter: String): List<Account> = query(
        "WHERE role = 'student' AND status = 'approved' AND school = ? AND grade = ? AND letter = ? " +
            "ORDER BY full_name",
        arrayOf(school, grade, letter)
    )

    fun classesOf(school: String): List<Triple<String, Int, Account?>> {
        val out = ArrayList<Triple<String, Int, Account?>>()
        db.rawQuery(
            "SELECT grade, letter, COUNT(*) FROM users WHERE role = 'student' AND status = 'approved' " +
                "AND school = ? GROUP BY grade, letter ORDER BY grade, letter",
            arrayOf(school)
        ).use { c ->
            while (c.moveToNext()) {
                val grade = c.getString(0) ?: ""
                val letter = c.getString(1) ?: ""
                out.add(Triple(Grades.label(grade, letter), c.getInt(2), homeroomOf(school, grade, letter)))
            }
        }
        return out
    }

    fun addScore(userId: Long, score: Int) {
        if (userId <= 0L || score <= 0) return
        db.execSQL("UPDATE users SET score = score + ? WHERE id = ?", arrayOf(score.toString(), userId.toString()))
    }

    fun classRating(account: Account): List<Account> = if (account.isStudent)
        query(
            "WHERE role = 'student' AND status = 'approved' AND school = ? AND grade = ? " +
                "AND letter = ? ORDER BY score DESC",
            arrayOf(account.school, account.grade, account.letter)
        ) else emptyList()

    fun schoolTop(school: String, limit: Int = 50): List<Account> =
        query("WHERE status = 'approved' AND school = ? ORDER BY score DESC LIMIT " + limit, arrayOf(school))

    fun all(limit: Int = 200): List<Account> = query("WHERE status = 'approved' ORDER BY id LIMIT ?", arrayOf(limit.toString()))

    private fun query(where: String, args: Array<String>): List<Account> {
        val out = ArrayList<Account>()
        db.rawQuery(
            "SELECT id, role, position, full_name, birth_date, gender, phone, region, school, grade, " +
                "letter, subject, homeroom, has_class, goal, login, password, status, approved_by, " +
                "avatar, about, score, nick_color, prefix " +
                "FROM users $where",
            args
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    Account(
                        id = c.getLong(0), role = c.getString(1) ?: "", position = c.getString(2) ?: "",
                        fullName = c.getString(3) ?: "", birthDate = c.getString(4) ?: "",
                        gender = c.getString(5) ?: "m", phone = c.getString(6) ?: "",
                        region = c.getString(7) ?: "", school = c.getString(8) ?: "",
                        grade = c.getString(9) ?: "", letter = c.getString(10) ?: "",
                        subject = c.getString(11) ?: "", homeroom = c.getString(12) ?: "",
                        hasClass = c.getInt(13) == 1, goal = c.getInt(14), login = c.getString(15) ?: "",
                        password = c.getString(16) ?: "", status = c.getString(17) ?: "",
                        approvedBy = c.getString(18) ?: "", avatar = c.getString(19) ?: "",
                        about = c.getString(20) ?: "", score = c.getInt(21),
                        nickColor = c.getString(22) ?: "", prefix = c.getString(23) ?: ""
                    )
                )
            }
        }
        return out
    }
}
