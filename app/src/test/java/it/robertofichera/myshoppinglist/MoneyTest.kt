package it.robertofichera.myshoppinglist

import it.robertofichera.myshoppinglist.data.Item
import it.robertofichera.myshoppinglist.data.lineTotalCents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    @Test
    fun `parses prices with either decimal separator`() {
        assertEquals(150L, parsePriceCents("1.50"))
        assertEquals(150L, parsePriceCents("1,50"))
        assertEquals(50L, parsePriceCents(".5"))
        assertEquals(300L, parsePriceCents("3"))
        assertEquals(150L, parsePriceCents("  1.50  "))
    }

    @Test
    fun `rounds sub-cent prices half up instead of truncating`() {
        assertEquals(101L, parsePriceCents("1.005"))
        assertEquals(100L, parsePriceCents("1.004"))
    }

    @Test
    fun `rejects prices that are not usable amounts`() {
        assertNull(parsePriceCents(""))
        assertNull(parsePriceCents("abc"))
        assertNull(parsePriceCents("-1.00"))
        assertNull(parsePriceCents("1.2.3"))
    }

    @Test
    fun `rejects quantities that are not positive numbers`() {
        assertEquals(1.5, parseQuantity("1,5"))
        assertEquals(0.3, parseQuantity("0.3"))
        assertNull(parseQuantity("0"))
        assertNull(parseQuantity("-2"))
        assertNull(parseQuantity(""))
        assertNull(parseQuantity("two"))
    }

    @Test
    fun `line total rounds once per line`() {
        assertEquals(540L, item(quantity = 0.3, priceCents = 1800).lineTotalCents)
        assertEquals(299L, item(quantity = 1.5, priceCents = 199).lineTotalCents)
    }

    @Test
    fun `list total sums rounded line totals`() {
        val items = listOf(
            item(quantity = 1.5, priceCents = 199),
            item(quantity = 0.3, priceCents = 1800),
            item(quantity = 2.0, priceCents = 120),
        )
        assertEquals(299L + 540L + 240L, items.sumOf { it.lineTotalCents })
    }

    @Test
    fun `whole quantities format without a trailing decimal`() {
        assertEquals("2", formatQuantity(2.0))
        assertEquals("1.5", formatQuantity(1.5))
        assertEquals("0.75", formatQuantity(0.75))
    }

    private fun item(quantity: Double, priceCents: Long) =
        Item(listId = 1, productId = 1, quantity = quantity, priceCents = priceCents)
}
