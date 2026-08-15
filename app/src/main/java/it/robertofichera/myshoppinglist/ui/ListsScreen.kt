package it.robertofichera.myshoppinglist.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import it.robertofichera.myshoppinglist.data.ListWithItems
import it.robertofichera.myshoppinglist.data.ShoppingList
import it.robertofichera.myshoppinglist.data.totalCents
import it.robertofichera.myshoppinglist.formatCents

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreen(
    lists: List<ListWithItems>,
    showPrice: Boolean,
    budgetEnabled: Boolean,
    viewModel: ShoppingViewModel,
    onOpenList: (Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var showNewDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ShoppingList?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.action_settings))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showNewDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.list_new))
            }
        },
    ) { padding ->
        if (lists.isEmpty()) {
            EmptyState(stringResource(R.string.lists_empty), Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(lists, key = { it.list.id }) { entry ->
                    ListCard(
                        entry = entry,
                        showPrice = showPrice,
                        onOpen = { onOpenList(entry.list.id) },
                        onEdit = { editing = entry.list },
                        onDuplicate = { viewModel.copyList(entry) },
                        onDelete = { viewModel.deleteList(entry.list) },
                    )
                }
            }
        }
    }

    if (showNewDialog) {
        ListDialog(
            title = stringResource(R.string.list_new),
            showBudget = budgetEnabled,
            onDismiss = { showNewDialog = false },
            onConfirm = { name, budgetCents ->
                viewModel.addList(name, budgetCents)
                showNewDialog = false
            },
        )
    }

    editing?.let { list ->
        ListDialog(
            title = stringResource(R.string.list_edit),
            initialName = list.name,
            initialBudgetCents = list.budgetCents,
            showBudget = budgetEnabled,
            onDismiss = { editing = null },
            onConfirm = { name, budgetCents ->
                viewModel.updateList(list, name, budgetCents)
                editing = null
            },
        )
    }
}

@Composable
private fun ListCard(
    entry: ListWithItems,
    showPrice: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val boughtCount = entry.items.count { it.item.bought }

    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.list.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    pluralStringResource(
                        R.plurals.list_summary,
                        entry.items.size,
                        entry.items.size,
                        boughtCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (showPrice) {
                Text(
                    formatCents(entry.totalCents),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.list_options))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_edit)) },
                        onClick = { menuOpen = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_duplicate)) },
                        onClick = { menuOpen = false; onDuplicate() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
