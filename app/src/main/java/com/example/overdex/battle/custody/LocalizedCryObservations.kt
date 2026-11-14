package com.example.overdex.battle.custody

/** A complete reference waveform localized in preserved PCM; article time is its onset. */
data class BattleCryLocalized(
    val speciesId: Int,
    val referenceSha256: String,
    val correlation: Float,
    val durationNanos: Long,
    val analysisVersion: String = "cry-waveform-v1",
) : TestimonyPayload

/** Scheduling/supporting evidence only. Never a faint, lost ball, move use or KO credit. */
data class RepeatCryDepartureCandidateObserved(
    val speciesId: Int,
    val side: ActivePokemonSide?,
    val possibleAppearanceIds: List<String>,
    val cryOccurrenceIds: List<String>,
) : TestimonyPayload
