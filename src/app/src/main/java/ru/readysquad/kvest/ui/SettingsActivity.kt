package ru.readysquad.kvest.ui

import android.content.Intent
import android.graphics.Typeface
import android.text.InputType
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ScrollView
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import ru.readysquad.kvest.R
import ru.readysquad.kvest.util.Avatars
import ru.readysquad.kvest.util.Palettes
import ru.readysquad.kvest.data.AccountStore
import ru.readysquad.kvest.data.Goals
import ru.readysquad.kvest.data.DeviceInfo
import ru.readysquad.kvest.data.Prefs
import ru.readysquad.kvest.data.SessionStore
import ru.readysquad.kvest.util.Validate
import ru.readysquad.kvest.data.Repository
import ru.readysquad.kvest.data.SeedData
import ru.readysquad.kvest.data.Subject
import ru.readysquad.kvest.databinding.ActivitySettingsBinding
import ru.readysquad.kvest.databinding.ItemOnboardSubjectBinding
import ru.readysquad.kvest.util.Anim
import ru.readysquad.kvest.util.Greetings
import ru.readysquad.kvest.util.Icons
import ru.readysquad.kvest.util.SubjectIcons

class SettingsActivity : AppCompatActivity() {

    private lateinit var b: ActivitySettingsBinding
    private val prefs by lazy { Prefs(this) }
    private val repo by lazy { Repository.get(this) }

    private val store by lazy { AccountStore(this) }
    private var subjects: List<Subject> = emptyList()

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        val name = Avatars.save(this, uri)
        if (name == null) {
            Toast.makeText(this, "Не удалось загрузить изображение", Toast.LENGTH_SHORT).show()
        } else {
            prefs.avatarImage = name
            updateAvatarPreview()
            Toast.makeText(this, "Своя иконка сохранена", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Palettes.apply(this, Prefs(this).palette)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)

        subjects = repo.subjects()

        b.btnBack.setOnClickListener { finish() }
        b.btnDone.setOnClickListener {
            saveToAccount()
            finish()
        }
        b.btnProfile.setOnClickListener {
            saveToAccount()
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        b.btnHelp.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Помощь")
                .setMessage(
                    "Квесты — короткая работа по предмету с четырьмя вариантами ответа.\n\n" +
                        "Обучение — шпаргалки по темам; отмеченная тема ставится в кнопку «Продолжить подготовку».\n\n" +
                        "Результаты — история попыток, точность и слабые темы.\n\n" +
                        "Достижения — открываются за серию, точность и скорость.\n\n" +
                        "Профиль — школа, класс, рейтинг, сообщения и связь с учителями."
                )
                .setPositiveButton("Понятно", null)
                .show()
        }
        b.btnReset.setOnClickListener { confirmReset() }

        b.nameInput.setText(prefs.name)
        b.nameInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val v = s?.toString()?.trim().orEmpty()
                if (v.isNotEmpty()) prefs.name = v
            }
        })

        b.themeSwitch.isChecked = prefs.themeMode == AppCompatDelegate.MODE_NIGHT_YES
        b.hintsSwitch.isChecked = prefs.soundHints

        b.themeSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.saveThemeMode(
                if (checked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
            AppCompatDelegate.setDefaultNightMode(prefs.themeMode)
        }
        b.hintsSwitch.setOnCheckedChangeListener { _, checked -> prefs.soundHints = checked }

        b.btnPickImage.setOnClickListener { pickImage.launch("image/*") }
        b.btnClearImage.setOnClickListener {
            Avatars.clear(this)
            prefs.avatarImage = null
            updateAvatarPreview()
            Toast.makeText(this, "Своя иконка убрана", Toast.LENGTH_SHORT).show()
        }

        buildAvatars()
        buildGender()
        buildGoals()
        buildAddress()
        buildPalette()
        buildSources()
        buildSubjects()
        buildTopics()
        buildSections()
        updateAvatarPreview()
        updateAbout()

        Anim.fadeInUp(b.nameInput, 80)
    }

    private fun buildAvatars() {
        b.avatarGrid.removeAllViews()
        val size = dp(54)
        SeedData.avatarChoices.forEach { emoji ->
            val tv = TextView(this).apply {
                text = emoji
                textSize = 23f
                gravity = Gravity.CENTER
                background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.bg_avatar_choice)
                isSelected = emoji == prefs.avatar
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    prefs.avatar = emoji

                    if (prefs.avatarImage != null) {
                        Avatars.clear(this@SettingsActivity)
                        prefs.avatarImage = null
                        updateAvatarPreview()
                    }
                    buildAvatars()
                    Anim.pop(this, 0)
                }
            }
            val lp = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            )
                lp.width = size
            lp.height = size
            lp.setMargins(dp(5), dp(5), dp(5), dp(5))
            tv.layoutParams = lp
            b.avatarGrid.addView(tv)
        }
    }

    private fun buildGoals() {
        b.goalRow.removeAllViews()
        Goals.options.forEach { value ->
            val chip = TextView(this).apply {
                text = Goals.label(value)
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(13), dp(6), dp(13))
                background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.bg_chip_choice)
                setTextColor(ContextCompat.getColorStateList(this@SettingsActivity, R.color.chip_text))
                isSelected = value == prefs.goalPerDay
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    prefs.goalPerDay = value
                    saveToAccount()
                    buildGoals()
                    Anim.pop(this, 0)
                }
            }
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.setMargins(dp(5), 0, dp(5), 0)
            chip.layoutParams = lp
            b.goalRow.addView(chip)
        }
    }

    private fun buildAddress() {
        b.addressRow.removeAllViews()
        val options = listOf(true to "По имени", false to "По фамилии")
        options.forEach { (value, label) ->
            val chip = TextView(this).apply {
                text = label
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(13), dp(6), dp(13))
                background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.bg_chip_choice)
                setTextColor(ContextCompat.getColorStateList(this@SettingsActivity, R.color.chip_text))
                isSelected = prefs.addressByName == value
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    prefs.addressByName = value
                    val account = prefs.currentUserId.takeIf { it > 0L }?.let { store.byId(it) }
                    if (account != null) {
                        prefs.name = account.address(value).ifBlank { account.fullName }
                        b.nameInput.setText(prefs.name)
                    }
                    buildAddress()
                    Anim.pop(this, 0)
                }
            }
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.setMargins(dp(5), 0, dp(5), 0)
            chip.layoutParams = lp
            b.addressRow.addView(chip)
        }
    }

    private fun saveToAccount() {
        val id = prefs.currentUserId
        if (id <= 0L) return
        val account = store.byId(id) ?: return
        store.updateProfile(
            id = id,
            fullName = account.fullName,
            avatar = prefs.avatar,
            about = account.about,
            goal = prefs.goalPerDay,
            gender = prefs.gender
        )
    }

    private fun buildGender() {
        b.genderRow.removeAllViews()
        val options = listOf(
            Greetings.MALE to ("Мужской" to R.drawable.ic_card_male),
            Greetings.FEMALE to ("Женский" to R.drawable.ic_card_female)
        )
        options.forEach { (value, data) ->
            val (label, icon) = data
            val chip = TextView(this).apply {
                text = label
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(13), dp(6), dp(13))
                background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.bg_chip_choice)
                setTextColor(ContextCompat.getColorStateList(this@SettingsActivity, R.color.chip_text))
                isSelected = prefs.gender == value
                isClickable = true
                isFocusable = true
                Icons.start(this, icon, R.color.chip_text, 15)
                setOnClickListener {
                    prefs.gender = value
                    buildGender()
                    Anim.pop(this, 0)
                }
            }
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.setMargins(dp(5), 0, dp(5), 0)
            chip.layoutParams = lp
            b.genderRow.addView(chip)
        }
    }

    private fun buildSources() {
        b.sourceRow.removeAllViews()
        val options = listOf(
            Prefs.LEARN_STEPIK to "Курсы Stepik",
            Prefs.LEARN_WIKI to "Википедия"
        )
        options.forEach { (value, label) ->
            val chip = TextView(this).apply {
                text = label
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(13), dp(6), dp(13))
                background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.bg_chip_choice)
                setTextColor(ContextCompat.getColorStateList(this@SettingsActivity, R.color.chip_text))
                isSelected = value == prefs.learnSource
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    prefs.learnSource = value
                    buildSources()
                    Anim.pop(this, 0)
                }
            }
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.setMargins(dp(5), 0, dp(5), 0)
            chip.layoutParams = lp
            b.sourceRow.addView(chip)
        }
    }

    private fun buildSubjects() {
        b.subjectBox.removeAllViews()
        val chosen = prefs.chosenSubjects.toMutableSet()
        subjects.forEach { s ->
            val row = ItemOnboardSubjectBinding.inflate(layoutInflater, b.subjectBox, false)
            row.onboardSubjectIcon.setImageResource(SubjectIcons.of(s.code))
            row.onboardSubjectTitle.text = s.title
            row.onboardSubjectTopics.text = s.topics.replace(", ", " · ")
            val apply = {
                val on = chosen.add(s.code).let { if (it) true else { chosen.remove(s.code); false } }
                row.root.isSelected = on
                row.onboardSubjectCheck.visibility = if (on) View.VISIBLE else View.INVISIBLE
                prefs.chosenSubjects = chosen.toSet()

                buildTopics()
                Anim.release(row.root)
            }
            val initiallyOn = chosen.contains(s.code)
            row.root.isSelected = initiallyOn
            row.onboardSubjectCheck.visibility = if (initiallyOn) View.VISIBLE else View.INVISIBLE
            row.root.setOnClickListener { apply() }
            b.subjectBox.addView(row.root)
        }
    }

    private fun buildTopics() {
        b.topicBox.removeAllViews()
        val focus = prefs.focusTopics.toMutableSet()
        val codes = if (prefs.chosenSubjects.isEmpty()) null else prefs.chosenSubjects
        val subjects = repo.subjects().filter { codes == null || codes.contains(it.code) }

        subjects.forEach { subject ->
            val topics = SeedData.topicsBySubject[subject.code].orEmpty()
            if (topics.isEmpty()) return@forEach

            b.topicBox.addView(
                TextView(this).apply {
                    text = subject.title
                    textSize = 14f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.on_bg))
                    setPadding(dp(2), dp(14), 0, dp(6))
                    Icons.start(this, SubjectIcons.of(subject.code), R.color.on_bg, 15)
                }
            )

            val grid = GridLayout(this).apply { columnCount = 2 }
            topics.forEach { topic ->
                val chip = TextView(this).apply {
                    text = topic
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setPadding(dp(10), dp(9), dp(10), dp(9))
                    background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.bg_topic_choice)
                    setTextColor(ContextCompat.getColorStateList(this@SettingsActivity, R.color.chip_text))
                    isSelected = focus.contains(topic)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        if (!focus.add(topic)) focus.remove(topic)
                        isSelected = focus.contains(topic)
                        prefs.focusTopics = focus.toSet()
                        Anim.pop(this, 0)
                    }
                }
                val lp = GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f)
                )
                lp.width = 0
                lp.setMargins(dp(4), dp(4), dp(4), dp(4))
                chip.layoutParams = lp
                grid.addView(chip)
            }
            b.topicBox.addView(grid)
        }
    }

    private fun updateAbout() {
        val stats = repo.stats()
        b.dataHint.text = "Пройдено квестов: ${stats.quests} · очков: ${stats.totalScore} · " +
            "открыто бейджей: ${repo.unlockedCount()} из ${repo.badges().size}.\n" +
            "Вся статистика хранится только на устройстве, в локальной базе SQLite."
        b.about.text = "Квестефи " + ru.readysquad.kvest.CrashLog.appVersion(this) + "\n" +
            "Реальные задания приходят из открытого банка в интернете и не сохраняются в приложении. " +
            "Без интернета работают ${SeedData.tasks.size} встроенных заданий, генератор тренировки, " +
            "теория и статистика.\n" +
            "Официального API у ФИПИ нет: открытые банки заданий — это веб-приложения " +
            "без публичного интерфейса. Свой источник подключается через интерфейс TaskSource."
    }

    private fun confirmReset() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Сбросить прогресс?")
            .setMessage("История, очки, бейджи и настройки будут очищены. Действие необратимо.")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Сбросить") { _, _ ->
                repo.resetAll()
                OnboardingDraft.reset()
                prefs.bestStreak = 0
                prefs.bestDayStreak = 0
                prefs.streak = 0
                prefs.lastActiveDay = ""
                prefs.activeDays = emptySet()
                prefs.learnedTopics = emptySet()
                prefs.onboarded = false
                startActivity(Intent(this, OnboardingActivity::class.java))

                finishAffinity()
            }
            .show()
    }

    private fun buildPalette() {
        b.paletteRow.removeAllViews()
        val size = dp(44)
        Palettes.all.forEach { item ->
            val color = ContextCompat.getColor(this, item.swatch)
            val selected = prefs.palette == item.key
            val swatch = View(this).apply {
                background = if (selected) {
                    ringDrawable(color, ContextCompat.getColor(this@SettingsActivity, R.color.on_bg))
                } else {
                    circleDrawable(color)
                }
                isClickable = true
                isFocusable = true
                contentDescription = item.title
                setOnClickListener {
                    if (prefs.palette == item.key) return@setOnClickListener
                    prefs.palette = item.key

                    recreate()
                }
            }
            val lp = LinearLayout.LayoutParams(size, size)
            lp.marginEnd = dp(10)
            swatch.layoutParams = lp
            b.paletteRow.addView(swatch)
        }
        b.paletteHint.text = "Текущий цвет: ${Palettes.titleOf(prefs.palette)}"
    }

    private fun circleDrawable(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    private fun ringDrawable(color: Int, ring: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke(dp(3), ring)
    }

    private fun updateAvatarPreview() {
        val bitmap = Avatars.load(this, prefs.avatarImage)
        if (bitmap != null) {
            b.avatarPreview.setImageBitmap(bitmap)
            b.avatarPreview.visibility = View.VISIBLE
            b.btnClearImage.visibility = View.VISIBLE
        } else {
            b.avatarPreview.visibility = View.GONE
            b.btnClearImage.visibility = View.GONE
        }
    }

    private fun buildSections() {
        val column = settingsColumn() ?: return
        val aboutCard = b.about.parent as? View ?: return
        val at = column.indexOfChild(aboutCard).coerceAtLeast(0)
        column.addView(privacyCard(), at)
        column.addView(dataCard(), at + 1)
        column.addView(securityCard(), at + 2)
    }

    private fun settingsColumn(): LinearLayout? {
        var view: View? = b.about
        while (view != null) {
            if (view.parent is ScrollView) return view as? LinearLayout
            view = view.parent as? View
        }
        return null
    }

    private fun sectionCard(title: String): Pair<MaterialCardView, LinearLayout> {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }
        box.addView(
            TextView(this).apply {
                text = title
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.06f
                setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.accent))
            }
        )
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(14) }
            radius = dp(20).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(ContextCompat.getColor(this@SettingsActivity, R.color.surface_container))
            addView(box)
        }
        return card to box
    }

    private fun sectionSwitch(
        box: LinearLayout,
        title: String,
        subtitle: String,
        checked: Boolean,
        onChange: (Boolean) -> Unit
    ) {
        box.addView(
            TextView(this).apply {
                text = title
                textSize = 14.5f
                setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.on_bg))
                setPadding(0, dp(12), 0, 0)
            }
        )
        box.addView(
            TextView(this).apply {
                text = subtitle
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.on_bg_variant))
            }
        )
        box.addView(
            MaterialSwitch(this).apply {
                isChecked = checked
                setPadding(0, dp(4), 0, 0)
                setOnCheckedChangeListener { _, value -> onChange(value) }
            }
        )
    }

    private fun sectionAction(box: LinearLayout, title: String, subtitle: String, onClick: () -> Unit) {
        box.addView(
            TextView(this).apply {
                text = title
                textSize = 14.5f
                setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.on_bg))
                setPadding(0, dp(14), 0, 0)
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
            }
        )
        box.addView(
            TextView(this).apply {
                text = subtitle
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.on_bg_variant))
            }
        )
    }

    private fun privacyCard(): MaterialCardView {
        val (card, box) = sectionCard("КОНФИДЕНЦИАЛЬНОСТЬ")
        sectionSwitch(
            box,
            "Участвовать в рейтинге класса",
            "Если выключить, тебя не будет в списке класса по очкам.",
            prefs.visibleInRating
        ) { prefs.visibleInRating = it }
        sectionSwitch(
            box,
            "Показывать мой профиль другим",
            "ФИО, школа и класс видны одноклассникам и учителям.",
            prefs.publicProfile
        ) { prefs.publicProfile = it }
        box.addView(
            TextView(this).apply {
                text = "Оценки за задания классный руководитель видит всегда — это часть учёбы."
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.on_bg_variant))
                setPadding(0, dp(12), 0, 0)
            }
        )
        return card
    }

    private fun dataCard(): MaterialCardView {
        val (card, box) = sectionCard("ДАННЫЕ")
        val size = dbSizeText()
        box.addView(
            TextView(this).apply {
                text = "Объём данных: $size"
                textSize = 13f
                setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.on_bg))
                setPadding(0, dp(12), 0, 0)
            }
        )
        box.addView(
            TextView(this).apply {
                text = "Здесь хранятся попытки, шпаргалки, задания и переписка."
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.on_bg_variant))
            }
        )
        sectionAction(
            box, "Очистить журнал входов",
            "Убирает записи о том, с каких устройств заходили в аккаунт."
        ) {
            SessionStore(this).clear(prefs.currentUserId)
            Toast.makeText(this, "Журнал входов очищен", Toast.LENGTH_SHORT).show()
        }
        return card
    }

    private fun securityCard(): MaterialCardView {
        val (card, box) = sectionCard("БЕЗОПАСНОСТЬ")
        box.addView(
            TextView(this).apply {
                text = "Сменить пароль"
                textSize = 14.5f
                setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.on_bg))
                setPadding(0, dp(12), 0, 0)
            }
        )
        val first = passwordField("Новый пароль")
        val second = passwordField("Повтори пароль")
        box.addView(
            first,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        )
        box.addView(
            second,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        )
        box.addView(
            MaterialButton(this).apply {
                text = "Сменить пароль"
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(10) }
                setOnClickListener {
                    val a = first.text.toString()
                    val b2 = second.text.toString()
                    if (a != b2) {
                        Toast.makeText(this@SettingsActivity, "Пароли не совпадают", Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                    val problem = Validate.password(a)
                    if (problem != null) {
                        Toast.makeText(this@SettingsActivity, problem, Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                    val id = prefs.currentUserId
                    val ok = if (id > 0L) store.setPassword(id, a) else false
                    Toast.makeText(
                        this@SettingsActivity,
                        if (ok) "Пароль изменён" else "Пароль меняется только у аккаунта в приложении",
                        Toast.LENGTH_LONG
                    ).show()
                    first.setText("")
                    second.setText("")
                }
            }
        )
        sectionAction(
            box, "Выйти со всех устройств",
            "Журнал входов очистится: на других устройствах нужно будет войти заново."
        ) {
            val id = prefs.currentUserId
            SessionStore(this).clear(id)
            SessionStore(this).note(id, prefs.name, DeviceInfo.model(), DeviceInfo.place())
            Toast.makeText(this, "Выполнен выход со всех устройств", Toast.LENGTH_SHORT).show()
        }
        sectionAction(
            box, "Аккаунт школы",
            "Вход или регистрация учителя и ученика: класс, задания, рейтинг. Гостевой профиль можно оставить как есть."
        ) {
            saveToAccount()
            prefs.currentUserId = 0L
            prefs.devMode = false
            prefs.onboarded = false
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
        }
        return card
    }

    private fun passwordField(hintText: String): EditText {
        return EditText(this).apply {
            hint = hintText
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.bg_input)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.on_bg))
            setHintTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.on_bg_variant))
        }
    }

    private fun dbSizeText(): String {
        val file = getDatabasePath("kvest_marathon.db")
        if (!file.exists()) return "пусто"
        val kb = file.length() / 1024.0
        return if (kb < 1024) String.format("%.0f КБ", kb) else String.format("%.1f МБ", kb / 1024)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
