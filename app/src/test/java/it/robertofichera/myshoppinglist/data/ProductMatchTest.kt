package it.robertofichera.myshoppinglist.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProductMatchTest {

    private fun catalogue(vararg names: String) =
        names.mapIndexed { index, name -> Product(id = index + 1L, name = name) }

    @Test
    fun `matches a name recognition spelled with a look-alike character`() {
        val products = catalogue("Rigoni di Asiago", "Latte")
        assertEquals("Rigoni di Asiago", matchProduct("RIGONI DI ASIAG0", products)?.name)
    }

    @Test
    fun `matches the several look-alikes recognition favours`() {
        val products = catalogue("Bio", "Sale", "Olio", "Gorgonzola")
        assertEquals("Bio", matchProduct("8IO", products)?.name)
        assertEquals("Sale", matchProduct("5ale", products)?.name)
        assertEquals("Olio", matchProduct("0li0", products)?.name)
        assertEquals("Gorgonzola", matchProduct("6orgonzola", products)?.name)
    }

    @Test
    fun `keeps two real products apart though one letter separates them`() {
        // The case that rules out edit distance: apples and honey, one letter apart.
        val products = catalogue("Mele", "Miele")
        assertEquals("Mele", matchProduct("Mele", products)?.name)
        assertEquals("Miele", matchProduct("Miele", products)?.name)
        assertNull(matchProduct("Mlele", products))
    }

    @Test
    fun `an exact name wins over a look-alike`() {
        val products = catalogue("L0tus", "Lotus")
        assertEquals("Lotus", matchProduct("Lotus", products)?.name)
    }

    @Test
    fun `refuses to choose when two products read alike`() {
        // "IO" and "10" share a key; picking either would be a guess.
        val products = catalogue("IO", "10")
        assertNull(matchProduct("1O", products))
    }

    @Test
    fun `ignores case and spacing`() {
        val products = catalogue("Olio di oliva")
        assertEquals("Olio di oliva", matchProduct("  OLIO DI OLIVA ", products)?.name)
    }

    @Test
    fun `names nothing when the catalogue is empty or the name is blank`() {
        assertNull(matchProduct("Latte", emptyList()))
        assertNull(matchProduct("   ", catalogue("Latte")))
    }
}
