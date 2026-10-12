package io.github.marshallhelenm.photobracket

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A folder photos can be sorted into, at `Pictures/<name>/` on disk. */
data class Folder(
    val id: String,
    val name: String,
    val emoji: String,
    val starred: Boolean,
) {
    val relativePath: String get() = "Pictures/$name/"
}

/** How many folders can be starred, i.e. shown beside each photo on the main screen. */
const val MAX_STARRED_FOLDERS = 5

/** Starter folders, offered the first time sorting is turned on. */
val SUGGESTED_FOLDERS = listOf(
    "Cats" to "🐱",
    "Dogs" to "🐶",
    "Places" to "🏞️",
    "People" to "🧑",
    "Memes" to "😂",
)

enum class FolderNameProblem { Blank, Slash, LeadingDot, TooLong, Duplicate }

/** Why [name] can't be used as a folder name, or null if it's fine. [others] excludes the folder being edited. */
fun folderNameProblem(name: String, others: List<Folder>): FolderNameProblem? {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() -> FolderNameProblem.Blank
        '/' in trimmed -> FolderNameProblem.Slash
        trimmed.startsWith('.') -> FolderNameProblem.LeadingDot
        trimmed.length > 40 -> FolderNameProblem.TooLong
        others.any { it.name.equals(trimmed, ignoreCase = true) } -> FolderNameProblem.Duplicate
        else -> null
    }
}

class FolderStore(context: Context) {

    private val prefs = context.getSharedPreferences("folders", Context.MODE_PRIVATE)

    fun load(): List<Folder> {
        val json = prefs.getString(KEY_FOLDERS, null) ?: return emptyList()
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val folder = array.getJSONObject(i)
            Folder(
                id = folder.getString("id"),
                name = folder.getString("name"),
                emoji = folder.getString("emoji"),
                starred = folder.getBoolean("starred"),
            )
        }
    }

    fun save(folders: List<Folder>) {
        val array = JSONArray()
        folders.forEach { folder ->
            array.put(
                JSONObject()
                    .put("id", folder.id)
                    .put("name", folder.name)
                    .put("emoji", folder.emoji)
                    .put("starred", folder.starred),
            )
        }
        prefs.edit { putString(KEY_FOLDERS, array.toString()) }
    }

    /** Adds the suggested folders the first time, so deleting them all doesn't bring them back. */
    fun seedSuggestionsOnce(): List<Folder> {
        if (prefs.getBoolean(KEY_SEEDED, false)) return load()
        val folders = load() + SUGGESTED_FOLDERS.map { (name, emoji) -> newFolder(name, emoji, starred = true) }
        save(folders)
        prefs.edit { putBoolean(KEY_SEEDED, true) }
        return folders
    }

    private companion object {
        const val KEY_FOLDERS = "folders"
        const val KEY_SEEDED = "seeded"
    }
}

fun newFolder(name: String, emoji: String, starred: Boolean) =
    Folder(id = UUID.randomUUID().toString(), name = name.trim(), emoji = emoji, starred = starred)
