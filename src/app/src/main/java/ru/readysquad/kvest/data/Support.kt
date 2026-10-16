package ru.readysquad.kvest.data

import android.content.ContentValues
import android.content.Context

data class SupportMessage(
    val id: Long,
    val kind: String,
    val fromName: String,
    val fromLogin: String,
    val school: String,
    val text: String,
    val createdAt: Long,
    val status: String,
    val reply: String = "",
    val repliedBy: String = "",
    val repliedAt: Long = 0L
) {
    val isOpen: Boolean get() = status != SupportStore.CLOSED

    val statusLabel: String get() = when (status) {
        SupportStore.REVIEW -> "На рассмотрении"
        SupportStore.CLOSED -> "Закрыто"
        else -> "Ожидает ответа"
    }
}

class SupportStore(private val context: Context) {

    private val db = DbHelper(context).writableDatabase

    companion object {

        val kinds = listOf("Вопрос", "Ошибка", "Предложение")

        const val PENDING = "pending"

        const val REVIEW = "review"
        const val CLOSED = "closed"

        const val OPEN = PENDING
    }

    fun send(account: Account?, kind: String, text: String): Boolean {
        val body = text.trim()
        if (body.isEmpty()) return false
        val id = db.insert("support_messages", null, ContentValues().apply {
            put("kind", kind)
            put("from_name", account?.fullName.orEmpty())
            put("from_login", account?.login.orEmpty())
            put("school", account?.school.orEmpty())
            put("text", body)
            put("status", OPEN)
            put("created_at", System.currentTimeMillis())
        })
        return id > 0
    }

    fun all(): List<SupportMessage> = read("WHERE 1 = 1 ORDER BY created_at DESC", emptyArray())

    fun open(): List<SupportMessage> =
        read("WHERE status IS NULL OR status <> ? ORDER BY created_at DESC", arrayOf(CLOSED))

    fun mine(login: String): List<SupportMessage> =
        read("WHERE from_login = ? ORDER BY created_at DESC", arrayOf(login))

    fun guest(): List<SupportMessage> =
        read("WHERE from_login = '' OR from_login IS NULL ORDER BY created_at DESC", emptyArray())

    fun close(id: Long): Boolean = setStatus(id, CLOSED)

    fun setStatus(id: Long, status: String): Boolean {
        if (id <= 0L) return false
        db.execSQL("UPDATE support_messages SET status = ? WHERE id = ?", arrayOf<Any>(status, id))
        return true
    }

    fun reply(id: Long, text: String, by: String): Boolean {
        if (id <= 0L) return false
        val body = text.trim()
        if (body.isEmpty()) return false
        db.execSQL(
            "UPDATE support_messages SET reply = ?, replied_by = ?, replied_at = ?, status = ? WHERE id = ?",
            arrayOf<Any>(body, by, System.currentTimeMillis(), REVIEW, id)
        )
        return true
    }

    private fun read(where: String, args: Array<String>): List<SupportMessage> {
        val out = ArrayList<SupportMessage>()
        db.rawQuery(
            "SELECT id, kind, from_name, from_login, school, text, created_at, status, " +
                "reply, replied_by, replied_at FROM support_messages $where",
            args
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    SupportMessage(
                        id = c.getLong(0),
                        kind = c.getString(1) ?: "",
                        fromName = c.getString(2) ?: "",
                        fromLogin = c.getString(3) ?: "",
                        school = c.getString(4) ?: "",
                        text = c.getString(5) ?: "",
                        createdAt = c.getLong(6),
                        status = c.getString(7) ?: PENDING,
                        reply = c.getString(8) ?: "",
                        repliedBy = c.getString(9) ?: "",
                        repliedAt = c.getLong(10)
                    )
                )
            }
        }
        return out
    }
}

data class LoginEvent(
    val id: Long,
    val userId: Long,
    val userName: String,
    val device: String,
    val place: String,
    val createdAt: Long
)
