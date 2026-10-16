package ru.readysquad.kvest.data

import android.content.ContentValues
import android.content.Context

data class Message(
    val id: Long,
    val fromId: Long,
    val fromName: String,
    val toId: Long,
    val toName: String,
    val text: String,
    val createdAt: Long,
    val readAt: Long = 0L,
    val replyToId: Long = 0L,
    val replyText: String = "",
    val attachment: String = ""
) {
    val isRead: Boolean get() = readAt > 0L
    val hasAttachment: Boolean get() = attachment.isNotBlank()
    val isEmpty: Boolean get() = text.isBlank() && attachment.isBlank()
}

class MessageStore(private val context: Context) {

    private val db = DbHelper(context).writableDatabase

    fun send(
        from: Account,
        to: Account,
        text: String,
        replyToId: Long = 0L,
        replyText: String = "",
        attachment: String = ""
    ): Boolean {
        val body = text.trim()
        if (body.isEmpty() && attachment.isBlank()) return false
        if (to.id <= 0L) return false
        val id = db.insert("messages", null, ContentValues().apply {
            put("from_id", from.id)
            put("from_name", from.fullName)
            put("to_id", to.id)
            put("to_name", to.fullName)
            put("text", body)
            put("created_at", System.currentTimeMillis())
            put("read_at", 0L)
            put("reply_to_id", replyToId)
            put("reply_text", replyText)
            put("attachment", attachment)
        })
        return id > 0
    }

    /** Удалить можно только своё сообщение. */
    fun delete(messageId: Long, mine: Long): Boolean =
        db.delete("messages", "id = ? AND from_id = ?", arrayOf(messageId.toString(), mine.toString())) > 0

    fun thread(me: Long, other: Long): List<Message> = query(
        "WHERE (from_id = ? AND to_id = ?) OR (from_id = ? AND to_id = ?) ORDER BY created_at ASC",
        arrayOf(me.toString(), other.toString(), other.toString(), me.toString())
    )

    /** Поиск по переписке: ищем по тексту сообщения и по цитате ответа. */
    fun search(me: Long, other: Long, query: String): List<Message> {
        val needle = query.trim()
        if (needle.isEmpty()) return thread(me, other)
        return thread(me, other).filter {
            it.text.contains(needle, true) || it.replyText.contains(needle, true)
        }
    }

    fun inbox(me: Long): List<Message> =
        query("WHERE to_id = ? ORDER BY created_at DESC", arrayOf(me.toString()))

    fun outbox(me: Long): List<Message> =
        query("WHERE from_id = ? ORDER BY created_at DESC", arrayOf(me.toString()))

    fun unreadCount(me: Long): Int = db.rawQuery(
        "SELECT COUNT(*) FROM messages WHERE to_id = ? AND (read_at IS NULL OR read_at = 0)",
        arrayOf(me.toString())
    ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun unreadFrom(me: Long, other: Long): Int = db.rawQuery(
        "SELECT COUNT(*) FROM messages WHERE to_id = ? AND from_id = ? AND (read_at IS NULL OR read_at = 0)",
        arrayOf(me.toString(), other.toString())
    ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    /** Время последнего сообщения в переписке — по нему сортируется список чатов. */
    fun lastAt(me: Long, other: Long): Long = db.rawQuery(
        "SELECT MAX(created_at) FROM messages WHERE (from_id = ? AND to_id = ?) " +
            "OR (from_id = ? AND to_id = ?)",
        arrayOf(me.toString(), other.toString(), other.toString(), me.toString())
    ).use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else 0L }

    /** Сколько моих сообщений в этой переписке уже прочитано. */
    fun readMine(from: Long, to: Long): Int = db.rawQuery(
        "SELECT COUNT(*) FROM messages WHERE from_id = ? AND to_id = ? AND read_at > 0",
        arrayOf(from.toString(), to.toString())
    ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun total(me: Long, other: Long): Int = db.rawQuery(
        "SELECT COUNT(*) FROM messages WHERE (from_id = ? AND to_id = ?) " +
            "OR (from_id = ? AND to_id = ?)",
        arrayOf(me.toString(), other.toString(), other.toString(), me.toString())
    ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun markRead(me: Long, other: Long) {
        db.execSQL(
            "UPDATE messages SET read_at = ? WHERE to_id = ? AND from_id = ? " +
                "AND (read_at IS NULL OR read_at = 0)",
            arrayOf<Any>(System.currentTimeMillis(), me, other)
        )
    }

    private fun query(where: String, args: Array<String>): List<Message> {
        val out = ArrayList<Message>()
        db.rawQuery(
            "SELECT id, from_id, from_name, to_id, to_name, text, created_at, read_at, " +
                "reply_to_id, reply_text, attachment FROM messages $where",
            args
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    Message(
                        id = c.getLong(0),
                        fromId = c.getLong(1),
                        fromName = c.getString(2) ?: "",
                        toId = c.getLong(3),
                        toName = c.getString(4) ?: "",
                        text = c.getString(5) ?: "",
                        createdAt = c.getLong(6),
                        readAt = if (c.isNull(7)) 0L else c.getLong(7),
                        replyToId = if (c.isNull(8)) 0L else c.getLong(8),
                        replyText = c.getString(9) ?: "",
                        attachment = c.getString(10) ?: ""
                    )
                )
            }
        }
        return out
    }
}
