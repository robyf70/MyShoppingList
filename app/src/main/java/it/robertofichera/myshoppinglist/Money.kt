package it.robertofichera.myshoppinglist

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/**
 * Prices are integer cents everywhere. Parsing goes through BigDecimal rather than
 * Double so "1.005" lands on 101 and not 100.
 * Accepts both "." and "," as the decimal separator.
 */
fun parsePriceCents(input: String): Long? {
    val decimal = input.trim().replace(',', '.').toBigDecimalOrNull() ?: return null
    if (decimal.signum() < 0) return null
    return decimal.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
}

fun parseQuantity(input: String): Double? {
    val quantity = input.trim().replace(',', '.').toDoubleOrNull() ?: return null
    if (!quantity.isFinite() || quantity <= 0.0) return null
    return quantity
}

/**
 * Pairs the reader's language with [country], and lets CLDR decide how that pair writes money:
 * "en" plus "IT" gives "1.234,50 €", the Italian layout, not an English one wearing a euro sign.
 * [country] is the one chosen in Settings; empty follows the phone, which is only ever as good as
 * the region its language entry names — and that region is the language's, not the one a vendor
 * "Region" setting shows. Building a locale rather than overriding the currency on a ready-made
 * format is what keeps the fraction digits right: a zero-decimal currency like JPY gets none.
 */
internal fun currencyLocale(format: Locale, phone: List<Locale>, country: String): Locale {
    val region = country.ifEmpty { phone.firstOrNull { it.country.isNotEmpty() }?.country.orEmpty() }
    if (region.isEmpty()) return format
    return runCatching { Locale.Builder().setLocale(format).setRegion(region).build() }
        .getOrDefault(format)
}

/** The system configuration, not the app's: a per-app language override must not hide the region. */
private fun phoneLocales(): List<Locale> =
    Resources.getSystem().configuration.locales.let { List(it.size()) { i -> it[i] } }

/** What the phone would pick unaided — the country the Settings picker starts on. */
fun phoneCountry(): String = phoneLocales().firstOrNull { it.country.isNotEmpty() }?.country.orEmpty()

/** Holds the resolved [NumberFormat] so a screenful of prices builds it once, not once per row. */
class MoneyFormat(val country: String) {
    private val format = NumberFormat.getCurrencyInstance(
        currencyLocale(Locale.getDefault(Locale.Category.FORMAT), phoneLocales(), country),
    )

    fun format(cents: Long): String = format.format(BigDecimal.valueOf(cents, 2))
}

/** Ambient, so the chosen country reaches every price without threading through each composable. */
val LocalMoneyFormat = staticCompositionLocalOf { MoneyFormat("") }

@Composable
fun formatCents(cents: Long): String = LocalMoneyFormat.current.format(cents)

/** Whole quantities read as "2", not "2.0". */
fun formatQuantity(quantity: Double): String =
    BigDecimal.valueOf(quantity).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros()
        .toPlainString()
