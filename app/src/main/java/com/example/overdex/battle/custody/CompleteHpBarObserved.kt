package com.example.overdex.battle.custody

/** Complete geometry and literal color evidence. No damage amount or KO conclusion. */
data class CompleteHpBarObserved(
    val side: ActivePokemonSide,
    val barLeft: Int, val barTop: Int, val barRight: Int, val barBottom: Int,
    val sourceWidth: Int, val sourceHeight: Int,
    val interiorColumns: Int, val liveColumns: Int, val damageColumns: Int,
    val status: String, val candidateStartedAtNanos: Long?,
    val appearanceStartedAtNanos: Long?,
    val railBrightness: Float, val backgroundBrightness: Float?,
    val backgroundMaskedColumns: Int = 0,
    val unmaskedUncertainColumns: Int? = null,
) : TestimonyPayload
