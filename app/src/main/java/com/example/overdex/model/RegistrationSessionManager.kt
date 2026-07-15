package com.example.overdex.model

import com.example.overdex.model.observation.Observation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single owner of the active registration session.
 * Manages the lifecycle of a RegistrationSession.
 */
object RegistrationSessionManager {
    private val _activeSession = MutableStateFlow<RegistrationSession?>(null)
    val activeSession: StateFlow<RegistrationSession?> = _activeSession.asStateFlow()

    /**
     * Starts a new registration session. 
     * If another session is active, it is replaced with a new empty session.
     */
    fun startSession() {
        _activeSession.value = RegistrationSession()
    }

    /**
     * Adds an observation to the active session.
     */
    fun addObservation(observation: Observation) {
        _activeSession.value = _activeSession.value?.addObservation(observation)
    }

    /**
     * Completes the current session and returns it, then clears the active session.
     */
    fun completeSession(): RegistrationSession? {
        val session = _activeSession.value
        _activeSession.value = null
        return session
    }

    /**
     * Cancels the active session and clears it.
     */
    fun cancelSession() {
        _activeSession.value = null
    }
}
