package ru.readysquad.kvest.lan

import android.content.Context

object WebPage {

    private const val PATH = "web/index.html"

    const val STUDENT_PATH = PATH
    const val TEACHER_PATH = "web/teacher.html"

    fun load(context: Context, path: String = PATH): String = try {
        context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
    } catch (e: Exception) {
        android.util.Log.e("KvestefiLan", "Не открылся $path", e)
        FALLBACK
    }

    private val FALLBACK = """
        <!DOCTYPE html><html lang="ru"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Квестефи</title></head>
        <body style="font:16px system-ui;padding:24px">
        <h1>Квестефи</h1>
        <p>Страница класса не найдена в сборке приложения.</p>
        <p>Задания по-прежнему можно получить в приложении, введя адрес учителя.</p>
        </body></html>
    """.trimIndent()
}
