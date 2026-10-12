package io.github.marshallhelenm.photobracket

import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BracketUiState(
    val isLoading: Boolean = true,
    val pair: Pair<Uri, Uri>? = null,
    val canUndo: Boolean = false,
    val trash: List<Uri> = emptyList(),
    val keptCount: Int = 0,
    val settings: Settings = Settings(),
    val folders: List<Folder> = emptyList(),
    /** Folder ID each photo in [pair] will be sorted into if kept. */
    val pairFolders: Map<Uri, String> = emptyMap(),
    /** Kept photos waiting to be moved, with the ID of the folder each goes into. */
    val sortQueue: List<Pair<Uri, String>> = emptyList(),
    /** Every folder on the phone holding photos, for choosing which to leave out. */
    val libraryFolders: List<LibraryFolder> = emptyList(),
)

/** Outcome of moving queued photos into their folders. */
data class SortResult(val moved: Int, val failed: Int)

class BracketViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PhotoRepository(application)
    private val prefs = application.getSharedPreferences("bracket", Context.MODE_PRIVATE)
    private var library: List<LibraryPhoto> = emptyList()
    private var libraryFolders: List<LibraryFolder> = emptyList()
    private var bracket: Bracket? = null
    private val settingsStore = SettingsStore(application)
    private var settings = settingsStore.load()
    private val folderStore = FolderStore(application)
    private var folders = folderStore.load()

    var uiState by mutableStateOf(BracketUiState(settings = settings, folders = folders))
        private set

    private val sortResultChannel = Channel<SortResult>(Channel.BUFFERED)
    val sortResults: Flow<SortResult> = sortResultChannel.receiveAsFlow()

    private val deleteRequestChannel = Channel<List<Uri>>(Channel.BUFFERED)

    /** Photos to delete right away, emitted when [Settings.deleteImmediately] is on. */
    val deleteRequests: Flow<List<Uri>> = deleteRequestChannel.receiveAsFlow()

    /** Reads the photo library. Only does work the first time, so rotating doesn't reshuffle the pair. */
    fun load() {
        if (bracket != null) return
        viewModelScope.launch {
            library = withContext(Dispatchers.IO) { repository.loadImages() }
            libraryFolders = library.groupingBy { it.relativePath }.eachCount()
                .map { (path, count) -> LibraryFolder(path, count) }
                .sortedBy { it.relativePath.lowercase() }
            rebuild()
        }
    }

    fun keep(winner: Uri) = decide { keep(ContentUris.parseId(winner)) }

    fun keepBoth() = decide { keepBoth() }

    fun keepNeither() = decide { keepNeither() }

    fun undo() = update { undo() }

    fun restore(uri: Uri) = update { restore(ContentUris.parseId(uri)) }

    fun restoreAll() = update { restoreAll() }

    /** Call once photos have been deleted (or trashed by the system) so they're no longer shown. */
    fun onDeleted(uris: List<Uri>) = update { forget(uris.map(ContentUris::parseId)) }


    /** Tags a photo in the current pair with a folder to move it into if kept; null clears it. */
    fun tag(uri: Uri, folderId: String?) = update { tag(ContentUris.parseId(uri), folderId) }

    fun unqueue(uri: Uri) = update { unqueue(ContentUris.parseId(uri)) }

    /** Moves [uris] into their queued folders. Call after Android grants write access to them. */
    fun applySort(uris: List<Uri>) {
        val queue = bracket?.sortQueue ?: return
        val folderPaths = folders.associate { it.id to it.relativePath }
        viewModelScope.launch {
            val moved = withContext(Dispatchers.IO) {
                uris.filter { uri ->
                    val path = queue[ContentUris.parseId(uri)]?.let(folderPaths::get)
                    path != null && repository.moveTo(uri, path)
                }
            }
            update { onSorted(moved.map(ContentUris::parseId)) }
            sortResultChannel.trySend(SortResult(moved = moved.size, failed = uris.size - moved.size))
        }
    }

    fun setFolderExcluded(relativePath: String, excluded: Boolean) {
        val folders = if (excluded) settings.excludedFolders + relativePath else settings.excludedFolders - relativePath
        updateSettings(settings.copy(excludedFolders = folders))
    }

    /** Leaves out every folder on the phone, or brings them all back. */
    fun setAllFoldersExcluded(excluded: Boolean) {
        val folders = if (excluded) libraryFolders.map { it.relativePath }.toSet() else emptySet()
        updateSettings(settings.copy(excludedFolders = folders))
    }

    fun setSortingEnabled(enabled: Boolean) {
        if (enabled) setFolders(folderStore.seedSuggestionsOnce())
        updateSettings(settings.copy(sortingEnabled = enabled))
    }

    fun addFolder(name: String, emoji: String) {
        val starred = folders.count { it.starred } < MAX_STARRED_FOLDERS
        setFolders(folders + newFolder(name, emoji, starred))
    }

    fun updateFolder(folder: Folder) = setFolders(folders.map { if (it.id == folder.id) folder else it })

    fun setStarred(folderId: String, starred: Boolean) {
        if (starred && folders.count { it.starred } >= MAX_STARRED_FOLDERS) return
        setFolders(folders.map { if (it.id == folderId) it.copy(starred = starred) else it })
    }

    fun deleteFolder(folderId: String) {
        bracket?.forgetFolder(folderId)
        setFolders(folders.filterNot { it.id == folderId })
    }

    private fun setFolders(new: List<Folder>) {
        folders = new
        folderStore.save(new)
        if (bracket == null) uiState = uiState.copy(folders = new) else publish()
    }

    /** Forgets which photos were kept, so they all come up again even with [Settings.includeKept] off. */
    fun forgetKept() {
        prefs.edit { remove(KEY_KEPT) }
        if (bracket != null) rebuild()
    }

    fun setIncludeKept(include: Boolean) = updateSettings(settings.copy(includeKept = include))

    fun setDeleteImmediately(immediately: Boolean) =
        updateSettings(settings.copy(deleteImmediately = immediately))

    fun setReminderEnabled(enabled: Boolean) = updateSettings(settings.copy(reminderEnabled = enabled))

    fun setReminderRandom(random: Boolean) = updateSettings(settings.copy(reminderRandom = random))

    fun setReminderTime(minuteOfDay: Int) =
        updateSettings(settings.copy(reminderRandom = false, reminderMinuteOfDay = minuteOfDay))

    private fun updateSettings(new: Settings) {
        val old = settings
        settings = new
        settingsStore.save(new)
        if (old.reminderEnabled != new.reminderEnabled || old.reminderRandom != new.reminderRandom ||
            old.reminderMinuteOfDay != new.reminderMinuteOfDay
        ) {
            DailyReminder.schedule(getApplication(), ExistingWorkPolicy.REPLACE)
        }
        val poolChanged = old.includeKept != new.includeKept || old.excludedFolders != new.excludedFolders
        if (!poolChanged || bracket == null) {
            uiState = uiState.copy(settings = new)
        } else {
            rebuild()
        }
    }

    private fun rebuild() {
        bracket = Bracket(
            ids = library.map { it.id },
            trashed = prefs.loadIds(KEY_TRASH),
            kept = prefs.loadIds(KEY_KEPT),
            sortQueue = prefs.getStringSet(KEY_SORT_QUEUE, emptySet()).orEmpty().mapNotNull { entry ->
                val (id, folderId) = entry.split(':', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
                id.toLongOrNull()?.let { it to folderId }
            }.toMap(),
            excluded = library.filter { it.relativePath in settings.excludedFolders }.map { it.id },
            includeKept = settings.includeKept,
        )
        publish()
    }

    private fun decide(action: Bracket.() -> Unit) {
        val before = bracket?.trashed.orEmpty().toSet()
        update(action)
        if (settings.deleteImmediately) {
            val newlyTrashed = bracket?.trashed.orEmpty() - before
            if (newlyTrashed.isNotEmpty()) {
                deleteRequestChannel.trySend(newlyTrashed.map(repository::uriFor))
            }
        }
    }

    private fun update(action: Bracket.() -> Unit) {
        bracket?.action()
        publish()
    }

    private fun publish() {
        val bracket = bracket ?: return
        prefs.edit {
            putStringSet(KEY_TRASH, bracket.trashed.map(Long::toString).toSet())
            putStringSet(KEY_KEPT, bracket.kept.map(Long::toString).toSet())
            putStringSet(KEY_SORT_QUEUE, bracket.sortQueue.map { (id, folderId) -> "$id:$folderId" }.toSet())
        }
        val pair = bracket.current
        uiState = BracketUiState(
            isLoading = false,
            pair = pair?.let { (a, b) -> repository.uriFor(a) to repository.uriFor(b) },
            canUndo = bracket.canUndo,
            trash = bracket.trashed.map(repository::uriFor),
            keptCount = bracket.kept.size,
            settings = settings,
            folders = folders,
            pairFolders = pair?.toList().orEmpty()
                .mapNotNull { id -> bracket.folderFor(id)?.let { repository.uriFor(id) to it } }
                .toMap(),
            sortQueue = bracket.sortQueue.map { (id, folderId) -> repository.uriFor(id) to folderId },
            libraryFolders = libraryFolders,
        )
    }

    private fun SharedPreferences.loadIds(key: String): List<Long> =
        getStringSet(key, emptySet()).orEmpty().mapNotNull(String::toLongOrNull)

    private companion object {
        const val KEY_TRASH = "trash"
        const val KEY_KEPT = "kept"
        const val KEY_SORT_QUEUE = "sort_queue"
    }
}
