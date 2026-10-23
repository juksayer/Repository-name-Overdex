package com.example.overdex.battle.custody

/** A sampled finger contact, not a tap count and never proof of an executed attack. */
data class PlayerChargeButtonTouchWitnessed(
    val slot: Int,
    val x: Float,
    val y: Float,
    val displayWidth: Int,
    val displayHeight: Int,
    val rawText: String,
) : TestimonyPayload

/** Raw text from one charged-move label; legality and identity are inferred downstream. */
data class PlayerChargeMoveLabelRead(
    val slot: Int, val text: String,
    /** OCR, or a fresh pixel comparison citing the original OCR crop and the current crop. */
    val basis: String = "OCR",
) : TestimonyPayload

/** The matchup hint under an equipped charged button, not evidence of any attack occurring. */
data class PlayerChargedMoveEffectivenessObserved(
    val slot: Int,
    val effectiveness: FastMoveEffectiveness,
    val recognizedText: String,
    val basis: String = "OCR",
) : TestimonyPayload

/** A comparison, never a replacement for the user's saved team or the original witnesses. */
data class PlayerBattleInformationChecked(
    val speciesName: String, val field: String, val configuredValue: String?,
    val observedValue: String, val status: String, val basis: String,
) : TestimonyPayload

/** Experimental fill HEIGHT, in captured crop pixels; null means unknown, never zero energy. */
data class PlayerChargeButtonFillHeightMeasured(
    val slot: Int,
    val status: String,
    val centerX: Int?,
    val centerY: Int?,
    val radius: Int?,
    val separatorY: Float?,
    val fillHeightFraction: Float?,
    val pixelTolerance: Float,
    val cropLeft: Int,
    val cropTop: Int,
    val cropWidth: Int,
    val cropHeight: Int,
    val sourceWidth: Int,
    val sourceHeight: Int
) : TestimonyPayload
