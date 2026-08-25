package it.robertofichera.myshoppinglist.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import it.robertofichera.myshoppinglist.ImportState
import it.robertofichera.myshoppinglist.R

@Composable
fun ImportDialog(
    state: ImportState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        is ImportState.None -> Unit

        is ImportState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            text = { Text(stringResource(R.string.import_failed)) },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            },
        )

        is ImportState.Offered -> AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    stringResource(
                        if (state.existing == null) R.string.import_title else R.string.import_update_title,
                        state.shared.name,
                    )
                )
            },
            text = {
                Text(
                    stringResource(
                        if (state.existing == null) R.string.import_message else R.string.import_update_message,
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_continue)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
