package it.robertofichera.myshoppinglist.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.robertofichera.myshoppinglist.R
import it.robertofichera.myshoppinglist.data.ScannedItem
import it.robertofichera.myshoppinglist.formatCents
import it.robertofichera.myshoppinglist.formatQuantity

/**
 * What was read from the picture, for the reader to approve. Everything starts ticked because
 * recognition is usually right; the untickable rubbish is the exception, not the rule.
 */
@Composable
fun ScanReviewDialog(
    items: List<ScannedItem>,
    showQuantity: Boolean,
    showPrice: Boolean,
    onConfirm: (List<ScannedItem>) -> Unit,
    onDismiss: () -> Unit,
) {
    val checked = remember(items) { mutableStateListOf<Boolean>().apply { addAll(items.map { true }) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scan_title)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                itemsIndexed(items) { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { checked[index] = !checked[index] }
                            .padding(vertical = 4.dp),
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
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = checked.any { it },
                onClick = { onConfirm(items.filterIndexed { index, _ -> checked[index] }) },
            ) { Text(stringResource(R.string.action_continue)) }
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
        quantity != null && price != null ->
            stringResource(R.string.format_qty_price_total, quantity, price, formatCents(Math.round(item.quantity * item.priceCents)))

        quantity != null -> stringResource(R.string.format_qty_only, quantity)
        price != null -> price
        else -> null
    }
}
