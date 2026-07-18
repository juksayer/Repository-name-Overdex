package com.example.overdex.battle.observation

import com.example.overdex.battle.timeline.event.TimelineEvent

/**
 * Contract responsible for transforming transient [Observation] data
 * into immutable [TimelineEvent] records.
 */
interface ObservationReconciler {
    fun reconcile(workspace: ObservationWorkspace): List<TimelineEvent>
}
