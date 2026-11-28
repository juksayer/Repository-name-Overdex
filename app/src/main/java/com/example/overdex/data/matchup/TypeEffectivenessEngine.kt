package com.example.overdex.data.matchup

import com.example.overdex.model.Effectiveness
import com.example.overdex.model.PokemonType

object TypeEffectivenessEngine {
    
    fun getEffectiveness(attackType: PokemonType, defenderTypes: List<PokemonType>): Effectiveness {
        var multiplier = 1.0
        
        defenderTypes.forEach { defenderType ->
            multiplier *= getMultiplier(attackType, defenderType)
        }
        
        return when {
            multiplier >= 1.6 -> Effectiveness.SUPER_EFFECTIVE
            multiplier <= 0.4 -> Effectiveness.IMMUNE
            multiplier < 1.0 -> Effectiveness.NOT_VERY_EFFECTIVE
            else -> Effectiveness.NEUTRAL
        }
    }
    
    private fun getMultiplier(attackType: PokemonType, defenderType: PokemonType): Double {
        // GO treats a traditional immunity as two resistance steps. This matters
        // when the defender's other type is weak to the same attack.
        val doubleResistance = when (attackType) {
            PokemonType.NORMAL, PokemonType.FIGHTING -> defenderType == PokemonType.GHOST
            PokemonType.GHOST -> defenderType == PokemonType.NORMAL
            PokemonType.ELECTRIC -> defenderType == PokemonType.GROUND
            PokemonType.GROUND -> defenderType == PokemonType.FLYING
            PokemonType.PSYCHIC -> defenderType == PokemonType.DARK
            PokemonType.DRAGON -> defenderType == PokemonType.FAIRY
            PokemonType.POISON -> defenderType == PokemonType.STEEL
            else -> false
        }
        return when {
            doubleResistance -> 0.625 * 0.625
            defenderType.getWeaknesses().contains(attackType) -> 1.6
            defenderType.getResistances().contains(attackType) -> 0.625
            else -> 1.0
        }
    }
}
