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
    val trash: List<Uri> = emptyList(),
    val keptCount: Int = 0,
    val settings: Settings = Settings(),
)

class BracketViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PhotoRepository(application)
    private val prefs = application.getSharedPreferences("bracket", Context.MODE_PRIVATE)
    private var libraryIds: List<Long> = emptyList()
    private var bracket: Bracket? = null
    private val settingsStore = SettingsStore(application)
    private var settings = settingsStore.load()

    var uiState by mutableStateOf(BracketUiState(settings = settings))
        private set

    private val deleteRequestChannel = Channel<List<Uri>>(Channel.BUFFERED)

    /** Photos to delete right away, emitted when [Settings.deleteImmediately] is on. */
    val deleteRequests: Flow<List<Uri>> = deleteRequestChannel.receiveAsFlow()

    /** Reads the photo library. Only does work the first time, so rotating doesn't reshuffle the pair. */
    fun load() {
        if (bracket != null) return
        viewModelScope.launch {
            libraryIds = withContext(Dispatchers.IO) { repository.loadImageIds() }
            rebuild()
        }
    }

    fun keep(winner: Uri) = decide { keep(ContentUris.parseId(winner)) }

    fun keepBoth() = decide { keepBoth() }

    fun keepNeither() = decide { keepNeither() }

    fun restore(uri: Uri) = update { restore(ContentUris.parseId(uri)) }

    fun restoreAll() = update { restoreAll() }

    /** Call once photos have been deleted (or trashed by the system) so they're no longer shown. */
    fun onDeleted(uris: List<Uri>) = update { forget(uris.map(ContentUris::parseId)) }

    fun deleteLegacy(uris: List<Uri>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.deleteLegacy(uris) }
            onDeleted(uris)
        }
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
        if (old.includeKept == new.includeKept || bracket == null) {
            uiState = uiState.copy(settings = new)
        } else {
            rebuild()
        }
    }

    private fun rebuild() {
        bracket = Bracket(
            ids = libraryIds,
            trashed = prefs.loadIds(KEY_TRASH),
            kept = prefs.loadIds(KEY_KEPT),
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
        }
        uiState = BracketUiState(
            isLoading = false,
            pair = bracket.current?.let { (a, b) -> repository.uriFor(a) to repository.uriFor(b) },
            trash = bracket.trashed.map(repository::uriFor),
            keptCount = bracket.kept.size,
            settings = settings,
        )
    }

    private fun SharedPreferences.loadIds(key: String): List<Long> =
        getStringSet(key, emptySet()).orEmpty().mapNotNull(String::toLongOrNull)

    private companion object {
        const val KEY_TRASH = "trash"
        const val KEY_KEPT = "kept"
    }
}
