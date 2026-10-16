package ru.readysquad.kvest.util

import android.app.Activity
import androidx.annotation.ColorRes
import androidx.annotation.StyleRes
import ru.readysquad.kvest.R

object Palettes {

    class Item(
        val key: Int,
        val title: String,
        @ColorRes val swatch: Int,
        @StyleRes val theme: Int?
    )

    val all: List<Item> = listOf(
        Item(0, "Фиолетовый", R.color.pal_violet_primary, null),
        Item(1, "Изумрудный", R.color.pal_emerald_primary, R.style.ThemeKvestEmerald),
        Item(2, "Океан", R.color.pal_ocean_primary, R.style.ThemeKvestOcean),
        Item(3, "Янтарь", R.color.pal_amber_primary, R.style.ThemeKvestAmber),
        Item(4, "Роза", R.color.pal_rose_primary, R.style.ThemeKvestRose),
        Item(5, "Индиго", R.color.pal_indigo_primary, R.style.ThemeKvestIndigo),
        Item(6, "Графит", R.color.pal_graphite_primary, R.style.ThemeKvestGraphite)
    )

    @StyleRes
    fun themeFor(key: Int): Int? = all.firstOrNull { it.key == key }?.theme

    fun apply(activity: Activity, key: Int) {
        themeFor(key)?.let { activity.setTheme(it) }
    }

    fun titleOf(key: Int): String = all.firstOrNull { it.key == key }?.title ?: all.first().title
}
