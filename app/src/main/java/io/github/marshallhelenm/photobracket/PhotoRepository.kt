package io.github.marshallhelenm.photobracket

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

class PhotoRepository(private val context: Context) {

    fun loadImageIds(): List<Long> {
        val ids = mutableListOf<Long>()
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            null,
            null,
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) ids.add(cursor.getLong(idColumn))
        }
        return ids
    }

    fun uriFor(id: Long): Uri =
        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

    /** Deletes directly. Only usable on Android 10 and below (legacy storage + WRITE_EXTERNAL_STORAGE). */
    fun deleteLegacy(uris: List<Uri>) {
        uris.forEach { uri ->
            runCatching { context.contentResolver.delete(uri, null, null) }
        }
    }
}
