package io.github.marshallhelenm.photobracket.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.widget.Toast
import io.github.marshallhelenm.photobracket.PhotoFolder
import io.github.marshallhelenm.photobracket.R

/** Things the user can do with a single photo from the full-size viewer. */
object PhotoActions {

    private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

    fun copy(context: Context, uri: Uri) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(
            ClipData.newUri(context.contentResolver, context.getString(R.string.photo_clip_label), uri),
        )
        // Android 13+ shows its own "copied" confirmation.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
        }
    }

    fun share(context: Context, uri: Uri) {
        val send = Intent(Intent.ACTION_SEND)
            .setType(context.contentResolver.getType(uri) ?: "image/*")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, null))
    }

    /**
     * Asks the Files app to open the folder holding the photo. Android has no standard
     * "reveal file" action, so if nothing handles it we just say where the photo is.
     */
    fun showInFolder(context: Context, folder: PhotoFolder?) {
        if (folder == null) {
            Toast.makeText(context, R.string.folder_unknown, Toast.LENGTH_SHORT).show()
            return
        }
        // External storage document IDs look like "primary:Pictures/Cats" (or "1A2B-3C4D:..." for SD cards).
        val root = if (folder.volume == MediaStore.VOLUME_EXTERNAL_PRIMARY) "primary" else folder.volume.uppercase()
        val documentUri = DocumentsContract.buildDocumentUri(
            EXTERNAL_STORAGE_AUTHORITY,
            "$root:${folder.relativePath.trimEnd('/')}",
        )
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(documentUri, DocumentsContract.Document.MIME_TYPE_DIR)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(view)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                context,
                context.getString(R.string.folder_location, folder.relativePath.trimEnd('/')),
                Toast.LENGTH_LONG,
            ).show()
        }
    }
}
