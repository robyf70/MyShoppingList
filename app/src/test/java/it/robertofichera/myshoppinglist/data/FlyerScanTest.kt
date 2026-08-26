package it.robertofichera.myshoppinglist.data

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The fixtures are the real recogniser's output for three photographed Conad flyers, captured on a
 * device rather than invented: a flyer's layout is the thing being parsed, so made-up coordinates
 * would test nothing.
 */
class FlyerScanTest {

    private fun fixture(name: String): List<ScannedLine> {
        val json = checkNotNull(javaClass.getResourceAsStream("/flyers/$name.json")) {
            "missing fixture $name"
        }.bufferedReader().readText()
        val array = JSONArray(json)
        return List(array.length()) { index ->
            val o = array.getJSONObject(index)
            ScannedLine(o.getString("t"), o.getInt("l"), o.getInt("y"), o.getInt("r"), o.getInt("b"))
        }
    }

    @Test
    fun `reads the label beside the price, ignoring the packaging`() {
        assertEquals(
            "PROSCIUTTO DI NORCIA IGP SAPORI&DINTORNI CONAD",
            parseFlyer(fixture("prosciutto"))?.name,
        )
    }

    @Test
    fun `keeps the size as part of the name`() {
        assertEquals(
            "DEODORANTE NIVEA vari tipi spray 150 ml",
            parseFlyer(fixture("deodorante"))?.name,
        )
    }

    @Test
    fun `keeps a pack count as part of the name`() {
        assertEquals(
            "SALVIETTINE IGIENE INTIMA FRIA senior conf. da 60 pezzi",
            parseFlyer(fixture("salviettine"))?.name,
        )
    }

    @Test
    fun `takes no price and no quantity from a flyer`() {
        listOf("prosciutto", "deodorante", "salviettine").forEach { name ->
            val item = checkNotNull(parseFlyer(fixture(name))) { name }
            assertEquals(name, 0L, item.priceCents)
            assertEquals(name, 1.0, item.quantity, 0.0)
        }
    }

    @Test
    fun `a written list is not a flyer`() {
        // Lines of one size, as a note or a screenshot gives: nothing towers over the rest.
        val lines = listOf("Shopping", "Milk", "Bread", "Ham").mapIndexed { index, text ->
            ScannedLine(text, left = 80, top = 100 + index * 120, right = 600, bottom = 160 + index * 120)
        }
        assertNull(parseFlyer(lines))
    }

    @Test
    fun `no lines is not a flyer`() {
        assertNull(parseFlyer(emptyList()))
    }

    @Test
    fun `a flyer routes through parseScan, a list does not`() {
        assertEquals(1, parseScan(fixture("deodorante")).size)
        val note = listOf("Milk 1.50", "2 x Bread").mapIndexed { index, text ->
            ScannedLine(text, left = 80, top = 100 + index * 120, right = 600, bottom = 160 + index * 120)
        }
        assertEquals(2, parseScan(note).size)
    }
}
