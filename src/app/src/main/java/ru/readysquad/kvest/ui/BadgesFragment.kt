package ru.readysquad.kvest.ui

import android.os.Bundle
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import android.view.ViewGroup
import android.widget.GridLayout
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ru.readysquad.kvest.R
import ru.readysquad.kvest.data.Badge
import ru.readysquad.kvest.data.Repository
import ru.readysquad.kvest.data.Account
import ru.readysquad.kvest.lan.LanServer
import ru.readysquad.kvest.lan.LanSync
import ru.readysquad.kvest.data.Assignment
import ru.readysquad.kvest.data.AssignmentStore
import ru.readysquad.kvest.data.AccountStore
import ru.readysquad.kvest.data.Approval
import ru.readysquad.kvest.data.Grades
import ru.readysquad.kvest.data.Prefs
import ru.readysquad.kvest.data.Roles
import ru.readysquad.kvest.util.Avatars
import ru.readysquad.kvest.databinding.FragmentBadgesBinding
import ru.readysquad.kvest.databinding.ItemBadgeBinding
import ru.readysquad.kvest.util.Anim
import ru.readysquad.kvest.util.Dialogs
import ru.readysquad.kvest.util.Notifier

class BadgesFragment : Fragment(R.layout.fragment_badges), Refreshable {

    private var _b: FragmentBadgesBinding? = null
    private val b get() = _b!!
    private val repo by lazy { Repository.get(requireContext()) }
    private val prefs by lazy { Prefs(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _b = FragmentBadgesBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        refresh()
    }

    override fun refresh() {
        val bind = _b ?: return
        val badges = repo.badges()
        val unlocked = badges.count { it.isUnlocked }
        val percent = if (badges.isEmpty()) 0 else unlocked * 100 / badges.size

        b.badgeCount.text = "$unlocked из ${badges.size} открыто"
        b.badgePercent.text = "$percent%"
        b.badgeBar.setProgress(percent, true)

        Notifier.markBadgesSeen(prefs, badges)

        runCatching { bindProfile() }.onFailure { android.util.Log.e("Kvestefi", "Профиль", it) }
        fillGrid(b.badgeGrid, badges)
    }

    private fun fillGrid(grid: GridLayout, badges: List<Badge>) {
        grid.removeAllViews()
        val density = resources.displayMetrics.density
        val gap = (6 * density).toInt()

        badges.forEachIndexed { index, badge ->
            val row = ItemBadgeBinding.inflate(layoutInflater, grid, false)
            BadgeAdapter.bind(row, badge, index)

            row.badgeRoot.setOnClickListener { Dialogs.badge(requireContext(), badge) }

            val lp = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            )
            lp.width = 0
            lp.height = GridLayout.LayoutParams.WRAP_CONTENT
            lp.setMargins(gap, gap, gap, gap)
            row.root.layoutParams = lp
            grid.addView(row.root)
        }
    }

    private val store by lazy { AccountStore(requireContext()) }
    private val assignments by lazy { AssignmentStore(requireContext()) }
    private var server: LanServer? = null
    private var receiveCount = 0

    private fun bindProfile() {
        val me = prefs.currentUserId.takeIf { it > 0 }?.let { store.byId(it) }
        b.profileName.text = me?.address(prefs.addressByName)?.ifBlank { me.fullName }
            ?: prefs.name.ifBlank { "Гость" }
        b.profilePosition.text = me?.positionLine ?: "Гость"
        b.profileSub.text = if (me == null)
            "Войди или зарегистрируйся, чтобы попасть в рейтинг класса и школы"
        else me.subtitle()
        b.profileAbout.text = me?.about.orEmpty()
        b.profileAbout.visibility = if (me?.about.isNullOrBlank()) View.GONE else View.VISIBLE

        val custom = Avatars.load(requireContext(), prefs.avatarImage)
        if (custom != null) {
            b.profileAvatar.setImageBitmap(Avatars.circleBitmap(custom, 240))
        } else {
            b.profileAvatar.setImageResource(R.drawable.ic_card_person)
        }

        val requests = me?.let { store.pendingFor(it) } ?: emptyList()
        val mine = me?.let { assignments.byTeacher(it.id) } ?: emptyList()
        val toSolve = me?.takeIf { it.isStudent }?.let { assignments.forStudent(it) } ?: emptyList()
        b.btnRequests.text = when {
            me == null -> "Заявки"
            me.isTeacher -> if (requests.isEmpty()) "Заявки" else "Заявки (${requests.size})"
            else -> if (toSolve.isEmpty()) "Задания" else "Задания (${toSolve.size})"
        }
        // Гостю эти разделы недоступны — не показываем их вовсе.
        val guest = me == null
        b.btnRequests.visibility = if (guest) android.view.View.GONE else android.view.View.VISIBLE
        b.btnRequests.isEnabled = !guest
        b.btnRating.text = if (me?.isTeacher == true) "Мои задания" else "Рейтинг класса"
        b.btnRating.visibility = if (guest) android.view.View.GONE else android.view.View.VISIBLE
        b.btnRating.isEnabled = !guest
        // Заявок и подтверждения больше нет: аккаунт создаётся сразу, поэтому кнопка не нужна.
        b.btnRequests.visibility = android.view.View.GONE
        b.btnTopSchool.text = if (me?.isTeacher == true) "Режим класса" else "Связь с учителем"
        b.btnTopSchool.visibility = if (guest) android.view.View.GONE else android.view.View.VISIBLE
        b.btnTopSchool.isEnabled = !guest
        b.btnAccount.text = if (me == null) "Войти или зарегистрироваться" else "Выйти из аккаунта"

        b.btnRequests.setOnClickListener {
            when {
                me == null -> Unit
                me.isTeacher -> showRequests(me, requests)
                else -> showStudentAssignments(me, toSolve)
            }
        }
        b.btnRating.setOnClickListener {
            if (me == null) return@setOnClickListener
            if (me.isTeacher) showMyAssignments(me) else showClassRating(me)
        }
        b.btnTopSchool.setOnClickListener {
            if (me == null) return@setOnClickListener
            if (me.isTeacher) openClassMode(me) else openStudentSync(me)
        }
        b.btnProfileSettings.setOnClickListener {
            startActivity(Intent(requireContext(), ProfileActivity::class.java))
        }
        b.btnAccount.setOnClickListener {
            if (me == null) startActivity(Intent(requireContext(), AuthActivity::class.java))
            else confirmSignOut()
        }
    }

    private fun showRequests(me: Account, requests: List<Pair<Approval, Account>>) {
        Dialogs.approvals(requireContext(), requests) { approval, approve ->
            store.decide(approval.id, approve, me.fullName)
            Toast.makeText(
                requireContext(),
                if (approve) "Аккаунт подтверждён" else "Заявка отклонена",
                Toast.LENGTH_SHORT
            ).show()
            bindProfile()
        }
    }

    private fun showStudentAssignments(me: Account, items: List<Assignment>) {
        val passed = items.map { it.id }.filter { assignments.results(it).any { r -> r.studentId == me.id } }.toSet()
        Dialogs.studentAssignments(requireContext(), items, passed) { assignment ->
            val subject = repo.subjects().firstOrNull { it.code == assignment.subjectCode }
            startActivity(
                Intent(requireContext(), QuizActivity::class.java)
                    .putExtra(QuizActivity.EXTRA_SUBJECT, assignment.subjectCode)
                    .putExtra(QuizActivity.EXTRA_TITLE, subject?.title ?: "Задание")
                    .putExtra(QuizActivity.EXTRA_EMOJI, subject?.emoji ?: "")
                    .putExtra(QuizActivity.EXTRA_ASSIGNMENT, assignment.id)
            )
        }
    }

    private fun showMyAssignments(me: Account) {
        val mine = assignments.byTeacher(me.id)
        if (mine.isEmpty()) {
            Dialogs.info(
                requireContext(),
                "Заданий пока нет",
                "Нажми «Выдать задание» и выбери предмет, уровень и тему — приложение подберёт задачи."
            )
            return
        }
        val titles = mine.map { "${it.exam} · ${it.topic} · ${it.size} задач" }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Мои задания")
            .setItems(titles.toTypedArray()) { _, which -> showReport(me, mine[which]) }
            .show()
    }

    private fun showReport(me: Account, assignment: Assignment) {
        val results = assignments.results(assignment.id)
        val passed = results.map { it.studentId }.toSet()
        val waiting = store.classmates(me).filter { it.id != me.id && !passed.contains(it.id) }
            .map { it.fullName }
        Dialogs.report(requireContext(), assignment, results, waiting)
    }

    private fun discoverTeacher(me: Account) {
        Dialogs.info(requireContext(), "Ищем учителя", "Это занимает пару секунд. Убедись, что вы в одном Wi-Fi.")
        Thread {
            val found = LanSync.discover(3000)
            requireActivity().runOnUiThread {
                val first = found.firstOrNull()
                when {
                    first == null -> Dialogs.info(
                        requireContext(),
                        "Учитель не найден",
                        "Проверь, что включён режим класса и оба устройства в одной сети Wi-Fi. " +
                            "Адрес можно ввести вручную."
                    )
                    else -> {
                        prefs.teacherAddress = first.address
                        Dialogs.info(
                            requireContext(),
                            "Учитель найден",
                            first.teacher + ", " + first.school + "\nАдрес: " + first.address +
                                "\n\nНажми «Получить задания»."
                        )
                        openStudentSync(me)
                    }
                }
            }
        }.start()
    }

    private fun ensureServer(me: Account): LanServer = server
        ?: LanServer(requireContext(), me) { message ->
            android.util.Log.i("KvestefiLan", message)
        }.also { server = it }

    private fun openClassMode(me: Account) {
        val srv = ensureServer(me)
        val shown = { Dialogs.classMode(
            requireContext(),
            if (srv.isRunning()) srv.address() else "",
            srv.isRunning(),
            receiveCount,
            onStart = {
                val address = try {
                    srv.start()
                } catch (e: Exception) {
                    android.util.Log.e("KvestefiLan", "Сервер не запустился", e)
                    ""
                }
                Dialogs.info(
                    requireContext(),
                    if (address.isBlank()) "Не удалось включить" else "Режим класса включён",
                    if (address.isBlank())
                        "Порт занят другим приложением. Закрой его или перезапусти Квестефи."
                    else "Назови ученикам адрес: $address\n\n" +
                        "Они открывают его в браузере или вводят в профиле приложения."
                )
                openClassMode(me)
            },
            onStop = { srv.stop(); server = null; openClassMode(me) },
            onAssign = { createAssignment(me) },
            onRefresh = { openClassMode(me) }
        ) }
        shown()
    }

    private fun openStudentSync(me: Account) {
        fun open() {
            val address = prefs.teacherAddress
            Dialogs.connectTeacher(
                requireContext(),
                address,
                onAddress = { prefs.teacherAddress = it },

                onPull = { addr ->
                    android.widget.Toast.makeText(
                        requireContext(), "Подключаюсь к $addr…", android.widget.Toast.LENGTH_SHORT
                    ).show()
                    Thread {
                        val (name, error) = LanSync.ping(addr)
                        val (added, problem) =
                            if (error.isEmpty()) LanSync.pullAssignments(requireContext(), addr, me)
                            else 0 to ""
                        requireActivity().runOnUiThread {
                            when {
                                error.isNotEmpty() -> Dialogs.info(requireContext(), "Связи нет", error)
                                problem.isNotEmpty() -> Dialogs.info(requireContext(), "Ошибка", problem)
                                else -> Dialogs.info(
                                    requireContext(), "Задания получены",
                                    "Учитель: $name\nНовых заданий: $added\n\nОни лежат в разделе «Задания»."
                                )
                            }
                            bindProfile()
                        }
                    }.start()
                },
                onPush = { addr ->
                    Thread {
                        val (sent, error) = LanSync.pushResults(requireContext(), addr, me)
                        requireActivity().runOnUiThread {
                            Dialogs.info(
                                requireContext(),
                                if (error.isEmpty()) "Отчёты отправлены" else "Отправлено частично",
                                if (error.isEmpty()) "Учитель получил отчётов: $sent"
                                else "$error\nОтправлено: $sent"
                            )
                        }
                    }.start()
                },
                onDiscover = { discoverTeacher(me) }
            )
        }
        open()
    }

    private fun createAssignment(me: Account) {
        Dialogs.assignTask(
            requireContext(),
            repo.subjects(),
            { code, exam -> assignments.topicsFor(code, exam) }
        ) { subject, exam, topic, count ->

            val tasks = assignments.tasksFor(
                subject.code, exam, topic, count, assignments.issuedTaskIds(me.id)
            )
            if (tasks.isEmpty()) {
                Dialogs.info(requireContext(), "Задач не нашлось", "Попробуй другую тему или уровень.")
                return@assignTask
            }
            val id = assignments.create(me, subject.code, exam, topic, tasks)
            if (id > 0L) {
                val bank = assignments.availableCount(subject.code, exam, topic)
                val note = if (bank < count)
                    "\n\nВ теме только $bank задач — остальные добраны из других тем предмета."
                else ""
                Dialogs.info(
                    requireContext(),
                    "Задание выдано",
                    "${subject.title} · $exam · $topic\nЗадач: ${tasks.size}\n" +
                        "Ученики увидят его в профиле, отчёты придут сюда же.$note"
                )
                bindProfile()
            }
        }
    }

    private fun showClassRating(me: Account) {
        val rows = store.classRating(me)
        Dialogs.ratingClass(requireContext(), Grades.label(me.grade, me.letter), rows, me.id)
    }

    private fun showSchoolTop(me: Account) {
        val rows = store.schoolTop(me.school)
        Dialogs.ratingClass(requireContext(), "школы: ${me.school}", rows, me.id)
    }

    private fun confirmSignOut() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Сменить профиль")
            .setMessage("Прогресс останется на устройстве. Профиль школы отвяжется, рейтинг класса будет недоступен.")
            .setPositiveButton("Выйти") { _, _ ->
                prefs.currentUserId = 0L
                prefs.role = Roles.GUEST
                startActivity(Intent(requireContext(), AuthActivity::class.java))
                requireActivity().finish()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }
}
