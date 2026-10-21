package com.example.overdex.battle.audio

/** Result music, QTE bubbles and attack effects must not request a species match. */
internal object BattleCryListeningCue {
    private val kinds = setOf(
        "VS_SCREEN", "SPECIES_ENTRY", "COUNTDOWN_3", "COUNTDOWN_2", "COUNTDOWN_1",
        "COUNTDOWN_GO", "COUNTDOWN_VISUAL_PRESENCE", "POKEMON_FAINTED", "OPPONENT_DEPARTED_NON_FAINT",
    )

    fun accepts(kind: String): Boolean = kind in kinds
}
