package ru.readysquad.kvest

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLog {

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { save(context.applicationContext, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun save(context: Context, error: Throwable) {
        val text = StringWriter()
        error.printStackTrace(PrintWriter(text))
        val report = buildString {
            append("Время: ")
            append(SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale("ru")).format(Date()))
            append("\nAndroid: ")
            append(Build.VERSION.RELEASE)
            append(" (API ")
            append(Build.VERSION.SDK_INT)
            append(")\nУстройство: ")
            append(Build.MANUFACTURER)
            append(" ")
            append(Build.MODEL)
            append("\nВерсия приложения: ")
            append(appVersion(context))
            append("\n\n")
            append(text.toString())
        }
        file(context).writeText(report)
    }

    fun appVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    }.getOrDefault("?")

    private const val FILE_NAME = "last_crash.txt"

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun lastCrash(context: Context): String? {
        val f = file(context)
        return if (f.exists() && f.length() > 0) f.readText() else null
    }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }
}
