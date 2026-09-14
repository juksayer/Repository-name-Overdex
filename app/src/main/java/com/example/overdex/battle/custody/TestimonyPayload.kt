package com.example.overdex.battle.custody

import com.example.overdex.battle.observation.BattleCropProvenance
import com.example.overdex.battle.artifact.CropArtifactReference
import com.example.overdex.battle.artifact.AudioArtifactReference
import com.example.overdex.model.BattleResult
import com.example.overdex.model.PokemonType

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
 * Droidball began preserving the record for a possible battle. This precedes
 * GO, which independently establishes the live battle and its match clock.
 */
data object MatchRecordStarted : TestimonyPayload

/** The central VS screen was seen in the preserved pre-battle crop. */
data object VsScreenWitnessed : TestimonyPayload

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

/** A durably preserved microphone snippet, before cry recognition or interpretation. */
data class AudioCaptured(
    val artifact: AudioArtifactReference,
    val sampleRateHz: Int,
    val channelCount: Int,
    val durationNanos: Long,
    val cueKind: String
) : TestimonyPayload {
    init {
        require(sampleRateHz > 0)
        require(channelCount > 0)
        require(durationNanos > 0)
    }
}

/**
 * An immutable report that a named witness began or ceased operating.
 *
 * The article source identifies the witness. This is coverage evidence, not
 * evidence that the witness observed any particular battle phenomenon.
 */
data class WitnessOperating(
    val operating: Boolean
) : TestimonyPayload

/** The side whose active Pokémon type icons were witnessed. */
enum class ActivePokemonSide { PLAYER, OPPONENT }

/**
 * One active-type-icon witness result from one preserved Pokémon GO crop.
 *
 * This identifies the visible type-icon classification only. It neither
 * identifies the Pokémon nor replaces the cited crop artifact.
 */
data class ActivePokemonTypesWitnessed(
    val side: ActivePokemonSide,
    val types: List<PokemonType>,
    val similarity: Float,
    val basis: String = "POKEMON_GO_TYPE_ICON_REFERENCE_MATCH"
) : TestimonyPayload {
    init {
        require(types.size in 1..2) { "An active Pokémon has one or two visible types." }
        require(types.distinct().size == types.size) { "Visible active types must be distinct." }
        require(similarity in 0f..1f) { "Similarity must be normalized." }
    }
}

data object GetReadyWitnessed : TestimonyPayload
data object ChargeMoveUsedAnnounced : TestimonyPayload

/** The visible fill fraction of one player-inactive Pokémon HP bar. */
data class PlayerInactiveHpBarMeasured(
    val slot: Int,
    val filledFraction: Float
) : TestimonyPayload

/** Reproducible visual measurement of one inactive species-sprite area. */
data class PlayerInactiveSpeciesSpriteFingerprintMeasured(
    val slot: Int,
    val fingerprint: String,
    val sampleLeft: Int,
    val sampleTop: Int,
    val sampleRight: Int,
    val sampleBottom: Int
) : TestimonyPayload

/** A derived match boundary whose predecessor is the accepted GO glyph article. */
data object MatchStarted : TestimonyPayload

/** Derived immutable boundary from accepted win or loss testimony. */
data class MatchEnded(val result: BattleResult) : TestimonyPayload

/** Ranked acoustic reference candidates from one preserved, visually-cued audio artifact. */
data class BattleCryCandidatesMeasured(
    val cueKind: String,
    val candidates: List<BattleCryCandidateMeasurement>
) : TestimonyPayload
data class BattleCryCandidateMeasurement(val speciesId: Int, val referenceSha256: String, val similarity: Float)
