package com.example.overdex.battle.observation

import android.util.Log

/**
 * Coordinator responsible for managing the lifecycle of multiple [Observer] instances.
 * 
 * The dispatcher bridges the gap between individual sensing technologies and the
 * active [Match], ensuring that all registered observers are started
 * and stopped correctly.
 */
class ObservationDispatcher {
    private val observers = mutableListOf<Observer>()

    /**
     * Registers an observer to participate in battle observation.
     */
    fun register(observer: Observer) {
        observers.add(observer)
    }

    /**
     * Unregisters an observer from the dispatcher.
     */
    fun unregister(observer: Observer) {
        observers.remove(observer)
    }

    /**
     * Starts all registered observers and attaches them to the provided match.
     */
    fun startAll(match: Match) {
        Log.d("DEPLOY", "4 startAll()")
        observers.forEach {
            Log.d("DEPLOY", "Starting ${it.javaClass.simpleName}")
            it.start(match)
        }
    }

    /**
     * Stops all registered observers.
     */
    fun stopAll() {
        observers.forEach { it.stop() }
    }
}
