package it.robertofichera.myshoppinglist.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import it.robertofichera.myshoppinglist.R
import it.robertofichera.myshoppinglist.data.ShareCodec

/** The name that signs a shared list. Blank is allowed: it means shares go out unsigned. */
@Composable
fun NameDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_your_name)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= ShareCodec.MAX_SHARED_BY) name = it },
                label = { Text(stringResource(R.string.settings_your_name)) },
                supportingText = { Text(stringResource(R.string.settings_your_name_desc)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
