package com.example.overdex.model

data class Pokemon(
    val id: Int,
    val name: String,
    val types: List<PokemonType>,
    val region: String,
    val genus: String = "",

    val height: String = "",
    val weight: String = "",

    val fastMoves: List<Move>,
    val chargedMoves: List<Move>,
    val spriteUrl: String = "",
    val cryUrl: String = "",
    val description: String = "",
    val baseAttack: Int = 0,
    val baseDefense: Int = 0,
    val baseStamina: Int = 0,
) {
    val formattedId: String get() = "#${id.toString().padStart(3, '0')}"

    fun getWeaknesses(): Map<PokemonType, Double> {
        val multipliers = mutableMapOf<PokemonType, Double>()
        types.forEach { type ->
            type.getWeaknesses().forEach { weakness ->
                multipliers[weakness] = (multipliers[weakness] ?: 1.0) * 1.6
            }
            type.getResistances().forEach { resistance ->
                multipliers[resistance] = (multipliers[resistance] ?: 1.0) * 0.625
            }
        }
        // Immunity in Pokemon GO is 0.390625x (0.625 * 0.625)
        // Note: For simplicity, I'm using 1.6x and 0.625x as standard multipliers.
        // If a type is resistant in both, it becomes 0.39x.
        return multipliers.filter { it.value != 1.0 }
    }
}
