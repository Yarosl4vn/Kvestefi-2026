package ru.readysquad.kvest.data

data class RealTask(
    val id: Long,
    val subjectCode: String,
    val exam: String,
    val topic: String,
    val number: String,
    val condition: String,
    val answer: String,
    val solution: String,
    val image: String?,
    val url: String
) : java.io.Serializable {
    val hasImage: Boolean get() = !image.isNullOrBlank()
}
