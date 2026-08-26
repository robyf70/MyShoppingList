package it.robertofichera.myshoppinglist.data

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            ScannedLine(
                o.getString("t"), o.getInt("l"), o.getInt("y"), o.getInt("r"), o.getInt("b"),
                o.optInt("k", 0),
            )
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
    fun `finds the offer though the recogniser mangled the price into letters`() {
        // The 2,69 on this page comes back as "L69": display type defeats recognition often
        // enough that a landmark cannot be required to read as a number.
        assertEquals(
            "FIORDIFRUTTA BIO RIGONI DI ASIAG0 vari tipi e grammature",
            parseFlyer(fixture("fiordifrutta"))?.name,
        )
    }

    @Test
    fun `stops the label where the flyer starts giving an example`() {
        // "un esempio: albicocche 250 g" names one variant of an offer covering all of them;
        // carrying it into the name would put apricot jam on a list that meant any flavour.
        val name = checkNotNull(parseFlyer(fixture("fiordifrutta"))).name
        assertFalse(name, name.contains("esempio", ignoreCase = true))
        assertFalse(name, name.contains("albicocche", ignoreCase = true))
    }

    @Test
    fun `ignores a loyalty badge sitting between the label and the price`() {
        // "SOLO TITOLARI" and its card logos sit closer to the price than the label does, so
        // proximity alone picks the badge. A label is set in one size; the badge is not.
        assertEquals(
            "BAGNODOCCIA PALMOLIVE vari tipi",
            parseFlyer(fixture("palmolive"))?.name,
        )
    }

    @Test
    fun `takes no price and no quantity from a flyer`() {
        listOf("prosciutto", "deodorante", "salviettine", "fiordifrutta", "palmolive").forEach { name ->
            val item = checkNotNull(parseFlyer(fixture(name))) { name }
            assertEquals(name, 0L, item.priceCents)
            assertEquals(name, 1.0, item.quantity, 0.0)
        }
    }

    @Test
    fun `a shouted price recognition could not read is not offered as one`() {
        // Display type defeats recognition on every one of these: "2", "2:", "L69".
        listOf("prosciutto", "deodorante", "salviettine", "fiordifrutta", "palmolive").forEach { name ->
            assertNull(name, proposedPrice(fixture(name)))
        }
    }

    @Test
    fun `the small print beside an offer is not its price`() {
        // "al kg € 26,90" prices a kilo, "-22,92%" is a discount, "02.09.2024" is a date.
        listOf("al kg € 26,90", "-22,92%", "02.09.2024").forEach { text ->
            val line = ScannedLine(text, left = 0, top = 0, right = 200, bottom = 40)
            if (text.startsWith("al kg")) {
                assertEquals(text, 2690L, priceOf(line))
            } else {
                assertNull(text, priceOf(line))
            }
        }
    }

    @Test
    fun `a picture of barely any text proposes its first line and its only sum`() {
        val name = ScannedLine("Latte Parmalat", left = 80, top = 100, right = 700, bottom = 190)
        val price = ScannedLine("1,99", left = 80, top = 260, right = 300, bottom = 340)
        val lines = listOf(name, price)

        assertEquals(price, proposedPrice(lines))
        assertEquals(listOf(name), proposedLabel(lines, proposedPrice(lines)))
        assertEquals(199L, priceOf(price))
    }

    @Test
    fun `a written list proposes nothing to mark`() {
        val lines = listOf("Shopping", "Milk", "Bread", "Ham").mapIndexed { index, text ->
            ScannedLine(text, left = 80, top = 100 + index * 120, right = 600, bottom = 160 + index * 120)
        }
        assertEquals(emptyList<ScannedLine>(), proposedLabel(lines, proposedPrice(lines)))
    }

    @Test
    fun `a page of several sums proposes none of them`() {
        val lines = listOf("Milk 1,99", "Bread 2,50", "Ham 4,00").mapIndexed { index, text ->
            ScannedLine(text, left = 80, top = 100 + index * 120, right = 600, bottom = 160 + index * 120)
        }
        assertNull(proposedPrice(lines))
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

}
