package it.robertofichera.myshoppinglist.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.robertofichera.myshoppinglist.R
import it.robertofichera.myshoppinglist.parsePriceCents

/**
 * Create or edit a list. Duplicate list names are allowed, so the only rule is a
 * non-blank name. A blank budget means none, and the field is hidden entirely
 * when budgeting is switched off in Settings.
 */
@Composable
fun ListDialog(
    title: String,
    initialName: String = "",
    initialBudgetCents: Long = 0,
    showBudget: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String, budgetCents: Long) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
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
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = { onConfirm(name, budgetCents ?: 0L) },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
