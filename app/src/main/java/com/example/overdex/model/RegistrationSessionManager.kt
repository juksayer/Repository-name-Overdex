package com.example.overdex.model

import com.example.overdex.model.observation.Observation as DomainObservation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Central coordinator for the active [RegistrationSession] lifecycle.
 * 
 * This manager owns the state of the current registration attempt, allowing
 * different screens and recognizers to contribute evidence to a shared session.
 */
object RegistrationSessionManager {
    private val _activeSession = MutableStateFlow<RegistrationSession?>(null)
    /** Reactive flow of the current active session. */
    val activeSession: StateFlow<RegistrationSession?> = _activeSession.asStateFlow()

    /**
     * Starts a new registration session. 
     * 
     * If another session is already active, it is replaced with a new empty session.
     * @param initialObservations Optional list of observations to prepopulate the session.
     */
    fun startSession(initialObservations: List<DomainObservation> = emptyList()) {
        _activeSession.value = RegistrationSession(observations = initialObservations)
    }

    /**
     * Adds an observation to the active session.
     */
    fun addObservation(observation: DomainObservation) {
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
