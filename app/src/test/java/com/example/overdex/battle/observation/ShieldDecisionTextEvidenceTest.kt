package com.example.overdex.battle.observation

import org.junit.Assert.*
import org.junit.Test

class ShieldDecisionTextEvidenceTest {
    private val reader = ShieldDecisionTextEvidence()
    private fun read(slot: Int, text: String, millis: Long, basis: String = "OCR") =
        reader.accept(slot, text, basis, millis * 1_000_000, millis, "$slot:$millis")

    @Test fun `Pixel recorded fragments corroborate shield pause with original source time`() {
        assertNull(read(2, "NOW", 60400))
        val signal = read(1, "NOT", 62030)!!
        assertEquals(60_400_000_000L, signal.at)
        assertEquals(setOf("2:60400", "1:62030"), signal.ids.toSet())
        assertNull(read(2, "NOW", 63150))
    }
    @Test fun `single fragments stale pairs and ordinary effectiveness text do not trigger`() {
        assertNull(read(1, "NOT VERY EFFECTIVE", 1000))
        assertNull(read(2, "NOW", 1500))
        assertNull(read(1, "NOT", 4000))
        assertNull(read(1, "NOW", 4100))
    }
    @Test fun `cached readings cannot supply independent evidence`() {
        assertNull(read(1, "NOT", 1000))
        assertNull(read(2, "NOW", 1100, "UNCHANGED_LETTER_PIXELS"))
    }
    @Test fun `full label works and impact resets for next attack`() {
        assertNotNull(read(1, "Not  now", 1000))
        assertNull(read(1, "NOT NOW", 2000))
        reader.reset()
        assertNotNull(read(1, "NOT NOW", 5000))
    }
}
