package com.example.overdex.model

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

@Serializable
enum class PokemonType(val colorHex: Long) {
    NORMAL(0xFFA8A77A),
    FIRE(0xFFEE8130),
    WATER(0xFF6390F0),
    ELECTRIC(0xFFF7D02C),
    GRASS(0xFF7AC74C),
    ICE(0xFF96D9D6),
    FIGHTING(0xFFC22E28),
    POISON(0xFFA33EA1),
    GROUND(0xFFE2BF65),
    FLYING(0xFFA98FF3),
    PSYCHIC(0xFFF95587),
    BUG(0xFFA6B91A),
    ROCK(0xFFB6A136),
    GHOST(0xFF735797),
    DRAGON(0xFF6F35FC),
    STEEL(0xFFB7B7CE),
    FAIRY(0xFFD685AD),
    DARK(0xFF705746);

    val color: Color get() = Color(colorHex)

    fun getWeaknesses(): List<PokemonType> {
        return when (this) {
            NORMAL -> listOf(FIGHTING)
            FIRE -> listOf(WATER, GROUND, ROCK)
            WATER -> listOf(ELECTRIC, GRASS)
            ELECTRIC -> listOf(GROUND)
            GRASS -> listOf(FIRE, ICE, POISON, FLYING, BUG)
            ICE -> listOf(FIRE, FIGHTING, ROCK, STEEL)
            FIGHTING -> listOf(FLYING, PSYCHIC, FAIRY)
            POISON -> listOf(GROUND, PSYCHIC)
            GROUND -> listOf(WATER, GRASS, ICE)
            FLYING -> listOf(ELECTRIC, ICE, ROCK)
            PSYCHIC -> listOf(BUG, GHOST, DARK)
            BUG -> listOf(FIRE, FLYING, ROCK)
            ROCK -> listOf(WATER, GRASS, FIGHTING, GROUND, STEEL)
            GHOST -> listOf(GHOST, DARK)
            DRAGON -> listOf(ICE, DRAGON, FAIRY)
            STEEL -> listOf(FIRE, FIGHTING, GROUND)
            FAIRY -> listOf(POISON, STEEL)
            DARK -> listOf(FIGHTING, BUG, FAIRY)
        }
    }

    fun getResistances(): List<PokemonType> {
        return when (this) {
            NORMAL -> listOf(GHOST)
            FIRE -> listOf(FIRE, GRASS, ICE, BUG, STEEL, FAIRY)
            WATER -> listOf(FIRE, WATER, ICE, STEEL)
            ELECTRIC -> listOf(ELECTRIC, FLYING, STEEL)
            GRASS -> listOf(WATER, ELECTRIC, GRASS, GROUND)
            ICE -> listOf(ICE)
            FIGHTING -> listOf(BUG, ROCK, DARK)
            POISON -> listOf(GRASS, FIGHTING, POISON, BUG, FAIRY)
            GROUND -> listOf(ELECTRIC, POISON, ROCK)
            FLYING -> listOf(GRASS, FIGHTING, BUG, GROUND)
            PSYCHIC -> listOf(FIGHTING, PSYCHIC)
            BUG -> listOf(GRASS, FIGHTING, GROUND)
            ROCK -> listOf(NORMAL, FIRE, POISON, FLYING)
            GHOST -> listOf(NORMAL, FIGHTING, POISON, BUG)
            DRAGON -> listOf(FIRE, WATER, ELECTRIC, GRASS)
            STEEL -> listOf(NORMAL, GRASS, ICE, FLYING, PSYCHIC, BUG, ROCK, DRAGON, STEEL, FAIRY, POISON)
            FAIRY -> listOf(FIGHTING, BUG, DRAGON, DARK)
            DARK -> listOf(GHOST, DARK, PSYCHIC)
        }
    }
}
