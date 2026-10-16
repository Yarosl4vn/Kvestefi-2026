package ru.readysquad.kvest.data

import android.content.ContentValues
import android.content.Context

data class SchoolEntry(
    val name: String,
    val city: String,
    val address: String = "",
    val kind: String = "",
    val addedBy: String = ""
) {

    val caption: String get() = listOf(name, city).plus(
        if (address.isBlank()) emptyList() else listOf(address)
    ).joinToString(" · ")
}

object SchoolSeed {

    val cities = listOf("Мытищи")

    val schools = listOf(
        SchoolEntry("МБОУ «Гимназия № 1»", "Мытищи", "ул. Индустриальная, д. 7, корп. 2", "муниципальная"),
        SchoolEntry("МБОУ «Лицей № 2»", "Мытищи", "1-й Щёлковский проезд, д. 9", "муниципальная"),
        SchoolEntry("МБОУ «СОШ № 3 им. Б. А. Феофанова»", "Мытищи", "ул. Академика Каргина, д. 36, корп. 2а", "муниципальная"),
        SchoolEntry("МБОУ «СОШ № 4»", "Мытищи", "ул. Лётная, д. 30, корп. 2", "муниципальная"),
        SchoolEntry("МБОУ «СОШ № 5»", "Мытищи", "ул. 1-я Крестьянская, д. 14", "муниципальная"),
        SchoolEntry("МБОУ «СОШ № 6»", "Мытищи", "Новомытищинский проспект, д. 38; корпус 1 — Новомытищинский пр-т, д. 74", "муниципальная"),
        SchoolEntry("МБОУ «СОШ № 7»", "Мытищи", "Школьный переулок, д. 3", "муниципальная"),
        SchoolEntry("МБОУ «СОШ № 8»", "Мытищи", "ул. Мира, д. 22", "муниципальная"),
        SchoolEntry("МБОУ «СОШ № 9»", "Мытищи", "Силикатная ул., д. 33", "муниципальная"),
        SchoolEntry("МБОУ «СОШ № 10 им. А. К. Астрахова»", "Мытищи", "Новомытищинский проспект, д. 88, корп. 5", "муниципальная"),
        SchoolEntry("МБОУ «СОШ № 12»", "Мытищи", "Октябрьская ул., д. 4а", "муниципальная"),
        SchoolEntry("МБОУ «Школа для обучающихся с ограниченными возможностями здоровья» (школа № 13)", "Мытищи", "ул. Селезнёва, д. 30", "коррекционная"),
        SchoolEntry("МБОУ «СОШ № 14»", "Мытищи", "Шараповская ул., д. 6а, корп. 2", "муниципальная"),
        SchoolEntry("МБОУ «Лицей № 15»", "Мытищи", "ул. 2-я Институтская, д. 2", "муниципальная"),
        SchoolEntry("МБОУ «Гимназия № 16»", "Мытищи", "ул. Белобородова, д. 5", "муниципальная"),
        SchoolEntry("МБОУ «Гимназия № 17»", "Мытищи", "ул. Щербакова, д. 1а", "муниципальная"),
        SchoolEntry("МБОУ «СОШ № 24 им. 9-й Гвардейской Краснознаменной стрелковой дивизии»", "Мытищи", "Новомытищинский проспект, д. 82, корп. 10", "муниципальная"),
        SchoolEntry("МБОУ «СОШ № 25»", "Мытищи", "Новомытищинский проспект, д. 39, корп. 3", "муниципальная"),
        SchoolEntry("МБОУ «Школа № 26»", "Мытищи", "Юбилейная ул., д. 13, корп. 2", "муниципальная"),
        SchoolEntry("МБОУ «СОШ № 27»", "Мытищи", "Юбилейная ул., д. 42, корп. 2", "муниципальная"),
        SchoolEntry("МБОУ «СОШ № 28»", "Мытищи", "ул. Колпакова, д. 34", "муниципальная"),
        SchoolEntry("МБОУ «СОШ № 31»", "Мытищи", "ул. Борисовка, стр. 6", "муниципальная"),
        SchoolEntry("МБОУ «СОШ № 32»", "Мытищи", "Юбилейная ул., стр. 6А", "муниципальная"),
        SchoolEntry("МБОУ «Многопрофильная лингвистическая гимназия № 33»", "Мытищи", "проспект Астрахова, д. 8А", "муниципальная"),
        SchoolEntry("МБОУ «Лицей № 34»", "Мытищи", "ул. Мира, стр. 39А", "муниципальная"),
        SchoolEntry("МБОУ «СОШ № 35 им. В. В. Рюмина»", "Мытищи", "Тенистый бульвар, стр. 4", "муниципальная"),
        SchoolEntry("МБОУ «СОШ № 36 „Новые горизонты“»", "Мытищи", "п. Бородино, ул. Малая Бородинская, стр. 3А; корпус — п. Нагорное, ул. Полковника Романова, стр. 4", "муниципальная"),
        SchoolEntry("ЧОУ «Классика-М» (школа-гимназия)", "Мытищи", "Новомытищинский проспект, д. 32А", "частная"),
        SchoolEntry("ЧОУ «Логос М»", "Мытищи", "ул. Лётная, д. 16, корп. 1", "частная"),
        SchoolEntry("ОАНО «Школа ГЕЛИОС»", "Мытищи", "ул. Кропоткина, д. 2А", "частная"),
        SchoolEntry("АНОО «Школа Вектор»", "Мытищи", "ул. Лётная, стр. 21Б; Новомытищинский пр-т, д. 26А", "частная"),
        SchoolEntry("Прогимназия «Академическая Гимназия»", "Мытищи", "ул. Лётная, стр. 21Б", "частная"),
        SchoolEntry("Общеобразовательное отделение при Московском кооперативном техникуме им. Г. Н. Альтшуля", "Мытищи", "3-я Крестьянская ул., д. 17", "частная/при СПО"),
        SchoolEntry("МБОУ «СОШ № 29»", "Мытищи", "д. Беляниново, ул. Московская, д. 19", "муниципальная"),
        SchoolEntry("МБОУ «Поведниковская СОШ»", "Мытищи", "пос. Поведники", "муниципальная"),
        SchoolEntry("МБОУ «СОШ № 19»", "Мытищи", "пос. Пирогово (мкр. Пироговский), ул. Тимирязева, стр. 7", "муниципальная"),
        SchoolEntry("МБОУ «Марфинская СОШ» им. В. Ф. Бутузова", "Мытищи", "с. Марфино, ул. Зелёная, вл. 8, стр. 1; мкр. Сухарево, Школьная, вл. 18; с. Федоскино, Лукутинская, стр. 23", "муниципальная"),
        SchoolEntry("ОАНО «Гимназия имени Петра Первого»", "Мытищи", "пос. Нагорное, д. 9", "частная"),
        SchoolEntry("АНО ОСШ «Город Солнца»", "Мытищи", "пос. Нагорное, ул. Центральная, стр. 23", "частная")
    )
}

class SchoolStore(private val context: Context) {

    private val db = DbHelper(context).writableDatabase

    fun all(city: String = ""): List<SchoolEntry> {
        val added = ArrayList<SchoolEntry>()
        db.rawQuery(
            "SELECT name, city, address, added_by FROM schools ORDER BY name",
            null
        ).use { c ->
            while (c.moveToNext()) {
                added.add(
                    SchoolEntry(
                        c.getString(0) ?: "", c.getString(1) ?: "",
                        c.getString(2) ?: "", "", c.getString(3) ?: ""
                    )
                )
            }
        }
        return (SchoolSeed.schools + added)
            .distinctBy { it.name.lowercase() }
            .filter { city.isBlank() || it.city.equals(city, true) }
            .sortedBy { it.name }
    }

    fun search(query: String, city: String = "", limit: Int = 40): List<SchoolEntry> {
        val q = query.trim()
        return all(city).filter { entry ->
            q.isBlank() ||
                entry.name.contains(q, true) ||
                entry.city.contains(q, true) ||
                entry.address.contains(q, true)
        }.take(limit)
    }

    fun exists(name: String): Boolean = all().any { it.name.equals(name.trim(), true) }

    fun addByDeveloper(name: String, city: String, address: String, by: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || exists(trimmed)) return false
        val id = db.insert("schools", null, ContentValues().apply {
            put("name", trimmed)
            put("city", city.trim())
            put("address", address.trim())
            put("added_by", by)
            put("created_at", System.currentTimeMillis())
        })
        return id > 0
    }

    fun removeByDeveloper(name: String): Boolean =
        db.delete("schools", "name = ?", arrayOf(name.trim())) > 0

    fun request(name: String, city: String, byName: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        val id = db.insert("school_requests", null, ContentValues().apply {
            put("name", trimmed)
            put("city", city.trim())
            put("by_name", byName)
            put("status", "pending")
            put("created_at", System.currentTimeMillis())
        })
        return id > 0
    }

    fun requests(): List<Triple<String, String, String>> {
        val out = ArrayList<Triple<String, String, String>>()
        db.rawQuery(
            "SELECT name, city, by_name FROM school_requests WHERE status = 'pending' ORDER BY created_at DESC",
            null
        ).use { c ->
            while (c.moveToNext()) {
                out.add(Triple(c.getString(0) ?: "", c.getString(1) ?: "", c.getString(2) ?: ""))
            }
        }
        return out
    }

    fun decideRequest(name: String, approve: Boolean): Boolean {
        if (approve) {
            val row = requests().firstOrNull { it.first == name } ?: return false
            addByDeveloper(row.first, row.second, "", "разработчик")
        }
        return db.delete("school_requests", "name = ?", arrayOf(name)) > 0
    }

    fun codeOf(school: String): String {
        db.rawQuery("SELECT code FROM school_codes WHERE school = ?", arrayOf(school)).use { c ->
            if (c.moveToFirst()) return c.getString(0) ?: ""
        }
        return ""
    }

    fun setCode(school: String, code: String, by: String): Boolean {
        if (school.isBlank()) return false
        if (!code.matches(Regex("\\d{4}"))) return false
        db.execSQL("DELETE FROM school_codes WHERE school = ?", arrayOf<Any>(school))
        val id = db.insert("school_codes", null, ContentValues().apply {
            put("school", school)
            put("code", code)
            put("created_by", by)
            put("created_at", System.currentTimeMillis())
        })
        return id > 0
    }

    fun codeMatches(school: String, code: String): Boolean {
        val current = codeOf(school)
        if (current.isEmpty()) return true
        return current == code.trim()
    }
}
