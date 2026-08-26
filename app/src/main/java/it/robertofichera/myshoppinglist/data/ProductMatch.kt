package it.robertofichera.myshoppinglist.data

/**
 * Recognition confuses characters that look alike, so "Rigoni di Asiago" comes back as
 * "RIGONI DI ASIAG0" and would start a second product beside the first.
 *
 * Only digits are equated with the letters they resemble, and letters are never equated with each
 * other. A product name rarely holds a digit, so that is where recognition's mistakes land;
 * treating letters as interchangeable folds "Mele" into "Miele" — apples into honey — and a
 * catalogue quietly merging two real products is worse than one holding a duplicate.
 */
private val CONFUSABLE = mapOf(
    '0' to 'O', '1' to 'I', '5' to 'S', '8' to 'B', '6' to 'G', '2' to 'Z',
)

/** What two names share when only recognition tells them apart. */
fun confusableKey(name: String): String =
    name.uppercase()
        .filter { !it.isWhitespace() }
        .map { CONFUSABLE[it] ?: it }
        .joinToString("")

/**
 * The catalogue product [name] means, or null when it names something new. An exact match always
 * wins; a look-alike is accepted only when nothing else claims the same key, because two products
 * reading alike is the case where guessing does harm.
 */
fun matchProduct(name: String, products: List<Product>): Product? {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return null

    products.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }?.let { return it }

    val key = confusableKey(trimmed)
    val alike = products.filter { confusableKey(it.name) == key }
    return alike.singleOrNull()
}
