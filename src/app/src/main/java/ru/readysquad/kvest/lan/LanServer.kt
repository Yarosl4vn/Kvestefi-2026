package ru.readysquad.kvest.lan

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import ru.readysquad.kvest.data.Account
import ru.readysquad.kvest.data.AccountStore
import ru.readysquad.kvest.data.AssignmentStore
import ru.readysquad.kvest.data.Exam
import ru.readysquad.kvest.data.Repository
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

class LanServer(
    private val context: Context,
    private val teacher: Account,
    private val port: Int = DEFAULT_PORT,
    private val onLog: (String) -> Unit = {}
) {

    private var server: ServerSocket? = null
    private var worker: Thread? = null
    private var announcer: Thread? = null
    private var beacon: DatagramSocket? = null
    @Volatile private var running = false

    fun start(): String {
        if (running) return address()
        val socket = ServerSocket(port)
        server = socket
        running = true
        worker = Thread {
            while (running) {
                try {
                    val client = socket.accept()
                    handle(client)
                } catch (e: Exception) {
                    if (running) onLog("Сбой соединения: ${e.message}")
                }
            }
        }.apply { isDaemon = true; start() }
        startAnnouncer()
        onLog("Сервер запущен: ${address()}")
        return address()
    }

    fun stop() {
        running = false
        runCatching { server?.close() }
        server = null
        stopAnnouncer()
        onLog("Сервер остановлен")
    }

    fun isRunning(): Boolean = running

    fun address(): String = "${localIp()}:$port"

    private fun localIp(): String {
        runCatching {
            for (nif in NetworkInterface.getNetworkInterfaces()) {
                if (!nif.isUp || nif.isLoopback) continue
                for (addr in nif.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress && addr.isSiteLocalAddress) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        }
        return "127.0.0.1"
    }

    private fun handle(client: Socket) {
        runCatching {
            client.soTimeout = 8000
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val path = parts[1]
            val route = path.substringBefore('?')
            val query = queryOf(path)

            var length = 0
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                if (line.lowercase().startsWith("content-length:")) {
                    length = line.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }
            val body = if (length > 0) {
                val buf = CharArray(length)
                var read = 0
                while (read < length) {
                    val r = reader.read(buf, read, length - read)
                    if (r <= 0) break
                    read += r
                }
                String(buf, 0, read)
            } else ""

            val answer = route(route, query, body)

            val asHtml = route == "/" || route == "/web" || route == "/index.html" ||
                route == "/teacher" || route == "/cabinet"
            respond(client.getOutputStream(), answer, asHtml)
        }.onFailure { onLog("Ошибка запроса: ${it.message}") }
        runCatching { client.close() }
    }

    private fun queryOf(path: String): Map<String, String> {
        val out = HashMap<String, String>()
        val raw = path.substringAfter('?', "")
        if (raw.isBlank()) return out
        raw.split("&").forEach { pair ->
            val k = pair.substringBefore('=')
            val v = pair.substringAfter('=', "")
            if (k.isNotBlank()) {
                out[URLDecoder.decode(k, "UTF-8")] = URLDecoder.decode(v, "UTF-8")
            }
        }
        return out
    }

    private fun respond(out: OutputStream, body: String, asHtml: Boolean = false) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val type = if (asHtml) "text/html; charset=utf-8" else "application/json; charset=utf-8"
        val header = "HTTP/1.1 200 OK\r\n" +
            "Content-Type: " + type + "\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n\r\n"
        out.write(header.toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    private fun route(route: String, query: Map<String, String>, body: String): String = when (route) {

        "/", "/web", "/index.html" -> WebPage.load(context)

        "/teacher", "/cabinet" -> WebPage.load(context, WebPage.TEACHER_PATH)
        "/teacher/subjects" -> subjectsJson()
        "/teacher/topics" -> topicsJson(query)
        "/teacher/assignments" -> myAssignmentsJson()
        "/teacher/report" -> reportJson(query)
        "/teacher/assign" -> assign(body)
        "/ping" -> ping()
        "/assignments" -> assignments(query)
        "/result" -> acceptResult(body)
        "/rating" -> rating(query)
        else -> JSONObject().put("error", "unknown route").toString()
    }

    private fun ping() = JSONObject()
        .put("app", "kvestefi")
        .put("school", teacher.school)
        .put("teacher", teacher.fullName)
        .put("position", teacher.position)
        .put("homeroom", teacher.homeroom)
        .toString()

    private fun assignments(query: Map<String, String>): String {
        val store = AssignmentStore(context)
        val grade = query["grade"] ?: ""
        val letter = query["letter"] ?: ""
        val forAll = query["all"] == "1"
        val list = store.byTeacher(teacher.id).filter { a ->
            forAll || a.grade.isBlank() || (a.grade == grade && (a.letter.isBlank() || a.letter == letter))
        }
        val array = JSONArray()
        list.forEach { a ->
            val tasks = JSONArray()
            store.tasksOf(a).forEach { t ->
                tasks.put(
                    JSONObject()
                        .put("question", t.question)
                        .put("topic", t.topic)
                        .put("exam", t.exam)
                        .put("difficulty", t.difficulty)
                        .put("correct", t.correctIndex)
                        .put("explanation", t.explanation)
                        .put("options", JSONArray(t.options))
                )
            }
            array.put(
                JSONObject()
                    .put("id", a.id)
                    .put("exam", a.exam)
                    .put("topic", a.topic)
                    .put("subject", a.subjectCode)
                    .put("grade", a.grade)
                    .put("letter", a.letter)
                    .put("teacher", a.teacherName)
                    .put("created", a.createdAt)
                    .put("tasks", tasks)
            )
        }
        return array.toString()
    }

    private fun acceptResult(body: String): String {
        val json = runCatching { JSONObject(body) }.getOrNull()
            ?: return JSONObject().put("ok", false).put("error", "bad json").toString()
        val assignmentId = json.optLong("assignment_id", 0L)
        val studentName = json.optString("student", "Ученик")
        val store = AssignmentStore(context)
        val assignment = store.byId(assignmentId)
            ?: return JSONObject().put("ok", false).put("error", "assignment not found").toString()

        val users = AccountStore(context)
        val student = users.byRole("student").firstOrNull { it.fullName.equals(studentName, true) }
            ?: Account(
                role = "student", position = "Ученик", fullName = studentName,
                school = teacher.school, grade = assignment.grade, letter = assignment.letter
            )
        store.saveRemoteResult(
            assignment = assignment,
            student = student,
            correct = json.optInt("correct", 0),
            total = json.optInt("total", 0),
            score = json.optInt("score", 0),
            grade = json.optInt("grade", 0),
            details = json.optString("details", "")
        )
        onLog("Отчёт от $studentName: ${json.optInt("correct", 0)} из ${json.optInt("total", 0)}")
        return JSONObject().put("ok", true).toString()
    }

    private fun rating(query: Map<String, String>): String {
        val school = query["school"] ?: teacher.school
        val array = JSONArray()
        AccountStore(context).schoolTop(school)
            .forEach { array.put(JSONObject().put("name", it.fullName).put("score", it.score).put("grade", it.grade)) }
        return array.toString()
    }

    private fun subjectsJson(): String {
        val array = JSONArray()
        Repository.get(context).subjects().forEach {
            array.put(JSONObject().put("code", it.code).put("title", it.title))
        }
        return array.toString()
    }

    private fun topicsJson(query: Map<String, String>): String {
        val array = JSONArray()
        AssignmentStore(context)
            .topicsFor(query["subject"] ?: "", query["exam"] ?: Exam.OGE)
            .forEach { array.put(it) }
        return array.toString()
    }

    private fun myAssignmentsJson(): String {
        val store = AssignmentStore(context)
        val array = JSONArray()
        store.byTeacher(teacher.id).forEach { a ->
            array.put(
                JSONObject()
                    .put("id", a.id).put("exam", a.exam).put("topic", a.topic)
                    .put("size", a.size)
                    .put("passed", store.passedStudentIds(a.id).size)
            )
        }
        return array.toString()
    }

    private fun reportJson(query: Map<String, String>): String {
        val store = AssignmentStore(context)
        val id = query["id"]?.toLongOrNull() ?: 0L
        val assignment = store.byId(id)
        val results = JSONArray()
        if (assignment != null) {
            store.results(id).forEach { r ->
                val mistakes = JSONArray()
                r.mistakes.forEach { m ->
                    mistakes.put(
                        JSONObject().put("topic", m.topic).put("question", m.question)
                            .put("typed", m.typed).put("expected", m.expected)
                    )
                }
                results.put(
                    JSONObject().put("student", r.studentName).put("correct", r.correct)
                        .put("total", r.total).put("score", r.score).put("grade", r.grade)
                        .put("mistakes", mistakes)
                )
            }
        }
        val passed = if (assignment != null) store.passedStudentIds(id) else emptySet()
        val waiting = JSONArray()
        AccountStore(context).classmates(teacher)
            .filter { !passed.contains(it.id) }
            .forEach { waiting.put(it.fullName) }
        return JSONObject()
            .put("topic", assignment?.topic ?: "")
            .put("results", results)
            .put("waiting", waiting)
            .toString()
    }

    private fun assign(body: String): String {
        val json = runCatching { JSONObject(body) }.getOrNull()
            ?: return JSONObject().put("ok", false).put("error", "Непонятный запрос").toString()
        val store = AssignmentStore(context)
        val subject = json.optString("subject")
        val exam = json.optString("exam", Exam.OGE)
        val topic = json.optString("topic")
        val count = json.optInt("count", 5)
        val tasks = store.tasksFor(subject, exam, topic, count)
        if (tasks.isEmpty()) {
            return JSONObject().put("ok", false).put("error", "По этой теме задач не нашлось").toString()
        }
        val id = store.create(teacher, subject, exam, topic, tasks)
        onLog("Выдано задание по теме «$topic»: задач ${tasks.size}")
        return JSONObject().put("ok", id > 0L).put("id", id).put("tasks", tasks.size).toString()
    }

    private fun startAnnouncer() {
        if (announcer != null) return
        announcer = Thread {
            runCatching {
                val socket = DatagramSocket(DISCOVERY_PORT).apply { broadcast = true }
                beacon = socket
                val buffer = ByteArray(64)
                while (running) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val text = String(packet.data, 0, packet.length).trim()
                    if (text.startsWith(DISCOVERY_WORD)) {
                        val answer = DISCOVERY_WORD + ":" + port + ":" + teacher.fullName + ":" +
                            teacher.school + ":" + teacher.homeroom
                        val bytes = answer.toByteArray(Charsets.UTF_8)
                        socket.send(DatagramPacket(bytes, bytes.size, packet.address, packet.port))
                    }
                }
            }.onFailure { onLog("Поиск в сети недоступен: " + it.message) }
        }.apply { isDaemon = true; start() }
    }

    private fun stopAnnouncer() {
        running = false
        runCatching { beacon?.close() }
        beacon = null
        announcer = null
    }

    companion object {
        const val DEFAULT_PORT = 8767

        const val DISCOVERY_PORT = 8768
        const val DISCOVERY_WORD = "KVESTEFI"
    }
}
