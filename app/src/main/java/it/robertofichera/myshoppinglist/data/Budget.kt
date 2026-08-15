package it.robertofichera.myshoppinglist.data

/** What has actually gone in the trolley, as opposed to [totalCents] which is the whole plan. */
val ListWithItems.spentCents: Long
    get() = items.filter { it.item.bought }.sumOf { it.lineTotalCents }

/** Negative once the budget is breached, which is what makes the overspend visible. */
fun remainingCents(budgetCents: Long, spentCents: Long): Long = budgetCents - spentCents

/**
 * How much marking another [addedCents] as bought would exceed [budgetCents] by,
 * or null if it stays within. Landing exactly on the budget is not a breach —
 * spending your last euro is still inside it.
 */
fun overspendCents(spentCents: Long, addedCents: Long, budgetCents: Long): Long? {
    if (budgetCents <= 0) return null
    val excess = spentCents + addedCents - budgetCents
    return if (excess > 0) excess else null
}
