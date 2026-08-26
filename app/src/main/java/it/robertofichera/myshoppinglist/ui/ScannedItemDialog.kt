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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.robertofichera.myshoppinglist.R
import it.robertofichera.myshoppinglist.data.ScannedItem
import it.robertofichera.myshoppinglist.formatQuantity
import it.robertofichera.myshoppinglist.parsePriceCents
import it.robertofichera.myshoppinglist.parseQuantity

/** Settling an item before it is added: the same rules as typing one in by hand. */
@Composable
fun ScannedItemDialog(
    item: ScannedItem,
    showQuantity: Boolean,
    showPrice: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (ScannedItem) -> Unit,
) {
    var name by remember { mutableStateOf(item.name) }
    var quantityText by remember { mutableStateOf(formatQuantity(item.quantity)) }
    var priceText by remember {
        mutableStateOf(if (item.priceCents > 0) "%.2f".format(item.priceCents / 100.0) else "")
    }

    val quantity = if (!showQuantity) item.quantity else parseQuantity(quantityText)
    val priceCents = when {
        !showPrice -> item.priceCents
        priceText.isBlank() -> 0L
        else -> parsePriceCents(priceText)
    }
    val isValid = name.isNotBlank() && quantity != null && priceCents != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.item_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.field_product)) },
                    singleLine = true,
                )
                if (showQuantity) {
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { quantityText = it },
                        label = { Text(stringResource(R.string.field_quantity)) },
                        singleLine = true,
                        isError = quantity == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                if (showPrice) {
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it },
                        label = { Text(stringResource(R.string.field_price)) },
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
                onClick = {
                    onConfirm(
                        item.copy(
                            name = name.trim(),
                            quantity = quantity ?: item.quantity,
                            priceCents = priceCents ?: 0L,
                        ),
                    )
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
