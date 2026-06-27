package com.example.overdex

import androidx.compose.runtime.mutableStateListOf
import com.example.overdex.model.BattleEvent
import com.example.overdex.model.BattleEventType
import com.example.overdex.model.Confidence
import com.example.overdex.model.ConfidenceLevel
import kotlinx.coroutines.delay

data class BattleMemory(
    val seenPokemon: MutableSet<String> = mutableSetOf(),
    val seenFastMoves: MutableMap<String, MutableSet<String>> = mutableMapOf(),
    val seenChargedMoves: MutableMap<String, MutableSet<String>> = mutableMapOf(),
    val observedEnemyPokemon: MutableList<String> = mutableListOf(),
    val enemyTeam: MutableList<EnemyPokemonMemory> = mutableStateListOf<EnemyPokemonMemory>(),
    var enemyRemainingPokemon: Int? = null,
    val battleHistory: MutableList<BattleEvent> = mutableStateListOf<BattleEvent>()
) {
    fun populatePrototypeEnemyTeam() {
        enemyTeam.clear()
        enemyTeam.add(EnemyPokemonMemory(species = "Swampert", estimatedEnergy = 0, alive = true, isActive = true))
        enemyTeam.add(EnemyPokemonMemory(species = "Talonflame", estimatedEnergy = 0, alive = true, isActive = false))
        enemyTeam.add(EnemyPokemonMemory(species = "Azumarill", estimatedEnergy = 0, alive = true, isActive = false))
        
        recordEvent(BattleEventType.POKEMON_OBSERVED, "Swampert")
        recordEvent(BattleEventType.POKEMON_OBSERVED, "Talonflame")
        recordEvent(BattleEventType.POKEMON_OBSERVED, "Azumarill")
    }

    private fun recordEvent(
        type: BattleEventType, 
        species: String? = null, 
        value: Int? = null, 
        message: String? = null,
        confidence: Confidence = Confidence(ConfidenceLevel.OBSERVED)
    ) {
        val event = BattleEvent(
            type = type, 
            species = species, 
            value = value, 
            message = message,
            confidence = confidence
        )
        battleHistory.add(event)
        android.util.Log.d("BATTLE_TIMELINE", "Event: ${event.type} | Species: ${event.species} | Value: ${event.value} | Confidence: ${event.confidence.level}")
    }

    /**
     * PROTOTYPE ONLY: Simulates a battle sequence to prove BattleMemory drives the UI.
     */
    suspend fun runPrototypeSimulation() {
        recordEvent(BattleEventType.BATTLE_STARTED)
        populatePrototypeEnemyTeam()
        recordEvent(BattleEventType.POKEMON_ACTIVE, "Swampert")
        
        while (true) {
            // State 1: Swampert gaining energy
            delay(2000)
            updateSpecies("Swampert") { it.estimatedEnergy = 45 }
            recordEvent(
                type = BattleEventType.ENERGY_UPDATED, 
                species = "Swampert", 
                value = 45,
                confidence = Confidence(ConfidenceLevel.INFERRED)
            )
            
            // State 2: Switch to Talonflame
            delay(2000)
            updateSpecies("Swampert") { it.isActive = false }
            updateSpecies("Talonflame") { it.isActive = true }
            recordEvent(BattleEventType.POKEMON_SWITCHED, "Talonflame", message = "Swampert switched out")
            recordEvent(BattleEventType.POKEMON_ACTIVE, "Talonflame")
            
            // State 3: Talonflame gaining energy
            delay(2000)
            updateSpecies("Talonflame") { it.estimatedEnergy = 60 }
            recordEvent(
                type = BattleEventType.ENERGY_UPDATED, 
                species = "Talonflame", 
                value = 60,
                confidence = Confidence(ConfidenceLevel.INFERRED)
            )
            
            // State 4: Azumarill enters and faints
            delay(2000)
            updateSpecies("Talonflame") { it.isActive = false }
            updateSpecies("Azumarill") { 
                it.isActive = true 
                it.estimatedEnergy = 80
            }
            recordEvent(BattleEventType.POKEMON_SWITCHED, "Azumarill")
            recordEvent(BattleEventType.POKEMON_ACTIVE, "Azumarill")
            recordEvent(
                type = BattleEventType.ENERGY_UPDATED, 
                species = "Azumarill", 
                value = 80,
                confidence = Confidence(ConfidenceLevel.INFERRED)
            )

            delay(2000)
            updateSpecies("Azumarill") { 
                it.alive = false 
                it.isActive = false
                it.estimatedEnergy = 0
            }
            recordEvent(BattleEventType.POKEMON_FAINTED, "Azumarill")
            
            // State 5: Swampert returns
            delay(2000)
            updateSpecies("Swampert") { it.isActive = true }
            recordEvent(BattleEventType.POKEMON_ACTIVE, "Swampert")
            
            // End Battle Sequence
            delay(2000)
            recordEvent(BattleEventType.BATTLE_ENDED)

            // Reset for loop
            delay(4000)
            battleHistory.clear()
            recordEvent(BattleEventType.BATTLE_STARTED)
            populatePrototypeEnemyTeam()
            recordEvent(BattleEventType.POKEMON_ACTIVE, "Swampert")
        }
    }

    private fun updateSpecies(species: String, block: (EnemyPokemonMemory) -> Unit) {
        val index = enemyTeam.indexOfFirst { it.species == species }
        if (index != -1) {
            val updated = enemyTeam[index].copy()
            block(updated)
            enemyTeam[index] = updated
        }
    }
}
