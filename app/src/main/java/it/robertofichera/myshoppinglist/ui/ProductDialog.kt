package it.robertofichera.myshoppinglist.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.robertofichera.myshoppinglist.R
import it.robertofichera.myshoppinglist.data.Product
import it.robertofichera.myshoppinglist.parsePriceCents

/**
 * Create or edit a catalog product. [takenNames] must be lowercased and exclude the
 * product being edited; product names are unique, so a clash is refused here rather
 * than left to the database.
 */
@Composable
fun ProductDialog(
    product: Product?,
    takenNames: Set<String>,
    showPrice: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String, defaultPriceCents: Long) -> Unit,
) {
    var name by remember { mutableStateOf(product?.name.orEmpty()) }
    var priceText by remember {
        mutableStateOf(
            product?.takeIf { it.defaultPriceCents > 0 }
                ?.let { "%.2f".format(it.defaultPriceCents / 100.0) } ?: ""
        )
    }

    val isTaken = name.trim().lowercase() in takenNames
    val priceCents = when {
        !showPrice -> product?.defaultPriceCents ?: 0L
        priceText.isBlank() -> 0L
        else -> parsePriceCents(priceText)
    }
    val isValid = name.isNotBlank() && !isTaken && priceCents != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (product == null) R.string.product_add else R.string.product_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.field_name)) },
                    singleLine = true,
                    isError = isTaken,
                )
                if (isTaken) {
                    Text(
                        stringResource(R.string.name_taken, name.trim()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (showPrice) {
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it },
                        label = { Text(stringResource(R.string.field_default_price)) },
                        singleLine = true,
                        isError = priceText.isNotBlank() && priceCents == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = { onConfirm(name, priceCents ?: 0L) },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
