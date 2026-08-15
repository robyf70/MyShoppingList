package it.robertofichera.myshoppinglist

import it.robertofichera.myshoppinglist.data.Item
import it.robertofichera.myshoppinglist.data.ItemWithProduct
import it.robertofichera.myshoppinglist.data.ListWithItems
import it.robertofichera.myshoppinglist.data.Product
import it.robertofichera.myshoppinglist.data.ShoppingList
import it.robertofichera.myshoppinglist.data.overspendCents
import it.robertofichera.myshoppinglist.data.remainingCents
import it.robertofichera.myshoppinglist.data.spentCents
import it.robertofichera.myshoppinglist.data.totalCents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BudgetTest {

    @Test
    fun `spent counts only bought items, total counts everything`() {
        val list = listOf(
            row(quantity = 2.0, priceCents = 120, bought = true),   // 2.40
            row(quantity = 1.0, priceCents = 250, bought = true),   // 2.50
            row(quantity = 1.0, priceCents = 790, bought = false),  // 7.90
        )
        assertEquals(240L + 250L, list.spentCents)
        assertEquals(240L + 250L + 790L, list.totalCents)
    }

    @Test
    fun `spent is zero when nothing is ticked`() {
        val list = listOf(row(quantity = 1.0, priceCents = 500, bought = false))
        assertEquals(0L, list.spentCents)
    }

    @Test
    fun `remaining goes negative once the budget is passed`() {
        assertEquals(3000L, remainingCents(budgetCents = 5000, spentCents = 2000))
        assertEquals(0L, remainingCents(budgetCents = 5000, spentCents = 5000))
        assertEquals(-500L, remainingCents(budgetCents = 5000, spentCents = 5500))
    }

    @Test
    fun `no budget set never reports an overspend`() {
        assertNull(overspendCents(spentCents = 9999, addedCents = 9999, budgetCents = 0))
    }

    @Test
    fun `staying within the budget reports nothing`() {
        assertNull(overspendCents(spentCents = 2000, addedCents = 500, budgetCents = 5000))
    }

    @Test
    fun `landing exactly on the budget is not a breach`() {
        assertNull(overspendCents(spentCents = 4500, addedCents = 500, budgetCents = 5000))
    }

    @Test
    fun `passing the budget reports the exact excess`() {
        assertEquals(300L, overspendCents(spentCents = 4500, addedCents = 800, budgetCents = 5000))
    }

    @Test
    fun `already over budget still reports, so every further tick asks`() {
        assertEquals(700L, overspendCents(spentCents = 5200, addedCents = 500, budgetCents = 5000))
    }

    private val List<ItemWithProduct>.spentCents: Long
        get() = wrap(this).spentCents

    private val List<ItemWithProduct>.totalCents: Long
        get() = wrap(this).totalCents

    private fun wrap(items: List<ItemWithProduct>) =
        ListWithItems(ShoppingList(id = 1, name = "test"), items)

    private fun row(quantity: Double, priceCents: Long, bought: Boolean) = ItemWithProduct(
        item = Item(
            listId = 1,
            productId = 1,
            quantity = quantity,
            priceCents = priceCents,
            bought = bought,
        ),
        product = Product(id = 1, name = "test"),
    )
}
