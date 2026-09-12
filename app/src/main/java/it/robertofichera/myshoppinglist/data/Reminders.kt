package it.robertofichera.myshoppinglist.data

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import it.robertofichera.myshoppinglist.MainActivity
import it.robertofichera.myshoppinglist.R
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** How far the notification's Postpone action pushes a reminder. */
const val POSTPONE_MILLIS = 60 * 60 * 1000L

const val REMINDER_CHANNEL = "reminders"

private const val ACTION_FIRE = "it.robertofichera.myshoppinglist.REMINDER_FIRE"
private const val ACTION_DONE = "it.robertofichera.myshoppinglist.REMINDER_DONE"
private const val ACTION_POSTPONE = "it.robertofichera.myshoppinglist.REMINDER_POSTPONE"
private const val EXTRA_LIST_ID = "listId"

/** How late a reminder may run when exact scheduling has been withdrawn on Android 12. */
private const val WINDOW_MILLIS = 10 * 60 * 1000L

/**
 * The instant a reminder fires. [dateUtcMillis] is the picked day as Material's date picker
 * reports it, midnight UTC; the hour and minute are the user's, read in [zone].
 */
fun reminderAt(dateUtcMillis: Long, hour: Int, minute: Int, zone: ZoneId): Long =
    Instant.ofEpochMilli(dateUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
        .atTime(hour, minute)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

/**
 * Arms the list's reminder, replacing any earlier one; a list with none set arms nothing.
 * A [ShoppingList.remindAt] already in the past fires at once, which is what a reminder that
 * came due while the phone was off should do.
 */
fun scheduleReminder(context: Context, list: ShoppingList) {
    if (list.remindAt <= 0) return
    val alarms = context.getSystemService(AlarmManager::class.java)
    val fire = receiverIntent(context, ACTION_FIRE, list.id)
    if (alarms.canScheduleExactAlarms()) {
        alarms.setAlarmClock(
            AlarmManager.AlarmClockInfo(list.remindAt, openListIntent(context, list.id)),
            fire,
        )
    } else {
        alarms.setWindow(AlarmManager.RTC_WAKEUP, list.remindAt, WINDOW_MILLIS, fire)
    }
}

/** Drops the alarm and any notification already showing for the list. */
fun cancelReminder(context: Context, listId: Long) {
    context.getSystemService(AlarmManager::class.java)
        .cancel(receiverIntent(context, ACTION_FIRE, listId))
    NotificationManagerCompat.from(context).cancel(listId.toInt())
}

/** Idempotent: Android keeps the first definition and whatever the user has since changed on it. */
fun ensureReminderChannel(context: Context) {
    val channel = NotificationChannel(
        REMINDER_CHANNEL,
        context.getString(R.string.reminder_channel),
        // High importance is what makes the notification heads-up.
        NotificationManager.IMPORTANCE_HIGH,
    )
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}

/** The request code is the list id, so scheduling again replaces and cancelling finds the same alarm. */
private fun receiverIntent(context: Context, action: String, listId: Long): PendingIntent =
    PendingIntent.getBroadcast(
        context,
        listId.toInt(),
        Intent(context, ReminderReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_LIST_ID, listId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

/** SINGLE_TOP matches the activity's launch mode, so an app already open lands inside the list. */
private fun openListIntent(context: Context, listId: Long): PendingIntent =
    PendingIntent.getActivity(
        context,
        listId.toInt(),
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_LIST_ID, listId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

/** Without the permission the system would drop it anyway; the reminder itself stays set. */
private fun showReminder(context: Context, list: ShoppingList) {
    // The permission exists from Android 13; below it, notifications need no grant.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }
    ensureReminderChannel(context)
    val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL)
        .setSmallIcon(R.drawable.ic_reminder)
        .setContentTitle(list.name)
        .setContentText(context.getString(R.string.reminder_notification_text))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        // A tap is a look at the list, not an answer: the notification stays until Done or Postpone.
        .setContentIntent(openListIntent(context, list.id))
        // Swiping it away is an answer, so nothing lingers silently overdue.
        .setDeleteIntent(receiverIntent(context, ACTION_DONE, list.id))
        .addAction(0, context.getString(R.string.reminder_done), receiverIntent(context, ACTION_DONE, list.id))
        .addAction(0, context.getString(R.string.reminder_postpone), receiverIntent(context, ACTION_POSTPONE, list.id))
        .build()
    NotificationManagerCompat.from(context).notify(list.id.toInt(), notification)
}

/** Every action reads or writes the database, so the receiver stays alive under goAsync until it is done. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val listId = intent.getLongExtra(EXTRA_LIST_ID, 0L)
        if (listId == 0L) return
        val dao = AppDatabase.getInstance(context).shoppingDao()
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    // A list deleted since the alarm was set yields null, and nothing is posted.
                    ACTION_FIRE -> dao.getList(listId)?.let { showReminder(context, it) }
                    ACTION_DONE -> {
                        dao.setRemindAt(listId, 0L, System.currentTimeMillis())
                        cancelReminder(context, listId)
                    }
                    ACTION_POSTPONE -> {
                        val now = System.currentTimeMillis()
                        dao.setRemindAt(listId, now + POSTPONE_MILLIS, now)
                        NotificationManagerCompat.from(context).cancel(listId.toInt())
                        dao.getList(listId)?.let { scheduleReminder(context, it) }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** Alarms do not survive a reboot; every pending reminder is armed again, and one already due fires at once. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val dao = AppDatabase.getInstance(context).shoppingDao()
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                dao.listsWithReminder().forEach { scheduleReminder(context, it) }
            } finally {
                pending.finish()
            }
        }
    }
}
