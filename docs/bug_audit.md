# Аудит Kvestefi

Статический аудит Android‑приложения (Kotlin), проект `KvestMarathon`, пакет
`ru.readysquad.kvest`. Исходники только читались, ничего не изменялось.

Разобрано 62 файла `*.kt`. Архитектура: локальная SQLite (`DbHelper` + `Repository`),
экраны собираются кодом (`util/Screens`, `util/Dialogs`), мини‑сервер учителя
(`lan/LanServer`) + UDP‑поиск и HTTP‑клиент (`lan/LanSync`), 5 фрагментов в
`MainActivity` (bottom‑nav) и отдельные Activity. `targetSdk = 34`
(`app/build.gradle:13`), `usesCleartextTraffic="true"` (`AndroidManifest.xml:18`).

Банк заданий небольшой и считается вручную: максимум задач на пару
(предмет + уровень + тема) — 9 (`Орфография/ОГЭ`), у многих тем 1–5
(напр. `Геометрия/ОГЭ` = 5). Это прямо подтверждает часть находок ниже.

---

## Критичные (ломают работу)

### 1. `Assignments.kt:65-76` — «выдал 10 заданий, ученик видит 5»: количество режется банком темы
**Суть.** Учитель выбирает, сколько задач выдать (UI предлагает 5/10/15/20 — `AssignActivity.kt:48`,
и 5/10/15 — `Dialogs.kt:694`), но `tasksFor()` сначала делает `take(limit)` по пулу,
который уже сужен до строго выбранной темы. Если в теме задач меньше запрошенного
числа, лишние просто не добавляются — задание молча сохраняется с меньшим `task_ids`.
Пополнения из других тем/уровня и дедупликации нет.

**Доказательство.**
```kotlin
// Assignments.kt:65-76
fun tasksFor(subjectCode: String, exam: String, topic: String, limit: Int): List<Task> {
    val all = repo.queryTasks(
        "... FROM tasks WHERE subject_code = ? ORDER BY RANDOM()", arrayOf(subjectCode))
    val byExam = all.filter { it.exam.equals(exam, true) }
    val pool = byExam.ifEmpty { all }
    val exact = pool.filter { it.topic.equals(topic, true) }
    val chosen = exact.ifEmpty { pool }
    return chosen.take(limit.coerceAtLeast(1))   // ← больше, чем есть в теме, не взять
}
```
Вызов: `AssignActivity.kt:186` (`tasks.tasksFor(subjectCode, exam, topic, count)`),
`BadgesFragment.kt:336`, `LanServer.kt:338`.

**Воспроизведение.** Математика → ОГЭ → тема «Геометрия», количество 10. В банке
темы «Геометрия/ОГЭ» ровно **5** задач (`SeedTasksMath.kt`) → ученик видит 5.
Для «Орфография/ОГЭ» — 9, т.е. 10 превращается в 9.

**Исправление.** После отбора по теме добирать до `limit` из остальных задач того же
предмета/уровня (и только потом — из всего предмета), исключая уже выбранные;
если набрать не удалось — вернуть фактическое число и показать учителю
предупреждение «в банке только N задач».

---

### 2. `Assignments.kt:116-125` + `LanSync.kt:165` — задания, полученные по Wi‑Fi, у ученика не отображаются
**Суть.** `importRemote` вызывается с `school = ""` (пустая строка), а `forStudent()`
для «классовых» заданий требует совпадения школы. В итоге полученное по сети
задание не проходит фильтр и не попадает в список «Задания от учителя».

**Доказательство.**
```kotlin
// LanSync.kt:162-173  ← школа не передаётся
val id = store.importRemote(
    teacherAssignmentId = a.optLong("id", 0L),
    teacherName = a.optString("teacher", "Учитель"),
    school = "",                       // ← пустая школа
    ...)
// Assignments.kt:287  put("school", school)   // в БД запишется ""

// Assignments.kt:116-125
fun forStudent(student: Account): List<Assignment> =
    read("WHERE student_id = ? OR student_id = 0", arrayOf(student.id.toString()))
        .filter { a ->
            a.studentId == student.id || (
                a.school.equals(student.school, true) &&   // "" != "Школа №1" → false
                    a.grade == student.grade &&
                    (a.letter.isBlank() || a.letter == student.letter))
        }
```
`importRemote` пишет `student_id = 0` (стр. 289), поэтому и `a.studentId == student.id`
не срабатывает. Результат: `added++` в `LanSync.pullAssignments` (стр. 174) растёт,
но `HomeFragment.renderExtras`/`BadgesFragment.bindProfile` (`forStudent`) его не видят.

**Исправление.** Передавать реальную школу учителя (`LanServer` её знает: `teacher.school`)
в `/assignments` и/или в `forStudent` считать пустую школу «любой»:
`a.school.isBlank() || a.school.equals(student.school, true)`.

---

### 3. `BadgesFragment.kt:298,302,315` + `LanSync.kt:27-56` — весь обмен по сети идёт в главном потоке, поэтому «связи нет» всегда
**Суть.** `LanSync.ping/pullAssignments/pushResults` вызываются прямо из обработчиков
кнопок диалога (главный поток). `LanClient` делает HTTP на главном потоке → при
`targetSdk = 34` летит `NetworkOnMainThreadException`, который ловится
`catch (e: Exception)` и превращается в `null` → пользователь всегда видит
«Не удалось связаться…». Исключения нет, но функция не работает вообще.
`discover()` при этом выполняется в `Thread` (`BadgesFragment.kt:222`) — то есть
несогласованность очевидна.

**Доказательство.**
```kotlin
// BadgesFragment.kt:297-302  (вызов из onPull диалога — главный поток)
val (name, error) = LanSync.ping(addr)
if (error.isNotEmpty()) { Dialogs.info(... "Связи нет" ...) } else {
    val (added, problem) = LanSync.pullAssignments(requireContext(), addr, me)
// LanSync.kt:27-39
fun get(address: String, path: String): String? = try {
    val conn = (URL(base(address) + path).openConnection() as HttpURLConnection)...
    val code = conn.responseCode            // ← сеть в вызывающем потоке
    ...
} catch (e: Exception) { null }             // ← глотает NetworkOnMainThreadException
```

**Исправление.** Выполнять `ping/pull/push` в фоне (как уже сделано для `discover`):
`Thread { … runOnUiThread { … } }` или `Executors`. Не глотать сетевые ошибки молча.

---

### 4. `LanServer.kt:157-172` — переписка «по локальной сети» реально не сетевая
**Суть.** Требование «переписка учитель‑ученик по локальной сети». Фактически
`MessagesActivity`/`MessageStore` читают/пишут только локальную БД устройства, а в
маршрутах мини‑сервера нет ни одного эндпоинта для сообщений. Значит на разных
телефонах учитель и ученик друг друга не увидят — «общая» переписка возможна только
в пределах одной копии БД.

**Доказательство.**
```kotlin
// LanServer.kt:157-172 — маршруты
"/", "/web", "/index.html" -> WebPage.load(context)
"/teacher", "/cabinet"      -> WebPage.load(context, WebPage.TEACHER_PATH)
"/teacher/subjects" ... "/teacher/assign" -> ...
"/ping" -> ping(); "/assignments" -> assignments(query)
"/result" -> acceptResult(body); "/rating" -> rating(query)
else -> JSONObject().put("error", "unknown route")
// ── для messages/support маршрутов нет ──
// MessagesActivity.kt:169  messages.send(account, other, text) → только db.insert("messages")
```
Также `SupportActivity`/`SupportStore` — только локальная таблица `support_messages`.

**Исправление.** Либо честно назвать переписку «локальной внутри приложения», либо
добавить обмен сообщениями по LAN (маршруты `/messages?to=…`, `/message`, `/support`),
а на клиенте — синхронизацию `MessageStore` с сервером учителя.

---

## Средние (заметные)

### 5. `Assignments.kt:65-76` — нет защиты от повтора уже выданных заданий (требование №2)
**Суть.** Подбор всегда `ORDER BY RANDOM()` по всему банку и **никак** не исключает
задачи, которые уже входили в предыдущие задания этого класса/ученика. Требование
«не повторять, пока банк не исчерпан» не выполнено.

**Доказательство.** В `tasksFor` нет ни `excludeIds`, ни обращения к таблице
`assignments`; `create()` (стр. 90-113) просто сохраняет полученный список.

**Исправление.** Собирать `alreadyIssued: Set<Long>` из `assignments.task_ids`
школы/класса и передавать в `tasksFor` для фильтра; если после фильтрации задач
меньше `limit` — сначала повторять (банк исчерпан) и сообщать учителю.

---

### 6. `Assignments.kt:134-135` + `Repository.kt:379-384` — задачи задания молча теряются
**Суть.** `tasksOf` собирает задачи через `mapNotNull`, т.е. битые/удалённые id
тихо выпадают. При «Сбросить прогресс» (`Repository.resetAll`) таблица `tasks`
дропается и заполняется заново (`helper.onCreate(db)`), а таблица `assignments`
**не** трогается — выданные задания остаются со старыми `task_ids`, которые после
пересоздания банка указывают на другие/несуществующие строки.

**Доказательство.**
```kotlin
// Repository.kt:379-384 — assignments НЕ удаляется
listOf("answers", "attempts", "tasks", "theory", "badges", "subjects").forEach {
    db.execSQL("DROP TABLE IF EXISTS " + it)
}
helper.onCreate(db)   // ← tasks пересоздан с новыми id
// Assignments.kt:134-135
fun tasksOf(assignment: Assignment): List<Task> =
    assignment.taskIds.mapNotNull { repo.taskById(it) }   // ← пропавшие просто исчезают
```
Пользователь видит «задач: 5» вместо 10 или «В задании нет доступных задач».

**Исправление.** Хранить снимок задач в самом задании (JSON) либо чистить/пересобирать
`assignments` при сбросе; при `tasksOf` предупреждать, что часть задач недоступна.

---

### 7. `Assignments.kt:240-241` — дедупликация импорта только по времени создания
**Суть.** `alreadyImported(createdAt)` считает задание дублем по одному совпадению
`created_at`, без привязки к учителю/remote_id. Совпадение времени (разные задания в
одну миллисекунду, повторное создание) → задание будет ошибочно пропущено.

**Доказательство.**
```kotlin
fun alreadyImported(createdAt: Long): Boolean =
    db.rawQuery("SELECT 1 FROM assignments WHERE created_at = ?", arrayOf(createdAt.toString()))
        .use { it.moveToFirst() }
```
Плюс `importRemote` (стр. 262-277) при каждом импорте **заново вставляет** задачи в
`tasks` — банк растёт дублями, а фильтр по `topic`/`survey` затем путается.

**Исправление.** Ключ дедупликации — `(remote_id, teacher_name)` или составной хэш;
задачи при импорте искать/переиспользовать, а не вставлять слепо.

---

### 8. `Support.kt:30-35,62-66` + `SupportActivity.kt:119-160` — поддержка: нет статуса «На рассмотрении» и нет ответа разработчика (требование №3)
**Суть.** Есть ровно два статуса: `open`/`closed`. Нет «Ожидает / На рассмотрении /
Закрыто», нет поля ответа разработчика и UI для ответа — только кнопка «Закрыть».

**Доказательство.**
```kotlin
// Support.kt:33-34
const val OPEN = "open"
const val CLOSED = "closed"
// Support.kt:62-66
fun close(id: Long): Boolean {
    if (id <= 0L) return false
    db.execSQL("UPDATE support_messages SET status = ? WHERE id = ?", arrayOf<Any>(CLOSED, id))
    return true
}
// SupportActivity.kt:152-158 — единственное «действие» разработчика
if (canClose) { target.addView(ui.button("Закрыть обращение", primary = false) { support.close(message.id); rebuild() }) }
```
Таблица `support_messages` (`DbHelper.kt:182-187`) не имеет ни колонки `reply`,
ни `status`‑значения «в работе», ни автора ответа.

**Исправление.** Добавить статусы `pending/review/closed` (+ миграцию), колонки
`reply TEXT`, `replied_by`, `replied_at`; в UI — поле ответа и смена статуса,
у пользователя — отображение ответа.

---

### 9. `LanSync.kt:77-114` — автопоиск учителя есть, но хрупкий (UDP‑broadcast без MulticastLock) и не даёт список устройств (требование №4)
**Суть.** Ручной ввод IP — в `Dialogs.connectTeacher` (`Dialogs.kt:881-896`),
`EditText` с адресом. Автопоиск уже реализован (`LanSync.discover` + `LanServer.startAnnouncer`),
но рассылка идёт на `255.255.255.255` без захвата `WifiManager.MulticastLock`; на
многих устройствах broadcast в этом случае блокируется, и пользователь видит
«Учитель не найден» и снова вбивает адрес вручную. Список найденных устройств не
показывается — берётся только `found.firstOrNull()`.

**Доказательство.**
```kotlin
// LanSync.kt:80-87
DatagramSocket().use { socket ->
    socket.broadcast = true
    socket.send(DatagramPacket(request, request.size,
        InetAddress.getByName("255.255.255.255"), LanServer.DISCOVERY_PORT))
}
// BadgesFragment.kt:225
val first = found.firstOrNull()          // ← список не показываем
// Dialogs.kt:887-896 — ручной ввод адреса как основной путь
val input = EditText(ctx).apply { hint = "192.168.1.5:8767"; setText(address) }
```

**Исправление.** Упрощение: (1) сделать автопоиск основным действием, а ручной ввод —
«дополнительно»; (2) брать `MulticastLock`/использовать mDNS(NSD); (3) показывать
список найденных учителей (имя + школа + адрес) и подключаться по тапу.

---

### 10. `ProfileActivity.kt:58,474-491,742-752` — навигация по подстраницам профиля: состояние не сохраняется, «Устройства» — мёртвый дубль
**Суть.** Подстраницы (Достижения/Статистика/…) рисуются в текущем коде корректно
(есть guard `if (page != 0) renderPage()`), но:
* `page`, `pageTitleCache`, `pageBody` — обычные поля, **не** пишутся в
  `onSaveInstanceState` → после поворота экрана/пересоздания Activity подстраница
  «закрывается» и профиль откатывается на главную;
* строка «Устройства» открывает **другую** Activity (`openPeople(PeopleActivity.MODE_DEVICES)`),
  а внутренняя ветка `PAGE_DEVICES` (`renderPage`) недостижима — мёртвый код.

**Доказательство.**
```kotlin
// ProfileActivity.kt:310-316 — Устройства открывают PeopleActivity
ui.row(R.drawable.ic_nav_results, "Устройства", "с каких устройств открыт аккаунт")
    { openPeople(PeopleActivity.MODE_DEVICES) }
// ProfileActivity.kt:488-491
private fun openPage(target: Int) { page = target; rebuild() }   // state не сохраняется
// ProfileActivity.kt:742-752 — PAGE_DEVICES (9) никогда не открывается через openPage
PAGE_DEVICES -> { ui.card("Устройства") { addView(ui.text("Входы в аккаунт видны в разделе «Устройства» профиля...")) } }
```
`PAGE_DEVICES` объявлен и обрабатывается, но `openPage(PAGE_DEVICES)` не вызывается
нигде (проверено поиском по проекту).

**Исправление.** Сохранять `page`/`pageTitleCache` в `savedInstanceState` (или
вынести подстраницы в отдельные Activity/Fragment); убрать дубль «Устройства» —
оставить один источник правды.

---

### 11. Массовые обращения к SQLite из главного потока (`AssignActivity.render`, `ProfileActivity.renderPage`, `BadgesFragment.refresh` и др.)
**Суть.** Сторы (`AssignmentStore`, `AccountStore`, `MessageStore`) при создании
сразу открывают `DbHelper(context).writableDatabase` и делают `rawQuery` прямо в
`onCreate`/`render` главного потока. При больших таблицах это ANR; кроме того
`LanServer` пишет в ту же БД из своего worker‑потока (`LanServer.kt:48-57`) без
блокировок → возможны `SQLiteDatabaseLockedException`/гонки.

**Доказательство.**
```kotlin
// Assignments.kt:61 / Support.kt:28 / Messages.kt:28 — новый helper на каждый стор
private val db = DbHelper(context).writableDatabase
// BadgesFragment.kt:240-245 — сервер пишет из фонового потока, UI читает из главного
```
**Исправление.** Читать/писать через фоновый диспетчер (`Executors`/корутины),
держать один экземпляр БД (`Repository`/singleton helper), включить WAL или
`enableWriteAheadLogging()`.

---

## Мелкие / UX

### 12. `AssignActivity.kt:191-197` — учитель не узнаёт, что выдано меньше запрошенного
`Toast "Задание выдано: $topic, задач ${picked.size}"` показывает факт, но не
сравнивает с `count`; карточка «Выдано» (`renderIssued`) тоже печатает `assignment.size`
без пометки. **Fix:** если `picked.size < count`, сообщать «В банке только N задач по теме».

### 13. `Dialogs.kt:887-896` — нет валидации адреса учителя
`setText(address)` без проверки формата `host:port`; любой мусор уходит в `LanClient`
и даёт общее «Не удалось связаться». **Fix:** валидировать через regex/`InetSocketAddress`,
добавить порт по умолчанию `8767`.

### 14. `SupportActivity.kt:105-115` — гость не видит своих обращений
`mine(login)` фильтрует по `from_login`, а у гостя (`account == null`) он пустой;
кнопка отправки при этом активна (`support.send(me, …)` с `me == null` пишет пустые
поля). **Fix:** запоминать локальный идентификатор обращения или запрещать отправку гостю.

### 15. `Dialogs.kt:694` vs `AssignActivity.kt:48` — разные наборы количеств
В веб‑кабинете/диалоге — `listOf(5, 10, 15)`, в `AssignActivity` — `listOf(5, 10, 15, 20)`.
**Fix:** единый источник (`AssignActivity.COUNTS`).

### 16. `Accounts.kt:556-561` — рейтинг класса требует точного совпадения буквы
`classRating` фильтрует `AND letter = ?`; ученики с пустой буквой не видят друг друга,
хотя отображаются как один класс. **Fix:** при пустой букве не ограничивать по ней.

### 17. `ProfileActivity.kt:509` / `303-309` — рассинхрон подписей
Строка меню «Список учителей» открывает страницу с заголовком «Связь с учителями».
**Fix:** привести заголовки к одной формулировке.

---

## Требует проверки

### A. «Подстраницы профиля не открываются/не переключаются»
В текущем коде переключение реализовано и, по коду, работает: `openPage()` → `rebuild()`
→ `render()` с guard `if (page != 0) renderPage()` (`ProfileActivity.kt:93-105,488-491`),
а комментарий `:94-96` прямо описывает, что этот баг уже был исправлен. Воспроизвести
«не открывается» статически не удалось. Вероятно, симптом относится к старым сборкам
или к конкретному устройству. Проверить: (1) реально ли клик по `ui.row` доходит
(строка `isClickable` + `setOnClickListener` — `Screens.kt:131-135`); (2) не гасит ли
тап вложенный `MaterialCardView`; (3) поведение после поворота экрана (см. находку 10).
Если требуется «железная» навигация — вынести подстраницы в отдельные Activity.

### B. `LanServer.kt:43-45` — создание `ServerSocket` в главном потоке
`fun start(): String { … val socket = ServerSocket(port) … }` вызывается из
`onStart` диалога (`BadgesFragment.kt:266-271`) — главный поток. Операции с сокетами
в главном потоке на Android обычно бросают `NetworkOnMainThreadException`; здесь
исключение ловится `try/catch` и показывается «Порт занят». Нужно проверить на
устройстве, запускается ли «Режим класса» вообще; при подтверждении — вынести
`start()` в фоновый поток.

### C. `LanSync.kt:77-114` — доступность UDP‑поиска
Без `MulticastLock` (и при некоторых настройках Wi‑Fi/AP‑изоляции) broadcast
`255.255.255.255` не доходит → «Учитель не найден». Проверить на реальном Wi‑Fi;
при провале — `MulticastLock` или NSD/mDNS.

### D. `LanServer.kt:48-57` — конкурентный доступ к БД
Worker‑поток сервера (`assignments`, `acceptResult`) читает/пишет SQLite, пока
UI‑поток делает то же через свой `DbHelper`. Возможны блокировки. Проверить под
нагрузкой (несколько учеников одновременно отправляют отчёты).

---

## Резюме

Всего находок: **17** (4 критичные, 7 средних, 6 мелких) + 4 пункта «требует проверки».

Начинать с этих четырёх (они ломают заявленный функционал):
1. **`Assignments.kt:65-76`** — задание выдаётся не тем числом задач, что выбрал
   учитель (10 → 5), потому что `take(limit)` не добирает из банка.
2. **`LanSync.kt:165` + `Assignments.kt:116-125`** — задания, полученные по Wi‑Fi,
   вообще не видны ученику (пустая `school` против фильтра `forStudent`).
3. **`BadgesFragment.kt:298/302/315`** — сеть в главном потоке, `NetworkOnMainThreadException`
   глушится и превращается в «нет связи»; весь обмен заданиями/отчётами неисправен.
4. **`LanServer.kt:157-172`** — переписка и поддержка не передаются по сети
   (только локальная БД), т.е. «переписка по локальной сети» фактически отсутствует.

Дальше по важности: статусы и ответы в поддержке (№8), повтор заданий (№5),
потеря задач после сброса (№6), сохранение состояния навигации профиля (№10).
