package it.robertofichera.myshoppinglist.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareCodecTest {

    private val sample = SharedList(
        uuid = "8f14e45fceea167a5a36dedd4bea2543",
        name = "Groceries",
        budgetCents = 5000,
        items = listOf(
            SharedItem(name = "Milk", quantity = 2.0, priceCents = 150, bought = false),
            SharedItem(name = "Ham", quantity = 0.25, priceCents = 1999, bought = true),
        ),
    )

    @Test
    fun `round trips a list unchanged`() {
        assertEquals(sample, ShareCodec.decode(ShareCodec.encode(sample)))
    }

    @Test
    fun `keeps prices as exact cents`() {
        val decoded = ShareCodec.decode(ShareCodec.encode(sample))
        assertEquals(listOf(150L, 1999L), decoded?.items?.map { it.priceCents })
        assertEquals(5000L, decoded?.budgetCents)
    }

    @Test
    fun `round trips a list with no budget and no items`() {
        val empty = SharedList(uuid = "abc", name = "Empty", budgetCents = 0, items = emptyList())
        assertEquals(empty, ShareCodec.decode(ShareCodec.encode(empty)))
    }

    @Test
    fun `finds the token surrounded by chat text`() {
        val message = "Here you go!\n${ShareCodec.encode(sample)}\nSee you later"
        assertEquals(sample, ShareCodec.decode(message))
    }

    @Test
    fun `rejects text with no token`() {
        assertNull(ShareCodec.decode("Remember to buy milk"))
        assertNull(ShareCodec.decode(""))
    }

    @Test
    fun `rejects an unknown version`() {
        val future = ShareCodec.encode(sample).replace("msl:1:", "msl:2:")
        assertNull(ShareCodec.decode(future))
    }

    @Test
    fun `rejects a payload that is not gzip`() {
        assertNull(ShareCodec.decode("msl:1:bm90Z3ppcA"))
    }

    @Test
    fun `rejects a payload that is not json`() {
        assertNull(ShareCodec.decode(ShareCodec.encodeRaw("not json at all")))
    }

    @Test
    fun `rejects json missing the uuid`() {
        val without = ShareCodec.encodeRaw("""{"v":1,"n":"Groceries","b":0,"i":[]}""")
        assertNull(ShareCodec.decode(without))
    }
}
