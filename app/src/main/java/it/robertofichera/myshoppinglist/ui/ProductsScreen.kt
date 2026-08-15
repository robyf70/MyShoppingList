package it.robertofichera.myshoppinglist.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.robertofichera.myshoppinglist.R
import it.robertofichera.myshoppinglist.ShoppingViewModel
import it.robertofichera.myshoppinglist.data.Product
import it.robertofichera.myshoppinglist.data.ProductWithUsage
import it.robertofichera.myshoppinglist.formatCents

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(
    products: List<ProductWithUsage>,
    showPrice: Boolean,
    viewModel: ShoppingViewModel,
    onBack: () -> Unit,
) {
    var editing by remember { mutableStateOf<Product?>(null) }
    var adding by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.products_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.product_new))
            }
        },
    ) { padding ->
        if (products.isEmpty()) {
            EmptyState(
                stringResource(R.string.products_empty),
                Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(products, key = { it.product.id }) { entry ->
                    ProductRow(
                        entry = entry,
                        showPrice = showPrice,
                        onEdit = { editing = entry.product },
                        onDelete = { viewModel.deleteProduct(entry.product) },
                    )
                }
            }
        }
    }

    val allNames = products.map { it.product.name.lowercase() }.toSet()

    if (adding) {
        ProductDialog(
            product = null,
            takenNames = allNames,
            showPrice = showPrice,
            onDismiss = { adding = false },
            onConfirm = { name, priceCents ->
                viewModel.addProduct(name, priceCents)
                adding = false
            },
        )
    }

    editing?.let { product ->
        ProductDialog(
            product = product,
            takenNames = allNames - product.name.lowercase(),
            showPrice = showPrice,
            onDismiss = { editing = null },
            onConfirm = { name, priceCents ->
                viewModel.updateProduct(product, name, priceCents)
                editing = null
            },
        )
    }
}

@Composable
private fun ProductRow(
    entry: ProductWithUsage,
    showPrice: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val isInUse = entry.usageCount > 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.product.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle(entry, showPrice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Deleting a product still on a list would break those items, so it stays disabled
        // until they are gone; the usage count above says why.
        IconButton(onClick = onDelete, enabled = !isInUse) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_named, entry.product.name))
        }
    }
}

@Composable
private fun subtitle(entry: ProductWithUsage, showPrice: Boolean): String {
    val usage = if (entry.usageCount == 0) {
        stringResource(R.string.product_unused)
    } else {
        pluralStringResource(R.plurals.product_usage, entry.usageCount, entry.usageCount)
    }
    val price = entry.product.defaultPriceCents
    return if (showPrice && price > 0) {
        stringResource(R.string.format_dot_pair, formatCents(price), usage)
    } else {
        usage
    }
}
