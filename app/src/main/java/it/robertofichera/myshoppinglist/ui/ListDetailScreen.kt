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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.content.Context
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import it.robertofichera.myshoppinglist.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.robertofichera.myshoppinglist.BarcodeState
import it.robertofichera.myshoppinglist.ScanState
import it.robertofichera.myshoppinglist.ShoppingViewModel
import it.robertofichera.myshoppinglist.data.ItemWithProduct
import it.robertofichera.myshoppinglist.data.ScannedItem
import it.robertofichera.myshoppinglist.data.nameOf
import it.robertofichera.myshoppinglist.data.newCameraImageUri
import it.robertofichera.myshoppinglist.data.ListWithItems
import it.robertofichera.myshoppinglist.data.Product
import it.robertofichera.myshoppinglist.data.lineTotalCents
import it.robertofichera.myshoppinglist.data.overspendCents
import it.robertofichera.myshoppinglist.data.remainingCents
import it.robertofichera.myshoppinglist.data.spentCents
import it.robertofichera.myshoppinglist.data.totalCents
import it.robertofichera.myshoppinglist.LocalMoneyFormat
import it.robertofichera.myshoppinglist.MoneyFormat
import it.robertofichera.myshoppinglist.formatCents
import it.robertofichera.myshoppinglist.formatQuantity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListDetailScreen(
    entry: ListWithItems,
    products: List<Product>,
    showQuantity: Boolean,
    showPrice: Boolean,
    budgetEnabled: Boolean,
    confirmDelete: Boolean,
    viewModel: ShoppingViewModel,
    onBack: () -> Unit,
) {
    var addingItem by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<ItemWithProduct?>(null) }
    var deletingItem by remember { mutableStateOf<ItemWithProduct?>(null) }
    var pendingTick by remember { mutableStateOf<ItemWithProduct?>(null) }
    var scanSourceOpen by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf<ScannedItem?>(null) }
    val barcode by viewModel.barcode.collectAsStateWithLifecycle()

    val scan by viewModel.scan.collectAsStateWithLifecycle()
    val scanContext = LocalContext.current
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.scanImage(it) }
    }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        if (taken) cameraUri?.let { viewModel.scanImage(it) }
    }

    val budgetCents = if (budgetEnabled) entry.list.budgetCents else 0L
    val spent = entry.spentCents
    val listColor = entry.list.colorArgb
    val barColors = if (listColor == COLOR_DEFAULT) {
        TopAppBarDefaults.topAppBarColors()
    } else {
        // One derived colour for every element on the bar, so a custom pick stays legible.
        val on = readableOn(listColor)
        TopAppBarDefaults.topAppBarColors(
            containerColor = Color(listColor),
            titleContentColor = on,
            navigationIconContentColor = on,
            actionIconContentColor = on,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = barColors,
                title = {
                    Text(
                        if (budgetCents > 0) {
                            stringResource(
                                R.string.format_title_budget,
                                entry.list.name,
                                formatCents(budgetCents),
                            )
                        } else {
                            entry.list.name
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    val context = LocalContext.current
                    val money = LocalMoneyFormat.current
                    IconButton(
                        onClick = {
                            sendText(context, entry.list.name, viewModel.shareText(entry, money))
                        },
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = stringResource(R.string.action_share),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SmallFloatingActionButton(onClick = { scanSourceOpen = true }) {
                    Icon(
                        painterResource(R.drawable.ic_photo_camera),
                        contentDescription = stringResource(R.string.item_add_from_image),
                    )
                }
                FloatingActionButton(onClick = { addingItem = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.item_add))
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (showPrice) {
                SummaryBar(
                    toSpendCents = entry.totalCents,
                    spentCents = spent,
                    budgetCents = budgetCents,
                )
            }
            if (entry.items.isEmpty()) {
                EmptyState(stringResource(R.string.detail_empty))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(entry.items, key = { it.item.id }) { row ->
                        ItemRow(
                            row = row,
                            showQuantity = showQuantity,
                            showPrice = showPrice,
                            onToggle = {
                                // Only ticking on can breach the budget; unticking frees it up.
                                val overspend = if (row.item.bought) null else {
                                    overspendCents(spent, row.lineTotalCents, budgetCents)
                                }
                                if (overspend == null) {
                                    viewModel.toggleBought(row.item)
                                } else {
                                    pendingTick = row
                                }
                            },
                            onEdit = { editingItem = row },
                            onDelete = {
                                if (confirmDelete) deletingItem = row else viewModel.deleteItem(row.item)
                            },
                        )
                    }
                }
            }
        }
    }

    if (scanSourceOpen) {
        AlertDialog(
            onDismissRequest = { scanSourceOpen = false },
            title = { Text(stringResource(R.string.item_add_from_image)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.scan_source_message))
                    TextButton(
                        onClick = {
                            scanSourceOpen = false
                            scanBarcode(
                                context = scanContext,
                                onScanned = { code -> viewModel.resolveBarcode(code) },
                                onUnavailable = viewModel::barcodeUnavailable,
                            )
                        },
                    ) { Text(stringResource(R.string.scan_barcode)) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scanSourceOpen = false
                    val uri = newCameraImageUri(scanContext)
                    cameraUri = uri
                    takePicture.launch(uri)
                }) { Text(stringResource(R.string.scan_camera)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    scanSourceOpen = false
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text(stringResource(R.string.scan_pick)) }
            },
        )
    }


    when (val state = barcode) {
        is BarcodeState.None -> Unit

        is BarcodeState.Unavailable -> AlertDialog(
            onDismissRequest = viewModel::dismissBarcode,
            text = { Text(stringResource(R.string.barcode_unavailable)) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissBarcode) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )

        is BarcodeState.Working -> AlertDialog(
            onDismissRequest = viewModel::dismissBarcode,
            text = { Text(stringResource(R.string.barcode_working)) },
            confirmButton = {},
        )

        is BarcodeState.Found -> ScannedItemDialog(
            item = ScannedItem(
                name = state.name,
                quantity = 1.0,
                priceCents = state.product?.defaultPriceCents ?: 0,
            ),
            showQuantity = showQuantity,
            showPrice = showPrice,
            onDismiss = viewModel::dismissBarcode,
            onConfirm = { viewModel.addScannedBarcode(entry.list.id, state.barcode, it) },
        )
    }

    when (val state = scan) {
        is ScanState.None -> Unit

        is ScanState.Working -> AlertDialog(
            onDismissRequest = viewModel::dismissScan,
            text = { Text(stringResource(R.string.scan_working)) },
            confirmButton = {},
        )

        is ScanState.Empty -> AlertDialog(
            onDismissRequest = viewModel::dismissScan,
            text = { Text(stringResource(R.string.scan_none)) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissScan) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )

        is ScanState.Found -> {
            // The picture is always what the reader works from: a rule for finding the label is a
            // guess, and pointing at it is not. What was guessed starts marked.
            val settled = settling
            when {
                settled != null -> ScannedItemDialog(
                    item = settled,
                    showQuantity = showQuantity,
                    showPrice = showPrice,
                    onDismiss = { settling = null },
                    onConfirm = { item ->
                        settling = null
                        viewModel.addScanned(entry.list.id, listOf(item))
                    },
                )


                else -> ScanPickScreen(
                    uri = state.uri,
                    lines = state.lines,
                    initiallyPicked = state.picked,
                    onConfirm = { chosen ->
                        settling = ScannedItem(nameOf(chosen), quantity = 1.0, priceCents = 0)
                    },
                    onBack = viewModel::dismissScan,
                )
            }
        }
    }

    pendingTick?.let { row ->
        val overspend = overspendCents(spent, row.lineTotalCents, budgetCents) ?: 0L
        AlertDialog(
            onDismissRequest = { pendingTick = null },
            title = { Text(stringResource(R.string.budget_exceeded_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.budget_exceeded_message,
                        row.product.name,
                        formatCents(overspend),
                        formatCents(budgetCents),
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.toggleBought(row.item)
                    pendingTick = null
                }) { Text(stringResource(R.string.action_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingTick = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    deletingItem?.let { row ->
        ConfirmDeleteDialog(
            name = row.product.name,
            message = stringResource(R.string.delete_item_message),
            onConfirm = { viewModel.deleteItem(row.item) },
            onDismiss = { deletingItem = null },
        )
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

/** Remaining is only meaningful against a budget, so that column appears only when one is set. */
@Composable
private fun SummaryBar(toSpendCents: Long, spentCents: Long, budgetCents: Long) {
    val remaining = remainingCents(budgetCents, spentCents)

    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            SummaryFigure(stringResource(R.string.summary_to_spend), formatCents(toSpendCents), Modifier.weight(1f))
            SummaryFigure(stringResource(R.string.summary_spent), formatCents(spentCents), Modifier.weight(1f))
            if (budgetCents > 0) {
                SummaryFigure(
                    label = stringResource(R.string.summary_remaining),
                    value = formatCents(remaining),
                    modifier = Modifier.weight(1f),
                    valueColor = if (remaining < 0) MaterialTheme.colorScheme.error else null,
                )
            }
        }
    }
}

@Composable
private fun SummaryFigure(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
) {
    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
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
            itemSubtitle(LocalContext.current, LocalMoneyFormat.current, row, showQuantity, showPrice)?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_named, row.product.name))
        }
    }
}

/**
 * With quantity hidden the price alone would misrepresent a "3 × €2" line,
 * so that case shows the line total instead.
 */
private fun itemSubtitle(
    context: Context,
    money: MoneyFormat,
    row: ItemWithProduct,
    showQuantity: Boolean,
    showPrice: Boolean,
): String? = when {
    showQuantity && showPrice ->
        context.getString(
            R.string.format_qty_price_total,
            formatQuantity(row.item.quantity),
            money.format(row.item.priceCents),
            money.format(row.lineTotalCents),
        )

    showQuantity -> context.getString(R.string.format_qty_only, formatQuantity(row.item.quantity))
    showPrice -> money.format(row.lineTotalCents)
    else -> null
}
