package com.example.overdex.battle.observation

/**
 * Coordinator responsible for managing multiple [Observer] instances.
 * Bridges the gap between individual sensing technologies and the [ObservationSession].
 */
class ObservationDispatcher {
    private val observers = mutableListOf<Observer>()

    /**
     * Registers an observer to participate in future sessions.
     */
    fun register(observer: Observer) {
        observers.add(observer)
    }

    /**
     * Unregisters an observer.
     */
    fun unregister(observer: Observer) {
        observers.remove(observer)
    }

    /**
     * Starts all registered observers for the given session.
     */
    fun startAll(session: ObservationSession) {
        observers.forEach { it.start(session) }
    }

    /**
     * Stops all registered observers.
     */
    fun stopAll() {
        observers.forEach { it.stop() }
    }
}
