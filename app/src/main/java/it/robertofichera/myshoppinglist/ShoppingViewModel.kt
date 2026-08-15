package it.robertofichera.myshoppinglist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import it.robertofichera.myshoppinglist.data.AppDatabase
import it.robertofichera.myshoppinglist.data.Item
import it.robertofichera.myshoppinglist.data.ListWithItems
import it.robertofichera.myshoppinglist.data.Product
import it.robertofichera.myshoppinglist.data.SettingsStore
import it.robertofichera.myshoppinglist.data.ShoppingList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ShoppingViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.getInstance(app)
    private val dao = db.shoppingDao()
    private val settingsStore = SettingsStore(app)

    val settings = settingsStore.settings

    val lists = dao.observeLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val products = dao.observeProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val productsWithUsage = dao.observeProductsWithUsage()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun observeList(listId: Long): Flow<ListWithItems?> = dao.observeList(listId)

    fun addList(name: String, budgetCents: Long) = viewModelScope.launch {
        dao.insertList(ShoppingList(name = name.trim(), budgetCents = budgetCents))
    }

    fun updateList(list: ShoppingList, name: String, budgetCents: Long) = viewModelScope.launch {
        dao.updateList(list.copy(name = name.trim(), budgetCents = budgetCents))
    }

    fun deleteList(list: ShoppingList) = viewModelScope.launch { dao.deleteList(list) }

    /** A copy starts a fresh trip: same items and budget, nothing ticked off yet. */
    fun copyList(entry: ListWithItems) = viewModelScope.launch {
        db.withTransaction {
            val newId = dao.insertList(
                ShoppingList(
                    name = getApplication<Application>()
                        .getString(R.string.list_copy_name, entry.list.name),
                    budgetCents = entry.list.budgetCents,
                )
            )
            entry.items.forEach { row ->
                dao.insertItem(row.item.copy(id = 0, listId = newId, bought = false))
            }
        }
    }

    fun addItem(listId: Long, productName: String, quantity: Double, priceCents: Long) =
        viewModelScope.launch {
            val productId = productIdFor(productName, priceCents)
            dao.insertItem(
                Item(
                    listId = listId,
                    productId = productId,
                    quantity = quantity,
                    priceCents = priceCents,
                )
            )
        }

    fun updateItem(item: Item, productName: String, quantity: Double, priceCents: Long) =
        viewModelScope.launch {
            val productId = productIdFor(productName, priceCents)
            dao.updateItem(
                item.copy(productId = productId, quantity = quantity, priceCents = priceCents)
            )
        }

    fun toggleBought(item: Item) = viewModelScope.launch {
        dao.updateItem(item.copy(bought = !item.bought))
    }

    fun deleteItem(item: Item) = viewModelScope.launch { dao.deleteItem(item) }

    /**
     * The dialog already refuses a duplicate name; this re-checks because the unique
     * index would otherwise throw rather than telling the user anything useful.
     */
    fun addProduct(name: String, defaultPriceCents: Long) = viewModelScope.launch {
        val trimmed = name.trim()
        val existing = dao.findProductByName(trimmed)
        if (existing == null) {
            dao.insertProduct(Product(name = trimmed, defaultPriceCents = defaultPriceCents))
        } else {
            dao.updateProduct(existing.copy(defaultPriceCents = defaultPriceCents))
        }
    }

    fun updateProduct(product: Product, name: String, defaultPriceCents: Long) =
        viewModelScope.launch {
            dao.updateProduct(
                product.copy(name = name.trim(), defaultPriceCents = defaultPriceCents)
            )
        }

    fun deleteProduct(product: Product) = viewModelScope.launch { dao.deleteProduct(product) }

    fun setShowQuantity(show: Boolean) = settingsStore.setShowQuantity(show)

    fun setShowPrice(show: Boolean) = settingsStore.setShowPrice(show)

    fun setBudgetEnabled(enabled: Boolean) = settingsStore.setBudgetEnabled(enabled)

    /**
     * Reuses the matching product or creates it, and remembers the price entered
     * so the next list prefills what this one actually cost.
     */
    private suspend fun productIdFor(name: String, priceCents: Long): Long {
        val trimmed = name.trim()
        val existing = dao.findProductByName(trimmed)
        if (existing == null) {
            return dao.insertProduct(Product(name = trimmed, defaultPriceCents = priceCents))
        }
        if (priceCents > 0 && priceCents != existing.defaultPriceCents) {
            dao.updateProductPrice(existing.id, priceCents)
        }
        return existing.id
    }
}
