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

    /** Where a photo lives on disk, e.g. volume "external_primary", path "Pictures/Cats/". */
    fun folderOf(uri: Uri): PhotoFolder? =
        context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.VOLUME_NAME, MediaStore.MediaColumns.RELATIVE_PATH),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val volume = cursor.getString(0) ?: return@use null
            val path = cursor.getString(1) ?: return@use null
            PhotoFolder(volume, path)
        }
}

data class PhotoFolder(val volume: String, val relativePath: String)
