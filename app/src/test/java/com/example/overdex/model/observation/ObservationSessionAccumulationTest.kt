package com.example.overdex.model.observation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationSessionAccumulationTest {

    @Test
    fun `session accumulation - adding results to existing results`() {
        val r1 = RecognitionResult("Pikachu", 1.0f, "R1")
        val session1 = ObservationSession(
            recognitionResults = mapOf("Species" to listOf(r1))
        )

        val r2 = RecognitionResult(500, 1.0f, "R2")
        // Simulating GuidedObservationPipeline accumulation logic
        val allResults = session1.recognitionResults.toMutableMap()
        val currentResults = allResults["CP"] ?: emptyList()
        allResults["CP"] = currentResults + listOf(r2)

        val session2 = session1.copy(recognitionResults = allResults.toMap())

        assertEquals(2, session2.recognitionResults.size)
        assertTrue(session2.recognitionResults.containsKey("Species"))
        assertTrue(session2.recognitionResults.containsKey("CP"))
        assertEquals("Pikachu", session2.recognitionResults["Species"]?.first()?.value)
        assertEquals(500, session2.recognitionResults["CP"]?.first()?.value)
    }

    @Test
    fun `session accumulation - appending results to same region`() {
        val r1 = RecognitionResult("Pikachu", 0.8f, "R1")
        val session1 = ObservationSession(
            recognitionResults = mapOf("Species" to listOf(r1))
        )

        val r2 = RecognitionResult("Raichu", 0.9f, "R1")
        val allResults = session1.recognitionResults.toMutableMap()
        val currentResults = allResults["Species"] ?: emptyList()
        allResults["Species"] = currentResults + listOf(r2)

        val session2 = session1.copy(recognitionResults = allResults.toMap())

        assertEquals(1, session2.recognitionResults.size)
        val speciesHistory = session2.recognitionResults["Species"]
        assertEquals(2, speciesHistory?.size)
        
        // Resolution should pick the higher confidence one
        assertEquals("Raichu", session2.resolveResults()["Species"]?.first()?.value)
    }
}
