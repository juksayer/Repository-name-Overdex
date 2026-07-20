package com.example.overdex.battle.observation

import com.example.overdex.battle.timeline.event.TimelineEvent

/**
 * Responsible for distilling transient [Observation] data into immutable [TimelineEvent] records.
 * 
 * Reconcilers implement the logic that decides when a collection of evidence in an
 * [ObservationWorkspace] is sufficient to declare a factual event in the battle timeline.
 */
interface ObservationReconciler {
    /**
     * Analyzes the workspace and produces a list of timeline events.
     */
    fun reconcile(workspace: ObservationWorkspace): List<TimelineEvent>
}
