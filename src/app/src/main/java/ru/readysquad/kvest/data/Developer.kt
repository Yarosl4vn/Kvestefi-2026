package ru.readysquad.kvest.data

object Developer {

    const val LOGIN = "developer"

    private const val ACCESS_CODE = "a3Zlc3QyMDI2"

    const val SCHOOL_CODE = "4525"

    private val accessCode: String
        get() = String(android.util.Base64.decode(ACCESS_CODE, android.util.Base64.DEFAULT))

    fun matches(login: String, password: String): Boolean =
        login.trim().equals(LOGIN, ignoreCase = true) && password == accessCode
}

object SeedSchools {

    val typical: List<String> = listOf(
        "Школа № 1", "Школа № 2", "Школа № 3", "Школа № 5", "Школа № 7",
        "Гимназия № 1", "Гимназия № 2", "Лицей № 1", "Лицей № 2",
        "Средняя общеобразовательная школа № 1", "СОШ № 4", "Лицей № 15"
    )
}
