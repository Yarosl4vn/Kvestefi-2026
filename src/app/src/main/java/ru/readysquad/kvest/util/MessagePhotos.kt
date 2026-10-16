package ru.readysquad.kvest.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/** Фотографии из переписки: хранятся в файлах приложения, в базе лежит только имя файла. */
object MessagePhotos {

    private const val FOLDER = "chat"
    private const val MAX_SIDE = 1280

    fun save(context: Context, uri: Uri): String? = try {
        val probe = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, probe) }
        if (probe.outWidth <= 0 || probe.outHeight <= 0) {
            null
        } else {
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(probe.outWidth, probe.outHeight)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
            if (decoded == null) {
                null
            } else {
                val scaled = scaleDown(decoded)
                val dir = File(context.filesDir, FOLDER).apply { mkdirs() }
                val name = "msg_" + System.currentTimeMillis() + ".jpg"
                FileOutputStream(File(dir, name)).use { out ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
                }
                if (scaled !== decoded) scaled.recycle()
                decoded.recycle()
                name
            }
        }
    } catch (e: OutOfMemoryError) {
        null
    } catch (e: Exception) {
        null
    }

    fun load(context: Context, name: String?): Bitmap? {
        if (name.isNullOrBlank()) return null
        val file = File(File(context.filesDir, FOLDER), name)
        if (!file.exists()) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            null
        }
    }

    fun delete(context: Context, name: String?) {
        if (name.isNullOrBlank()) return
        runCatching { File(File(context.filesDir, FOLDER), name).delete() }
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        var longest = maxOf(width, height)
        while (longest / 2 >= MAX_SIDE) {
            longest /= 2
            sample *= 2
        }
        return sample
    }

    private fun scaleDown(source: Bitmap): Bitmap {
        val longest = maxOf(source.width, source.height)
        if (longest <= MAX_SIDE) return source
        val ratio = MAX_SIDE.toFloat() / longest
        return Bitmap.createScaledBitmap(
            source,
            (source.width * ratio).toInt().coerceAtLeast(1),
            (source.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }
}
