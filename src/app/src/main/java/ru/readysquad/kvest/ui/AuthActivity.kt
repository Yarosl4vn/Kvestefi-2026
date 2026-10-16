package ru.readysquad.kvest.ui

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ru.readysquad.kvest.R
import ru.readysquad.kvest.data.Account
import ru.readysquad.kvest.data.AccountStore
import ru.readysquad.kvest.data.Developer
import ru.readysquad.kvest.data.Goals
import ru.readysquad.kvest.data.Grades
import ru.readysquad.kvest.data.Positions
import ru.readysquad.kvest.data.Prefs
import ru.readysquad.kvest.data.Regions
import ru.readysquad.kvest.data.Repository
import ru.readysquad.kvest.data.ClassStore
import ru.readysquad.kvest.data.DeviceInfo
import ru.readysquad.kvest.data.SchoolSeed
import ru.readysquad.kvest.data.SchoolStore
import ru.readysquad.kvest.data.SessionStore
import ru.readysquad.kvest.data.Roles
import ru.readysquad.kvest.util.Anim
import ru.readysquad.kvest.util.Greetings
import ru.readysquad.kvest.util.Icons
import ru.readysquad.kvest.util.Palettes
import ru.readysquad.kvest.util.Validate

class AuthActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var store: AccountStore

    private var mode = MODE_GUEST
    private var role = Roles.STUDENT
    private var gender = Greetings.MALE
    private var goal = Goals.DEFAULT
    private var region = Regions.MOSCOW_REGION
    private var grade = "9"
    private var letter = "А"
    private var hasClass = false

    private val teacherPositions = linkedSetOf(Positions.SUBJECT)

    private val schools by lazy { SchoolStore(this) }

    private val repo by lazy { Repository.get(this) }

    private lateinit var root: LinearLayout
    private lateinit var modeRow: LinearLayout
    private lateinit var form: LinearLayout

    private val fields = HashMap<String, EditText>()

    private val draft = HashMap<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        Palettes.apply(this, prefs.palette)
        super.onCreate(savedInstanceState)
        store = AccountStore(this)

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.color.bg))
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        val scroll = ScrollView(this)
        scroll.addView(root, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setContentView(scroll)

        buildHeader()
        buildModeChips()
        form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(form)
        render()
    }

    private fun buildHeader() {
        val logo = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_hello)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val icon = android.widget.ImageView(this).apply {
            setImageResource(R.drawable.ic_card_rocket)
            setColorFilter(color(R.color.on_primary_container))
        }
        logo.addView(icon, LinearLayout.LayoutParams(dp(30), dp(30)))
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }
        texts.addView(
            TextView(this).apply {
                text = "Квестефи"
                textSize = 24f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(color(R.color.on_primary_container))
            }
        )
        texts.addView(
            TextView(this).apply {
                text = "Подготовка к ОГЭ и ЕГЭ"
                textSize = 14f
                setTextColor(color(R.color.on_primary_container))
            }
        )
        logo.addView(texts)
        root.addView(logo)

        modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(16), 0, 0)
        }
        root.addView(modeRow)
    }

    private fun buildModeChips() {
        modeRow.removeAllViews()
        listOf(
            MODE_GUEST to "Гость",
            MODE_LOGIN to "Вход",
            MODE_REGISTER to "Регистрация"
        ).forEach { (value, label) ->
            val chip = TextView(this).apply {
                text = label
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(10), dp(16), dp(10))
                background = ContextCompat.getDrawable(this@AuthActivity, R.drawable.bg_chip_choice)
                setTextColor(ContextCompat.getColorStateList(this@AuthActivity, R.color.chip_text))
                isSelected = mode == value
                isClickable = true
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginEnd = dp(8) }
                setOnClickListener {
                    mode = value
                    buildModeChips()
                    render()
                    Anim.pop(this, 0)
                }
            }
            modeRow.addView(chip)
        }
    }

    private fun render() {

        fields.forEach { (key, view) -> draft[key] = view.text.toString() }
        form.removeAllViews()
        fields.clear()
        when (mode) {
            MODE_GUEST -> renderGuest()
            MODE_LOGIN -> renderLogin()
            else -> renderRegister()
        }
    }

    private fun renderGuest() {
        form.addView(hint("Можно попробовать приложение без регистрации: квесты, шпаргалки и статистика работают полностью, но не попадут в рейтинг класса."))
        form.addView(field("name", "Имя", "например Ярослав", top = 14))
        form.addView(
            primaryButton("Продолжить как гость") {
                val name = fields["name"]?.text?.toString()?.trim().orEmpty()
                prefs.currentUserId = 0L
                prefs.role = Roles.GUEST
                prefs.devMode = false
                if (name.isNotBlank()) prefs.name = name
                startActivity(Intent(this, OnboardingActivity::class.java))
                finish()
            }
        )
    }

    private fun renderLogin() {
        form.addView(hint("Вход по логину и паролю, которые выдала школа или которые ты указал при регистрации."))
        form.addView(field("login", "Логин", "например ivanov9a", top = 14))
        form.addView(field("password", "Пароль", "пароль", password = true, top = 12))
        form.addView(primaryButton("Войти") { doLogin() })
        form.addView(
            ghostButton("Забыл пароль") {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Восстановление пароля")
                    .setMessage(
                        "Пароль меняет школа: обратись к классному руководителю или в администрацию. " +
                            "Аккаунт создаётся здесь же, на этом устройстве."
                    )
                    .setPositiveButton("Понятно", null)
                    .show()
            }
        )
    }

    private fun doLogin() {
        val login = fields["login"]?.text?.toString()?.trim().orEmpty()
        val password = fields["password"]?.text?.toString().orEmpty()

        if (login.isBlank()) {
            Anim.shake(fields["login"]!!)
            Toast.makeText(this, "Введи логин", Toast.LENGTH_SHORT).show()
            return
        }
        if (password.isBlank()) {
            Anim.shake(fields["password"]!!)
            Toast.makeText(this, "Введи пароль", Toast.LENGTH_SHORT).show()
            return
        }

        if (Developer.matches(login, password)) {
            prefs.currentUserId = 0L
            prefs.role = Roles.DEVELOPER
            prefs.devMode = true
            prefs.name = "Разработчик"
            prefs.onboarded = true
            Toast.makeText(this, "Служебный режим открыт", Toast.LENGTH_SHORT).show()
            openMain()
            return
        }

        val (account, error) = store.login(login, password)
        if (account == null) {
            Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            return
        }
        signIn(account)
    }

    private fun renderRegister() {
        form.addView(
            hint(
                "Аккаунт создаётся сразу на этом устройстве: подтверждать и ждать ничего не нужно."
            )
        )

        form.addView(section("КТО ТЫ", 16))
        form.addView(
            chipsRow(
                "",
                listOf(Roles.STUDENT to "Ученик", Roles.TEACHER to "Учитель"),
                if (role == Roles.STUDENT) 0 else 1
            ) { idx ->
                role = if (idx == 0) Roles.STUDENT else Roles.TEACHER
            }
        )

        form.addView(section("ЛИЧНЫЕ ДАННЫЕ", 18))
        field("full_name", "ФИО", "Иванов Иван Иванович").also { form.addView(it) }
        form.addView(hint("Два или три слова, только буквы."))
        field("birth_date", "Дата рождения", "дд.мм.гг", numeric = true, top = 12) { text ->
            mask("birth_date", Validate.formatDate(text))
        }.also { form.addView(it) }
        form.addView(hint("Строго по образцу: 01.09.09"))
        form.addView(
            chipsRow(
                "ПОЛ",
                listOf(Greetings.MALE to "Мужской", Greetings.FEMALE to "Женский"),
                if (gender == Greetings.MALE) 0 else 1
            ) { idx -> gender = if (idx == 0) Greetings.MALE else Greetings.FEMALE }
        )
        field("phone", "Почта", "pupil@school.ru", numeric = false, top = 12) { text ->
            draft["phone"] = text
        }.also { form.addView(it) }
        form.addView(hint("Например pupil@school.ru — для входа и связи с учителем"))

        form.addView(section("РЕГИОН", 18))
        form.addView(hint("Пока приложение работает по одному региону."))
        form.addView(chipsRow("", listOf(Regions.MOSCOW_REGION to Regions.MOSCOW_REGION), 0) { })

        form.addView(section("НАСЕЛЁННЫЙ ПУНКТ", 18))
        val cityNames = citiesOfRegion()
        if (cityNames.size == 1) {
            form.addView(hint("Пока доступен один город: ${cityNames.first()}."))
            form.addView(chipsRow("", cityNames.map { it to it }, 0) { })
        } else {
            form.addView(
                chipsRow(
                    "ГОРОД ИЛИ СЕЛО",
                    cityNames.map { it to it },
                    cityNames.indexOf(selectedCity).coerceAtLeast(0)
                ) { idx ->
                    selectedCity = cityNames[idx]
                    selectedSchool = ""
                    render()
                }
            )
        }

        form.addView(section("ШКОЛА", 18))
        form.addView(
            hint(
                "Школу выбирают из справочника — придумать свою нельзя. " +
                    "Начни вводить название или адрес и выбери строку из списка."
            )
        )
        val schoolField = field("school", "Поиск школы", "например МБОУ СОШ") { text ->
            refreshSchoolHints(text)
        }
        form.addView(schoolField)
        form.addView(
            hint(
                if (selectedSchool.isBlank()) "Школа не выбрана"
                else "Выбрано: $selectedSchool"
            )
        )
        schoolHintBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        form.addView(schoolHintBox)
        refreshSchoolHints(draft["school"].orEmpty())
        form.addView(
            ghostButton("Нет моей школы — отправить запрос") {
                requestSchool(fields["school"]?.text?.toString().orEmpty())
            }
        )

        if (role == Roles.STUDENT) {
            form.addView(section("КЛАСС", 18))
            val numbers = Grades.numbers
            form.addView(
                chipsRow("", numbers.map { it to it }, numbers.indexOf(grade).coerceAtLeast(0)) { idx ->
                    grade = numbers[idx]
                }
            )
            val letters = Grades.letters
            form.addView(
                chipsRow("БУКВА", letters.map { it to it }, letters.indexOf(letter).coerceAtLeast(0)) { idx ->
                    letter = letters[idx]
                }
            )

            val allSubjects = repo.subjects()
            val byTitle = allSubjects.associateBy { it.title }
            form.addView(section("ПРЕДМЕТЫ, КОТОРЫЕ СДАЁШЬ", 18))
            form.addView(
                hint("Отмеченные предметы появятся в «Квестах». Позже их можно изменить в настройках.")
            )
            form.addView(
                chipsMulti(
                    allSubjects.map { it.title },
                    allSubjects.filter { studentSubjects.contains(it.code) }.map { it.title }.toSet()
                ) { change ->
                    studentSubjects.clear()
                    change.forEach { title -> byTitle[title]?.let { studentSubjects.add(it.code) } }
                    render()
                }
            )
        } else {
            form.addView(section("ДОЛЖНОСТИ", 18))
            form.addView(hint("Можно выбрать несколько. Обязательно хотя бы одну."))
            form.addView(
                chipsMulti(Positions.teacher, teacherPositions) { change ->

                    teacherPositions.clear()
                    teacherPositions.addAll(change)
                    if (teacherPositions.isEmpty()) teacherPositions.add(Positions.SUBJECT)
                    render()
                }
            )

            form.addView(section("ПРЕДМЕТ", 18))
            form.addView(hint("Выбери из списка предметов приложения."))
            val titles = repo.subjects().map { it.title }
            val current = draft["subject"].orEmpty()
            form.addView(
                chipsRow(
                    "", titles.map { it to it },
                    titles.indexOfFirst { it.equals(current, true) }.coerceAtLeast(0)
                ) { idx ->
                    draft["subject"] = titles[idx]
                    render()
                }
            )

            if (teacherPositions.contains(Positions.HOMEROOM)) {
                val numbers = Grades.numbers
                form.addView(
                    chipsRow(
                        "КЛАСС, КОТОРЫЙ ВЕДЁШЬ",
                        numbers.map { it to it },
                        numbers.indexOf(grade).coerceAtLeast(0)
                    ) { idx -> grade = numbers[idx] }
                )
                val letters = Grades.letters
                form.addView(
                    chipsRow("БУКВА КЛАССА", letters.map { it to it }, letters.indexOf(letter).coerceAtLeast(0)) { idx ->
                        letter = letters[idx]
                    }
                )
            } else {
                form.addView(
                    hint(
                        "Поля «класс, который ведёшь» появятся, если выбрать должность " +
                            "«классный руководитель»."
                    )
                )
            }
        }

        form.addView(section("СКОЛЬКО ГОТОВ ЗАНИМАТЬСЯ", 18))
        val goals = if (role == Roles.STUDENT) Goals.studentOptions else Goals.options
        form.addView(
            chipsRow(
                "КВЕСТОВ В ДЕНЬ",
                goals.map { it to Goals.label(it) },
                goals.indexOf(Goals.normalize(goal)).coerceAtLeast(0)
            ) { idx -> goal = goals[idx] }
        )
        form.addView(
            hint(
                if (role == Roles.TEACHER) {
                    "Для учителя это необязательно: «не занимаюсь» — тоже допустимый выбор."
                } else {
                    "Уведомления и цель дня подстроятся под этот темп."
                }
            )
        )

        form.addView(section("ДОСТУП", 18))
        if (role == Roles.TEACHER) {
            val hasCode = selectedSchool.isNotBlank() && schools.codeOf(selectedSchool).isNotEmpty()
            if (hasCode) {
                form.addView(field("school_code", "Код школы", "четыре цифры", numeric = true, top = 4))
                form.addView(hint("Код выдаёт администрация школы. Спроси его у неё."))
            } else {
                form.addView(
                    hint(
                        "Код школы пока не задан — администрация школы его ещё не создала. " +
                            "Аккаунт готов сразу — заявок и ожидания нет."
                    )
                )
            }
        }
        field("login", "Логин", "латиница, цифры, подчёркивание", top = 4).also { form.addView(it) }
        field(
            "password",
            "Пароль",
            "не короче ${Validate.PASSWORD_MIN} символов",
            password = true,
            top = 12
        ) { text -> passwordHint.text = passwordHintText(text) }.also { form.addView(it) }
        passwordHint = hint(
            "Пароль от ${Validate.PASSWORD_MIN} символов: строчные и заглавные буквы, цифры и знак."
        )
        form.addView(passwordHint)

        form.addView(primaryButton("Создать аккаунт") { submit() })
        form.addView(
            ghostButton("У меня уже есть аккаунт") {
                mode = MODE_LOGIN
                buildModeChips()
                render()
            }
        )
    }

    private var selectedSchool = ""

    private val studentSubjects = linkedSetOf<String>()

    private var selectedCity = SchoolSeed.cities.first()
    private var schoolHintBox: LinearLayout? = null

    private lateinit var passwordHint: TextView

    private fun citiesOfRegion(): List<String> =
        if (region == Regions.MOSCOW_REGION) SchoolSeed.cities else SchoolSeed.cities

    private val masking = HashSet<String>()

    private fun mask(key: String, formatted: String) {
        val box = fields[key] ?: return
        if (masking.contains(key)) return
        if (box.text.toString() == formatted) return
        masking.add(key)
        box.setText(formatted)
        // Курсор всегда в конце строки: иначе при вставке точки или дефиса
        // следующая цифра уходила в середину и цифры «перекидывались».
        box.setSelection(formatted.length)
        draft[key] = formatted
        masking.remove(key)
    }

    private fun refreshSchoolHints(query: String) {
        val box = schoolHintBox ?: return
        box.removeAllViews()
        val found = schools.search(query, selectedCity, limit = 12)
        if (found.isEmpty()) {
            box.addView(
                hint(
                    if (query.isBlank()) {
                        val total = schools.all(selectedCity).size
                        if (total == 0) "В этом городе справочник пока пуст"
                        else "Показаны первые школы города. Всего их $total — уточни поиск."
                    } else {
                        "Такой школы нет в справочнике. Отправь запрос — его увидят разработчики."
                    }
                )
            )
            return
        }
        found.forEach { entry ->
            val row = TextView(this).apply {
                text = entry.name + "\n" + listOf(entry.city, entry.address)
                    .filter { it.isNotBlank() }
                    .joinToString(", ")
                textSize = 13.5f
                setTextColor(color(R.color.on_bg))
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = androidx.core.content.ContextCompat.getDrawable(
                    this@AuthActivity, R.drawable.bg_stat
                )
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(6) }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    selectedSchool = entry.name
                    draft["school"] = entry.name
                    render()
                    Anim.pop(this, 0)
                }
            }
            box.addView(row)
        }
    }

    private fun requestSchool(name: String) {
        val trimmed = name.trim()
        if (trimmed.length < 4) {
            Toast.makeText(this, "Напиши название школы подробнее", Toast.LENGTH_LONG).show()
            return
        }
        if (schools.exists(trimmed)) {
            Toast.makeText(this, "Такая школа уже есть в справочнике", Toast.LENGTH_LONG).show()
            return
        }
        val by = draft["full_name"].orEmpty().ifBlank { "не указано" }
        val ok = schools.request(trimmed, selectedCity, by)
        Toast.makeText(
            this,
            if (ok) "Запрос отправлен разработчикам" else "Не удалось отправить запрос",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun submit() {
        fun value(key: String) = fields[key]?.text?.toString()?.trim().orEmpty()

        val fullName = value("full_name")
        val birth = value("birth_date")
        val phone = value("phone")
        val login = value("login")
        val password = value("password")
        val school = selectedSchool.trim()

        val problems = listOf(
            Validate.fullName(fullName),
            if (school.isBlank()) "Выбери школу из справочника" else null,
            if (region.isBlank()) "Выбери регион" else null,
            Validate.birthDate(birth),
            Validate.email(phone),
            if (role == Roles.TEACHER && teacherPositions.isEmpty()) "Выбери хотя бы одну должность" else null,
            if (role == Roles.TEACHER && draft["subject"].orEmpty().isBlank())
                "Выбери предмет из списка" else null,
            if (role == Roles.TEACHER && schools.codeOf(school).isNotEmpty() &&
                !schools.codeMatches(school, value("school_code"))
            ) "Неверный код школы. Уточни его в администрации школы" else null,
            Validate.login(login),
            Validate.password(password),
            if (store.byLogin(login) != null) "Такой логин уже занят" else null
        )
        val problem = problems.firstOrNull { it != null }
        if (problem != null) {
            Toast.makeText(this, problem, Toast.LENGTH_LONG).show()
            return
        }

        val leadingClass = if (role == Roles.TEACHER && teacherPositions.contains(Positions.HOMEROOM))
            Grades.label(grade, letter) else ""

        val account = Account(
            role = role,
            position = if (role == Roles.TEACHER) Positions.join(teacherPositions) else Positions.student.first(),
            fullName = fullName,
            birthDate = Validate.formatDate(birth),
            gender = gender,
            phone = phone.trim(),
            region = region,
            school = school,
            grade = if (role == Roles.STUDENT || leadingClass.isNotBlank()) grade else "",
            letter = if (role == Roles.STUDENT || leadingClass.isNotBlank()) letter else "",
            subject = if (role == Roles.TEACHER) draft["subject"].orEmpty() else "",
            homeroom = leadingClass,
            hasClass = leadingClass.isNotBlank(),
            goal = if (role == Roles.TEACHER) goal else goal,
            login = login,
            password = password
        )

        val (id, error) = store.register(account)
        if (id <= 0L) {
            Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            return
        }

        if (role == Roles.STUDENT) {

            if (studentSubjects.isNotEmpty()) prefs.chosenSubjects = studentSubjects

            runCatching { ClassStore(this).ensure(school, grade, letter) }
        }

        val isAdmin = role == Roles.TEACHER && teacherPositions.contains(Positions.ADMIN)
        val addressee = when {
            isAdmin -> "Разработчикам"
            role == Roles.TEACHER -> "администрацию школы $school"
            else -> "классного руководителя ${Grades.label(grade, letter)}"
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Аккаунт создан")
            .setMessage(
                "Логин: $login\n\nЗаявка ушла в $addressee. " +
                    "Как только её подтвердят, можно будет войти под этим логином.\n\n" +
                    "Аккаунт создаётся сразу: заявок и ожидания нет."
            )
            .setPositiveButton("Понятно") { _, _ ->
                mode = MODE_LOGIN
                buildModeChips()
                render()
            }
            .show()
    }

    private fun signIn(account: Account) {
        prefs.currentUserId = account.id

        runCatching {
            SessionStore(this).note(account.id, account.fullName, DeviceInfo.model(), DeviceInfo.place())
        }
        prefs.role = account.role
        prefs.devMode = false

        prefs.name = account.address(prefs.addressByName).ifBlank { account.fullName }
        prefs.gender = account.gender
        prefs.goalPerDay = account.goal
        if (account.avatar.isNotBlank()) prefs.avatar = account.avatar
        prefs.onboarded = true
        Toast.makeText(this, "Здравствуйте, ${prefs.name}!", Toast.LENGTH_SHORT).show()
        openMain()
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun pickRegion() {
        val list = Regions.list
        MaterialAlertDialogBuilder(this)
            .setTitle("Субъект РФ")
            .setItems(list.toTypedArray()) { _, which ->
                region = list[which]
                render()
            }
            .show()
    }

    private fun color(res: Int) = ContextCompat.getColor(this, res)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun passwordHintText(value: String): String {
        if (value.isEmpty()) {
            return "Пароль от ${Validate.PASSWORD_MIN} символов: строчные и заглавные буквы, цифры и знак."
        }
        val strength = Validate.strength(value)
        val problem = Validate.password(value)
        return "Надёжность: ${strength.label.lowercase()} (${strength.score} из 4) · ${strength.tip}" +
            if (problem == null) "" else " · $problem"
    }

    private fun hint(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(color(R.color.on_bg_variant))
        setPadding(0, dp(8), 0, dp(4))
    }

    private fun section(text: String, top: Int): TextView = TextView(this).apply {
        this.text = text
        textSize = 12f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(color(R.color.accent))
        letterSpacing = 0.08f
        setPadding(0, dp(top), 0, dp(8))
    }

    private fun field(
        key: String,
        label: String,
        placeholder: String,
        top: Int = 0,
        password: Boolean = false,
        numeric: Boolean = false,
        onText: ((String) -> Unit)? = null
    ): LinearLayout {
        val box = EditText(this).apply {
            this.hint = placeholder
            textSize = 15f
            setTextColor(color(R.color.on_bg))
            setHintTextColor(color(R.color.on_bg_variant))
            background = ContextCompat.getDrawable(this@AuthActivity, R.drawable.bg_input)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            minHeight = dp(52)
            inputType = when {
                password -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                numeric -> InputType.TYPE_CLASS_PHONE
                else -> InputType.TYPE_CLASS_TEXT
            }
        }
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (label.isNotEmpty()) {
            wrap.addView(
                TextView(this).apply {
                    text = label
                    textSize = 13f
                    setTextColor(color(R.color.on_bg_variant))
                    setPadding(0, dp(if (top > 0) top else 6), 0, dp(6))
                }
            )
        }
        draft[key]?.let { if (it.isNotEmpty()) box.setText(it) }
        if (onText != null) {
            box.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    draft[key] = s?.toString().orEmpty()
                    onText(s?.toString().orEmpty())
                }
            })
        }
        wrap.addView(box)
        fields[key] = box
        return wrap
    }

    private fun chipsRow(
        label: String,
        options: List<Pair<Any, String>>,
        selected: Int,
        onPick: (Int) -> Unit
    ): LinearLayout {
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (label.isNotEmpty()) wrap.addView(
            TextView(this).apply {
                text = label
                textSize = 13f
                setTextColor(color(R.color.on_bg_variant))
                setPadding(0, dp(12), 0, dp(6))
            }
        )
        val scroll = android.widget.HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        options.forEachIndexed { index, (_, text) ->
            val chip = TextView(this).apply {
                this.text = text
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(10), dp(14), dp(10))
                background = ContextCompat.getDrawable(this@AuthActivity, R.drawable.bg_chip_choice)
                setTextColor(ContextCompat.getColorStateList(this@AuthActivity, R.color.chip_text))
                isSelected = index == selected
                isClickable = true
                setOnClickListener {
                    onPick(index)
                    render()
                }
            }
            row.addView(
                chip,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(8) }
            )
        }
        scroll.addView(row, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        wrap.addView(scroll)
        return wrap
    }

    private fun chipsMulti(
        options: List<String>,
        selected: Set<String>,
        onToggle: (Set<String>) -> Unit
    ): LinearLayout {
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        options.chunked(1).forEach { pair ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(6), 0, 0)
            }
            pair.forEach { option ->
                val on = selected.contains(option)
                val chip = TextView(this).apply {
                    text = option
                    textSize = 13f
                    gravity = Gravity.CENTER
                    maxLines = 1
                    includeFontPadding = false
                    setPadding(dp(14), 0, dp(14), 0)
                    background = ContextCompat.getDrawable(this@AuthActivity, R.drawable.bg_chip_choice)
                    setTextColor(ContextCompat.getColorStateList(this@AuthActivity, R.color.chip_text))
                    isSelected = on
                    isClickable = true
                    isFocusable = true
                    val lp = LinearLayout.LayoutParams(0, dp(44), 1f)
                    lp.marginEnd = dp(8)
                    layoutParams = lp
                    setOnClickListener {
                        val next = LinkedHashSet(selected)
                        if (!next.add(option)) next.remove(option)
                        onToggle(next)
                        Anim.pop(this, 0)
                    }
                }
                row.addView(chip)
            }

            wrap.addView(row)
        }
        return wrap
    }

    private fun primaryButton(text: String, onClick: () -> Unit): MaterialButton =
        MaterialButton(this).apply {
            this.text = text
            textSize = 15f
            minHeight = dp(52)
            insetTop = 0
            insetBottom = 0
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(18) }
            setOnClickListener { onClick() }
        }

    private fun ghostButton(text: String, onClick: () -> Unit): MaterialButton =
        MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            this.text = text
            textSize = 14f
            minHeight = dp(48)
            insetTop = 0
            insetBottom = 0
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
            setOnClickListener { onClick() }
        }

    private companion object {
        const val MODE_GUEST = 0
        const val MODE_LOGIN = 1
        const val MODE_REGISTER = 2
    }
}
