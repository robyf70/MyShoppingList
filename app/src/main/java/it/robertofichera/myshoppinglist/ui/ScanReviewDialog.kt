package it.robertofichera.myshoppinglist.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import it.robertofichera.myshoppinglist.R
import it.robertofichera.myshoppinglist.data.ScannedItem
import it.robertofichera.myshoppinglist.formatCents
import it.robertofichera.myshoppinglist.formatQuantity
import it.robertofichera.myshoppinglist.parsePriceCents
import it.robertofichera.myshoppinglist.parseQuantity

/**
 * What was read from the picture, for the reader to approve. Everything starts ticked because the
 * words are usually right; a row can be corrected before it is added, because the numbers often
 * are not — recognition reads a handwritten "0.5" as "5" readily enough, and a wrong price that
 * looks plausible is worse than one that is obviously missing.
 */
@Composable
fun ScanReviewDialog(
    items: List<ScannedItem>,
    showQuantity: Boolean,
    showPrice: Boolean,
    onConfirm: (List<ScannedItem>) -> Unit,
    onDismiss: () -> Unit,
) {
    val rows = remember(items) { items.toMutableStateList() }
    val checked = remember(items) { items.map { true }.toMutableStateList() }
    var editing by remember(items) { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scan_title)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                itemsIndexed(rows) { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { checked[index] = !checked[index] }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked[index],
                            onCheckedChange = { checked[index] = it },
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.bodyLarge)
                            scannedSubtitle(item, showQuantity, showPrice)?.let { subtitle ->
                                Text(
                                    subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        IconButton(onClick = { editing = index }) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = stringResource(R.string.action_edit),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = checked.any { it },
                onClick = { onConfirm(rows.filterIndexed { index, _ -> checked[index] }) },
            ) { Text(stringResource(R.string.action_continue)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )

    editing?.let { index ->
        ScannedItemDialog(
            item = rows[index],
            showQuantity = showQuantity,
            showPrice = showPrice,
            onDismiss = { editing = null },
            onConfirm = { corrected ->
                rows[index] = corrected
                checked[index] = true
                editing = null
            },
        )
    }
}

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

/** Only what the picture actually named, and only what Settings is showing. */
@Composable
private fun scannedSubtitle(item: ScannedItem, showQuantity: Boolean, showPrice: Boolean): String? {
    val quantity = if (showQuantity && item.quantity != 1.0) formatQuantity(item.quantity) else null
    val price = if (showPrice && item.priceCents > 0) formatCents(item.priceCents) else null
    return when {
        quantity != null && price != null -> stringResource(
            R.string.format_qty_price_total,
            quantity,
            price,
            formatCents(Math.round(item.quantity * item.priceCents)),
        )

        quantity != null -> stringResource(R.string.format_qty_only, quantity)
        price != null -> price
        else -> null
    }
}
