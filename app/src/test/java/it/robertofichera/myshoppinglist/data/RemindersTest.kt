package it.robertofichera.myshoppinglist.data

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class RemindersTest {

    private val rome = ZoneId.of("Europe/Rome")

    // 2026-09-12 as the date picker reports it: midnight UTC.
    private val september12Utc = 1_789_171_200_000L

    @Test
    fun `the day is read as UTC midnight and the time in the given zone`() {
        // 18:30 in Rome on that day is 16:30Z under summer time.
        assertEquals(1_789_230_600_000L, reminderAt(september12Utc, 18, 30, rome))
    }

    @Test
    fun `the same wall-clock time in UTC is two hours later`() {
        assertEquals(1_789_237_800_000L, reminderAt(september12Utc, 18, 30, ZoneId.of("UTC")))
    }

    @Test
    fun `winter time uses the winter offset`() {
        // 2026-01-10 midnight UTC; 08:00 Rome is 07:00Z.
        assertEquals(1_768_028_400_000L, reminderAt(1_768_003_200_000L, 8, 0, rome))
    }
}
