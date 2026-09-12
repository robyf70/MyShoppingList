package it.robertofichera.myshoppinglist.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.robertofichera.myshoppinglist.R
import it.robertofichera.myshoppinglist.parsePriceCents

/**
 * Create or edit a list. Duplicate list names are allowed, so the only rule is a
 * non-blank name. A blank budget means none, and the field is hidden entirely
 * when budgeting is switched off in Settings. The reminder is part of the form:
 * picked through [ReminderDialog], kept with the name and colour until Save,
 * and dropped with them on Cancel.
 */
@Composable
fun ListDialog(
    title: String,
    initialName: String = "",
    initialBudgetCents: Long = 0,
    initialColorArgb: Int = COLOR_DEFAULT,
    initialRemindAt: Long = 0,
    showBudget: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String, budgetCents: Long, colorArgb: Int, remindAt: Long) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var colorArgb by remember { mutableStateOf(initialColorArgb) }
    var remindAt by remember { mutableStateOf(initialRemindAt) }
    var pickingReminder by remember { mutableStateOf(false) }
    var budgetText by remember {
        mutableStateOf(
            if (initialBudgetCents > 0) "%.2f".format(initialBudgetCents / 100.0) else ""
        )
    }

    val budgetCents = when {
        !showBudget -> initialBudgetCents
        budgetText.isBlank() -> 0L
        else -> parsePriceCents(budgetText)
    }
    val isValid = name.isNotBlank() && budgetCents != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.field_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
                if (showBudget) {
                    OutlinedTextField(
                        value = budgetText,
                        onValueChange = { budgetText = it },
                        label = { Text(stringResource(R.string.field_budget)) },
                        singleLine = true,
                        isError = budgetText.isNotBlank() && budgetCents == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                ColorChoice(selected = colorArgb, onSelect = { colorArgb = it })
                OutlinedButton(
                    onClick = { pickingReminder = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp).size(18.dp),
                    )
                    Text(
                        if (remindAt > 0) {
                            stringResource(R.string.list_reminder, formatWhen(remindAt))
                        } else {
                            stringResource(R.string.reminder_title)
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = { onConfirm(name, budgetCents ?: 0L, colorArgb, remindAt) },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )

    if (pickingReminder) {
        ReminderDialog(
            remindAt = remindAt,
            onSet = { at ->
                remindAt = at
                pickingReminder = false
            },
            onClear = {
                remindAt = 0
                pickingReminder = false
            },
            onDismiss = { pickingReminder = false },
        )
    }
}
