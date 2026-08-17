package it.robertofichera.myshoppinglist

import android.content.res.Resources
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
 * Currency belongs to the region, digit grouping to the language: an English-language phone
 * set to Italy spends euros and writes them "€1,234.50". Falls back to [format] when the phone
 * names no country. Building a locale rather than overriding the currency is what keeps the
 * fraction digits right — a zero-decimal currency such as JPY gets its own default.
 */
internal fun currencyLocale(format: Locale, phone: List<Locale>): Locale {
    val region = phone.firstOrNull { it.country.isNotEmpty() }?.country ?: return format
    return runCatching { Locale.Builder().setLocale(format).setRegion(region).build() }
        .getOrDefault(format)
}

fun formatCents(cents: Long): String {
    // The system configuration, not the app's: a per-app language override must not hide the region.
    val phone = Resources.getSystem().configuration.locales
    val locale = currencyLocale(
        format = Locale.getDefault(Locale.Category.FORMAT),
        phone = List(phone.size()) { phone[it] },
    )
    return NumberFormat.getCurrencyInstance(locale).format(BigDecimal.valueOf(cents, 2))
}

/** Whole quantities read as "2", not "2.0". */
fun formatQuantity(quantity: Double): String =
    BigDecimal.valueOf(quantity).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros()
        .toPlainString()
