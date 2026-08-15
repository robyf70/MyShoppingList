package it.robertofichera.myshoppinglist

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat

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

fun formatCents(cents: Long): String =
    NumberFormat.getCurrencyInstance().format(BigDecimal.valueOf(cents, 2))

/** Whole quantities read as "2", not "2.0". */
fun formatQuantity(quantity: Double): String =
    BigDecimal.valueOf(quantity).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros()
        .toPlainString()
