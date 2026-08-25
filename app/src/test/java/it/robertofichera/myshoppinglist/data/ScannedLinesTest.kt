package it.robertofichera.myshoppinglist.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannedLinesTest {

    @Test
    fun `reads a bare name`() {
        assertEquals(ScannedItem("Milk", 1.0, 0), parseScannedLine("Milk"))
    }

    @Test
    fun `strips the marks a written list starts lines with`() {
        listOf("- Milk", "• Milk", "* Milk", "☐ Milk", "✓ Milk", "1. Milk", "2) Milk") .forEach { line ->
            assertEquals(line, ScannedItem("Milk", 1.0, 0), parseScannedLine(line))
        }
    }

    @Test
    fun `reads a leading quantity in its several spellings`() {
        listOf("2 Milk", "2x Milk", "2 x Milk", "2× Milk").forEach { line ->
            assertEquals(line, ScannedItem("Milk", 2.0, 0), parseScannedLine(line))
        }
    }

    @Test
    fun `reads a decimal quantity`() {
        assertEquals(ScannedItem("Ham", 0.25, 0), parseScannedLine("0,25 Ham"))
    }

    @Test
    fun `reads a trailing price with either separator or a symbol`() {
        assertEquals(ScannedItem("Milk", 1.0, 150), parseScannedLine("Milk 1.50"))
        assertEquals(ScannedItem("Milk", 1.0, 150), parseScannedLine("Milk 1,50"))
        assertEquals(ScannedItem("Milk", 1.0, 150), parseScannedLine("Milk €1,50"))
        assertEquals(ScannedItem("Milk", 1.0, 150), parseScannedLine("Milk 1.50 €"))
    }

    @Test
    fun `reads a quantity and a price together`() {
        assertEquals(ScannedItem("Milk", 2.0, 150), parseScannedLine("2 x Milk 1.50"))
    }

    @Test
    fun `keeps a price as exact cents`() {
        assertEquals(1999L, parseScannedLine("Ham 19.99")?.priceCents)
    }

    @Test
    fun `reads this app's own shared line, taking the unit price rather than the total`() {
        val item = parseScannedLine("- Milk × 2 = €3.00")
        assertEquals(ScannedItem("Milk", 2.0, 0), item)
    }

    @Test
    fun `keeps a multi word name intact`() {
        assertEquals(ScannedItem("Olive oil", 1.0, 0), parseScannedLine("Olive oil"))
        assertEquals(ScannedItem("Extra virgin olive oil", 2.0, 599), parseScannedLine("2 Extra virgin olive oil 5,99"))
    }

    @Test
    fun `rejects a line with no word in it`() {
        assertNull(parseScannedLine(""))
        assertNull(parseScannedLine("   "))
        assertNull(parseScannedLine("-----"))
        assertNull(parseScannedLine("1.50"))
        assertNull(parseScannedLine("- ---"))
    }

    @Test
    fun `reads a whole scanned note, skipping its rubbish`() {
        val text = """
            Shopping
            - Milk 1.50
            ---
            2 x Bread

            0.5 Ham 19,99
        """.trimIndent()
        assertEquals(
            listOf(
                ScannedItem("Shopping", 1.0, 0),
                ScannedItem("Milk", 1.0, 150),
                ScannedItem("Bread", 2.0, 0),
                ScannedItem("Ham", 0.5, 1999),
            ),
            parseScannedText(text),
        )
    }

    @Test
    fun `never returns a blank name`() {
        val lines = listOf("- 2 x", "€1,50", "= 3.00", "•", "3)", "12 34")
        lines.forEach { line ->
            val item = parseScannedLine(line)
            assertTrue(line, item == null || item.name.isNotBlank())
        }
    }
}
