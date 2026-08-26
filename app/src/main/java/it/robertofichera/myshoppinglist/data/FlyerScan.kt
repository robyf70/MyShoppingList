package it.robertofichera.myshoppinglist.data

/**
 * A supermarket flyer names one offer in a way no wording reveals: a price set several times
 * larger than anything else, with the product's label in a column beside or above it. Everything
 * else on the page — the banner, the artwork on the packet, the tile next door — is text the
 * reader never meant to add to a shopping list.
 *
 * So the price is read as a landmark and never as a value. A flyer says what a thing *is*; what it
 * costs and how many are wanted belong to the shop and to the reader, not to the picture.
 */

/** Digits, separators and the symbols that keep them company. */
private val NUMERIC = Regex("""^[\p{Sc}\d.,:%\-\s]+$""")

/** A sum of money, wherever it sits: `€3,49`, `e3,89`, `al kg € 26,90`, `-30,85%`. */
private val PRICE_LIKE = Regex("""\d[.,]\d{2}(\D|$)|\p{Sc}|%""")

private val LETTER = Regex("""\p{L}""")

/** A price has to tower over the page for this to be a flyer rather than a list. */
private const val FLYER_PRICE_RATIO = 2.5

/** How far apart two lines of one label may sit, as a multiple of the taller one's height. */
private const val LABEL_LINE_GAP = 2.0

/**
 * The one offer this picture is about, or null when the picture is not a flyer — in which case
 * the caller reads it line by line, as a written list.
 */
fun parseFlyer(lines: List<ScannedLine>): ScannedItem? {
    if (lines.size < 3) return null

    val median = lines.map { it.height }.sorted()[lines.size / 2]
    if (median <= 0) return null

    val price = lines
        .filter { NUMERIC.matches(it.text.trim()) }
        .maxByOrNull { it.height }
        ?: return null
    if (price.height < median * FLYER_PRICE_RATIO) return null

    val candidates = lines.filter {
        LETTER.containsMatchIn(it.text) && !PRICE_LIKE.containsMatchIn(it.text)
    }
    val seed = candidates.minByOrNull { gapTo(price, it) } ?: return null

    val column = candidates
        .filter { it.left <= seed.right && it.right >= seed.left }
        .sortedBy { it.top }
    val label = runContaining(column, seed)

    val name = label.joinToString(" ") { it.text.trim() }.replace(Regex("""\s+"""), " ").trim()
    if (name.isEmpty()) return null
    return ScannedItem(name = name, quantity = 1.0, priceCents = 0)
}

/** A flyer yields its one offer; anything else is read as a written list, a line per item. */
fun parseScan(lines: List<ScannedLine>): List<ScannedItem> =
    parseFlyer(lines)?.let { listOf(it) } ?: lines.mapNotNull { parseScannedLine(it.text) }

/** Distance from [line] to [box], counting an overlap on either axis as no distance at all. */
private fun gapTo(box: ScannedLine, line: ScannedLine): Int {
    val dx = maxOf(0, maxOf(box.left - line.right, line.left - box.right))
    val dy = maxOf(0, maxOf(box.top - line.bottom, line.top - box.bottom))
    return dx + dy
}

/** The unbroken stretch of [sorted] around [seed]: a label's lines follow each other closely. */
private fun runContaining(sorted: List<ScannedLine>, seed: ScannedLine): List<ScannedLine> {
    val start = sorted.indexOf(seed)
    if (start < 0) return listOf(seed)

    var first = start
    while (first > 0 && adjoin(sorted[first - 1], sorted[first])) first--
    var last = start
    while (last < sorted.size - 1 && adjoin(sorted[last], sorted[last + 1])) last++
    return sorted.subList(first, last + 1)
}

private fun adjoin(above: ScannedLine, below: ScannedLine): Boolean =
    below.top - above.top <= maxOf(above.height, below.height) * LABEL_LINE_GAP
