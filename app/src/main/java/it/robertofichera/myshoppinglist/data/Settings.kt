package it.robertofichera.myshoppinglist.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(
    val showQuantity: Boolean = true,
    val showPrice: Boolean = true,
    val budgetEnabled: Boolean = false,
    val confirmDelete: Boolean = true,
)

/** Reads the values once at construction, so the blocking load is small enough to do inline. */
class SettingsStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(
        Settings(
            showQuantity = prefs.getBoolean(KEY_QUANTITY, true),
            showPrice = prefs.getBoolean(KEY_PRICE, true),
            budgetEnabled = prefs.getBoolean(KEY_BUDGET, false),
            confirmDelete = prefs.getBoolean(KEY_CONFIRM_DELETE, true),
        )
    )
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    fun setShowQuantity(show: Boolean) {
        prefs.edit().putBoolean(KEY_QUANTITY, show).apply()
        _settings.value = _settings.value.copy(showQuantity = show)
    }

    fun setShowPrice(show: Boolean) {
        prefs.edit().putBoolean(KEY_PRICE, show).apply()
        _settings.value = _settings.value.copy(showPrice = show)
    }

    fun setBudgetEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BUDGET, enabled).apply()
        _settings.value = _settings.value.copy(budgetEnabled = enabled)
    }

    /** Not part of [Settings]: it is bookkeeping for the update check, not something the user sets. */
    var lastUpdateCheck: Long
        get() = prefs.getLong(KEY_LAST_UPDATE_CHECK, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_UPDATE_CHECK, value).apply()

    fun setConfirmDelete(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CONFIRM_DELETE, enabled).apply()
        _settings.value = _settings.value.copy(confirmDelete = enabled)
    }

    private companion object {
        const val KEY_QUANTITY = "show_quantity"
        const val KEY_PRICE = "show_price"
        const val KEY_BUDGET = "budget_enabled"
        const val KEY_CONFIRM_DELETE = "confirm_delete"
        const val KEY_LAST_UPDATE_CHECK = "last_update_check"
    }
}
