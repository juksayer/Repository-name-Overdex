package com.example.overdex.battle.custody

import com.example.overdex.battle.observation.BattleCropProvenance
import com.example.overdex.battle.artifact.CropArtifactReference
import com.example.overdex.model.BattleResult

/**
 * A domain-neutral container for what a Witness experiences.
 * 
 * TestimonyPayload is explicitly decoupled from the phenomenon being observed.
 * It exists only to carry data from a Witness into Custody without forcing the
 * Custody layer to understand or interpret the sensing technology used.
 */
interface TestimonyPayload

/**
 * A simple, neutral implementation of [TestimonyPayload] used for generic data.
 */
data class RawTestimony(val data: Any) : TestimonyPayload

/**
 * Testimony representing the "Attack Incoming!" phenomenon.
 */
data object AttackIncoming : TestimonyPayload

/**
 * Testimony representing an identified Pokémon species.
 * 
 * @property species The name of the Pokémon as recognized by the Witness.
 */
data class PokemonIdentified(val species: String) : TestimonyPayload

/**
 * Testimony supporting Match Start derived from trainer inactive timer overlay clearance.
 */
data class SupportingMatchStart(
    val frameIndex: Int,
    val upperColorfulPixelFraction: Float,
    val lowerColorfulPixelFraction: Float,
    val basis: String = "TRAINER_INACTIVE_TIMER_OVERLAY_CLEARANCE"
) : TestimonyPayload

/**
 * Testimony that the countdown template matcher accepted a glyph in the countdown crop.
 *
 * This preserves the matcher output. It does not itself assert a match lifecycle state.
 */
data class CountdownGlyphWitnessed(
    val glyph: String,
    val similarity: Float,
    val frameIndex: Int,
    val cropProvenance: BattleCropProvenance? = null,
    val basis: String = "COUNTDOWN_GLYPH_TEMPLATE_MATCH"
) : TestimonyPayload

/** A durably written crop artifact, before any recognizer interprets it. */
data class CropCaptured(
    val artifact: CropArtifactReference,
    val cropProvenance: BattleCropProvenance
) : TestimonyPayload

/** A derived match boundary whose predecessor is the accepted GO glyph article. */
data object MatchStarted : TestimonyPayload

/** Derived immutable boundary from accepted win or loss testimony. */
data class MatchEnded(val result: BattleResult) : TestimonyPayload
