package ru.readysquad.kvest.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import java.net.Inet4Address
import java.net.NetworkInterface

class SessionStore(private val context: Context) {

    private val db = DbHelper(context).writableDatabase

    fun note(userId: Long, userName: String, device: String, place: String): Boolean {
        if (userId <= 0L) return false
        val id = db.insert("login_events", null, ContentValues().apply {
            put("user_id", userId)
            put("user_name", userName)
            put("device", device)
            put("place", place)
            put("created_at", System.currentTimeMillis())
        })
        return id > 0
    }

    fun clear(userId: Long): Boolean {
        if (userId <= 0L) return false
        db.execSQL("DELETE FROM login_events WHERE user_id = ?", arrayOf<Any>(userId))
        return true
    }

    fun all(limit: Int = 200): List<LoginEvent> =
        read("WHERE 1 = 1 ORDER BY created_at DESC LIMIT ?", arrayOf(limit.toString()))

    fun of(userId: Long, limit: Int = 60): List<LoginEvent> =
        read("WHERE user_id = ? ORDER BY created_at DESC LIMIT ?", arrayOf(userId.toString(), limit.toString()))

    fun devicesOf(userId: Long): List<LoginEvent> =
        of(userId).distinctBy { it.device }.sortedByDescending { it.createdAt }

    private fun read(where: String, args: Array<String>): List<LoginEvent> {
        val out = ArrayList<LoginEvent>()
        db.rawQuery(
            "SELECT id, user_id, user_name, device, place, created_at FROM login_events $where",
            args
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    LoginEvent(
                        id = c.getLong(0),
                        userId = c.getLong(1),
                        userName = c.getString(2) ?: "",
                        device = c.getString(3) ?: "",
                        place = c.getString(4) ?: "",
                        createdAt = c.getLong(5)
                    )
                )
            }
        }
        return out
    }
}

object DeviceInfo {

    fun model(): String {
        val maker = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        return "$maker ${Build.MODEL} · Android ${Build.VERSION.RELEASE}"
    }

    fun place(): String {
        val ip = localAddress() ?: return "школьная сеть, адрес не определён"
        return "школьная сеть · $ip"
    }

    private fun localAddress(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
    }.getOrNull()
}
