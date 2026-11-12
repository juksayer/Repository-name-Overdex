package com.example.overdex.battle.custody

import kotlinx.serialization.Serializable

/** Numeric pixel features. No point identifies a Pokémon, impact, recall or faint. */
@Serializable
data class TerminalParticleFeature(
    val x: Double, val y: Double, val area: Int, val width: Int, val height: Int,
    val freshFraction: Double, val haloCoverage: Double, val haloWeakestQuadrant: Double,
    val brightnessFalloff: Double,
)

/** Compact white component; its disappearance is not proof that a Pokémon vanished. */
@Serializable
data class TerminalFlashFeature(
    val x: Double, val y: Double, val area: Int, val width: Int, val height: Int,
    val density: Double,
)

@Serializable
data class TerminalParticleSample(
    val sourceAtNanos: Long, val wallTimeMillis: Long, val previousSourceAtNanos: Long?,
    val status: String, val sourceWidth: Int, val sourceHeight: Int,
    val bounds: List<Int>?,
    val publicationStream: Long?, val publicationSequence: Long?,
    val elapsedNanos: Long,
    val points: List<TerminalParticleFeature>,
    val flashes: List<TerminalFlashFeature> = emptyList(),
    val appearance: TerminalAppearanceMeasurement? = null,
)

/**
 * Bounded high-rate readings, batched without moving their individual source clocks.
 * requestedRecipient is scheduling context, not independently observed sprite ownership.
 * An empty ending batch closes an assignment even when no frame arrived.
 */
data class TerminalParticleObservations(
    val requestedRecipient: ActivePokemonSide,
    val phaseAtNanos: Long,
    val sourceDeadlineNanos: Long,
    val samples: List<TerminalParticleSample>,
    val ended: Boolean,
    val totalSamples: Int,
) : TestimonyPayload
