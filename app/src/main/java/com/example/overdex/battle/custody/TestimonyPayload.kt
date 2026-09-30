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

/** Droidball's presentation opened, independently timestamped from battle state. */
data class BattleOverlayOpened(val reason: BattleOverlayOpenReason) : TestimonyPayload
enum class BattleOverlayOpenReason { VS_SCREEN, COUNTDOWN_GLYPH, BATTLE_WITNESS, USER_REQUEST }

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
    val cueKind: String,
    val captureSource: String = "MICROPHONE",
    val peakAmplitude: Float? = null
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

/** A side-attributed active species result, derived from one preserved species crop. */
data class ActivePokemonSpeciesWitnessed(
    val side: ActivePokemonSide,
    val speciesName: String,
    val speciesId: Int? = null
) : TestimonyPayload

/** The pre-GO Pokémon GO Team Select surface was accepted from preserved league text. */
data object TeamSelectPartyWitnessed : TestimonyPayload

/** One Player-selected Team Select card, preserving its left-to-right roster slot. */
data class PlayerTeamRosterSlotWitnessed(
    val slot: Int,
    val speciesName: String,
    val speciesId: Int? = null
) : TestimonyPayload {
    init { require(slot in 1..3) }
}

/**
 * One player team slot deliberately configured in Overdex before capture.
 * This is reference input recorded with the Match, separate from visual testimony.
 */
data class PlayerTeamSlotConfigured(
    val slot: Int,
    val speciesName: String,
    val speciesId: Int,
    val fastMoveName: String,
    val chargedMoveNames: List<String>
) : TestimonyPayload {
    init {
        require(slot in 1..3)
        require(speciesName.isNotBlank())
        require(fastMoveName.isNotBlank())
        require(chargedMoveNames.size in 1..2)
    }
}

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

/** One visible active HP bar, measured within the cited active-HP crop. */
data class ActiveHpBarMeasured(
    val side: ActivePokemonSide,
    val barLeft: Int,
    val barTop: Int,
    val barRight: Int,
    val barBottom: Int,
    val filledFraction: Float
) : TestimonyPayload {
    init {
        require(barLeft >= 0 && barTop >= 0)
        require(barRight > barLeft && barBottom > barTop)
        require(filledFraction in 0f..1f)
    }
}

/**
 * The monotonic interval between two completed vertical excursions of one
 * active HP bar. This is a motion measurement, not yet a move-name claim.
 */
data class ActiveHpBarMotionCadenceMeasured(
    /** The side whose bar moved with its own Pokémon's animation. */
    val movingSide: ActivePokemonSide,
    val intervalNanos: Long,
    val verticalExcursionPixels: Float,
    val sampleCount: Int
) : TestimonyPayload {
    init {
        require(intervalNanos > 0L)
        require(verticalExcursionPixels >= 0f)
        require(sampleCount >= 2)
    }
}

/**
 * A persistent upward change measured in the player's charged-move energy
 * controls. Both controls receive the same earned energy, but their visible
 * fractions can differ because their moves have different costs.
 */
data class PlayerChargeMoveEnergyFillIncreased(
    val beforeBySlot: List<Float>,
    val afterBySlot: List<Float>,
    val changedSlots: List<Int>
) : TestimonyPayload {
    init {
        require(beforeBySlot.size == 2 && afterBySlot.size == 2)
        require(beforeBySlot.all { it in 0f..1f } && afterBySlot.all { it in 0f..1f })
        require(changedSlots.isNotEmpty() && changedSlots.all { it in 0..1 })
    }
}

/** Elapsed monotonic time between two confirmed player energy-fill increases. */
data class PlayerChargeMoveEnergyFillCadenceMeasured(
    val intervalNanos: Long,
    val contributingSlots: List<Int>
) : TestimonyPayload {
    init {
        require(intervalNanos > 0L)
        require(contributingSlots.isNotEmpty() && contributingSlots.all { it in 0..1 })
    }
}

/**
 * Cadence measured from the white-to-orange border pulse around a damaged HP bar.
 * The attacker is always the side opposite [damagedBarSide].
 */
data class ActiveHpBarBorderCadenceMeasured(
    val damagedBarSide: ActivePokemonSide,
    val intervalNanos: Long,
    val peakColorDistance: Float,
    val sampleCount: Int,
    val peakOrangeFraction: Float? = null,
    val baselineWhiteFraction: Float? = null
) : TestimonyPayload {
    init {
        require(intervalNanos > 0L)
        require(peakColorDistance >= 0f)
        require(sampleCount >= 1)
        require(peakOrangeFraction == null || peakOrangeFraction in 0f..1f)
        require(baselineWhiteFraction == null || baselineWhiteFraction in 0f..1f)
    }
}

/**
 * One rising edge of the white-to-orange border pulse around a damaged active HP
 * bar. The article timestamp is the pulse time; this payload deliberately
 * carries no cadence or move interpretation.
 */
data class ActiveHpBarBorderPulseObserved(
    val damagedBarSide: ActivePokemonSide,
    val peakColorDistance: Float,
    val sampleCount: Int,
    val peakOrangeFraction: Float? = null,
    val baselineWhiteFraction: Float? = null
) : TestimonyPayload {
    init {
        require(peakColorDistance >= 0f)
        require(sampleCount >= 1)
        require(peakOrangeFraction == null || peakOrangeFraction in 0f..1f)
        require(baselineWhiteFraction == null || baselineWhiteFraction in 0f..1f)
    }
}

/**
 * One transient visual signature measured near the Pokémon receiving damage.
 * It records color and spatial change only; move type and move name are later
 * conclusions that may combine this measurement with cadence and sound.
 */
data class FastMoveRecipientVisualArtifactMeasured(
    val damagedSide: ActivePokemonSide,
    val changedPixelFraction: Float,
    val meanColorDistance: Float,
    val meanRed: Float,
    val meanGreen: Float,
    val meanBlue: Float,
    val centroidX: Float,
    val centroidY: Float,
    val changedSampleCount: Int
) : TestimonyPayload {
    init {
        require(changedPixelFraction in 0f..1f)
        require(meanColorDistance in 0f..1f)
        require(meanRed in 0f..1f && meanGreen in 0f..1f && meanBlue in 0f..1f)
        require(centroidX in 0f..1f && centroidY in 0f..1f)
        require(changedSampleCount > 0)
    }
}

/** Cadence measured between two recipient-side visual artifact observations. */
data class FastMoveRecipientVisualCadenceMeasured(
    val damagedSide: ActivePokemonSide,
    val intervalNanos: Long
) : TestimonyPayload {
    init {
        require(intervalNanos > 0L)
    }
}

/** A move identity derived from measured cadence plus species reference knowledge. */
data class FastMoveIdentified(
    /** The Pokémon performing the move. */
    val side: ActivePokemonSide,
    val speciesName: String,
    val moveName: String,
    val moveDurationNanos: Long,
    val observedMedianIntervalNanos: Long,
    val cadenceSampleCount: Int,
    val basis: String
) : TestimonyPayload

/**
 * Energy generated by observed cadence intervals after a fast move is known.
 * This is derived generation, not a direct reading of current stored energy.
 */
data class FastMoveEnergyDerived(
    /** The Pokémon generating the energy. */
    val side: ActivePokemonSide,
    val moveName: String,
    val observedCompletedUses: Int,
    val energyPerUse: Int,
    val totalEnergyGenerated: Int,
    val basis: String
) : TestimonyPayload

/**
 * One named charged move whose cost was resolved from the combatant's
 * Pokédex move list. This is an accounted expenditure, not an estimate of
 * the Pokémon's current on-screen energy.
 */
data class ChargedMoveEnergySpent(
    /** The Pokémon that used the announced charged move. */
    val side: ActivePokemonSide,
    val speciesName: String,
    val moveName: String,
    /** The charged move's energy cost from Reference Knowledge. */
    val energyCost: Int,
    val basis: String
) : TestimonyPayload {
    init { require(energyCost > 0) }
}

/**
 * One acoustical measurement from a visually-cued fast-move window. It says
 * only what was heard; matching it to a move reference remains downstream.
 */
data class FastMoveSoundMeasured(
    val audible: Boolean,
    val onsetOffsetNanos: Long?,
    val soundDurationNanos: Long?,
    val spectralCentroidHz: Float?,
    val peakAmplitude: Float
) : TestimonyPayload {
    init {
        require(peakAmplitude in 0f..1f)
        require(!audible || (onsetOffsetNanos != null && soundDurationNanos != null && spectralCentroidHz != null))
        require(soundDurationNanos == null || soundDurationNanos > 0L)
        require(spectralCentroidHz == null || spectralCentroidHz >= 0f)
    }
}

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

/** A measured interval in which Droidball received no visual frame. */
data class VisualCaptureGapObserved(val durationNanos: Long) : TestimonyPayload
data object OutOfBattleMenuWitnessed : TestimonyPayload

/** Scheduling/latency evidence; elapsed time includes cue delivery delay, never frame counts. */
data class SpeciesCheckMeasured(
    val side: ActivePokemonSide,
    val windowId: Long,
    val status: String,
    val reason: String,
    val triggerMonotonicNanos: Long,
    val elapsedNanos: Long,
    val targetNanos: Long,
    val speciesName: String? = null
) : TestimonyPayload

/** Availability and signal are separate: a running playback stream may contain silence. */
data class AudioInputStatus(val captureSource: String, val state: String) : TestimonyPayload
