package com.example.overdex

import androidx.compose.runtime.mutableStateListOf
import com.example.overdex.data.observation.BattleObservationPipeline
import com.example.overdex.model.*
import com.example.overdex.model.observation.ObservationSource
import com.example.overdex.model.observation.PokemonNameObservation
import kotlinx.coroutines.delay

data class BattleMemory(
    val seenPokemon: MutableSet<String> = mutableSetOf(),
    val seenFastMoves: MutableMap<String, MutableSet<String>> = mutableMapOf(),
    val seenChargedMoves: MutableMap<String, MutableSet<String>> = mutableMapOf(),
    val observedEnemyPokemon: MutableList<String> = mutableListOf(),
    val enemyTeam: MutableList<EnemyPokemonMemory> = mutableStateListOf<EnemyPokemonMemory>(),
    var enemyRemainingPokemon: Int? = null,
    var playerActivePokemon: String? = "Charizard", // Prototype default
    val playerTeam: MutableList<String> = mutableStateListOf("Charizard", "Venusaur", "Blastoise"),
    val battleHistory: MutableList<BattleEvent> = mutableStateListOf<BattleEvent>(),
    var startTime: Long = System.currentTimeMillis(),
    var playerLead: String? = null,
    var enemyLead: String? = null,
    var playerShieldsUsed: Int = 0,
    var enemyShieldsUsed: Int = 0
) {
    private val observationPipeline = BattleObservationPipeline(this)

    fun populatePrototypeEnemyTeam() {
        enemyTeam.clear()
        // Injecting via pipeline instead of direct add
        observationPipeline.onObservationReceived(
            PokemonNameObservation(species = "Swampert", source = ObservationSource.PROTOTYPE, confidence = Confidence(ConfidenceLevel.OBSERVED))
        )
        observationPipeline.onObservationReceived(
            PokemonNameObservation(species = "Talonflame", source = ObservationSource.PROTOTYPE, confidence = Confidence(ConfidenceLevel.OBSERVED))
        )
        observationPipeline.onObservationReceived(
            PokemonNameObservation(species = "Azumarill", source = ObservationSource.PROTOTYPE, confidence = Confidence(ConfidenceLevel.OBSERVED))
        )
        
        updateSpecies("Swampert") { it.isActive = true }

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
        startTime = System.currentTimeMillis()
        recordEvent(BattleEventType.BATTLE_STARTED)
        populatePrototypeEnemyTeam()
        
        playerLead = "Charizard"
        enemyLead = "Swampert"
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
            
            // State 3: Talonflame gaining energy and throwing a move
            delay(2000)
            updateSpecies("Talonflame") { it.estimatedEnergy = 60 }
            recordEvent(BattleEventType.CHARGED_MOVE_THROWN, "Talonflame", message = "Brave Bird")
            
            delay(1000)
            playerShieldsUsed++
            recordEvent(BattleEventType.SHIELD_USED, playerActivePokemon, message = "Player used shield")

            // State 4: Azumarill enters and faints
            delay(2000)
            updateSpecies("Talonflame") { it.isActive = false }
            updateSpecies("Azumarill") { 
                it.isActive = true 
                it.estimatedEnergy = 80
            }
            recordEvent(BattleEventType.POKEMON_SWITCHED, "Azumarill")
            recordEvent(BattleEventType.POKEMON_ACTIVE, "Azumarill")

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
            
            // Generate BattleLog
            val battleLog = toBattleLog()
            android.util.Log.d("BATTLE_LOG", "BattleLog Generated: ID=${battleLog.id} | Duration=${battleLog.durationMs}ms | Result=${battleLog.result} | Shields(P/E)=${battleLog.playerShieldsUsed}/${battleLog.enemyShieldsUsed}")

            // Reset for loop
            delay(4000)
            battleHistory.clear()
            playerShieldsUsed = 0
            enemyShieldsUsed = 0
            startTime = System.currentTimeMillis()
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

    /**
     * Converts current battle memory into a finalized BattleLog snapshot.
     */
    fun toBattleLog(): BattleLog {
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        
        return BattleLog(
            startTime = startTime,
            endTime = endTime,
            durationMs = duration,
            playerTeam = playerTeam.toList(),
            enemyTeam = enemyTeam.toList(),
            timeline = battleHistory.toList(),
            playerLead = playerLead,
            enemyLead = enemyLead,
            playerShieldsUsed = playerShieldsUsed,
            enemyShieldsUsed = enemyShieldsUsed,
            result = if (enemyTeam.all { !it.alive }) BattleResult.WIN else BattleResult.UNKNOWN,
            overallConfidence = 0.9f,
            sourcesUsed = setOf(ObservationSource.PROTOTYPE),
            seenFastMoves = seenFastMoves.toMap(),
            seenChargedMoves = seenChargedMoves.toMap()
        )
    }
}
