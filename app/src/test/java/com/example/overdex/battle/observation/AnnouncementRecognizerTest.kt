package com.example.overdex.battle.observation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnnouncementRecognizerTest {

    private val vocabulary = setOf("LUCARIO", "SEALEO", "PIKACHU")

    @Test
    fun `processText accepts valid GO announcement for species in vocabulary`() {
        val rawText = "GO, LUCARIO!"
        val result = AnnouncementRecognizer.processText(rawText, vocabulary)

        assertEquals("Full raw text must be preserved", "GO, LUCARIO!", result.value)
        assertEquals(1.0f, result.confidence)
    }

    @Test
    fun `processText accepts valid SENT OUT announcement for species in vocabulary`() {
        val rawText = "SENT OUT SEALEO"
        val result = AnnouncementRecognizer.processText(rawText, vocabulary)

        assertEquals("SENT OUT SEALEO", result.value)
        assertEquals(1.0f, result.confidence)
    }

    @Test
    fun `processText handles uncertain species recovery`() {
        // Pattern matched but species not in vocabulary (e.g. mangled OCR)
        val rawText = "GO, LUCAR1O!"
        val result = AnnouncementRecognizer.processText(rawText, vocabulary)

        assertEquals("Full raw text must be preserved even if uncertain", "GO, LUCAR1O!", result.value)
        assertEquals("Uncertain recovery should have low confidence", 0.3f, result.confidence)
    }

    @Test
    fun `processText handles unknown species in valid pattern`() {
        val rawText = "GO, AGUMON!"
        val result = AnnouncementRecognizer.processText(rawText, vocabulary)

        assertEquals("GO, AGUMON!", result.value)
        assertEquals(0.3f, result.confidence)
    }

    @Test
    fun `processText rejects unrelated announcements`() {
        val result1 = AnnouncementRecognizer.processText("YOU WIN!", vocabulary)
        assertNull(result1.value)
        assertEquals(0.0f, result1.confidence)

        val result2 = AnnouncementRecognizer.processText("SUPER EFFECTIVE!", vocabulary)
        assertNull(result2.value)
        assertEquals(0.0f, result2.confidence)

        val result3 = AnnouncementRecognizer.processText("GET READY!", vocabulary)
        assertNull(result3.value)
        assertEquals(0.0f, result3.confidence)
    }
    
    @Test
    fun `processText handles whitespace and casing differences`() {
        val result = AnnouncementRecognizer.processText("  go,  pikachu !  ", vocabulary)
        assertEquals("go,  pikachu !", result.value)
        assertEquals(1.0f, result.confidence)
    }
}
