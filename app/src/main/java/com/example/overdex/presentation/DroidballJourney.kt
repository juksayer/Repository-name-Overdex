package com.example.overdex.presentation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DroidballJourneyPhase { DOCKED, DEPARTING, TRANSIT, ARRIVING, FIELD, RETURNING, DOCKING }

data class DroidballJourneyState(
    val phase: DroidballJourneyPhase = DroidballJourneyPhase.DOCKED,
    val generation: Long = 0,
    val startedAtNanos: Long = 0,
) {
    val shellOwnsBody: Boolean get() = when (phase) {
        DroidballJourneyPhase.DOCKED, DroidballJourneyPhase.DEPARTING, DroidballJourneyPhase.DOCKING -> true
        else -> false
    }
    val fieldOwnsBody: Boolean get() = when (phase) {
        DroidballJourneyPhase.ARRIVING, DroidballJourneyPhase.FIELD, DroidballJourneyPhase.RETURNING -> true
        else -> false
    }
}

/** Presentation only. The capture service never waits for an animation to stop. */
object DroidballJourney {
    private val mutableState = MutableStateFlow(DroidballJourneyState())
    val state = mutableState.asStateFlow()
    private fun move(phase: DroidballJourneyPhase, now: Long) {
        mutableState.value = DroidballJourneyState(phase, mutableState.value.generation + 1, now)
    }
    @Synchronized fun depart(now: Long = System.nanoTime()): Long {
        move(DroidballJourneyPhase.DEPARTING, now)
        return mutableState.value.generation
    }
    @Synchronized fun departureFinished(generation: Long, now: Long = System.nanoTime()): Boolean {
        if (mutableState.value.generation != generation || mutableState.value.phase != DroidballJourneyPhase.DEPARTING) return false
        move(DroidballJourneyPhase.TRANSIT, now)
        return true
    }
    @Synchronized fun enterField(now: Long = System.nanoTime()) {
        if (mutableState.value.phase in setOf(DroidballJourneyPhase.TRANSIT, DroidballJourneyPhase.DOCKED))
            move(DroidballJourneyPhase.ARRIVING, now)
    }
    @Synchronized fun arrivalFinished(generation: Long, now: Long = System.nanoTime()) {
        if (mutableState.value.generation == generation && mutableState.value.phase == DroidballJourneyPhase.ARRIVING)
            move(DroidballJourneyPhase.FIELD, now)
    }
    @Synchronized fun returnToShell(now: Long = System.nanoTime()): Long {
        val phase = mutableState.value.phase
        if (phase in setOf(DroidballJourneyPhase.FIELD, DroidballJourneyPhase.ARRIVING)) move(DroidballJourneyPhase.RETURNING, now)
        else if (phase !in setOf(DroidballJourneyPhase.DOCKED, DroidballJourneyPhase.DOCKING,
                DroidballJourneyPhase.DEPARTING, DroidballJourneyPhase.RETURNING))
            move(DroidballJourneyPhase.DOCKING, now)
        return mutableState.value.generation
    }
    @Synchronized fun fieldExitFinished(generation: Long, now: Long = System.nanoTime()) {
        if (mutableState.value.generation == generation && mutableState.value.phase == DroidballJourneyPhase.RETURNING)
            move(DroidballJourneyPhase.DOCKING, now)
    }
    @Synchronized fun dockingFinished(generation: Long, now: Long = System.nanoTime()) {
        if (mutableState.value.generation == generation && mutableState.value.phase == DroidballJourneyPhase.DOCKING)
            move(DroidballJourneyPhase.DOCKED, now)
    }
    @Synchronized fun hideInTransit(now: Long = System.nanoTime()) {
        if (mutableState.value.phase !in setOf(DroidballJourneyPhase.DEPARTING, DroidballJourneyPhase.TRANSIT))
            move(DroidballJourneyPhase.TRANSIT, now)
    }
    @Synchronized fun stopped(shellVisible: Boolean, now: Long = System.nanoTime()) {
        if (mutableState.value.phase == DroidballJourneyPhase.DOCKED) return
        move(if (shellVisible) DroidballJourneyPhase.DOCKING else DroidballJourneyPhase.TRANSIT, now)
    }
    @Synchronized fun reset() { mutableState.value = DroidballJourneyState(generation = mutableState.value.generation + 1) }
}
