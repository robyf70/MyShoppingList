package it.robertofichera.myshoppinglist.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import it.robertofichera.myshoppinglist.data.ItemWithProduct
import it.robertofichera.myshoppinglist.data.Product
import it.robertofichera.myshoppinglist.data.filterProducts
import it.robertofichera.myshoppinglist.data.isSettledOn
import it.robertofichera.myshoppinglist.formatCents
import it.robertofichera.myshoppinglist.formatQuantity
import it.robertofichera.myshoppinglist.parsePriceCents
import it.robertofichera.myshoppinglist.parseQuantity

/**
 * Add or edit an item. Hidden fields keep whatever the item already had,
 * so toggling a field off in Settings never destroys data.
 */
@Composable
fun ItemDialog(
    entry: ItemWithProduct?,
    products: List<Product>,
    showQuantity: Boolean,
    showPrice: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String, quantity: Double, priceCents: Long) -> Unit,
) {
    val item = entry?.item
    var name by remember { mutableStateOf(entry?.product?.name.orEmpty()) }
    var quantityText by remember {
        mutableStateOf(item?.let { formatQuantity(it.quantity) } ?: "1")
    }
    var priceText by remember { mutableStateOf(item?.priceCents.toPriceText()) }

    val quantity = if (showQuantity) parseQuantity(quantityText) else (item?.quantity ?: 1.0)
    val priceCents = when {
        !showPrice -> item?.priceCents ?: 0L
        priceText.isBlank() -> 0L
        else -> parsePriceCents(priceText)
    }
    val isValid = name.isNotBlank() && quantity != null && priceCents != null

    val matches = filterProducts(products, name)
    val suggestions = if (isSettledOn(matches, name)) emptyList() else matches

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "Add item" else "Edit item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Product") },
                    singleLine = true,
                )
                if (suggestions.isNotEmpty()) {
                    SuggestionList(
                        suggestions = suggestions,
                        showPrice = showPrice,
                        onPick = { product ->
                            name = product.name
                            if (showPrice && product.defaultPriceCents > 0) {
                                priceText = product.defaultPriceCents.toPriceText()
                            }
                        },
                    )
                }
                if (showQuantity) {
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { quantityText = it },
                        label = { Text("Quantity") },
                        singleLine = true,
                        isError = quantityText.isNotBlank() && quantity == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                if (showPrice) {
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it },
                        label = { Text("Price") },
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
                onClick = { onConfirm(name, quantity ?: 1.0, priceCents ?: 0L) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SuggestionList(
    suggestions: List<Product>,
    showPrice: Boolean,
    onPick: (Product) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            suggestions.forEachIndexed { index, product ->
                if (index > 0) HorizontalDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(product) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(product.name, style = MaterialTheme.typography.bodyMedium)
                    if (showPrice && product.defaultPriceCents > 0) {
                        Text(
                            formatCents(product.defaultPriceCents),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun Long?.toPriceText(): String =
    if (this == null || this <= 0) "" else "%.2f".format(this / 100.0)
