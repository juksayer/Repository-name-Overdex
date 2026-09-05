package com.example.overdex.battle.observation

/**
 * A unique identifier for a Match session.
 * 
 * Following the project's identity-wrapper convention to avoid primitive 
 * obsession and ensure type safety across architectural boundaries.
 */
data class MatchId(val value: String)
