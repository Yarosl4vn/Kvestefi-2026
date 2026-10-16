package ru.readysquad.kvest.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import ru.readysquad.kvest.R
import ru.readysquad.kvest.data.Attempt
import ru.readysquad.kvest.data.Repository
import ru.readysquad.kvest.databinding.FragmentResultsBinding
import ru.readysquad.kvest.util.Anim
import ru.readysquad.kvest.util.Dialogs
import ru.readysquad.kvest.util.Icons

class ResultsFragment : Fragment(R.layout.fragment_results), Refreshable {

    private var _b: FragmentResultsBinding? = null
    private val b get() = _b!!
    private val repo by lazy { Repository.get(requireContext()) }
    private val adapter = HistoryAdapter(emptyList()) { attempt -> openAttempt(attempt) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _b = FragmentResultsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.historyList.layoutManager = LinearLayoutManager(requireContext())
        b.historyList.adapter = adapter
        refresh()
    }

    override fun refresh() {
        val bind = _b ?: return
        val stats = repo.stats()

        bind.miniScore.text = stats.totalScore.toString()
        bind.miniBest.text = stats.bestScore.toString()
        bind.miniAccuracy.text = "${stats.accuracy}%"

        val points = repo.progress()
        bind.chart.setData(points)

        val attempts = repo.attempts(30)
        val recent = attempts.take(3)
        val older = attempts.drop(3).take(3)
        if (recent.isEmpty()) {
            bind.trendChip.text = "нет данных"
            bind.trendChip.setCompoundDrawables(null, null, null, null)
            bind.trendChip.setTextColor(ContextCompat.getColor(requireContext(), R.color.on_bg_variant))
        } else if (older.isEmpty()) {
            bind.trendChip.text = "старт"
            Icons.start(bind.trendChip, R.drawable.ic_card_rocket, R.color.secondary, 14)
            bind.trendChip.setTextColor(ContextCompat.getColor(requireContext(), R.color.secondary))
        } else {
            val r = recent.map { it.accuracy }.average()
            val o = older.map { it.accuracy }.average()
            val diff = (r - o).toInt()
            when {
                diff > 3 -> {
                    bind.trendChip.text = "растём +$diff%"
                    Icons.start(bind.trendChip, R.drawable.ic_card_up, R.color.success, 14)
                    bind.trendChip.setTextColor(ContextCompat.getColor(requireContext(), R.color.success))
                }
                diff < -3 -> {
                    bind.trendChip.text = "сдаём $diff%"
                    Icons.start(bind.trendChip, R.drawable.ic_card_down, R.color.danger, 14)
                    bind.trendChip.setTextColor(ContextCompat.getColor(requireContext(), R.color.danger))
                }
                else -> {
                    bind.trendChip.text = "ровно"
                    Icons.start(bind.trendChip, R.drawable.ic_subj_info, R.color.accent, 14)
                    bind.trendChip.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent))
                }
            }
        }

        adapter.submit(attempts)
        bind.historyEmpty.visibility = if (attempts.isEmpty()) View.VISIBLE else View.GONE

        Anim.fadeInUp(bind.chart, 100)
    }

    private fun openAttempt(attempt: Attempt) {
        Dialogs.attempt(
            requireContext(),
            attempt,
            repo.attemptAnswers(attempt.id),
            attempt.subjectTitle
        ) { openSubjectStats(attempt.subjectCode, attempt.subjectTitle) }
    }

    private fun openSubjectStats(code: String, title: String) {
        val attempts = repo.attempts(60).filter { it.subjectCode == code }
        Dialogs.subjectStats(
            requireContext(),
            title,
            attempts,
            repo.topicStats(code),
            onAttempt = { a -> Dialogs.attempt(requireContext(), a, repo.attemptAnswers(a.id), title) },
            subjectCode = code,
            onDrill = { topic -> startTopicDrill(code, title, topic) }
        )
    }

    /** Работа над слабой темой: квест идёт строго по этой теме. */
    private fun startTopicDrill(code: String, title: String, topic: String) {
        val subject = repo.subject(code)
        startActivity(
            android.content.Intent(requireContext(), QuizActivity::class.java)
                .putExtra(QuizActivity.EXTRA_SUBJECT, code)
                .putExtra(QuizActivity.EXTRA_TITLE, title)
                .putExtra(QuizActivity.EXTRA_EMOJI, subject?.emoji ?: "📘")
                .putExtra(QuizActivity.EXTRA_TOPIC, topic)
                .putExtra(QuizActivity.EXTRA_TOPIC_DRILL, true)
        )
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }
}
