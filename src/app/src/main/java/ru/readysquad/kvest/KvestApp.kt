package ru.readysquad.kvest

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import ru.readysquad.kvest.data.Prefs
import ru.readysquad.kvest.data.Repository
import ru.readysquad.kvest.data.Sdamgia

class KvestApp : Application() {

    override fun onCreate() {
        super.onCreate()

        CrashLog.install(this)
        val prefs = Prefs(this)
        AppCompatDelegate.setDefaultNightMode(prefs.themeMode)

        runCatching { Repository.get(this) }.onFailure {
            android.util.Log.e("Kvestefi", "База не открылась при запуске", it)
        }

        // Кэш заданий из открытого банка: чтобы квесты работали и без интернета.
        Sdamgia.attach(this)
    }
}
