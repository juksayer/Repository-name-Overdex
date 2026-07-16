package com.example.overdex.model.observation

import org.junit.Assert.assertEquals
import org.junit.Test

class ObservationResolutionTest {

    @Test
    fun `resolveResults - higher confidence wins`() {
        val lowConf = RecognitionResult(value = "Pikachu", confidence = 0.5f, recognizer = "R1")
        val highConf = RecognitionResult(value = "Raichu", confidence = 0.9f, recognizer = "R1")

        val session = ObservationSession(
            recognitionResults = mapOf("Species" to listOf(lowConf, highConf))
        )

        val resolved = session.resolveResults()
        assertEquals("Raichu", resolved["Species"]?.firstOrNull()?.value)
    }

    @Test
    fun `resolveResults - missing never wins`() {
        val existing = RecognitionResult(value = "Pikachu", confidence = 0.8f, recognizer = "R1")
        val missing = RecognitionResult<String>(value = null, confidence = 1.0f, recognizer = "R1")

        val session = ObservationSession(
            recognitionResults = mapOf("Species" to listOf(existing, missing))
        )

        val resolved = session.resolveResults()
        assertEquals("Pikachu", resolved["Species"]?.firstOrNull()?.value)
    }

    @Test
    fun `resolveResults - equal confidence preserves existing`() {
        val first = RecognitionResult(value = "Pikachu", confidence = 0.8f, recognizer = "R1")
        val second = RecognitionResult(value = "Raichu", confidence = 0.8f, recognizer = "R1")

        val session = ObservationSession(
            recognitionResults = mapOf("Species" to listOf(first, second))
        )

        val resolved = session.resolveResults()
        assertEquals("Pikachu", resolved["Species"]?.firstOrNull()?.value)
    }

    @Test
    fun `resolveResults - new information wins`() {
        val newInfo = RecognitionResult(value = "Pikachu", confidence = 0.1f, recognizer = "R1")

        val session = ObservationSession(
            recognitionResults = mapOf("Species" to listOf(newInfo))
        )

        val resolved = session.resolveResults()
        assertEquals("Pikachu", resolved["Species"]?.firstOrNull()?.value)
    }
    
    @Test
    fun `resolveResults - multiple recognizers are resolved independently`() {
        val r1a = RecognitionResult(value = "Pikachu", confidence = 0.5f, recognizer = "R1")
        val r1b = RecognitionResult(value = "Raichu", confidence = 0.8f, recognizer = "R1")
        val r2a = RecognitionResult(value = "Thunder Shock", confidence = 0.9f, recognizer = "R2")
        val r2b = RecognitionResult(value = "Quick Attack", confidence = 0.4f, recognizer = "R2")

        val session = ObservationSession(
            recognitionResults = mapOf("Data" to listOf(r1a, r1b, r2a, r2b))
        )

        val resolved = session.resolveResults()
        val dataResults = resolved["Data"] ?: emptyList()
        
        assertEquals(2, dataResults.size)
        assertEquals("Raichu", dataResults.find { it.recognizer == "R1" }?.value)
        assertEquals("Thunder Shock", dataResults.find { it.recognizer == "R2" }?.value)
    }
}
