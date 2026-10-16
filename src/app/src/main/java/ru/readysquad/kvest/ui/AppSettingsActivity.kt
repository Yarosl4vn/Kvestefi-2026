package ru.readysquad.kvest.ui

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import ru.readysquad.kvest.CrashLog
import ru.readysquad.kvest.R
import ru.readysquad.kvest.data.Prefs
import ru.readysquad.kvest.util.Chips
import ru.readysquad.kvest.util.Palettes
import ru.readysquad.kvest.util.Screens

/**
 * Настройки приложения — отдельный экран. Настройки профиля остались
 * в отдельном разделе, чтобы не смешивать оформление и личные данные.
 */
class AppSettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var ui: Screens

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        Palettes.apply(this, prefs.palette)
        super.onCreate(savedInstanceState)
        ui = Screens(this)
        ui.install("Настройки приложения") { finish() }
        render()
    }

    private fun rebuild() {
        ui.body.removeAllViews()
        ui.header("Настройки приложения") { finish() }
        render()
    }

    private fun render() {
        ui.card("Цвет приложения") {
            addView(
                ui.text(
                    "Цвет применяется ко всему приложению: кнопкам, чипсам, таймеру, " +
                        "графику результатов и клеткам для ответа.",
                    12f, R.color.on_bg_variant
                )
            )
            addView(
                Chips.scrollRow(
                    this@AppSettingsActivity,
                    Palettes.all.map { it.title to (it.key == prefs.palette) }
                ) { index ->
                    prefs.palette = Palettes.all[index].key
                    recreate()
                }
            )
            addView(
                ui.text("Сейчас: " + Palettes.titleOf(prefs.palette), 12f, R.color.on_bg_variant, top = 6)
            )
        }

        ui.card("Тема и подсказки") {
            addView(
                switchRow("Тёмная тема", prefs.themeMode == AppCompatDelegate.MODE_NIGHT_YES) { on ->
                    prefs.saveThemeMode(
                        if (on) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                    )
                    AppCompatDelegate.setDefaultNightMode(prefs.themeMode)
                }
            )
            addView(
                switchRow("Показывать разбор ответа", prefs.soundHints) { prefs.soundHints = it }
            )
        }

        ui.card("Уведомления") {
            addView(
                ui.text(
                    "Выключенный раздел не показывается в «Уведомлениях».",
                    12f, R.color.on_bg_variant
                )
            )
            addView(switchRow("Важные", prefs.notifyImportant) { prefs.notifyImportant = it })
            addView(switchRow("Учебные", prefs.notifyStudy) { prefs.notifyStudy = it })
            addView(switchRow("Системные", prefs.notifySystem) { prefs.notifySystem = it })
        }

        ui.card("Данные") {
            addView(
                ui.text(
                    "Вся статистика хранится только на устройстве, в локальной базе SQLite, " +
                        "и никуда не отправляется. Смена цвета или темы прогресс не затрагивает.",
                    13f, R.color.on_bg
                )
            )
            addView(
                ui.text(
                    "Обнулить достижения и очки может разработчик — в профиле, разделе «Разработчикам».",
                    12f, R.color.on_bg_variant, top = 8
                )
            )
        }

        ui.card("О приложении") {
            addView(ui.kv("Версия", CrashLog.appVersion(this@AppSettingsActivity)))
            addView(ui.kv("Команда", "ReadySquad"))
            addView(ui.kv("Предметов", "11, заданий — встроенный банк и открытый банк ОГЭ и ЕГЭ"))
            addView(
                ui.text(
                    "Приложение работает и без интернета: встроенные задания, шпаргалки, " +
                        "тренировка и статистика доступны офлайн.",
                    12f, R.color.on_bg_variant, top = 10
                )
            )
            addView(
                ui.button("Открыть профиль", primary = false) {
                    startActivity(Intent(this@AppSettingsActivity, ProfileActivity::class.java))
                }
            )
        }
    }

    private fun switchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, ui.dp(10), 0, ui.dp(10))
        }
        row.addView(
            TextView(this).apply {
                text = label
                textSize = 15f
                setTextColor(ContextCompat.getColor(this@AppSettingsActivity, R.color.on_bg))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        row.addView(
            SwitchCompat(this).apply {
                isChecked = checked
                setOnCheckedChangeListener { _, value -> onChange(value) }
            }
        )
        return row
    }
}
