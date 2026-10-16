package ru.readysquad.kvest.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ru.readysquad.kvest.CrashLog
import ru.readysquad.kvest.R
import ru.readysquad.kvest.util.Greetings
import ru.readysquad.kvest.util.Notifier
import ru.readysquad.kvest.util.Palettes
import ru.readysquad.kvest.data.Prefs
import ru.readysquad.kvest.data.Repository
import ru.readysquad.kvest.databinding.ActivityMainBinding
import ru.readysquad.kvest.databinding.DialogHelloBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val prefs by lazy { Prefs(this) }
    private val repo by lazy { Repository.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Palettes.apply(this, Prefs(this).palette)

        // Первый запуск: без входа и регистрации — сразу короткая настройка профиля.
        if (!prefs.onboarded) {

            prefs.currentUserId = 0L
            prefs.role = ru.readysquad.kvest.data.Roles.GUEST
            prefs.devMode = false
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNav.setOnItemSelectedListener { item ->
            show(item.itemId)

            updateNavBadge()
            true
        }
        binding.bottomNav.setOnItemReselectedListener { item ->
            (supportFragmentManager.findFragmentByTag(tagOf(item.itemId)) as? Refreshable)?.refresh()
        }

        val requested = when (intent?.getStringExtra(EXTRA_TAB)) {
            TAB_BADGES -> R.id.tab_badges
            TAB_LEARN -> R.id.tab_learn
            TAB_RESULTS -> R.id.tab_results
            TAB_QUESTS -> R.id.tab_quests
            else -> 0
        }
        val startItem = when {
            savedInstanceState != null -> binding.bottomNav.selectedItemId.takeIf { it != 0 } ?: R.id.tab_home
            requested != 0 -> requested
            else -> R.id.tab_home
        }

        runCatching { showLastCrash() }.onFailure { logIt("отчёт о падении", it) }

        binding.bottomNav.selectedItemId = startItem
        show(startItem)

        runCatching { updateNavBadge() }.onFailure { logIt("метка достижений", it) }

        if (savedInstanceState == null) {
            runCatching { showGreeting() }.onFailure { logIt("приветствие", it) }
        }
    }

    private fun logIt(where: String, error: Throwable) {
        android.util.Log.e("Kvestefi", "Сбой в разделе «$where»", error)
    }

    private fun showLastCrash() {
        val text = CrashLog.lastCrash(this) ?: return
        val view = android.widget.TextView(this).apply {
            this.text = text
            textSize = 11f
            setPadding(24, 16, 24, 8)
        }
        val scroll = android.widget.ScrollView(this).apply { addView(view) }
        MaterialAlertDialogBuilder(this)
            .setTitle("Прошлый запуск завершился ошибкой")
            .setView(scroll)
            .setPositiveButton("Скопировать") { _, _ ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Ошибка Квестефи", text))
                android.widget.Toast.makeText(this, "Текст скопирован", android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Скрыть") { _, _ -> CrashLog.clear(this) }
            .show()
    }

    private fun updateNavBadge() {
        val fresh = Notifier.newBadges(prefs, repo.badges())
        if (fresh.isEmpty()) {
            binding.bottomNav.removeBadge(R.id.tab_badges)
        } else {
            binding.bottomNav.getOrCreateBadge(R.id.tab_badges).apply {
                backgroundColor = ContextCompat.getColor(this@MainActivity, R.color.danger)
                badgeTextColor = android.graphics.Color.WHITE
                maxCharacterCount = 2
            }
        }
    }

    private fun showGreeting() {
        val view = DialogHelloBinding.inflate(layoutInflater)
        val stats = repo.stats()
        view.helloTitle.text = Greetings.helloTitle(prefs.name, prefs.gender, stats.quests)
        view.helloText.text = Greetings.helloText(prefs.gender, stats.quests, prefs.streak)
        view.helloStreak.text = Greetings.streakLine(prefs.streak, prefs.bestDayStreak)

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(view.root)
            .create()
        view.helloButton.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    override fun onResume() {
        super.onResume()

        if (!::binding.isInitialized) return
        val id = binding.bottomNav.selectedItemId
        (supportFragmentManager.findFragmentByTag(tagOf(id)) as? Refreshable)?.refresh()
        updateNavBadges()
    }

    fun openTab(itemId: Int) {
        binding.bottomNav.selectedItemId = itemId
        show(itemId)
    }

    private fun tagOf(itemId: Int): String = when (itemId) {
        R.id.tab_quests -> "quests"
        R.id.tab_learn -> "learn"
        R.id.tab_results -> "results"
        R.id.tab_badges -> "badges"
        else -> "home"
    }

    private fun show(itemId: Int) {
        val tag = tagOf(itemId)
        val fm = supportFragmentManager
        val tx = fm.beginTransaction().setCustomAnimations(
            android.R.anim.fade_in, android.R.anim.fade_out
        )
        fm.fragments.forEach { tx.hide(it) }

        val existing = fm.findFragmentByTag(tag)
        if (existing == null) {
            tx.add(R.id.navHost, newFragment(tag), tag)
        } else {
            tx.show(existing)
        }
        tx.commit()
        fm.executePendingTransactions()
        (fm.findFragmentByTag(tag) as? Refreshable)?.refresh()
    }

    companion object {
        const val EXTRA_TAB = "tab"
        const val TAB_BADGES = "badges"
        const val TAB_LEARN = "learn"
        const val TAB_RESULTS = "results"
        const val TAB_QUESTS = "quests"
    }

    private fun newFragment(tag: String): Fragment = when (tag) {
        "quests" -> SubjectsFragment()
        "learn" -> LearnFragment()
        "results" -> ResultsFragment()
        "badges" -> BadgesFragment()
        else -> HomeFragment()
    }

    private fun updateNavBadges() {
        val nav = binding.bottomNav
        val unlocked = repo.unlockedCount()
        val total = repo.badges().size

        if (unlocked in 1 until total) {
            nav.getOrCreateBadge(R.id.tab_badges).apply {
                number = unlocked
                isVisible = true
            }
        } else {
            nav.removeBadge(R.id.tab_badges)
        }
    }
}
