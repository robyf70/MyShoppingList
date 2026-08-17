package it.robertofichera.myshoppinglist

import it.robertofichera.myshoppinglist.data.isNewerVersion
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesTest {

    @Test
    fun `a higher version wins, whatever its depth`() {
        assertTrue(isNewerVersion("1.2", "1.1"))
        assertTrue(isNewerVersion("1.2", "1.1.9"))
        assertTrue(isNewerVersion("1.2.1", "1.2"))
        assertTrue(isNewerVersion("2.0", "1.9.9"))
        assertTrue(isNewerVersion("1.10", "1.9"))
    }

    @Test
    fun `the installed version and older ones are not offered`() {
        assertFalse(isNewerVersion("1.1", "1.1"))
        assertFalse(isNewerVersion("1.1.0", "1.1"))
        assertFalse(isNewerVersion("1.0", "1.1"))
        assertFalse(isNewerVersion("1.1", "1.2.3"))
    }

    @Test
    fun `a leading v on the tag is ignored`() {
        assertTrue(isNewerVersion("v1.2", "1.1"))
        assertFalse(isNewerVersion("v1.1", "1.1"))
    }

    @Test
    fun `an unreadable tag is never offered as an update`() {
        assertFalse(isNewerVersion("nightly", "1.1"))
        assertFalse(isNewerVersion("1.2-beta", "1.1"))
        assertFalse(isNewerVersion("", "1.1"))
    }
}
