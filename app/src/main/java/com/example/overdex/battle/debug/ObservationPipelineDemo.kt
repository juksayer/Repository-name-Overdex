package com.example.overdex.battle.debug

import android.util.Log
import com.example.overdex.battle.observation.*
import com.example.overdex.battle.observation.debug.*
import com.example.overdex.battle.timeline.*
import com.example.overdex.battle.timeline.event.*

/**
 * Developer-only demonstration of the end-to-end observation pipeline.
 */
object ObservationPipelineDemo {
    private const val TAG = "OBSERVATION_DEMO"

    fun run() {
        Log.d(TAG, "Starting Observation Pipeline Demo...")

        // 1. Setup Session and Builder
        val session = ObservationSession(sessionId = "DEMO_SESSION_001")
        val builder = BattleTimelineBuilder()
        val source = ManualObservationSource()

        // 2. Emit Observations
        Log.d(TAG, "Emitting observations...")
        source.emit(ObservationFactory.createVisualObservation("UI_CAM", "uri://frames/001"), session)
        source.emit(ObservationFactory.createStateObservation("SYS_INT", "BATTLE_STATE", "STARTED"), session)

        Log.d(TAG, "Workspace contains ${session.workspace.observations.size} observations.")

        // 3. Reconcile
        val reconciler = object : ObservationReconciler {
            override fun reconcile(workspace: ObservationWorkspace): List<TimelineEvent> {
                // Intentionally trivial reconciliation for demo
                return workspace.observations.map { obs ->
                    // Map every observation to a basic event for visibility
                    object : TimelineEvent {
                        override val timestamp: Long = obs.timestamp
                        override val observerId = obs.observerId
                    }
                }
            }
        }

        val events = reconciler.reconcile(session.workspace)
        Log.d(TAG, "Reconciler produced ${events.size} TimelineEvents.")

        // 4. Assemble Timeline
        events.forEach(builder::addEvent)
        val timeline = builder.build()

        Log.d(TAG, "Final BattleTimeline contains ${timeline.events.size} events.")
        Log.d(TAG, "Demo completed successfully.")
    }
}
