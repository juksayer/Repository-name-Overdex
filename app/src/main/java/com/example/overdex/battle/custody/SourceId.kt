package com.example.overdex.battle.custody

/**
 * A lightweight, unique identifier for a source of testimony or state signals.
 * 
 * Decoupled from specific occupations (Witness, Collector) to ensure 
 * the custody layer remains independent of the producer's nature.
 */
data class SourceId(val id: String)
