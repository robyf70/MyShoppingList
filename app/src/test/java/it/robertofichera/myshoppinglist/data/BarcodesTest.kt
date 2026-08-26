package it.robertofichera.myshoppinglist.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BarcodesTest {

    @Test
    fun `names a product in the reader's language, with its brand and size`() {
        val json = """
            {"status":1,"product":{
              "product_name":"Hazelnut spread",
              "product_name_it":"Crema alla nocciola",
              "brands":"Ferrero,Nutella",
              "quantity":"400 g"}}
        """.trimIndent()
        assertEquals("Ferrero Crema alla nocciola 400 g", productNameFrom(json, "it"))
    }

    @Test
    fun `falls back to the untranslated name`() {
        val json = """{"status":1,"product":{"product_name":"Baked beans","brands":"Heinz"}}"""
        assertEquals("Heinz Baked beans", productNameFrom(json, "it"))
    }

    @Test
    fun `does not repeat a brand the name already carries`() {
        val json = """{"status":1,"product":{"product_name":"Nutella","brands":"Nutella","quantity":"750 g"}}"""
        assertEquals("Nutella 750 g", productNameFrom(json, "it"))
    }

    @Test
    fun `takes only the first of several brands`() {
        val json = """{"status":1,"product":{"product_name":"Latte","brands":"Granarolo, Gruppo Granarolo"}}"""
        assertEquals("Granarolo Latte", productNameFrom(json, "it"))
    }

    @Test
    fun `names nothing for a barcode the database does not hold`() {
        assertNull(productNameFrom("""{"status":0,"status_verbose":"product not found"}""", "it"))
    }

    @Test
    fun `names nothing when the entry carries no name`() {
        assertNull(productNameFrom("""{"status":1,"product":{"brands":"Someone"}}""", "it"))
    }

    @Test
    fun `survives a reply that is not what was expected`() {
        assertNull(productNameFrom("", "it"))
        assertNull(productNameFrom("<html>503</html>", "it"))
        assertNull(productNameFrom("""{"status":1}""", "it"))
    }
}
