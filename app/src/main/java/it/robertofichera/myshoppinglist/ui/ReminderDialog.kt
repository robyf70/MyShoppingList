package it.robertofichera.myshoppinglist.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
 * The day first, then the time. [remindAt] is the reminder already set, or 0; a new one, or one
 * already past, starts [POSTPONE_MILLIS] from now. Saving asks for the notification permission
 * when it is not yet held, and saves whatever the answer: a refusal only mutes the notification,
 * and the system's app settings can grant it later without touching the reminder.
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
        // A reminder already past would preset a day the picker refuses.
        val now = System.currentTimeMillis()
        val at = if (remindAt > now) remindAt else now + POSTPONE_MILLIS
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
        // The permission exists from Android 13; below it, notifications need no grant.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
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
