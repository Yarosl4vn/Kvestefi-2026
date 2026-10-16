package ru.readysquad.kvest.data

interface TaskSource {

    val name: String

    val isOffline: Boolean

    fun load(subjectCode: String, limit: Int): List<Task>
}

class OfflineTaskSource(private val repository: Repository) : TaskSource {

    override val name: String = "Встроенный банк"
    override val isOffline: Boolean = true

    override fun load(subjectCode: String, limit: Int): List<Task> =
        repository.shuffledTasks(subjectCode, limit)
}
