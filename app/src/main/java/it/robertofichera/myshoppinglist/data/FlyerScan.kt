package it.robertofichera.myshoppinglist.data

import it.robertofichera.myshoppinglist.parsePriceCents

/**
 * A supermarket flyer names one offer in a way no wording reveals: a price set several times
 * larger than anything else, with the product's label in a column beside or above it. Everything
 * else on the page — the banner, the artwork on the packet, the tile next door — is text the
 * reader never meant to add to a shopping list.
 *
 * So the price is read as a landmark and never as a value. A flyer says what a thing *is*; what it
 * costs and how many are wanted belong to the shop and to the reader, not to the picture.
 */

/**
 * Whether a line could be the price a flyer shouts. Display type defeats recognition routinely —
 * a photographed "2,69" has come back as "L69" and "2:" — so the landmark cannot be required to
 * read as a number. Short, and mostly digits, is as much as can be asked of it.
 */
private fun looksLikePrice(text: String): Boolean {
    val compact = text.filterNot { it.isWhitespace() }
    if (compact.isEmpty() || compact.length > 8) return false
    val digits = compact.count { it.isDigit() }
    return digits >= 1 && digits * 2 >= compact.length
}

/** A sum of money, wherever it sits: `€3,49`, `e3,89`, `al kg € 26,90`, `-30,85%`. */
private val PRICE_LIKE = Regex("""\d[.,]\d{2}(\D|$)|\p{Sc}|%""")

private val LETTER = Regex("""\p{L}""")

/** A price has to tower over the page for this to be a flyer rather than a list. */
private const val FLYER_PRICE_RATIO = 2.5

/** How far apart two lines of one label may sit, as a multiple of the taller one's height. */
private const val LABEL_LINE_GAP = 2.0

/**
 * How unevenly sized a group's lines may be and still read as one label. A label is set in one
 * typeface at one size; a loyalty badge with a card logo under it, or the writing printed on the
 * packet in the photograph, is not, and that is what tells them apart when the badge happens to
 * sit closer to the price than the label does.
 */
private const val LABEL_HEIGHT_SPREAD = 2.0

/** How far from the price a label may sit, as a multiple of the price's own height. */
private const val LABEL_REACH = 3.0

/**
 * Where a label stops naming the offer and starts illustrating it. "vari tipi e grammature, un
 * esempio: albicocche 250 g" covers every flavour; carrying the example into the name would put
 * apricot jam on a list that meant any of them.
 */
private val EXAMPLE = Regex("""\b(un\s+)?esempio\b|\bes\.""", RegexOption.IGNORE_CASE)

/** A sum as recognition returns it — `€3,49`, `2.50 €`. Two decimals, and never part of a date. */
private val MONEY = Regex("""\d+[.,]\d{2}(?![.,]?\d)""")

/**
 * What [line] says in cents, or null when its text does not read as a sum of money. A discount is
 * written the same way and means the opposite, so a percentage is never one.
 */
fun priceOf(line: ScannedLine): Long? {
    if (line.text.contains('%')) return null
    return MONEY.find(line.text)?.value?.let { parsePriceCents(it) }
}

/** The price a flyer shouts: the tallest line that could be one, towering over the page. */
fun flyerPrice(lines: List<ScannedLine>): ScannedLine? {
    if (lines.isEmpty()) return null
    val median = lines.map { it.height }.sorted()[lines.size / 2]
    if (median <= 0) return null
    val price = lines.filter { looksLikePrice(it.text) }.maxByOrNull { it.height } ?: return null
    return price.takeIf { it.height >= median * FLYER_PRICE_RATIO }
}

/**
 * The sum to offer as the item's price. On a flyer only the shouted price counts, and only where
 * recognition made a legible number of it — display type usually defeats it, and the small print
 * beside it prices a kilo or a loyalty card rather than the offer. On any other picture a single
 * sum is unambiguous; where there are several, none is proposed and the reader says which.
 */
fun proposedPrice(lines: List<ScannedLine>): ScannedLine? {
    flyerPrice(lines)?.let { shouted -> return shouted.takeIf { priceOf(it) != null } }
    return lines.filter { priceOf(it) != null }.singleOrNull()
}

/**
 * What to mark before the reader has said anything. A flyer's label where there is one; otherwise
 * the first line of a picture holding barely any, since one or two lines are what a shelf label or
 * a photographed packet yields and the first of them names it. A price is never part of a name.
 */
fun proposedLabel(lines: List<ScannedLine>, price: ScannedLine?): List<ScannedLine> {
    flyerLabel(lines)?.let { return it }
    if (lines.size > 2) return emptyList()
    val first = lines.sortedWith(compareBy({ it.top }, { it.left })).firstOrNull { it !== price }
    return listOfNotNull(first)
}

/**
 * The one offer this picture is about, or null when the picture is not a flyer — in which case
 * the caller reads it line by line, as a written list.
 */
fun parseFlyer(lines: List<ScannedLine>): ScannedItem? {
    val label = flyerLabel(lines) ?: return null
    val name = nameOf(label)
    if (name.isEmpty()) return null
    return ScannedItem(name = name, quantity = 1.0, priceCents = 0)
}

/**
 * The lines that name the offer, so the reader can be shown what was chosen and change it. Kept
 * separate from the name because pointing at the picture needs the lines themselves.
 */
fun flyerLabel(lines: List<ScannedLine>): List<ScannedLine>? {
    if (lines.size < 3) return null
    val price = flyerPrice(lines) ?: return null

    val candidates = lines.filter {
        LETTER.containsMatchIn(it.text) && !PRICE_LIKE.containsMatchIn(it.text)
    }
    if (candidates.isEmpty()) return null

    val reach = price.height * LABEL_REACH
    val within = columns(candidates).filter { group -> gapToGroup(price, group) <= reach }

    // The most lines wins, because a label says more about the product than a badge does; where
    // two say as much, the one nearer the price. Groups of one line are a last resort: a caption
    // of one word competes with every stray word the packaging carries.
    val label = within
        .filter { it.size > 1 && evenlySet(it) }
        .maxWithOrNull(compareBy({ it.size }, { -gapToGroup(price, it) }))
        ?: within.minByOrNull { gapToGroup(price, it) }
        ?: return null

    return label.takeWhile { !EXAMPLE.containsMatchIn(it.text) }.ifEmpty { null }
}

/** Distance from [line] to [box], counting an overlap on either axis as no distance at all. */
private fun gapTo(box: ScannedLine, line: ScannedLine): Int {
    val dx = maxOf(0, maxOf(box.left - line.right, line.left - box.right))
    val dy = maxOf(0, maxOf(box.top - line.bottom, line.top - box.bottom))
    return dx + dy
}

/**
 * The page's text gathered into columns: lines that share a column and follow each other closely
 * are one piece of writing, whether that is a label, a banner or the packet in the photograph.
 */
private fun columns(candidates: List<ScannedLine>): List<List<ScannedLine>> {
    val groups = mutableListOf<MutableList<ScannedLine>>()
    candidates.sortedBy { it.top }.forEach { line ->
        val joined = groups.firstOrNull { group ->
            val last = group.last()
            val left = group.minOf { it.left }
            val right = group.maxOf { it.right }
            line.left <= right && line.right >= left && adjoin(last, line)
        }
        if (joined == null) groups.add(mutableListOf(line)) else joined.add(line)
    }
    return groups
}

/** Whether every line in [group] is set at much the same size, as one label's lines are. */
private fun evenlySet(group: List<ScannedLine>): Boolean {
    val shortest = group.minOf { it.height }
    val tallest = group.maxOf { it.height }
    return shortest > 0 && tallest <= shortest * LABEL_HEIGHT_SPREAD
}

private fun gapToGroup(price: ScannedLine, group: List<ScannedLine>): Int {
    val box = ScannedLine(
        text = "",
        left = group.minOf { it.left },
        top = group.minOf { it.top },
        right = group.maxOf { it.right },
        bottom = group.maxOf { it.bottom },
    )
    return gapTo(price, box)
}

private fun adjoin(above: ScannedLine, below: ScannedLine): Boolean =
    below.top - above.top <= maxOf(above.height, below.height) * LABEL_LINE_GAP
