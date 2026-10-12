package io.github.marshallhelenm.photobracket

import android.content.Context
import androidx.core.content.edit

data class Settings(
    /** Whether photos the user already kept can come up again. */
    val includeKept: Boolean = true,
    /** Whether to ask Android to delete photos as soon as they're passed on, skipping the app's trash. */
    val deleteImmediately: Boolean = false,
    /** Whether to send a once-a-day notification nudging the user to judge a pair. */
    val reminderEnabled: Boolean = true,
    /** Whether the daily reminder comes at a random time instead of [reminderMinuteOfDay]. */
    val reminderRandom: Boolean = true,
    /** Time of the daily reminder, in minutes after midnight. */
    val reminderMinuteOfDay: Int = 19 * 60,
)

/** Settings persistence, shared by the UI and the background reminder job. */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): Settings {
        val defaults = Settings()
        return Settings(
            includeKept = prefs.getBoolean(KEY_INCLUDE_KEPT, defaults.includeKept),
            deleteImmediately = prefs.getBoolean(KEY_DELETE_IMMEDIATELY, defaults.deleteImmediately),
            reminderEnabled = prefs.getBoolean(KEY_REMINDER_ENABLED, defaults.reminderEnabled),
            reminderRandom = prefs.getBoolean(KEY_REMINDER_RANDOM, defaults.reminderRandom),
            reminderMinuteOfDay = prefs.getInt(KEY_REMINDER_MINUTE, defaults.reminderMinuteOfDay),
        )
    }

    fun save(settings: Settings) {
        prefs.edit {
            putBoolean(KEY_INCLUDE_KEPT, settings.includeKept)
            putBoolean(KEY_DELETE_IMMEDIATELY, settings.deleteImmediately)
            putBoolean(KEY_REMINDER_ENABLED, settings.reminderEnabled)
            putBoolean(KEY_REMINDER_RANDOM, settings.reminderRandom)
            putInt(KEY_REMINDER_MINUTE, settings.reminderMinuteOfDay)
        }
    }

    /** Day the last reminder was shown, as `year * 1000 + dayOfYear`, so we never send two in one day. */
    var lastReminderDay: Int
        get() = prefs.getInt(KEY_LAST_REMINDER_DAY, 0)
        set(value) = prefs.edit { putInt(KEY_LAST_REMINDER_DAY, value) }

    /** Whether we've already asked for notification permission once. */
    var askedForNotifications: Boolean
        get() = prefs.getBoolean(KEY_ASKED_NOTIFICATIONS, false)
        set(value) = prefs.edit { putBoolean(KEY_ASKED_NOTIFICATIONS, value) }

    private companion object {
        const val KEY_INCLUDE_KEPT = "include_kept"
        const val KEY_DELETE_IMMEDIATELY = "delete_immediately"
        const val KEY_REMINDER_ENABLED = "reminder_enabled"
        const val KEY_REMINDER_RANDOM = "reminder_random"
        const val KEY_REMINDER_MINUTE = "reminder_minute"
        const val KEY_LAST_REMINDER_DAY = "reminder_last_day"
        const val KEY_ASKED_NOTIFICATIONS = "asked_notifications"
    }
}
