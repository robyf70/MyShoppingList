package it.robertofichera.myshoppinglist.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(
    val showQuantity: Boolean = true,
    val showPrice: Boolean = true,
)

/** Reads both values once at construction, so the blocking load is small enough to do inline. */
class SettingsStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(
        Settings(
            showQuantity = prefs.getBoolean(KEY_QUANTITY, true),
            showPrice = prefs.getBoolean(KEY_PRICE, true),
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

    private companion object {
        const val KEY_QUANTITY = "show_quantity"
        const val KEY_PRICE = "show_price"
    }
}
