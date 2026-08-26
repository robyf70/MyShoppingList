package it.robertofichera.myshoppinglist

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import it.robertofichera.myshoppinglist.data.AppDatabase
import it.robertofichera.myshoppinglist.data.Item
import it.robertofichera.myshoppinglist.data.ListWithItems
import it.robertofichera.myshoppinglist.data.Product
import it.robertofichera.myshoppinglist.data.DownloadResult
import it.robertofichera.myshoppinglist.data.Release
import it.robertofichera.myshoppinglist.data.ScannedItem
import it.robertofichera.myshoppinglist.data.ScannedLine
import it.robertofichera.myshoppinglist.data.matchProduct
import it.robertofichera.myshoppinglist.data.SettingsStore
import it.robertofichera.myshoppinglist.data.scanImageForItems
import it.robertofichera.myshoppinglist.data.ShoppingList
import it.robertofichera.myshoppinglist.data.downloadedApk
import it.robertofichera.myshoppinglist.data.enqueueDownload
import it.robertofichera.myshoppinglist.data.fetchLatestRelease
import it.robertofichera.myshoppinglist.data.installIntent
import it.robertofichera.myshoppinglist.data.ShareCodec
import it.robertofichera.myshoppinglist.data.SharedItem
import it.robertofichera.myshoppinglist.data.SharedList
import it.robertofichera.myshoppinglist.data.lineTotalCents
import it.robertofichera.myshoppinglist.data.totalCents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** [Offered.existing] is the local list this share replaces, or null when it is new here. */
/** What the picture yielded, from the moment one is chosen to the moment its items are added. */
sealed interface ScanState {
    data object None : ScanState
    data object Working : ScanState
    /** [lines] and [uri] are kept so the reader can point at the picture when the guess is wrong. */
    data class Found(
        val items: List<ScannedItem>,
        val lines: List<ScannedLine>,
        val picked: List<ScannedLine>,
        val uri: Uri,
    ) : ScanState
    data object Empty : ScanState
}

sealed interface ImportState {
    data object None : ImportState
    data class Offered(val shared: SharedList, val existing: ShoppingList?) : ImportState
    data object Failed : ImportState
}

class ShoppingViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.getInstance(app)
    private val dao = db.shoppingDao()
    private val settingsStore = SettingsStore(app)

    val settings = settingsStore.settings

    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update.asStateFlow()

    private val _scan = MutableStateFlow<ScanState>(ScanState.None)
    val scan: StateFlow<ScanState> = _scan.asStateFlow()

    /** Recognition happens off the main thread inside ML Kit; this only waits for it. */
    fun scanImage(uri: Uri) = viewModelScope.launch {
        _scan.value = ScanState.Working
        val result = scanImageForItems(getApplication(), uri)
        _scan.value = if (result.items.isEmpty()) {
            ScanState.Empty
        } else {
            ScanState.Found(result.items, result.lines, result.picked, uri)
        }
    }

    fun dismissScan() {
        _scan.value = ScanState.None
    }

    /** What the reader pointed at on the picture, joined in reading order into one item. */
    fun addPicked(listId: Long, lines: List<ScannedLine>) {
        val name = lines.joinToString(" ") { it.text.trim() }
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (name.isEmpty()) {
            dismissScan()
            return
        }
        addScanned(listId, listOf(ScannedItem(name = name, quantity = 1.0, priceCents = 0)))
    }

    /** One transaction, so a picture full of items either lands or does not. */
    fun addScanned(listId: Long, items: List<ScannedItem>) = viewModelScope.launch {
        _scan.value = ScanState.None
        db.withTransaction {
            // A recognised name may spell a product it already holds — "ASIAG0" for "Asiago" —
            // so the catalogue is consulted before a near-duplicate is created beside it.
            val catalogue = dao.allProducts()
            items.forEach { item ->
                val known = matchProduct(item.name, catalogue)
                dao.insertItem(
                    Item(
                        listId = listId,
                        productId = known?.id ?: productIdFor(item.name, item.priceCents),
                        quantity = item.quantity,
                        priceCents = item.priceCents,
                    ),
                )
            }
        }
    }

    private val _pendingImport = MutableStateFlow<ImportState>(ImportState.None)
    val pendingImport: StateFlow<ImportState> = _pendingImport.asStateFlow()

    init {
        checkForUpdate(force = false)
    }

    val lists = dao.observeLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val products = dao.observeProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val productsWithUsage = dao.observeProductsWithUsage()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun observeList(listId: Long): Flow<ListWithItems?> = dao.observeList(listId)

    fun addList(name: String, budgetCents: Long, colorArgb: Int) = viewModelScope.launch {
        dao.insertList(
            ShoppingList(name = name.trim(), budgetCents = budgetCents, colorArgb = colorArgb),
        )
    }

    fun updateList(
        list: ShoppingList,
        name: String,
        budgetCents: Long,
        colorArgb: Int,
    ) = viewModelScope.launch {
        dao.updateList(
            list.copy(name = name.trim(), budgetCents = budgetCents, colorArgb = colorArgb),
        )
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
                    colorArgb = entry.list.colorArgb,
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

    fun setConfirmDelete(enabled: Boolean) = settingsStore.setConfirmDelete(enabled)

    fun setCurrencyCountry(country: String) = settingsStore.setCurrencyCountry(country)

    /**
     * The launch check is silent and at most daily; [force] is the Settings button, which
     * always asks and is allowed to report that nothing was found.
     */
    fun checkForUpdate(force: Boolean) = viewModelScope.launch {
        if (_update.value == UpdateState.Checking) return@launch
        val age = System.currentTimeMillis() - settingsStore.lastUpdateCheck
        if (!force && age < CHECK_INTERVAL_MS) return@launch

        _update.value = UpdateState.Checking
        val app = getApplication<Application>()
        val release = withContext(Dispatchers.IO) {
            fetchLatestRelease(app.getString(R.string.releases_api_url), BuildConfig.VERSION_NAME)
        }
        // Only a check that actually reached GitHub resets the clock, so an offline day retries.
        if (release != null) settingsStore.lastUpdateCheck = System.currentTimeMillis()

        _update.value = when {
            release != null -> UpdateState.Available(release)
            force -> UpdateState.UpToDate
            else -> UpdateState.Idle
        }
    }

    /** Runs in [viewModelScope] so leaving Settings does not abandon a download in flight. */
    fun downloadAndInstall(release: Release) = viewModelScope.launch {
        val app = getApplication<Application>()
        _update.value = UpdateState.Downloading(release)
        val downloadId = runCatching { enqueueDownload(app, release) }.getOrNull()
        if (downloadId == null) {
            _update.value = UpdateState.Failed
            return@launch
        }

        while (true) {
            when (val result = downloadedApk(app, downloadId)) {
                is DownloadResult.Done -> {
                    app.startActivity(installIntent(result.apk))
                    _update.value = UpdateState.Idle
                    return@launch
                }

                DownloadResult.Failed -> {
                    _update.value = UpdateState.Failed
                    return@launch
                }

                DownloadResult.Running -> delay(POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * Readable on its own for someone without the app; the token on the last line is what the
     * app reads back. Quantity and price always travel, whatever Settings currently renders.
     */
    fun shareText(entry: ListWithItems, money: MoneyFormat): String {
        val app = getApplication<Application>()
        val lines = entry.items.map { row ->
            app.getString(
                R.string.format_share_line,
                row.product.name,
                formatQuantity(row.item.quantity),
                money.format(row.lineTotalCents),
            )
        }
        val total = app.getString(
            R.string.format_dot_pair,
            app.getString(R.string.summary_to_spend),
            money.format(entry.totalCents),
        )
        val payload = ShareCodec.encode(
            SharedList(
                uuid = entry.list.uuid,
                name = entry.list.name,
                budgetCents = entry.list.budgetCents,
                colorArgb = entry.list.colorArgb,
                items = entry.items.map { row ->
                    SharedItem(
                        name = row.product.name,
                        quantity = row.item.quantity,
                        priceCents = row.item.priceCents,
                        bought = row.item.bought,
                    )
                },
            ),
        )
        val link = app.getString(R.string.share_link_url, payload)
        return (listOf(entry.list.name) + lines + listOf(total, "", link)).joinToString("\n")
    }

    /** Decodes a share and asks before touching anything; [text] is whatever the intent carried. */
    fun offerImport(text: String?) = viewModelScope.launch {
        val shared = text?.let { ShareCodec.decode(it) }
        _pendingImport.value = if (shared == null) {
            ImportState.Failed
        } else {
            ImportState.Offered(shared, dao.findListByUuid(shared.uuid))
        }
    }

    fun dismissImport() {
        _pendingImport.value = ImportState.None
    }

    /**
     * One transaction, so a half-imported list cannot exist. An existing list keeps its row and
     * loses its items; the shared copy wins outright.
     */
    fun confirmImport() = viewModelScope.launch {
        val offered = _pendingImport.value as? ImportState.Offered ?: return@launch
        _pendingImport.value = ImportState.None
        db.withTransaction {
            val listId = offered.existing?.let { existing ->
                dao.updateList(
                    existing.copy(
                        name = offered.shared.name,
                        budgetCents = offered.shared.budgetCents,
                        colorArgb = offered.shared.colorArgb,
                    ),
                )
                dao.deleteItemsOfList(existing.id)
                existing.id
            } ?: dao.insertList(
                ShoppingList(
                    name = offered.shared.name,
                    budgetCents = offered.shared.budgetCents,
                    uuid = offered.shared.uuid,
                    colorArgb = offered.shared.colorArgb,
                ),
            )
            offered.shared.items.forEach { item ->
                // An existing product's defaultPriceCents is the last price this user entered,
                // which someone else's list is not, so it is left alone.
                val productId = dao.findProductByName(item.name)?.id
                    ?: dao.insertProduct(Product(name = item.name, defaultPriceCents = item.priceCents))
                dao.insertItem(
                    Item(
                        listId = listId,
                        productId = productId,
                        quantity = item.quantity,
                        priceCents = item.priceCents,
                        bought = item.bought,
                    ),
                )
            }
        }
    }

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

    private companion object {
        const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
        const val POLL_INTERVAL_MS = 500L
    }
}

/** What the Settings update row is showing. [Available] and [Downloading] carry what is on offer. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data object Failed : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release) : UpdateState
}
