package ru.readysquad.kvest.ui

import android.content.Intent
import android.widget.Toast
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import ru.readysquad.kvest.R
import ru.readysquad.kvest.data.Prefs
import ru.readysquad.kvest.data.Repository
import ru.readysquad.kvest.data.Sdamgia
import ru.readysquad.kvest.data.Subject
import ru.readysquad.kvest.databinding.FragmentSubjectsBinding
import ru.readysquad.kvest.util.Anim
import ru.readysquad.kvest.util.Chips
import ru.readysquad.kvest.util.SubjectIcons

class SubjectsFragment : Fragment(R.layout.fragment_subjects), Refreshable {

    private var _b: FragmentSubjectsBinding? = null
    private val b get() = _b!!
    private val repo by lazy { Repository.get(requireContext()) }

    private var category = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _b = FragmentSubjectsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.subjectList.layoutManager = LinearLayoutManager(requireContext())
        buildCategories()
        refresh()
    }

    private fun buildCategories() {
        b.categoryRow.removeAllViews()
        val items = listOf("Квесты" to 0, "Реальные задания" to 1)
        items.forEach { (label, id) ->

            val chip = Chips.create(requireContext(), label, id == category) { view ->
                if (category == id) return@create
                category = id
                buildCategories()
                refresh()
                Anim.pop(view, 0)
            }
            b.categoryRow.addView(chip)
        }
    }

    override fun onResume() {
        super.onResume()

        runCatching { refresh() }
    }

    override fun refresh() {
        val bind = _b ?: return
        val real = category == 1

        val chosen = Prefs(requireContext()).chosenSubjects

        // Показываем все предметы: выбранные при настройке — первыми, остальные ниже.
        val subjects = repo.subjects().sortedByDescending { chosen.contains(it.code) }
        val onlineCodes = subjects.filter { Sdamgia.hasSource(it.code) }.map { it.code }.toSet()

        bind.modeHint.text = if (real) {
            "Настоящие задания открытого банка: рисунки, разбор и ссылка на источник. " +
                "Ответ вписывается в клетки, как в бланке ответов."
        } else {
            "Задания берём из открытого банка: настоящие формулировки ОГЭ и ЕГЭ с рисунками и разбором. " +
                "Без интернета включится встроенный набор, а «Тренировка» собирает задания прямо на устройстве."
        }

        bind.subjectList.adapter = SubjectAdapter(
            items = subjects,
            best = repo.bestScores(),
            runs = repo.questsBySubject(),
            realMode = real,
            onlineCodes = onlineCodes,
            onShortfall = { subject ->
                Toast.makeText(
                    requireContext(),
                    "По этому предмету онлайн-заданий с кратким ответом нет — открываем встроенный набор",
                    Toast.LENGTH_LONG
                ).show()
            },
            onClick = { subject -> if (real) startReal(subject) else startQuiz(subject, train = false) },
            onTrain = { subject -> startQuiz(subject, train = true) }
        )

        val topics = repo.topicStats(null).filter { it.total > 0 }.take(3)
        bind.topicBox.removeAllViews()
        if (topics.isEmpty()) {
            bind.topicHint.text = "Пока нет данных — пройди первый квест, и я покажу слабые темы."
            bind.topicHint.visibility = View.VISIBLE
        } else {
            bind.topicHint.text = "Темы, где точность ниже всего:"
            bind.topicHint.visibility = View.VISIBLE
            topics.forEachIndexed { i, t ->
                val row = TextView(requireContext()).apply {
                    text = "${t.topic} — ${t.accuracy}%  (${t.correct} из ${t.total})"
                    setTextColor(
                        ContextCompat.getColor(
                            requireContext(),
                            if (t.accuracy < 50) R.color.danger else R.color.on_bg
                        )
                    )
                    textSize = 14f
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(6), 0, dp(6))
                }
                row.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
                bind.topicBox.addView(row)
                Anim.fadeInUp(row, 70L * i, 16f)
            }
        }
    }

    private fun startQuiz(subject: Subject, train: Boolean) {
        startActivity(
            Intent(requireContext(), QuizActivity::class.java)
                .putExtra(QuizActivity.EXTRA_SUBJECT, subject.code)
                .putExtra(QuizActivity.EXTRA_TITLE, subject.title)
                .putExtra(QuizActivity.EXTRA_EMOJI, subject.emoji)
                .putExtra(QuizActivity.EXTRA_TRAIN, train)
                .putExtra(QuizActivity.EXTRA_ONLINE, !train && Sdamgia.hasSource(subject.code))
        )
    }

    private fun startReal(subject: Subject) {
        startActivity(
            Intent(requireContext(), RealTasksActivity::class.java)
                .putExtra(RealTasksActivity.EXTRA_SUBJECT, subject.code)
        )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }
}
