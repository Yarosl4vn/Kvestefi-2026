package ru.readysquad.kvest.lan

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import ru.readysquad.kvest.data.Account
import ru.readysquad.kvest.data.Assignment
import ru.readysquad.kvest.data.AssignmentStore
import ru.readysquad.kvest.data.Task
import java.io.BufferedReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object LanClient {

    private fun base(address: String): String {
        val clean = address.trim().removePrefix("http://").removeSuffix("/")
        return "http://$clean"
    }

    fun get(address: String, path: String): String? = try {
        val conn = (URL(base(address) + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 4000
            readTimeout = 8000
        }
        val code = conn.responseCode
        val text = if (code in 200..299) conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() } else null
        conn.disconnect()
        text
    } catch (e: Exception) {
        null
    }

    fun post(address: String, path: String, body: String): String? = try {
        val conn = (URL(base(address) + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 4000
            readTimeout = 8000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val text = if (code in 200..299) conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() } else null
        conn.disconnect()
        text
    } catch (e: Exception) {
        null
    }

    fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}

object LanSync {

    data class Found(val address: String, val teacher: String, val school: String, val homeroom: String)

    fun discover(timeoutMs: Int = 3000): List<Found> {
        val out = ArrayList<Found>()
        runCatching {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 700
                val request = (LanServer.DISCOVERY_WORD + "?").toByteArray(Charsets.UTF_8)
                socket.send(
                    DatagramPacket(request, request.size,
                        InetAddress.getByName("255.255.255.255"), LanServer.DISCOVERY_PORT)
                )
                val buffer = ByteArray(256)
                val deadline = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < deadline) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (e: Exception) {
                        continue
                    }
                    val parts = String(packet.data, 0, packet.length).split(":")
                    if (parts.size >= 3 && parts[0] == LanServer.DISCOVERY_WORD) {
                        val host = packet.address?.hostAddress ?: continue
                        val address = host + ":" + parts[1]
                        if (out.none { it.address == address }) {
                            out.add(
                                Found(
                                    address, parts[2],
                                    parts.getOrElse(3) { "" }, parts.getOrElse(4) { "" }
                                )
                            )
                        }
                    }
                }
            }
        }
        return out
    }

    fun ping(address: String): Pair<String, String> {
        val body = LanClient.get(address, "/ping")
            ?: return "" to "Не удалось связаться. Проверь, что телефон в том же Wi-Fi и адрес введён верно."
        return runCatching {
            val json = JSONObject(body)
            val teacher = json.optString("teacher", "учитель")
            val school = json.optString("school", "")
            "$teacher${if (school.isBlank()) "" else ", $school"}" to ""
        }.getOrElse { "" to "Учитель ответил непонятно: ${it.message}" }
    }

    fun pullAssignments(context: Context, address: String, student: Account): Pair<Int, String> {
        val path = "/assignments?grade=${LanClient.enc(student.grade)}&letter=${LanClient.enc(student.letter)}"
        val body = LanClient.get(address, path)
            ?: return 0 to "Не удалось получить задания. Проверь адрес и Wi-Fi."
        val store = AssignmentStore(context)
        var added = 0
        runCatching {
            val array = JSONArray(body)
            for (i in 0 until array.length()) {
                val a = array.optJSONObject(i) ?: continue
                val created = a.optLong("created", 0L)
                if (created > 0L && store.alreadyImported(created)) continue
                val tasks = ArrayList<Task>()
                val list = a.optJSONArray("tasks") ?: JSONArray()
                for (k in 0 until list.length()) {
                    val t = list.optJSONObject(k) ?: continue
                    val options = ArrayList<String>()
                    val opts = t.optJSONArray("options") ?: JSONArray()
                    for (o in 0 until opts.length()) options.add(opts.optString(o))
                    tasks.add(
                        Task(
                            id = 0L,
                            subjectCode = a.optString("subject", student.grade),
                            topic = t.optString("topic", a.optString("topic")),
                            exam = t.optString("exam", a.optString("exam")),
                            difficulty = t.optInt("difficulty", 1),
                            question = t.optString("question"),
                            options = options,
                            correctIndex = t.optInt("correct", 0),
                            explanation = t.optString("explanation")
                        )
                    )
                }
                val id = store.importRemote(
                    teacherAssignmentId = a.optLong("id", 0L),
                    teacherName = a.optString("teacher", "Учитель"),
                    school = "",
                    subjectCode = a.optString("subject", ""),
                    exam = a.optString("exam", ""),
                    topic = a.optString("topic", ""),
                    grade = a.optString("grade", student.grade),
                    letter = a.optString("letter", student.letter),
                    createdAt = created,
                    tasks = tasks
                )
                if (id > 0L) added++
            }
        }.onFailure { return added to "Ответ учителя не удалось разобрать: ${it.message}" }
        return added to ""
    }

    fun pushResults(context: Context, address: String, student: Account): Pair<Int, String> {
        val store = AssignmentStore(context)
        val mine = store.forStudent(student).filter { it.teacherId == 0L }
        if (mine.isEmpty()) return 0 to "Сначала получи задания от учителя"
        var sent = 0
        var error = ""
        mine.forEach { a ->
            val result = store.results(a.id).firstOrNull { it.studentId == student.id } ?: return@forEach
            val json = JSONObject()
                .put("assignment_id", store.remoteIdOf(a))
                .put("student", student.fullName)
                .put("correct", result.correct)
                .put("total", result.total)
                .put("score", result.score)
                .put("grade", result.grade)
                .put("details", store.detailsRaw(a.id, student.id))
            val answer = LanClient.post(address, "/result", json.toString())
            if (answer == null) {
                error = "Не удалось отправить отчёт. Проверь, что учитель в сети."
            } else if (runCatching { JSONObject(answer).optBoolean("ok") }.getOrDefault(false)) {
                sent++
            }
        }
        return sent to error
    }
}
