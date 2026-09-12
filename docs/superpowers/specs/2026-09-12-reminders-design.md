# Reminders

A shopping list can be given one date and time. When it arrives the phone shows a heads-up
notification naming the list, with **Done** and **Postpone** on it; tapping it opens the list.

## Why

A list is written days before the trip. The reminder is what turns "the Saturday shop" into a
notification on Saturday morning, so the list is not found on Sunday evening with nothing bought.

## Scope

In:

- One reminder per list, set to the minute, fired once.
- A heads-up notification with Done and Postpone; the body tap opens the list.
- The pending time shown on the list's card in the overview.
- Reminders survive a reboot.

Out, deliberately:

- **Repeating reminders.** A weekly shop is a new list each week already; add an interval column
  when someone actually asks for "every Saturday".
- **Several reminders per list.** A list is one trip.
- **A per-reminder sound.** Since Android 8 the sound belongs to the notification channel, and the
  channel's system settings already let the user choose it. A Settings row opens them.
- **A full-screen alarm page.** `USE_FULL_SCREEN_INTENT` is revoked for non-alarm apps on
  Android 14 and up; the heads-up notification is what a shopping list warrants.
- **Time-zone re-scheduling.** `remindAt` is an epoch instant; a reminder set at 18:00 in Rome
  fires at 17:00 in London. Correct, and nobody sets a shopping reminder before boarding a plane.
- **The reminder in a share.** A share is a copy of the list, not of the sender's calendar.
  `ShareCodec` is not touched.

## Data

Database version 7 → 8.

```kotlin
/** When to remind about this list, as epoch millis; 0 means no reminder is set. */
@ColumnInfo(defaultValue = "0") val remindAt: Long = 0,
```

on `ShoppingList`. Zero-for-none matches `budgetCents` and `colorArgb`, so the overview card and
the dialog need no nullable branch.

`MIGRATION_7_8` is one statement:

```sql
ALTER TABLE shopping_lists ADD COLUMN remindAt INTEGER NOT NULL DEFAULT 0
```

The DDL is copied from the exported `app/schemas/.../8.json` after the first build, per `CLAUDE.md`.

The `loyalty-cards` branch also carries a `MIGRATION_7_8`. Whichever of the two lands on `main`
second renumbers its migration to 8 → 9 and its schema export to 9; the entity changes do not
overlap.

DAO additions:

```kotlin
@Query("UPDATE shopping_lists SET remindAt = :at, updatedAt = :now WHERE id = :listId")
suspend fun setRemindAt(listId: Long, at: Long, now: Long = System.currentTimeMillis())

@Query("SELECT * FROM shopping_lists WHERE remindAt > 0")
suspend fun listsWithReminder(): List<ShoppingList>

@Query("SELECT * FROM shopping_lists WHERE id = :id")
suspend fun getList(id: Long): ShoppingList?
```

Setting or clearing a reminder counts as touching the list, so `updatedAt` moves with it.

## Scheduling

One new file, `data/Reminders.kt`, holding everything that talks to `AlarmManager` and
`NotificationManager`.

```kotlin
fun scheduleReminder(context: Context, list: ShoppingList)   // no-op when remindAt == 0
fun cancelReminder(context: Context, listId: Long)
```

`scheduleReminder` uses `AlarmManager.setAlarmClock`. It is exact, it fires through Doze, and
Android surfaces the pending time in the shade as it does an alarm clock's. The `PendingIntent`
targets `ReminderReceiver` with `ACTION_FIRE` and the list id as an extra, and uses the list id as
its request code, so scheduling again replaces the previous alarm and `cancelReminder` finds it
with the same intent.

`ReminderReceiver`, `exported="false"`, handles three actions. Each does database work, so it runs
under `goAsync()` on `Dispatchers.IO` and calls `finish()` when done.

- `ACTION_FIRE`: reads the list by id. A list deleted meanwhile yields null and nothing is posted.
  Otherwise posts the notification below. `remindAt` is left as it is — the reminder is still open
  until the user answers it.
- `ACTION_DONE`: `setRemindAt(listId, 0)` and cancels the notification.
- `ACTION_POSTPONE`: `setRemindAt(listId, now + POSTPONE_MILLIS)` with `POSTPONE_MILLIS` one
  hour, re-arms via `scheduleReminder`, cancels the notification.

`BootReceiver`, `exported="true"` with the `BOOT_COMPLETED` filter, calls `scheduleReminder` for
every list from `listsWithReminder()`. A `remindAt` already in the past is handed to
`setAlarmClock` as is, and `AlarmManager` fires it at once, so a reminder that came due while the
phone was off shows on the next boot.

Deleting a list cancels its alarm in `ShoppingViewModel.deleteList`; the null check in
`ACTION_FIRE` is the backstop for a race between the two.

## The notification

One channel, `reminders`, `IMPORTANCE_HIGH` — that importance is what makes Android present it
heads-up over the current screen, and the channel's default sound plays unless the user has changed
it in the system's channel settings. The channel is created when the app first posts, in
`Reminders.kt`; `createNotificationChannel` is idempotent.

Content: title is the list name, text is `reminder_notification_text` ("Time to go shopping").
The notification id is the list id.

- **Body tap**: `MainActivity` with `EXTRA_LIST_ID`, `FLAG_ACTIVITY_SINGLE_TOP` matching the
  activity's `singleTop` launch mode, so an app already open lands inside the list without a second
  activity. Tapping does not clear the reminder; the list's own alarm icon is where a new time is
  picked, and Done is the explicit answer.
- **Done** action: `ACTION_DONE` broadcast.
- **Postpone 1 h** action: `ACTION_POSTPONE` broadcast.
- **Swipe away** (`deleteIntent`): `ACTION_DONE`. A dismissed reminder is an answered one; nothing
  lingers as silently overdue.

Auto-cancel is off, so the body tap leaves the notification standing until Done or Postpone — the
tap is a look at the list, not an answer.

## Permissions

Manifest:

- `USE_EXACT_ALARM` — granted at install, cannot be revoked. Google Play limits it to alarm and
  calendar apps, but this app ships from GitHub releases, so that policy does not bind it. It
  exists from API 33.
- `SCHEDULE_EXACT_ALARM` with `maxSdkVersion="32"` — the exact-alarm permission on Android 12,
  where it is granted by default. The user can revoke it there; `scheduleReminder` checks
  `canScheduleExactAlarms()` and falls back to `setWindow` with a ten-minute window, so a reminder
  is late rather than lost.
- `RECEIVE_BOOT_COMPLETED`.
- `POST_NOTIFICATIONS` — declared, requested at runtime.

`ReminderDialog` requests `POST_NOTIFICATIONS` through
`rememberLauncherForActivityResult(RequestPermission())` when the user saves a reminder and the
permission is not yet held. The reminder is saved and the alarm armed whatever the answer: a
refusal only means the system will not show the notification, and the user can grant it later from
app settings without touching the reminder.

## UI

`ui/ReminderDialog.kt` — a new dialog opened from an alarm icon in `ListDetailScreen`'s top bar.
It holds Material 3's `DatePicker` and `TimePicker`, both in the `material3` artifact already
depended on, preset to the existing reminder or to now + 1 h when none is set. Buttons: **Set**,
**Cancel**, and **Clear** when a reminder exists. A date and time in the past is refused with the
Set button disabled, since an alarm in the past would fire immediately and that is never what was
meant. The pickers work in the device's time zone; the dialog composes the two into epoch millis
with `java.time.ZonedDateTime`.

The alarm icon is the second half of Postpone: the notification's action defers by a fixed hour,
and the icon is where any other time is chosen.

`ListsScreen` card: below the "updated" line, an alarm glyph and the time through
`DateUtils.formatDateTime(context, remindAt, FORMAT_SHOW_DATE or FORMAT_SHOW_TIME)`, shown only
when `remindAt > 0`. A time already past is drawn in the error colour, so a list whose reminder was
never answered says so from the overview.

`MainActivity`: `EXTRA_LIST_ID` is read beside `offerImport` in both `onCreate` and `onNewIntent`
and handed to `ShoppingApp` as the initial `openListId`. It is marked consumed the same way the
share is (`EXTRA_OFFERED`), so a recreation does not reopen the list over whatever the user had
navigated to.

`SettingsScreen`: a "Reminder sound" row that opens the channel's system settings
(`ACTION_CHANNEL_NOTIFICATION_SETTINGS`), which is where sound, vibration and importance live.

`ShoppingViewModel.setReminder(listId, at)`: `dao.setRemindAt` then `scheduleReminder` or
`cancelReminder`. The receiver does its own DAO writes through `AppDatabase.getInstance`, since no
ViewModel exists when it runs.

Strings, in all twelve `values*` folders: dialog title, Set, Clear, Done, Postpone 1 h, channel
name, notification text, the Settings row. `Cancel` already exists.

## Testing

There is no pure logic here that a unit test would catch a regression in: the times are constants
and the composition of date and time is `java.time`'s. Verification is on the device, which is also
what `CLAUDE.md` prescribes for Room and UI wiring:

1. Install over a v7 database; open every list; `PRAGMA foreign_key_check` clean.
2. Set a reminder two minutes out, lock the phone: heads-up arrives on time with sound.
3. **Done** clears it from the card. **Postpone** moves the card's time an hour on and the
   notification returns then.
4. Body tap opens the list, notification stays; swipe away clears it.
5. Set one, reboot, confirm it still fires. Set one, power off past its time, boot: it fires on boot.
6. Delete a list with a pending reminder: no notification at its time.
7. Refuse `POST_NOTIFICATIONS`, set a reminder: the card shows it, nothing is posted, and granting
   it in app settings makes the next one show.

## Files

| File | Change |
|---|---|
| `data/Entities.kt` | `remindAt` on `ShoppingList` |
| `data/AppDatabase.kt` | version 8, `MIGRATION_7_8` |
| `data/ShoppingDao.kt` | `setRemindAt`, `listsWithReminder`, `getList` |
| `data/Reminders.kt` | new: schedule/cancel, channel, `ReminderReceiver`, `BootReceiver` |
| `ShoppingViewModel.kt` | `setReminder`; `deleteList` cancels |
| `ui/ReminderDialog.kt` | new |
| `ui/ListDetailScreen.kt` | alarm icon in the top bar |
| `ui/ListsScreen.kt` | reminder line on the card |
| `ui/SettingsScreen.kt` | "Reminder sound" row |
| `MainActivity.kt` | `EXTRA_LIST_ID` |
| `AndroidManifest.xml` | three permissions, two receivers |
| `res/values*/strings.xml` | ×12 |
| `CLAUDE.md` | layout table, a Key decisions entry |
