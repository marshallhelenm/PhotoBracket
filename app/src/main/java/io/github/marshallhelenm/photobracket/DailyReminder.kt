package io.github.marshallhelenm.photobracket

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** Random reminders land between 9 AM and 9 PM, so nobody gets pinged at 3 AM. */
const val RANDOM_WINDOW_START_MINUTE = 9 * 60
const val RANDOM_WINDOW_END_MINUTE = 21 * 60

/**
 * When the next daily reminder should fire after [now]. If one was already shown today, the
 * answer is always tomorrow, so changing settings mid-day can't cause a second notification.
 */
fun nextReminderTime(
    now: Calendar,
    settings: Settings,
    shownToday: Boolean,
    random: Random = Random.Default,
): Calendar {
    val nowMinute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    val (dayOffset, minute) = if (settings.reminderRandom) {
        val earliestToday = maxOf(RANDOM_WINDOW_START_MINUTE, nowMinute + 1)
        if (!shownToday && earliestToday < RANDOM_WINDOW_END_MINUTE) {
            0 to random.nextInt(earliestToday, RANDOM_WINDOW_END_MINUTE)
        } else {
            1 to random.nextInt(RANDOM_WINDOW_START_MINUTE, RANDOM_WINDOW_END_MINUTE)
        }
    } else {
        val target = settings.reminderMinuteOfDay
        if (!shownToday && target > nowMinute) 0 to target else 1 to target
    }
    return (now.clone() as Calendar).apply {
        add(Calendar.DAY_OF_YEAR, dayOffset)
        set(Calendar.HOUR_OF_DAY, minute / 60)
        set(Calendar.MINUTE, minute % 60)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
}

fun Calendar.dayKey(): Int = get(Calendar.YEAR) * 1000 + get(Calendar.DAY_OF_YEAR)

object DailyReminder {

    private const val WORK_NAME = "daily_reminder"
    private const val CHANNEL_ID = "daily_reminder"
    private const val NOTIFICATION_ID = 1

    /**
     * Schedules the next reminder, or cancels it if reminders are off. Use [ExistingWorkPolicy.KEEP]
     * on app start (so a random time isn't re-rolled every launch) and REPLACE after settings change.
     */
    fun schedule(context: Context, policy: ExistingWorkPolicy) {
        val store = SettingsStore(context)
        val settings = store.load()
        val workManager = WorkManager.getInstance(context)
        if (!settings.reminderEnabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val now = Calendar.getInstance()
        val next = nextReminderTime(now, settings, shownToday = store.lastReminderDay == now.dayKey())
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(next.timeInMillis - now.timeInMillis, TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniqueWork(WORK_NAME, policy, request)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    internal fun show(context: Context) {
        if (!canNotify(context)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(context.getString(R.string.reminder_text))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val store = SettingsStore(applicationContext)
        if (store.load().reminderEnabled) {
            DailyReminder.show(applicationContext)
            store.lastReminderDay = Calendar.getInstance().dayKey()
            // APPEND so the follow-up doesn't cancel this still-running work.
            DailyReminder.schedule(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
        }
        return Result.success()
    }
}
