package it.robertofichera.myshoppinglist.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import it.robertofichera.myshoppinglist.ShoppingViewModel
import it.robertofichera.myshoppinglist.data.ItemWithProduct
import it.robertofichera.myshoppinglist.data.ListWithItems
import it.robertofichera.myshoppinglist.data.Product
import it.robertofichera.myshoppinglist.data.lineTotalCents
import it.robertofichera.myshoppinglist.data.totalCents
import it.robertofichera.myshoppinglist.formatCents
import it.robertofichera.myshoppinglist.formatQuantity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListDetailScreen(
    entry: ListWithItems,
    products: List<Product>,
    showQuantity: Boolean,
    showPrice: Boolean,
    viewModel: ShoppingViewModel,
    onBack: () -> Unit,
) {
    var addingItem by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<ItemWithProduct?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(entry.list.name)
                        if (showPrice) {
                            Text(
                                formatCents(entry.totalCents),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { addingItem = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add item")
            }
        },
    ) { padding ->
        if (entry.items.isEmpty()) {
            EmptyState("Nothing to buy yet.\nTap + to add a product.", Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(entry.items, key = { it.item.id }) { row ->
                    ItemRow(
                        row = row,
                        showQuantity = showQuantity,
                        showPrice = showPrice,
                        onToggle = { viewModel.toggleBought(row.item) },
                        onEdit = { editingItem = row },
                        onDelete = { viewModel.deleteItem(row.item) },
                    )
                }
            }
        }
    }

    if (addingItem) {
        ItemDialog(
            entry = null,
            products = products,
            showQuantity = showQuantity,
            showPrice = showPrice,
            onDismiss = { addingItem = false },
            onConfirm = { name, quantity, priceCents ->
                viewModel.addItem(entry.list.id, name, quantity, priceCents)
                addingItem = false
            },
        )
    }

    editingItem?.let { row ->
        ItemDialog(
            entry = row,
            products = products,
            showQuantity = showQuantity,
            showPrice = showPrice,
            onDismiss = { editingItem = null },
            onConfirm = { name, quantity, priceCents ->
                viewModel.updateItem(row.item, name, quantity, priceCents)
                editingItem = null
            },
        )
    }
}

@Composable
private fun ItemRow(
    row: ItemWithProduct,
    showQuantity: Boolean,
    showPrice: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val item = row.item
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = item.bought, onCheckedChange = { onToggle() })
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.product.name,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (item.bought) TextDecoration.LineThrough else null,
            )
            itemSubtitle(row, showQuantity, showPrice)?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete ${row.product.name}")
        }
    }
}

/**
 * With quantity hidden the price alone would misrepresent a "3 × €2" line,
 * so that case shows the line total instead.
 */
private fun itemSubtitle(
    row: ItemWithProduct,
    showQuantity: Boolean,
    showPrice: Boolean,
): String? = when {
    showQuantity && showPrice ->
        "${formatQuantity(row.item.quantity)} × ${formatCents(row.item.priceCents)} = " +
            formatCents(row.lineTotalCents)

    showQuantity -> "× ${formatQuantity(row.item.quantity)}"
    showPrice -> formatCents(row.lineTotalCents)
    else -> null
}
