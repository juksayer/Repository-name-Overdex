package com.example.overdex.battle.archive

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A sealed, serializable archive payload hierarchy.
 */
@Serializable
sealed interface ArchivedTestimonyPayload

@Serializable
@SerialName("raw_text")
data class ArchivedRawText(
    val value: String
) : ArchivedTestimonyPayload

@Serializable
@SerialName("raw_int")
data class ArchivedRawInt(
    val value: Int
) : ArchivedTestimonyPayload

@Serializable
@SerialName("match_record_started")
data object ArchivedMatchRecordStarted : ArchivedTestimonyPayload

@Serializable
@SerialName("battle_overlay_opened")
data class ArchivedBattleOverlayOpened(val reason: String) : ArchivedTestimonyPayload

@Serializable
@SerialName("vs_screen_witnessed")
data object ArchivedVsScreenWitnessed : ArchivedTestimonyPayload

@Serializable
@SerialName("attack_incoming")
data object ArchivedAttackIncoming : ArchivedTestimonyPayload

@Serializable
@SerialName("pokemon_identified")
data class ArchivedPokemonIdentified(
    val species: String
) : ArchivedTestimonyPayload

@Serializable
@SerialName("active_pokemon_species_witnessed")
data class ArchivedActivePokemonSpeciesWitnessed(
    val side: String,
    val speciesName: String,
    val speciesId: Int? = null
) : ArchivedTestimonyPayload

@Serializable @SerialName("team_select_party") data object ArchivedTeamSelectPartyWitnessed : ArchivedTestimonyPayload
@Serializable @SerialName("player_team_roster_slot") data class ArchivedPlayerTeamRosterSlotWitnessed(val slot: Int, val speciesName: String, val speciesId: Int? = null) : ArchivedTestimonyPayload

@Serializable
@SerialName("supporting_match_start")
data class ArchivedSupportingMatchStart(
    val frameIndex: Int,
    val upperColorfulPixelFraction: Float,
    val lowerColorfulPixelFraction: Float,
    val basis: String
) : ArchivedTestimonyPayload

@Serializable
@SerialName("countdown_glyph_witnessed")
data class ArchivedCountdownGlyphWitnessed(
    val glyph: String,
    val similarity: Float,
    val frameIndex: Int,
    val cropName: String? = null,
    val sourceWidth: Int? = null,
    val sourceHeight: Int? = null,
    val cropLeft: Int? = null,
    val cropTop: Int? = null,
    val cropRight: Int? = null,
    val cropBottom: Int? = null,
    val basis: String
) : ArchivedTestimonyPayload

@Serializable
@SerialName("crop_captured")
data class ArchivedCropCaptured(
    val artifactPath: String,
    val sha256: String,
    val byteCount: Long,
    val mediaType: String,
    val cropName: String,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val cropLeft: Int,
    val cropTop: Int,
    val cropRight: Int,
    val cropBottom: Int
) : ArchivedTestimonyPayload

@Serializable
@SerialName("audio_captured")
data class ArchivedAudioCaptured(
    val artifactPath: String,
    val sha256: String,
    val byteCount: Long,
    val mediaType: String,
    val sampleRateHz: Int,
    val channelCount: Int,
    val durationNanos: Long,
    val cueKind: String,
    val captureSource: String = "MICROPHONE",
    val peakAmplitude: Float? = null
) : ArchivedTestimonyPayload

@Serializable
@SerialName("witness_operating")
data class ArchivedWitnessOperating(
    val operating: Boolean
) : ArchivedTestimonyPayload

@Serializable
@SerialName("active_pokemon_types_witnessed")
data class ArchivedActivePokemonTypesWitnessed(
    val side: String,
    val types: List<String>,
    val similarity: Float,
    val basis: String
) : ArchivedTestimonyPayload

@Serializable @SerialName("get_ready_witnessed") data object ArchivedGetReadyWitnessed : ArchivedTestimonyPayload
@Serializable @SerialName("charge_move_used_announced") data object ArchivedChargeMoveUsedAnnounced : ArchivedTestimonyPayload

@Serializable
@SerialName("player_inactive_hp_bar_measured")
data class ArchivedPlayerInactiveHpBarMeasured(
    val slot: Int,
    val filledFraction: Float
) : ArchivedTestimonyPayload

@Serializable
@SerialName("active_hp_bar_measured")
data class ArchivedActiveHpBarMeasured(
    val side: String,
    val barLeft: Int,
    val barTop: Int,
    val barRight: Int,
    val barBottom: Int,
    val filledFraction: Float
) : ArchivedTestimonyPayload

@Serializable
@SerialName("active_hp_bar_motion_cadence_measured")
data class ArchivedActiveHpBarMotionCadenceMeasured(
    val movingSide: String,
    val intervalNanos: Long,
    val verticalExcursionPixels: Float,
    val sampleCount: Int
) : ArchivedTestimonyPayload

@Serializable
@SerialName("active_hp_bar_border_cadence_measured")
data class ArchivedActiveHpBarBorderCadenceMeasured(
    val damagedBarSide: String,
    val intervalNanos: Long,
    val peakColorDistance: Float,
    val sampleCount: Int
) : ArchivedTestimonyPayload

@Serializable
@SerialName("active_hp_bar_border_pulse_observed")
data class ArchivedActiveHpBarBorderPulseObserved(
    val damagedBarSide: String,
    val peakColorDistance: Float,
    val sampleCount: Int
) : ArchivedTestimonyPayload

@Serializable
@SerialName("fast_move_identified")
data class ArchivedFastMoveIdentified(
    val side: String,
    val speciesName: String,
    val moveName: String,
    val moveDurationNanos: Long,
    val observedMedianIntervalNanos: Long,
    val cadenceSampleCount: Int,
    val basis: String
) : ArchivedTestimonyPayload

@Serializable
@SerialName("fast_move_energy_derived")
data class ArchivedFastMoveEnergyDerived(
    val side: String,
    val moveName: String,
    val observedCompletedUses: Int,
    val energyPerUse: Int,
    val totalEnergyGenerated: Int,
    val basis: String
) : ArchivedTestimonyPayload

@Serializable
@SerialName("charged_move_energy_spent")
data class ArchivedChargedMoveEnergySpent(
    val side: String,
    val speciesName: String,
    val moveName: String,
    val energyCost: Int,
    val basis: String
) : ArchivedTestimonyPayload

@Serializable
@SerialName("fast_move_sound_measured")
data class ArchivedFastMoveSoundMeasured(
    val audible: Boolean,
    val onsetOffsetNanos: Long? = null,
    val soundDurationNanos: Long? = null,
    val spectralCentroidHz: Float? = null,
    val peakAmplitude: Float
) : ArchivedTestimonyPayload

@Serializable
@SerialName("player_inactive_species_sprite_fingerprint_measured")
data class ArchivedPlayerInactiveSpeciesSpriteFingerprintMeasured(
    val slot: Int,
    val fingerprint: String,
    val sampleLeft: Int,
    val sampleTop: Int,
    val sampleRight: Int,
    val sampleBottom: Int
) : ArchivedTestimonyPayload

@Serializable
@SerialName("match_started")
data object ArchivedMatchStarted : ArchivedTestimonyPayload

@Serializable
@SerialName("match_ended")
data class ArchivedMatchEnded(val result: String) : ArchivedTestimonyPayload

@Serializable @SerialName("battle_cry_candidates_measured")
data class ArchivedBattleCryCandidatesMeasured(
    val cueKind: String,
    val candidates: List<ArchivedBattleCryCandidateMeasurement>
) : ArchivedTestimonyPayload
@Serializable data class ArchivedBattleCryCandidateMeasurement(val speciesId: Int, val referenceSha256: String, val similarity: Float)

@Serializable @SerialName("visual_capture_gap") data class ArchivedVisualCaptureGapObserved(val durationNanos: Long) : ArchivedTestimonyPayload
@Serializable @SerialName("out_of_battle_menu") data object ArchivedOutOfBattleMenuWitnessed : ArchivedTestimonyPayload

@Serializable
@SerialName("species_check_measured")
data class ArchivedSpeciesCheckMeasured(
    val side: String, val windowId: Long, val status: String, val reason: String,
    val triggerMonotonicNanos: Long, val elapsedNanos: Long, val targetNanos: Long,
    val speciesName: String? = null
) : ArchivedTestimonyPayload

@Serializable
@SerialName("audio_input_status")
data class ArchivedAudioInputStatus(val captureSource: String, val state: String) : ArchivedTestimonyPayload
