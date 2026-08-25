package it.robertofichera.myshoppinglist

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import it.robertofichera.myshoppinglist.data.Product
import it.robertofichera.myshoppinglist.data.Settings
import it.robertofichera.myshoppinglist.ui.ImportDialog
import it.robertofichera.myshoppinglist.ui.ListDetailScreen
import it.robertofichera.myshoppinglist.ui.ListsScreen
import it.robertofichera.myshoppinglist.ui.ProductsScreen
import it.robertofichera.myshoppinglist.ui.SettingsScreen
import it.robertofichera.myshoppinglist.ui.theme.MyShoppingListTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ShoppingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        offerImport(intent)
        setContent {
            MyShoppingListTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ShoppingApp(viewModel)
                }
            }
        }
    }

    /** singleTop, so a share arriving while the app is open lands here instead of a new activity. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        offerImport(intent)
    }

    /**
     * Both filters carry the payload as text; the codec finds the token wherever it sits.
     * The share is marked on the intent once offered, so a recreation re-reading that same
     * intent stays quiet while a newly arrived one is always offered.
     */
    private fun offerImport(intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_OFFERED, false)) return
        val text = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> return
        }
        intent.putExtra(EXTRA_OFFERED, true)
        viewModel.offerImport(text)
    }

    private companion object {
        const val EXTRA_OFFERED = "it.robertofichera.myshoppinglist.OFFERED"
    }
}

/** The whole back stack: both types are natively saveable, so no custom Saver is needed. */
@Composable
fun ShoppingApp(viewModel: ShoppingViewModel = viewModel()) {
    var openListId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showProducts by rememberSaveable { mutableStateOf(false) }

    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val productsWithUsage by viewModel.productsWithUsage.collectAsStateWithLifecycle()
    val update by viewModel.update.collectAsStateWithLifecycle()
    val pendingImport by viewModel.pendingImport.collectAsStateWithLifecycle()

    val listId = openListId

    BackHandler(enabled = showSettings || showProducts || listId != null) {
        when {
            showProducts -> showProducts = false
            showSettings -> showSettings = false
            else -> openListId = null
        }
    }

    CompositionLocalProvider(
        LocalMoneyFormat provides remember(settings.currencyCountry) {
            MoneyFormat(settings.currencyCountry)
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                showProducts -> ProductsScreen(
                    products = productsWithUsage,
                    showPrice = settings.showPrice,
                    viewModel = viewModel,
                    onBack = { showProducts = false },
                )

                showSettings -> SettingsScreen(
                    settings = settings,
                    productCount = products.size,
                    onShowQuantityChange = viewModel::setShowQuantity,
                    onShowPriceChange = viewModel::setShowPrice,
                    onBudgetEnabledChange = viewModel::setBudgetEnabled,
                    onConfirmDeleteChange = viewModel::setConfirmDelete,
                    onCurrencyCountryChange = viewModel::setCurrencyCountry,
                    update = update,
                    onCheckUpdate = { viewModel.checkForUpdate(force = true) },
                    onInstallUpdate = viewModel::downloadAndInstall,
                    onOpenProducts = { showProducts = true },
                    onBack = { showSettings = false },
                )

                listId != null -> ListDetailRoute(
                    listId = listId,
                    products = products,
                    settings = settings,
                    viewModel = viewModel,
                    onBack = { openListId = null },
                )

                else -> ListsScreen(
                    lists = lists,
                    showPrice = settings.showPrice,
                    budgetEnabled = settings.budgetEnabled,
                    confirmDelete = settings.confirmDelete,
                    viewModel = viewModel,
                    onOpenList = { openListId = it },
                    onOpenSettings = { showSettings = true },
                )
            }
            ImportDialog(
                state = pendingImport,
                onConfirm = viewModel::confirmImport,
                onDismiss = viewModel::dismissImport,
            )
        }
    }
}

@Composable
private fun ListDetailRoute(
    listId: Long,
    products: List<Product>,
    settings: Settings,
    viewModel: ShoppingViewModel,
    onBack: () -> Unit,
) {
    val entry by remember(listId) { viewModel.observeList(listId) }
        .collectAsStateWithLifecycle(initialValue = null)

    val current = entry
    if (current == null) {
        // Either still loading or the list is gone — nothing to show either way.
        Box(modifier = Modifier.fillMaxSize())
        return
    }

    ListDetailScreen(
        entry = current,
        products = products,
        showQuantity = settings.showQuantity,
        showPrice = settings.showPrice,
        budgetEnabled = settings.budgetEnabled,
        confirmDelete = settings.confirmDelete,
        viewModel = viewModel,
        onBack = onBack,
    )
}
