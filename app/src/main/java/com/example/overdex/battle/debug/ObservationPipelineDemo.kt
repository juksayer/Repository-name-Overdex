package com.example.overdex.battle.debug

import android.util.Log
import com.example.overdex.battle.custody.InMemoryTestimonyCustody
import com.example.overdex.battle.observation.BattleWorkspace
import com.example.overdex.battle.observation.Match
import com.example.overdex.battle.observation.ObservationDispatcher
import com.example.overdex.battle.observation.ObservationReconciler
import com.example.overdex.battle.observation.debug.DebugObserver
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.battle.timeline.BattleTimelineBuilder
import com.example.overdex.battle.timeline.event.TimelineEvent
import com.example.overdex.data.PokemonRepository

/**
 * Developer-only demonstration of the end-to-end observation pipeline.
 */
object ObservationPipelineDemo {
    private const val TAG = "OBSERVATION_DEMO"

    fun run(pokemonRepository: PokemonRepository) {
        Log.d(TAG, "Starting Observation Pipeline Demo...")

        // 1. Setup Match and Dispatcher
        val match = Match(
            matchId = "DEMO_MATCH_001",
            custody = InMemoryTestimonyCustody(),
            realityTimeline = InMemoryRealityTimeline(),
            pokemonRepository = pokemonRepository
        )
        val builder = BattleTimelineBuilder()
        val dispatcher = ObservationDispatcher()

        // 2. Register and Start Observers
        Log.d(TAG, "Configuring observers...")
        dispatcher.register(DebugObserver())

        Log.d(TAG, "Starting observers...")
        dispatcher.startAll(match)

        Log.d(TAG, "Workspace contains ${match.workspace.observations.size} observations.")

        // 3. Reconcile
        val reconciler = object : ObservationReconciler {
            override fun reconcile(workspace: BattleWorkspace): List<TimelineEvent> {
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

        val events = reconciler.reconcile(match.workspace)
        Log.d(TAG, "Reconciler produced ${events.size} TimelineEvents.")

        // 4. Assemble Timeline
        events.forEach(builder::addEvent)
        val timeline = builder.build()

        Log.d(TAG, "Final BattleTimeline contains ${timeline.events.size} events.")

        // 5. Cleanup
        dispatcher.stopAll()
        Log.d(TAG, "Demo completed successfully.")
    }
}