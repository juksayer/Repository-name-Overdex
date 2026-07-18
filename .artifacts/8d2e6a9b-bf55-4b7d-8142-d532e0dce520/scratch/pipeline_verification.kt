import com.example.overdex.battle.observation.*
import com.example.overdex.battle.timeline.*
import com.example.overdex.battle.timeline.event.*
import com.example.overdex.battle.timeline.observer.*
import com.example.overdex.battle.timeline.confidence.*
import com.example.overdex.battle.timeline.evidence.*

fun verifyPipeline() {
    // 1. Setup the session
    val session = ObservationSession(sessionId = "BATTLE_001")
    
    // 2. Simulate an observation (e.g. from Droidball)
    val observation = Observation(
        timestamp = System.currentTimeMillis(),
        observerId = ObserverId("DB_01", ObservationSource.DROIDBALL),
        evidence = listOf(VisualEvidence("FRAME_123", "uri://frame/123")),
        confidence = ConfidenceScore(0.95f, ConfidenceLevel.CONFIRMED)
    )
    
    // 3. Submit to the session
    session.submit(observation)
    println("Observation submitted to session: ${session.sessionId}")
    
    // 4. Pass workspace to reconciler
    val reconciler = object : ObservationReconciler {
        override fun reconcile(workspace: ObservationWorkspace): List<TimelineEvent> {
            // Trivial mapping for verification
            return workspace.observations.map { obs ->
                object : TimelineEvent {
                    override val timestamp = obs.timestamp
                    override val observerId = obs.observerId
                }
            }
        }
    }
    
    val derivedEvents = reconciler.reconcile(session.workspace)
    println("Reconciler derived ${derivedEvents.size} events")
    
    // 5. Assemble the timeline
    val builder = BattleTimelineBuilder()
    derivedEvents.forEach { builder.addEvent(it) }
    
    val timeline = builder.build()
    println("Final timeline assembled with ${timeline.events.size} events")
}
