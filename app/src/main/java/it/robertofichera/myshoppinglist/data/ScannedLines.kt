package it.robertofichera.myshoppinglist.data

import it.robertofichera.myshoppinglist.parsePriceCents
import it.robertofichera.myshoppinglist.parseQuantity

/** One line of recognised text, read as an item. [priceCents] is 0 when the line named no price. */
data class ScannedItem(
    val name: String,
    val quantity: Double,
    val priceCents: Long,
)

/** Bullets, checkboxes and numbering a written list starts its lines with. */
private val LEADING_MARKS = Regex("""^[\s\-–—•*·▪◦☐☑✓✔\[\]()]+""")
private val LEADING_NUMBER = Regex("""^\d+\s*[.)]\s+""")

/** "2 x milk", "2× milk", "2 milk" — the count a line opens with. */
private val LEADING_QUANTITY = Regex("""^(\d+(?:[.,]\d+)?)\s*(?:[xX×*]\s*)?(?=\D)""")

/**
 * A price closing the line, with or without a currency symbol on either side. The symbol is
 * matched as one, never as "any non-digit": a loose class eats the last word of the name.
 */
private val TRAILING_PRICE =
    Regex("""[=\s]\s*\p{Sc}?\s*(\d+(?:[.,]\d{1,2}))\s*\p{Sc}?\s*$""")

/** "Milk × 2" — the quantity this app itself writes into a shared list. */
private val TRAILING_QUANTITY = Regex("""\s*[xX×*]\s*(\d+(?:[.,]\d+)?)\s*$""")

private val HAS_LETTER = Regex("""\p{L}""")

/**
 * Reads one line of recognised text. Returns null for anything with no word in it, which is what
 * OCR produces from rules, prices in isolation and specks on the paper.
 *
 * The app's own share format is understood too, so a screenshot of a shared list reads back.
 */
fun parseScannedLine(raw: String): ScannedItem? {
    var line = raw.trim()
        .replace(LEADING_MARKS, "")
        .replace(LEADING_NUMBER, "")
        .trim()
    if (line.isEmpty() || !HAS_LETTER.containsMatchIn(line)) return null

    // A shared line reads "Milk × 2 = €3.00": the price after "=" is the line total, not the
    // unit price, so it is dropped rather than recorded as what one of them cost.
    val hadTotal = line.contains('=')
    var priceCents = 0L
    TRAILING_PRICE.find(line)?.let { match ->
        if (!hadTotal) {
            priceCents = parsePriceCents(match.groupValues[1]) ?: 0L
        }
        line = line.removeRange(match.range).trim()
    }
    if (hadTotal) line = line.substringBefore('=').trim()

    var quantity = 1.0
    LEADING_QUANTITY.find(line)?.let { match ->
        parseQuantity(match.groupValues[1])?.let { parsed ->
            quantity = parsed
            line = line.removeRange(match.range).trim()
        }
    }
    if (quantity == 1.0) {
        TRAILING_QUANTITY.find(line)?.let { match ->
            parseQuantity(match.groupValues[1])?.let { parsed ->
                quantity = parsed
                line = line.removeRange(match.range).trim()
            }
        }
    }

    val name = line.replace(Regex("""\s+"""), " ").trim(' ', '-', ':', ',', '.')
    if (name.isEmpty() || !HAS_LETTER.containsMatchIn(name)) return null
    return ScannedItem(name = name, quantity = quantity, priceCents = priceCents)
}

/** Every line worth offering, in the order they were read. */
fun parseScannedText(text: String): List<ScannedItem> =
    text.lineSequence().mapNotNull(::parseScannedLine).toList()
