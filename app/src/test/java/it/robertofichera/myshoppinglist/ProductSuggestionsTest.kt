package it.robertofichera.myshoppinglist

import it.robertofichera.myshoppinglist.data.Item
import it.robertofichera.myshoppinglist.data.ItemWithProduct
import it.robertofichera.myshoppinglist.data.Product
import it.robertofichera.myshoppinglist.data.filterProducts
import it.robertofichera.myshoppinglist.data.findItemNamed
import it.robertofichera.myshoppinglist.data.isSettledOn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductSuggestionsTest {

    private val catalog = listOf(
        product(1, "Bread"),
        product(2, "Milk"),
        product(3, "Milk chocolate"),
        product(4, "Whole milk"),
    )

    @Test
    fun `matches regardless of case`() {
        assertEquals(listOf("Milk", "Milk chocolate", "Whole milk"), names("mil"))
        assertEquals(listOf("Milk", "Milk chocolate", "Whole milk"), names("MILK"))
    }

    @Test
    fun `matches anywhere in the name, not just the start`() {
        assertTrue("Whole milk" in names("whole"))
        assertTrue("Whole milk" in names("milk"))
    }

    @Test
    fun `blank query suggests nothing`() {
        assertEquals(emptyList<String>(), names(""))
        assertEquals(emptyList<String>(), names("   "))
    }

    @Test
    fun `unknown query suggests nothing`() {
        assertEquals(emptyList<String>(), names("marzipan"))
    }

    @Test
    fun `preserves the order it was given, so an alphabetical catalog stays alphabetical`() {
        assertEquals(listOf("Milk", "Milk chocolate", "Whole milk"), names("m"))
    }

    @Test
    fun `honours the limit`() {
        val many = (1..20).map { product(it.toLong(), "Item $it") }
        assertEquals(6, filterProducts(many, "item").size)
        assertEquals(2, filterProducts(many, "item", limit = 2).size)
    }

    @Test
    fun `settled once the query names exactly one product`() {
        assertTrue(isSettledOn(listOf(product(2, "Milk")), "Milk"))
        assertTrue(isSettledOn(listOf(product(2, "Milk")), "  milk "))
        // still typing towards a longer name
        assertFalse(isSettledOn(listOf(product(4, "Whole milk")), "Whole"))
        // more than one candidate left
        assertFalse(isSettledOn(catalog.filter { it.name.contains("Milk") }, "Milk"))
    }

    @Test
    fun `finds the row already naming the product, whatever the typing`() {
        val onList = listOf(row(10, catalog[1]), row(11, catalog[0]))
        assertEquals(10L, findItemNamed(onList, "Milk")?.item?.id)
        assertEquals(10L, findItemNamed(onList, "  milk ")?.item?.id)
    }

    @Test
    fun `a name read off a picture finds the row it means`() {
        val onList = listOf(row(10, product(5, "Asiago")))
        assertEquals(10L, findItemNamed(onList, "ASIAG0")?.item?.id)
    }

    @Test
    fun `a product not on the list matches no row`() {
        val onList = listOf(row(10, catalog[1]))
        assertNull(findItemNamed(onList, "Milk chocolate"))
        assertNull(findItemNamed(emptyList(), "Milk"))
    }

    private fun names(query: String) = filterProducts(catalog, query).map { it.name }

    private fun row(id: Long, product: Product) =
        ItemWithProduct(Item(id = id, listId = 1, productId = product.id), product)

    private fun product(id: Long, name: String) = Product(id = id, name = name)
}
