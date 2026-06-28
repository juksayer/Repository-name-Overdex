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
    var enemyShieldsUsed: Int = 0,
    val timeline: BattleTimeline = BattleTimeline()
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

        // Factual ID-based records
        timeline.record(BattleEvent(type = BattleEventType.POKEMON_IDENTIFIED, actor = BattleActor.ENEMY, pokemonId = 260))
        timeline.record(BattleEvent(type = BattleEventType.POKEMON_IDENTIFIED, actor = BattleActor.ENEMY, pokemonId = 663))
        timeline.record(BattleEvent(type = BattleEventType.POKEMON_IDENTIFIED, actor = BattleActor.ENEMY, pokemonId = 184))
    }

    private fun recordEvent(
        type: BattleEventType,
        actor: BattleActor = BattleActor.SYSTEM,
        pokemonId: Int? = null,
        value: Int? = null,
        message: String? = null,
        confidence: Confidence = Confidence(ConfidenceLevel.OBSERVED)
    ) {
        val event = BattleEvent(
            type = type,
            actor = actor,
            pokemonId = pokemonId,
            value = value, 
            message = message,
            confidence = confidence
        )
        timeline.record(event)
        battleHistory.add(event) // Keeping for BattleLog until it's refactored
    }

    /**
     * PROTOTYPE ONLY: Simulates a battle sequence to prove BattleMemory drives the UI.
     */
    suspend fun runPrototypeSimulation() {
        startTime = System.currentTimeMillis()
        timeline.clear()
        recordEvent(type = BattleEventType.BATTLE_STARTED, actor = BattleActor.SYSTEM)
        populatePrototypeEnemyTeam()
        
        playerLead = "Charizard"
        enemyLead = "Swampert"
        recordEvent(type = BattleEventType.POKEMON_SWITCHED, pokemonId = 260, actor = BattleActor.ENEMY)
        
        while (true) {
            // State 1: Swampert gaining energy
            delay(2000)
            updateSpecies("Swampert") { it.estimatedEnergy = 45 }
            recordEvent(
                type = BattleEventType.ENERGY_UPDATED, 
                pokemonId = 260, 
                actor = BattleActor.ENEMY,
                value = 45,
                confidence = Confidence(ConfidenceLevel.INFERRED)
            )
            
            // State 2: Switch to Talonflame
            delay(2000)
            updateSpecies("Swampert") { it.isActive = false }
            updateSpecies("Talonflame") { it.isActive = true }
            recordEvent(
                type = BattleEventType.POKEMON_SWITCHED, 
                pokemonId = 663, 
                actor = BattleActor.ENEMY,
                message = "Swampert switched out"
            )
            
            // State 3: Talonflame gaining energy and throwing a move
            delay(2000)
            updateSpecies("Talonflame") { it.estimatedEnergy = 60 }
            recordEvent(
                type = BattleEventType.CHARGED_MOVE_THROWN, 
                pokemonId = 663, 
                actor = BattleActor.ENEMY,
                message = "Brave Bird"
            )
            
            delay(1000)
            playerShieldsUsed++
            recordEvent(
                type = BattleEventType.SHIELD_USED,
                pokemonId = 6, // Charizard
                actor = BattleActor.PLAYER,
                message = "Player used shield"
            )

            // State 4: Azumarill enters and faints
            delay(2000)
            updateSpecies("Talonflame") { it.isActive = false }
            updateSpecies("Azumarill") { 
                it.isActive = true 
                it.estimatedEnergy = 80
            }
            recordEvent(type = BattleEventType.POKEMON_SWITCHED, pokemonId = 184, actor = BattleActor.ENEMY)

            delay(2000)
            updateSpecies("Azumarill") { 
                it.alive = false 
                it.isActive = false
                it.estimatedEnergy = 0
            }
            recordEvent(type = BattleEventType.POKEMON_FAINTED, pokemonId = 184, actor = BattleActor.ENEMY)
            
            // State 5: Swampert returns
            delay(2000)
            updateSpecies("Swampert") { it.isActive = true }
            recordEvent(type = BattleEventType.POKEMON_SWITCHED, pokemonId = 260, actor = BattleActor.ENEMY)
            
            // End Battle Sequence
            delay(2000)
            recordEvent(type = BattleEventType.BATTLE_ENDED, actor = BattleActor.SYSTEM)
            
            // Generate BattleLog
            val battleLog = toBattleLog()
            android.util.Log.d("BATTLE_LOG", "BattleLog Generated: ID=${battleLog.id} | Events=${battleLog.timeline.size} | Result=${battleLog.result}")

            // Reset for loop
            delay(4000)
            battleHistory.clear()
            timeline.clear()
            playerShieldsUsed = 0
            enemyShieldsUsed = 0
            startTime = System.currentTimeMillis()
            recordEvent(type = BattleEventType.BATTLE_STARTED, actor = BattleActor.SYSTEM)
            populatePrototypeEnemyTeam()
            recordEvent(type = BattleEventType.POKEMON_SWITCHED, pokemonId = 260, actor = BattleActor.ENEMY)
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
            timeline = timeline.events.toList(), // Now using the new timeline
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
