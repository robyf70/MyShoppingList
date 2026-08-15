package it.robertofichera.myshoppinglist.data

/**
 * Input order is preserved, so [all] arriving alphabetically keeps the suggestions alphabetical.
 * A blank query matches nothing: suggestions are only worth showing once the user has typed.
 */
fun filterProducts(all: List<Product>, query: String, limit: Int = 6): List<Product> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return emptyList()
    return all.filter { it.name.contains(trimmed, ignoreCase = true) }.take(limit)
}

/** True once the query names exactly one product outright — nothing left to suggest. */
fun isSettledOn(matches: List<Product>, query: String): Boolean =
    matches.size == 1 && matches.single().name.equals(query.trim(), ignoreCase = true)
