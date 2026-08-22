package com.example.overdex.battle.timeline.evidence

import com.example.overdex.battle.observation.Observation
import com.example.overdex.battle.timeline.confidence.ConfidenceLevel
import com.example.overdex.battle.timeline.confidence.ConfidenceScore
import com.example.overdex.battle.timeline.event.ObservationEvent
import com.example.overdex.battle.timeline.observer.ObservationSource
import com.example.overdex.battle.timeline.observer.ObserverId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class EvidenceIdentityTest {

    @Test
    fun `Evidence instance has stable EvidenceId`() {
        val evidenceId = EvidenceId("E123")
        val evidence = VisualEvidence(evidenceId, "SOURCE_A", "uri://frame/123")
        
        assertEquals(evidenceId, evidence.id)
    }

    @Test
    fun `Observation preserves the EvidenceId`() {
        val evidenceId = EvidenceId("E123")
        val evidence = VisualEvidence(evidenceId, "SOURCE_A", "uri://frame/123")
        
        val observation = Observation(
            timestamp = 1000L,
            observerId = ObserverId("OBS_1", ObservationSource.SCREEN_CAPTURE),
            evidence = listOf(evidence),
            confidence = ConfidenceScore(1.0f, ConfidenceLevel.OBSERVED)
        )
        
        assertEquals(evidenceId, observation.evidence[0].id)
    }

    @Test
    fun `ObservationEvent preserves the EvidenceId`() {
        val evidenceId = EvidenceId("E123")
        val evidence = VisualEvidence(evidenceId, "SOURCE_A", "uri://frame/123")
        
        val event = ObservationEvent(
            timestamp = 1000L,
            observerId = ObserverId("OBS_1", ObservationSource.SCREEN_CAPTURE),
            evidence = evidence,
            confidence = ConfidenceScore(1.0f, ConfidenceLevel.OBSERVED)
        )
        
        assertEquals(evidenceId, event.evidence.id)
    }

    @Test
    fun `Evidence sourceId remains distinct from EvidenceId`() {
        val evidenceId = EvidenceId("UNIQUE_FRAME_ID")
        val sourceId = "CAMERA_SOURCE"
        
        val evidence = VisualEvidence(evidenceId, sourceId, "uri://frame/123")
        
        assertEquals(evidenceId, evidence.id)
        assertEquals(sourceId, evidence.sourceId)
        assertNotEquals(evidence.id.value, evidence.sourceId)
    }
}
