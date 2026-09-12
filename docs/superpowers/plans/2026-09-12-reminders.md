# Reminders Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A shopping list can carry one date and time; when it arrives the phone shows a heads-up notification naming the list with Done and Postpone on it, and tapping it opens the list.

**Architecture:** One `remindAt` column on `shopping_lists` (0 = none) is the source of truth; `AlarmManager.setAlarmClock` fires a `BroadcastReceiver` that posts the notification and handles its actions by writing that column back through the DAO; a second receiver re-arms every pending reminder after boot. UI is a two-step date/time dialog off the list's top bar, a line on the overview card, and a Settings row into the channel's system settings.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3 `DatePickerDialog` / `TimePicker`, already in the depended-on `material3` artifact), Room, `AlarmManager`, `NotificationCompat` from `androidx.core` (already depended on). No new dependencies.

**Spec:** `docs/superpowers/specs/2026-09-12-reminders-design.md`

## Global Constraints

- Branch `reminders`, off `main`. Work on it; never commit to `main`.
- Kotlin official style, 4-space indent, trailing commas on multi-line argument/parameter lists, no wildcard imports, prefer `val`, never `!!`.
- No user-visible string in Kotlin: every string goes into `res/values/strings.xml` **and** all eleven translation folders (`values-en`, `-it`, `-de`, `-nl`, `-es`, `-pt`, `-pt-rBR`, `-fr`, `-el`, `-pl`, `-hu`). `./gradlew lintDebug` fails on a missing translation.
- Comments describe the current code only: no history, no rejected alternatives, no "for now".
- Money is untouched by this feature. Do not modify `Money.kt` or `MoneyTest.kt`.
- Migration SQL must match Room's exported DDL exactly; build first, then copy the DDL from `app/schemas/it.robertofichera.myshoppinglist.data.AppDatabase/8.json`.
- The `loyalty-cards` branch also defines a `MIGRATION_7_8` and an `8.json`. That is expected; whichever branch reaches `main` second renumbers. Do nothing about it here.
- Build with `./gradlew assembleDebug`, unit tests with `./gradlew testDebugUnitTest`, lint with `./gradlew lintDebug`. All three must pass before every commit.

---

## File Structure

| File | Responsibility |
|---|---|
| `app/src/main/java/it/robertofichera/myshoppinglist/data/Entities.kt` | `ShoppingList.remindAt` |
| `.../data/AppDatabase.kt` | version 8, `MIGRATION_7_8` |
| `.../data/ShoppingDao.kt` | `setRemindAt`, `getList`, `listsWithReminder` |
| `.../data/Reminders.kt` (new) | `reminderAt` (pure), `scheduleReminder`, `cancelReminder`, `ensureReminderChannel`, `ReminderReceiver`, `BootReceiver` — everything that talks to `AlarmManager` and notifications |
| `app/src/test/java/it/robertofichera/myshoppinglist/data/RemindersTest.kt` (new) | `reminderAt` |
| `.../ShoppingViewModel.kt` | `setReminder`, `deleteList` cancels, `requestedList` for a notification tap |
| `.../MainActivity.kt` | reads `EXTRA_LIST_ID`; `ShoppingApp` opens the requested list |
| `.../ui/ReminderDialog.kt` (new) | date step, then time step; Set / Clear; asks `POST_NOTIFICATIONS` |
| `.../ui/ListDetailScreen.kt` | bell icon in the top bar opening the dialog |
| `.../ui/ListsScreen.kt` | reminder line on the card, error colour when overdue |
| `.../ui/SettingsScreen.kt` | "Reminder sound" row into the channel's system settings |
| `app/src/main/AndroidManifest.xml` | four permissions, two receivers |
| `app/src/main/res/drawable/ic_reminder.xml` (new) | bell, the notification's small icon |
| `app/src/main/res/values*/strings.xml` | eleven new strings × 12 folders |
| `CLAUDE.md` | layout table, a Key decisions entry, a Testing entry |

---

### Task 1: The `remindAt` column

**Files:**
- Modify: `app/src/main/java/it/robertofichera/myshoppinglist/data/Entities.kt` (the `ShoppingList` class, after `sharedBy`)
- Modify: `app/src/main/java/it/robertofichera/myshoppinglist/data/AppDatabase.kt`
- Modify: `app/src/main/java/it/robertofichera/myshoppinglist/data/ShoppingDao.kt` (after `findListByUuid`)
- Generated: `app/schemas/it.robertofichera.myshoppinglist.data.AppDatabase/8.json`

**Interfaces:**
- Produces: `ShoppingList.remindAt: Long` (0 = none); `ShoppingDao.setRemindAt(listId: Long, at: Long, now: Long)`, `ShoppingDao.getList(listId: Long): ShoppingList?`, `ShoppingDao.listsWithReminder(): List<ShoppingList>` — all `suspend`.

- [ ] **Step 1: Add the column to the entity**

In `Entities.kt`, add the last property of `ShoppingList`, after `val sharedBy: String? = null,`:

```kotlin
    /** When to remind about this list, as epoch millis; 0 means no reminder is set. */
    @ColumnInfo(defaultValue = "0") val remindAt: Long = 0,
```

- [ ] **Step 2: Bump the version and add the migration**

In `AppDatabase.kt` change `version = 7` to `version = 8` and extend the `addMigrations(...)` call to end with `MIGRATION_6_7, MIGRATION_7_8`. Then add after `MIGRATION_6_7`, inside the companion object:

```kotlin
        /** When to remind about the list; 0, which every existing row starts as, means never. */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `shopping_lists` ADD COLUMN `remindAt` INTEGER NOT NULL DEFAULT 0")
            }
        }
```

- [ ] **Step 3: Add the DAO methods**

In `ShoppingDao.kt`, after `findListByUuid`:

```kotlin
    /** Setting or clearing the reminder counts as touching the list. */
    @Query("UPDATE shopping_lists SET remindAt = :at, updatedAt = :now WHERE id = :listId")
    suspend fun setRemindAt(listId: Long, at: Long, now: Long)

    @Query("SELECT * FROM shopping_lists WHERE id = :listId")
    suspend fun getList(listId: Long): ShoppingList?

    @Query("SELECT * FROM shopping_lists WHERE remindAt > 0")
    suspend fun listsWithReminder(): List<ShoppingList>
```

- [ ] **Step 4: Build, and check the exported DDL against the migration**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL, and a new file `app/schemas/it.robertofichera.myshoppinglist.data.AppDatabase/8.json`.

Run: `grep -o '`remindAt` INTEGER NOT NULL DEFAULT 0' app/schemas/it.robertofichera.myshoppinglist.data.AppDatabase/8.json`
Expected: exactly that string printed once. If the exported `createSql` spells the column differently, change the migration's SQL to match the export, never the other way round.

- [ ] **Step 5: Run the existing tests**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL (nothing references the column yet; this guards the entity change).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/it/robertofichera/myshoppinglist/data/Entities.kt \
        app/src/main/java/it/robertofichera/myshoppinglist/data/AppDatabase.kt \
        app/src/main/java/it/robertofichera/myshoppinglist/data/ShoppingDao.kt \
        app/schemas/it.robertofichera.myshoppinglist.data.AppDatabase/8.json
git commit -m "feat: a list remembers when to remind about itself"
```

---

### Task 2: `reminderAt`, the one pure function, test-first

**Files:**
- Create: `app/src/test/java/it/robertofichera/myshoppinglist/data/RemindersTest.kt`
- Create: `app/src/main/java/it/robertofichera/myshoppinglist/data/Reminders.kt`

**Interfaces:**
- Produces: `fun reminderAt(dateUtcMillis: Long, hour: Int, minute: Int, zone: ZoneId): Long`

Why this exists: Material 3's `DatePicker` reports the picked day as **midnight UTC**, while the hour and minute the user picks are in the **device's zone**. Composing the two naively is off by the zone offset — a reminder for 18:30 in Rome would fire at 20:30. This is the only logic in the feature worth a unit test.

- [ ] **Step 1: Write the failing test**

```kotlin
package it.robertofichera.myshoppinglist.data

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class RemindersTest {

    private val rome = ZoneId.of("Europe/Rome")

    // 2026-09-12 as the date picker reports it: midnight UTC.
    private val september12Utc = 1_789_171_200_000L

    @Test
    fun `the day is read as UTC midnight and the time in the given zone`() {
        // 18:30 in Rome on that day is 16:30Z under summer time.
        assertEquals(1_789_230_600_000L, reminderAt(september12Utc, 18, 30, rome))
    }

    @Test
    fun `the same wall-clock time in UTC is two hours later`() {
        assertEquals(1_789_237_800_000L, reminderAt(september12Utc, 18, 30, ZoneId.of("UTC")))
    }

    @Test
    fun `winter time uses the winter offset`() {
        // 2026-01-10 midnight UTC; 08:00 Rome is 07:00Z.
        assertEquals(1_768_028_400_000L, reminderAt(1_768_003_200_000L, 8, 0, rome))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew testDebugUnitTest --tests 'it.robertofichera.myshoppinglist.data.RemindersTest'`
Expected: compilation FAILS with `Unresolved reference: reminderAt`.

- [ ] **Step 3: Write the function**

Create `Reminders.kt` with only this for now:

```kotlin
package it.robertofichera.myshoppinglist.data

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

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
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests 'it.robertofichera.myshoppinglist.data.RemindersTest'`
Expected: BUILD SUCCESSFUL, 3 tests passed.

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/it/robertofichera/myshoppinglist/data/RemindersTest.kt \
        app/src/main/java/it/robertofichera/myshoppinglist/data/Reminders.kt
git commit -m "feat: compose a picked day and time into the instant a reminder fires"
```

---

### Task 3: Scheduling, the notification, and the receivers

**Files:**
- Modify: `app/src/main/java/it/robertofichera/myshoppinglist/data/Reminders.kt`
- Modify: `app/src/main/java/it/robertofichera/myshoppinglist/MainActivity.kt` (companion object only — the rest of the activity changes in Task 4)
- Create: `app/src/main/res/drawable/ic_reminder.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml` and the eleven `values-*/strings.xml`

**Interfaces:**
- Consumes: `ShoppingDao.getList`, `setRemindAt`, `listsWithReminder` (Task 1).
- Produces: `fun scheduleReminder(context: Context, list: ShoppingList)`, `fun cancelReminder(context: Context, listId: Long)`, `fun ensureReminderChannel(context: Context)`, `const val POSTPONE_MILLIS: Long`, `const val REMINDER_CHANNEL: String`, `MainActivity.EXTRA_LIST_ID: String`.

- [ ] **Step 1: Give `MainActivity` the public extra name**

In `MainActivity.kt`, replace the whole `private companion object { ... }` block with:

```kotlin
    companion object {
        /** The list a notification tap wants opened. */
        const val EXTRA_LIST_ID = "it.robertofichera.myshoppinglist.LIST_ID"
        private const val EXTRA_OFFERED = "it.robertofichera.myshoppinglist.OFFERED"
    }
```

(`EXTRA_OFFERED` is renamed and generalised in Task 4; leave it as is here.)

- [ ] **Step 2: The notification's small icon**

Create `app/src/main/res/drawable/ic_reminder.xml` (Material's "notifications" bell, Apache-2.0, same shape as `ic_photo_camera.xml`):

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.89,2 2,2zM18,16v-5c0,-3.07 -1.64,-5.64 -4.5,-6.32V4c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68C7.63,5.36 6,7.92 6,11v5l-2,2v1h16v-1l-2,-2z" />
</vector>
```

- [ ] **Step 3: Strings the notification needs**

Add to `app/src/main/res/values/strings.xml`, after `list_shared_by`:

```xml
    <string name="reminder_channel">Reminders</string>
    <string name="reminder_notification_text">Time to go shopping</string>
    <string name="reminder_done">Done</string>
    <string name="reminder_postpone">Postpone 1 h</string>
```

And the same four keys in every translation folder, placed after each folder's `list_shared_by`:

| folder | reminder_channel | reminder_notification_text | reminder_done | reminder_postpone |
|---|---|---|---|---|
| values-en | Reminders | Time to go shopping | Done | Postpone 1 h |
| values-it | Promemoria | È ora di fare la spesa | Fatto | Rimanda di 1 h |
| values-de | Erinnerungen | Zeit zum Einkaufen | Erledigt | 1 Std. später |
| values-nl | Herinneringen | Tijd om boodschappen te doen | Klaar | 1 uur uitstellen |
| values-es | Recordatorios | Hora de ir a comprar | Hecho | Posponer 1 h |
| values-pt | Lembretes | Hora de ir às compras | Feito | Adiar 1 h |
| values-pt-rBR | Lembretes | Hora de fazer compras | Concluído | Adiar 1 h |
| values-fr | Rappels | C\'est l\'heure des courses | Terminé | Reporter d\'1 h |
| values-el | Υπενθυμίσεις | Ώρα για ψώνια | Έγινε | Αναβολή 1 ώρα |
| values-pl | Przypomnienia | Czas na zakupy | Gotowe | Odłóż o 1 godz. |
| values-hu | Emlékeztetők | Ideje bevásárolni | Kész | Halasztás 1 órával |

(The French apostrophes are escaped as `\'` — an unescaped one fails resource compilation.)

- [ ] **Step 4: Permissions and receivers in the manifest**

In `AndroidManifest.xml`, after the two existing `<uses-permission>` lines:

```xml
    <!--
      Reminders. USE_EXACT_ALARM is granted at install from Android 13; on Android 12 the exact-alarm
      permission is SCHEDULE_EXACT_ALARM, granted by default but revocable, and scheduleReminder falls
      back to an inexact window when it has been withdrawn.
    -->
    <uses-permission android:name="android.permission.USE_EXACT_ALARM" />
    <uses-permission
        android:name="android.permission.SCHEDULE_EXACT_ALARM"
        android:maxSdkVersion="32" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

And inside `<application>`, after the `</provider>` element:

```xml
        <!-- Fires a reminder, and takes Done / Postpone from its notification. -->
        <receiver
            android:name=".data.ReminderReceiver"
            android:exported="false" />

        <!-- Alarms do not survive a reboot; this arms every pending reminder again. -->
        <receiver
            android:name=".data.BootReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>
```

- [ ] **Step 5: The rest of `Reminders.kt`**

Replace the file's imports with the full set and append everything below `reminderAt`. The complete file:

```kotlin
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
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
    if (granted != PackageManager.PERMISSION_GRANTED) return
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
```

- [ ] **Step 6: Build, lint, test**

Run: `./gradlew assembleDebug lintDebug testDebugUnitTest`
Expected: BUILD SUCCESSFUL. If lint reports `MissingPermission` on `notify`, the `checkSelfPermission` guard above is what satisfies it — check it was not moved. If lint reports a missing translation, one of the twelve folders is missing a key from Step 3.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/it/robertofichera/myshoppinglist/data/Reminders.kt \
        app/src/main/java/it/robertofichera/myshoppinglist/MainActivity.kt \
        app/src/main/res/drawable/ic_reminder.xml \
        app/src/main/AndroidManifest.xml \
        app/src/main/res/values/strings.xml app/src/main/res/values-*/strings.xml
git commit -m "feat: fire a list's reminder as a heads-up notification with Done and Postpone"
```

---

### Task 4: The ViewModel writes, and a notification tap opens the list

**Files:**
- Modify: `app/src/main/java/it/robertofichera/myshoppinglist/ShoppingViewModel.kt`
- Modify: `app/src/main/java/it/robertofichera/myshoppinglist/MainActivity.kt`

**Interfaces:**
- Consumes: `scheduleReminder`, `cancelReminder` (Task 3); `ShoppingDao.setRemindAt`, `getList` (Task 1).
- Produces: `ShoppingViewModel.setReminder(listId: Long, at: Long)`, `ShoppingViewModel.requestedList: StateFlow<Long?>`, `ShoppingViewModel.openList(listId: Long)`, `ShoppingViewModel.consumeRequestedList()`.

- [ ] **Step 1: ViewModel imports**

In `ShoppingViewModel.kt` add, in alphabetical position among the `it.robertofichera.myshoppinglist.data` imports:

```kotlin
import it.robertofichera.myshoppinglist.data.cancelReminder
import it.robertofichera.myshoppinglist.data.scheduleReminder
```

- [ ] **Step 2: `setReminder`, and `deleteList` cancelling**

Replace the existing one-liner

```kotlin
    fun deleteList(list: ShoppingList) = viewModelScope.launch { dao.deleteList(list) }
```

with:

```kotlin
    fun deleteList(list: ShoppingList) = viewModelScope.launch {
        cancelReminder(getApplication(), list.id)
        dao.deleteList(list)
    }

    /** [at] is epoch millis, or 0 to clear. The alarm follows the row, so the two never disagree. */
    fun setReminder(listId: Long, at: Long) = viewModelScope.launch {
        dao.setRemindAt(listId, at, System.currentTimeMillis())
        val app = getApplication<Application>()
        if (at > 0) {
            dao.getList(listId)?.let { scheduleReminder(app, it) }
        } else {
            cancelReminder(app, listId)
        }
    }
```

- [ ] **Step 3: The requested list**

Directly after the `_pendingImport` / `pendingImport` pair in the ViewModel, add:

```kotlin
    private val _requestedList = MutableStateFlow<Long?>(null)
    /** The list a notification tap asked for; the screen takes it and calls [consumeRequestedList]. */
    val requestedList: StateFlow<Long?> = _requestedList.asStateFlow()

    fun openList(listId: Long) {
        _requestedList.value = listId
    }

    fun consumeRequestedList() {
        _requestedList.value = null
    }
```

- [ ] **Step 4: `MainActivity` reads the extra**

Replace the `offerImport` function, its KDoc, and the companion object with:

```kotlin
    /**
     * A notification tap names a list; a share carries text. Either is marked on the intent once
     * handled, so a recreation re-reading that same intent stays quiet while a newly arrived one
     * is always acted on.
     */
    private fun handle(intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_HANDLED, false)) return
        intent.putExtra(EXTRA_HANDLED, true)
        val listId = intent.getLongExtra(EXTRA_LIST_ID, 0L)
        if (listId != 0L) {
            viewModel.openList(listId)
            return
        }
        val text = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> return
        }
        viewModel.offerImport(text)
    }

    companion object {
        /** The list a notification tap wants opened. */
        const val EXTRA_LIST_ID = "it.robertofichera.myshoppinglist.LIST_ID"
        private const val EXTRA_HANDLED = "it.robertofichera.myshoppinglist.HANDLED"
    }
```

and change both call sites — `offerImport(intent)` in `onCreate` and in `onNewIntent` — to `handle(intent)`.

- [ ] **Step 5: `ShoppingApp` navigates to it**

Add the import `import androidx.compose.runtime.LaunchedEffect` to `MainActivity.kt`. In `ShoppingApp`, after the line `val pendingImport by viewModel.pendingImport.collectAsStateWithLifecycle()`, add:

```kotlin
    val requestedList by viewModel.requestedList.collectAsStateWithLifecycle()

    // Whatever was open gives way: the tap said which list, and Settings or Products would hide it.
    LaunchedEffect(requestedList) {
        val id = requestedList ?: return@LaunchedEffect
        showProducts = false
        showSettings = false
        openListId = id
        viewModel.consumeRequestedList()
    }
```

- [ ] **Step 6: Build, lint, test**

Run: `./gradlew assembleDebug lintDebug testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/it/robertofichera/myshoppinglist/ShoppingViewModel.kt \
        app/src/main/java/it/robertofichera/myshoppinglist/MainActivity.kt
git commit -m "feat: set and clear a list's reminder, and open the list from its notification"
```

---

### Task 5: The reminder dialog, from the list's top bar

**Files:**
- Create: `app/src/main/java/it/robertofichera/myshoppinglist/ui/ReminderDialog.kt`
- Modify: `app/src/main/java/it/robertofichera/myshoppinglist/ui/ListDetailScreen.kt`
- Modify: `app/src/main/res/values/strings.xml` and the eleven `values-*/strings.xml`

**Interfaces:**
- Consumes: `reminderAt`, `POSTPONE_MILLIS` (Tasks 2–3); `ShoppingViewModel.setReminder` (Task 4); `ShoppingList.remindAt` (Task 1).
- Produces: `@Composable fun ReminderDialog(remindAt: Long, onSet: (Long) -> Unit, onClear: () -> Unit, onDismiss: () -> Unit)`.

- [ ] **Step 1: Strings**

Add to `values/strings.xml`, after `reminder_postpone`:

```xml
    <string name="reminder_title">Reminder</string>
    <string name="reminder_next">Next</string>
    <string name="reminder_set">Set</string>
    <string name="reminder_clear">Clear</string>
```

And in every translation folder, after its `reminder_postpone`:

| folder | reminder_title | reminder_next | reminder_set | reminder_clear |
|---|---|---|---|---|
| values-en | Reminder | Next | Set | Clear |
| values-it | Promemoria | Avanti | Imposta | Rimuovi |
| values-de | Erinnerung | Weiter | Festlegen | Entfernen |
| values-nl | Herinnering | Volgende | Instellen | Verwijderen |
| values-es | Recordatorio | Siguiente | Establecer | Quitar |
| values-pt | Lembrete | Seguinte | Definir | Remover |
| values-pt-rBR | Lembrete | Avançar | Definir | Remover |
| values-fr | Rappel | Suivant | Définir | Supprimer |
| values-el | Υπενθύμιση | Επόμενο | Ορισμός | Αφαίρεση |
| values-pl | Przypomnienie | Dalej | Ustaw | Usuń |
| values-hu | Emlékeztető | Tovább | Beállítás | Törlés |

- [ ] **Step 2: The dialog**

Create `ui/ReminderDialog.kt`:

```kotlin
package it.robertofichera.myshoppinglist.ui

import android.Manifest
import android.content.pm.PackageManager
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import it.robertofichera.myshoppinglist.R
import it.robertofichera.myshoppinglist.data.POSTPONE_MILLIS
import it.robertofichera.myshoppinglist.data.reminderAt
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The day first, then the time. [remindAt] is the reminder already set, or 0; a new one starts an
 * hour from now. Saving asks for the notification permission when it is not yet held, and saves
 * whatever the answer: a refusal only mutes the notification, and the system's app settings can
 * grant it later without touching the reminder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderDialog(
    remindAt: Long,
    onSet: (Long) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val initial = remember {
        val at = if (remindAt > 0) remindAt else System.currentTimeMillis() + POSTPONE_MILLIS
        Instant.ofEpochMilli(at).atZone(zone)
    }
    // The picker speaks in UTC midnights; today and the initial day are handed to it that way.
    val todayUtc = remember { LocalDate.now(zone).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = initial.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayUtc
        },
    )
    val timeState = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = DateFormat.is24HourFormat(context),
    )
    var pickingTime by remember { mutableStateOf(false) }

    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val save: (Long) -> Unit = { at ->
        val held = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        if (held != PackageManager.PERMISSION_GRANTED) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        onSet(at)
    }

    if (!pickingTime) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    onClick = { pickingTime = true },
                    enabled = dateState.selectedDateMillis != null,
                ) {
                    Text(stringResource(R.string.reminder_next))
                }
            },
            dismissButton = {
                if (remindAt > 0) {
                    TextButton(onClick = onClear) { Text(stringResource(R.string.reminder_clear)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            DatePicker(state = dateState)
        }
    } else {
        val date = dateState.selectedDateMillis ?: return
        val at = reminderAt(date, timeState.hour, timeState.minute, zone)
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.reminder_title)) },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                // A time already past would fire at once, which is never what was meant.
                TextButton(onClick = { save(at) }, enabled = at > System.currentTimeMillis()) {
                    Text(stringResource(R.string.reminder_set))
                }
            },
            dismissButton = {
                TextButton(onClick = { pickingTime = false }) { Text(stringResource(R.string.action_back)) }
            },
        )
    }
}
```

- [ ] **Step 3: The bell in `ListDetailScreen`'s top bar**

Add these imports to `ListDetailScreen.kt`:

```kotlin
import androidx.compose.material.icons.filled.Notifications
```

Add a state next to the others at the top of `ListDetailScreen` (after `var settling by remember { mutableStateOf<ScannedItem?>(null) }`):

```kotlin
    var settingReminder by remember { mutableStateOf(false) }
```

In the `TopAppBar`'s `actions = { ... }` block, before the existing Share `IconButton`:

```kotlin
                    IconButton(onClick = { settingReminder = true }) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = stringResource(R.string.reminder_title),
                        )
                    }
```

At the end of the composable, next to where the other dialogs are shown (after the `editingItem?.let { ... }` block that shows `ItemDialog`):

```kotlin
    if (settingReminder) {
        ReminderDialog(
            remindAt = entry.list.remindAt,
            onSet = { at ->
                viewModel.setReminder(entry.list.id, at)
                settingReminder = false
            },
            onClear = {
                viewModel.setReminder(entry.list.id, 0L)
                settingReminder = false
            },
            onDismiss = { settingReminder = false },
        )
    }
```

- [ ] **Step 4: Build, lint, test**

Run: `./gradlew assembleDebug lintDebug testDebugUnitTest`
Expected: BUILD SUCCESSFUL. If `Icons.Default.Notifications` does not resolve, `material-icons-core` is missing the glyph on this BOM: replace it with `painterResource(R.drawable.ic_reminder)` as the first argument of `Icon` (the same call shape the camera FAB uses) and drop the import.

- [ ] **Step 5: Install and try it**

Run: `./gradlew installDebug`
Open a list, tap the bell, pick tomorrow, Next, pick a time, Set. On first use the notification permission prompt appears. Reopen the bell: it is preset to the chosen time and offers Clear. Choose a time earlier than now today: Set is disabled.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/it/robertofichera/myshoppinglist/ui/ReminderDialog.kt \
        app/src/main/java/it/robertofichera/myshoppinglist/ui/ListDetailScreen.kt \
        app/src/main/res/values/strings.xml app/src/main/res/values-*/strings.xml
git commit -m "feat: pick a list's reminder from its top bar"
```

---

### Task 6: The reminder on the overview card, and the sound row in Settings

**Files:**
- Modify: `app/src/main/java/it/robertofichera/myshoppinglist/ui/ListsScreen.kt`
- Modify: `app/src/main/java/it/robertofichera/myshoppinglist/ui/SettingsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml` and the eleven `values-*/strings.xml`

**Interfaces:**
- Consumes: `ShoppingList.remindAt` (Task 1); `ensureReminderChannel`, `REMINDER_CHANNEL` (Task 3).

- [ ] **Step 1: Strings**

Add to `values/strings.xml`, after `reminder_clear`:

```xml
    <string name="list_reminder">Reminder %1$s</string>
    <string name="settings_reminder_sound">Reminder sound</string>
    <string name="settings_reminder_sound_desc">Sound and vibration, in the system\'s notification settings</string>
```

And in every translation folder, after its `reminder_clear`:

| folder | list_reminder | settings_reminder_sound | settings_reminder_sound_desc |
|---|---|---|---|
| values-en | Reminder %1$s | Reminder sound | Sound and vibration, in the system\'s notification settings |
| values-it | Promemoria %1$s | Suono del promemoria | Suono e vibrazione, nelle impostazioni di notifica del sistema |
| values-de | Erinnerung %1$s | Erinnerungston | Ton und Vibration, in den Benachrichtigungseinstellungen des Systems |
| values-nl | Herinnering %1$s | Herinneringsgeluid | Geluid en trillen, in de meldingsinstellingen van het systeem |
| values-es | Recordatorio %1$s | Sonido del recordatorio | Sonido y vibración, en los ajustes de notificaciones del sistema |
| values-pt | Lembrete %1$s | Som do lembrete | Som e vibração, nas definições de notificações do sistema |
| values-pt-rBR | Lembrete %1$s | Som do lembrete | Som e vibração, nas configurações de notificação do sistema |
| values-fr | Rappel %1$s | Son du rappel | Son et vibration, dans les paramètres de notification du système |
| values-el | Υπενθύμιση %1$s | Ήχος υπενθύμισης | Ήχος και δόνηση, στις ρυθμίσεις ειδοποιήσεων του συστήματος |
| values-pl | Przypomnienie %1$s | Dźwięk przypomnienia | Dźwięk i wibracje, w ustawieniach powiadomień systemu |
| values-hu | Emlékeztető: %1$s | Emlékeztető hangja | Hang és rezgés, a rendszer értesítési beállításaiban |

- [ ] **Step 2: The line on the card**

In `ListsScreen.kt` add the import:

```kotlin
import androidx.compose.material.icons.filled.Notifications
```

In the card's `Column(modifier = Modifier.weight(1f))`, directly after the line
`CaptionLine(timestampLine(entry.list.createdAt, entry.list.updatedAt))`, add:

```kotlin
                if (entry.list.remindAt > 0) ReminderLine(entry.list.remindAt)
```

And add this private composable after `CaptionLine`:

```kotlin
/** A reminder never answered stays on the card in the error colour, so the overview says so. */
@Composable
private fun ReminderLine(remindAt: Long) {
    val overdue = remindAt < System.currentTimeMillis()
    val color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.Notifications,
            contentDescription = null,
            tint = color,
            modifier = Modifier.padding(end = 4.dp).size(14.dp),
        )
        Text(
            stringResource(R.string.list_reminder, formatWhen(remindAt)),
            style = MaterialTheme.typography.bodySmall,
            color = color,
        )
    }
}
```

(`formatWhen` already exists in this file and formats through `DateUtils.formatDateTime`.)

- [ ] **Step 3: The Settings row**

In `SettingsScreen.kt` add the imports:

```kotlin
import it.robertofichera.myshoppinglist.data.REMINDER_CHANNEL
import it.robertofichera.myshoppinglist.data.ensureReminderChannel
```

Inside `SettingsScreen`, the block that defines `val context = LocalContext.current` and the `openUrl` lambda comes right after the Products row. Directly after the `openUrl` lambda's closing `}` (and before `val appName = ...`), insert:

```kotlin
            NavigationRow(
                title = stringResource(R.string.settings_reminder_sound),
                subtitle = stringResource(R.string.settings_reminder_sound_desc),
                onClick = {
                    // The channel must exist before its settings page can be opened.
                    ensureReminderChannel(context)
                    runCatching {
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, REMINDER_CHANNEL),
                        )
                    }
                },
            )
            HorizontalDivider()
```

`android.provider.Settings` is written out because this file already imports the app's own `data.Settings`.

- [ ] **Step 4: Build, lint, test**

Run: `./gradlew assembleDebug lintDebug testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Install and look**

Run: `./gradlew installDebug`
The list with a reminder shows the bell line under its "Created / Updated" line. Settings → Reminder sound opens the system page for the "Reminders" channel.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/it/robertofichera/myshoppinglist/ui/ListsScreen.kt \
        app/src/main/java/it/robertofichera/myshoppinglist/ui/SettingsScreen.kt \
        app/src/main/res/values/strings.xml app/src/main/res/values-*/strings.xml
git commit -m "feat: show a list's reminder on its card, and reach the channel's sound settings"
```

---

### Task 7: Project guide

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: Layout table**

In the `data/` section of the layout table, change the `Entities.kt` line to read

```
  Entities.kt          ShoppingList (uuid, colorArgb, updatedAt, sharedBy, remindAt) + Product + Item @Entity,
                       ItemWithProduct, ListWithItems, ProductWithUsage, total helpers
```

change the `AppDatabase.kt` line to `@Database(version = 8)` and append `, MIGRATION_7_8` to its migration list, and add after the `Countries.kt` line:

```
  Reminders.kt         reminderAt (pure), schedule/cancel via AlarmManager, the notification,
                       ReminderReceiver (fire / done / postpone) and BootReceiver
```

In the `ui/` section, after the `NameDialog.kt` line:

```
  ReminderDialog.kt    the day, then the time, for a list's reminder; Clear when one is set
```

- [ ] **Step 2: Key decisions**

Add this paragraph after the "**A list carries when it changed and who sent it.**" paragraph:

```markdown
**A list's reminder is one column, and the alarm follows it.** `ShoppingList.remindAt` is epoch
millis, 0 for none, and every change to it goes through `ShoppingViewModel.setReminder` or
`ReminderReceiver`, each of which writes the row and then arms or cancels the alarm — so the two
cannot disagree, and `BootReceiver` can rebuild every alarm from the table alone after a reboot.
The alarm is `AlarmManager.setAlarmClock`: exact, awake through Doze, and shown in the shade as a
pending alarm. `USE_EXACT_ALARM` covers Android 13+ at install; on 12 the revocable
`SCHEDULE_EXACT_ALARM` applies and a withdrawal degrades to a ten-minute `setWindow` rather than
losing the reminder. The notification is heads-up because its channel is `IMPORTANCE_HIGH`; sound
is the channel's, set in the system's settings, which the Settings row opens. Done and Postpone are
broadcasts to the receiver; swiping the notification away is Done, so nothing lingers silently
overdue, while a body tap only opens the list and leaves it standing. The date picker reports a
day as midnight UTC and the time picker a wall-clock time, and `reminderAt` is the one place that
composes them in the device's zone.
```

- [ ] **Step 3: Testing**

Add after the `ShareCodecTest.kt` paragraph in the Testing section:

```markdown
`RemindersTest.kt` covers `reminderAt`. The date picker speaks UTC midnight and the time picker
wall-clock time; composed in the wrong zone, a reminder is silently an offset late, which is a
reminder that never worked.
```

- [ ] **Step 4: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: reminders in the project guide"
```

---

### Task 8: Device verification

No code. Everything below is checked on a phone or the emulator with the debug build from Task 6. Record what was and was not verified in a memory file the way `loyalty-cards-on-device.md` does.

- [ ] **Step 1: Migration.** On a device holding the v7 database (`main`'s build), `./gradlew installDebug` without uninstalling. Open every list. Then:
  `adb shell "run-as it.robertofichera.myshoppinglist sqlite3 databases/shopping.db 'PRAGMA foreign_key_check; PRAGMA user_version;'"` — expected: no rows, then `8`.
- [ ] **Step 2: Fires.** Set a reminder two minutes out, lock the phone. Expected: heads-up notification at that minute with the channel's sound, title = list name, actions Done and Postpone 1 h.
- [ ] **Step 3: Done.** Tap Done. Expected: notification gone; the card's reminder line gone.
- [ ] **Step 4: Postpone.** Set one two minutes out, wait for it, tap Postpone 1 h. Expected: notification gone; card shows a time one hour from now. (Optionally shorten `POSTPONE_MILLIS` locally to 2 minutes to see it return, then revert.)
- [ ] **Step 5: Body tap.** Set one, wait, tap the notification body while on the Settings screen. Expected: app lands inside the list, notification still showing. Swipe it away: card's line gone.
- [ ] **Step 6: Reboot.** Set one five minutes out, reboot before it fires. Expected: it fires at its time. Set one two minutes out, power off, wait past it, boot. Expected: it fires shortly after boot.
- [ ] **Step 7: Deleted list.** Set one, delete the list before it fires. Expected: nothing at its time.
- [ ] **Step 8: Permission refused.** Uninstall, install, set a reminder, refuse the permission prompt. Expected: card shows the reminder; nothing is posted at its time; after granting in system app settings, the next one shows.
- [ ] **Step 9: Overdue colour.** Leave a fired reminder unanswered, go back to the overview. Expected: its line in the error colour.
- [ ] **Step 10: Release build.** `./gradlew assembleRelease -PemulatorAbi`, install on the emulator, repeat Step 2 there. R8 must keep both receivers (they are named in the manifest, which AGP feeds to R8, so no keep rule is expected — this step is what proves it).
