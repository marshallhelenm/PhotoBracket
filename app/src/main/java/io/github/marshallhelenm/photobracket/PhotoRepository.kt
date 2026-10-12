package io.github.marshallhelenm.photobracket

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

class PhotoRepository(private val context: Context) {

    val contentResolver get() = context.contentResolver

    fun loadImages(): List<LibraryPhoto> {
        val photos = mutableListOf<LibraryPhoto>()
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.RELATIVE_PATH),
            null,
            null,
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            while (cursor.moveToNext()) {
                photos.add(LibraryPhoto(cursor.getLong(idColumn), cursor.getString(pathColumn).orEmpty()))
            }
        }
        return photos
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

/**
 * Moves a photo into [relativePath] (e.g. "Pictures/Cats/") by updating MediaStore, which moves
 * the file on disk. Needs a write grant from [android.provider.MediaStore.createWriteRequest] for
 * photos this app didn't create. Returns whether the move happened.
 */
fun PhotoRepository.moveTo(uri: Uri, relativePath: String): Boolean = runCatching {
    val values = ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath) }
    contentResolver.update(uri, values, null, null) > 0
}.getOrDefault(false)

/** A photo in the library and the folder it's in, e.g. "DCIM/Camera/". */
data class LibraryPhoto(val id: Long, val relativePath: String)

/** A folder on the phone that holds photos, for choosing which ones to leave out. */
data class LibraryFolder(val relativePath: String, val photoCount: Int)

data class PhotoFolder(val volume: String, val relativePath: String)
