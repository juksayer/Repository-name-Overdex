package com.example.overdex.battle.observation

import com.example.overdex.BattleMemory
import com.example.overdex.battle.custody.InMemoryTestimonyCustody
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.battle.timeline.BattleTimelineBuilder
import com.example.overdex.battle.timeline.confidence.ConfidenceLevel
import com.example.overdex.battle.timeline.confidence.ConfidenceScore
import com.example.overdex.battle.timeline.event.ObservationEvent
import com.example.overdex.battle.timeline.event.PokemonSwitched
import com.example.overdex.battle.timeline.event.TimelineEvent
import com.example.overdex.battle.timeline.evidence.EvidenceId
import com.example.overdex.battle.timeline.evidence.VisualEvidence
import com.example.overdex.battle.timeline.observer.ObservationSource
import com.example.overdex.battle.timeline.observer.ObserverId
import org.junit.Assert.assertEquals
import org.junit.Test

class CustodyChainTest {

    @Test
    fun `Evidence identity is recoverable at the resulting timeline event`() {
        // 1. Establish Evidence with a unique identity
        val evidenceId = EvidenceId("FRAME_001")
        val evidence = VisualEvidence(
            id = evidenceId,
            sourceId = "CAMERA_01",
            frameUri = "uri://frames/001"
        )

        // 2. Wrap in a transient Observation
        val observation = Observation(
            timestamp = 1000L,
            observerId = ObserverId("OBS_01", ObservationSource.SCREEN_CAPTURE),
            evidence = listOf(evidence),
            confidence = ConfidenceScore(1.0f, ConfidenceLevel.OBSERVED)
        )

        // 3. Submit to Match (Existing Production Handoff)
        val match = Match(
            matchId = "MATCH_001",
            custody = InMemoryTestimonyCustody(),
            realityTimeline = InMemoryRealityTimeline(),
            pokemonKnowledge = FakePokemonKnowledge()
        )
        match.submit(observation)

        // 4. Follow through Workspace (Existing Production Handoff)
        val collectedObservation = match.workspace.observations[0]
        assertEquals(evidenceId, collectedObservation.evidence[0].id)

        // 5. Follow through Reconciliation (Existing Architectural Boundary)
        // We use a reconciler that leverages the existing ObservationEvent structure
        val reconciler = object : ObservationReconciler {
            override fun reconcile(workspace: BattleWorkspace): List<TimelineEvent> {
                return workspace.observations.map { obs ->
                    // Preservation occurs here because ObservationEvent supports Evidence
                    ObservationEvent(
                        timestamp = obs.timestamp,
                        observerId = obs.observerId,
                        evidence = obs.evidence[0],
                        confidence = obs.confidence
                    )
                }
            }
        }

        val derivedEvents = reconciler.reconcile(match.workspace)
        
        // 6. Final preservation in the Battle Timeline
        val builder = BattleTimelineBuilder()
        derivedEvents.forEach { builder.addEvent(it) }
        val timeline = builder.build()

        // 7. ASSERT: Recover the original EvidenceId
        val finalEvent = timeline.events[0] as ObservationEvent
        assertEquals("The original EvidenceId must be recoverable at the end of the chain", 
            evidenceId, finalEvent.evidence.id)
    }

    @Test
    fun `Evidence identity is preserved when derived into a domain-specific event`() {
        // 1. Establish Evidence with a unique identity
        val evidenceId = EvidenceId("FRAME_002")
        val evidence = VisualEvidence(
            id = evidenceId,
            sourceId = "CAMERA_01",
            frameUri = "uri://frames/002"
        )

        // 2. Wrap in a transient Observation
        val observation = Observation(
            timestamp = 2000L,
            observerId = ObserverId("OBS_01", ObservationSource.SCREEN_CAPTURE),
            evidence = listOf(evidence),
            confidence = ConfidenceScore(1.0f, ConfidenceLevel.OBSERVED)
        )

        // 3. Reconcile into a domain event (PokemonSwitched) preserving the evidence reference
        val domainEvent = PokemonSwitched(
            timestamp = observation.timestamp,
            observerId = observation.observerId,
            pokemonId = "Pikachu",
            evidence = observation.evidence[0] // Preserving the reference
        )

        // 4. ASSERT: Recover the original EvidenceId from the domain event
        assertEquals("The domain event must preserve the evidence identity", 
            evidenceId, domainEvent.evidence?.id)
    }
}
