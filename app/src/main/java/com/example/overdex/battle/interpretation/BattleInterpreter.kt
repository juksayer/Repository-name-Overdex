package com.example.overdex.battle.interpretation

import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.model.BattleActor
import com.example.overdex.model.BattleEvent
import com.example.overdex.model.BattleEventType
import com.example.overdex.model.Confidence
import com.example.overdex.model.ConfidenceLevel

/**
 * The "Stenographer": Responsible for transcribing neutral [RealityArticle]
 * records into semantic [BattleEvent]s.
 *
 * The Interpreter uses provenance (SourceId) and payload type to recognize
 * specific phenomena for transcription.
 */
class BattleInterpreter(
    private val pokemonKnowledge: PokemonKnowledge
) {

    /**
     * Interprets testimony recorded in the Reality Timeline into
     * a battle-specific event representation.
     *
     * The Interpreter does not modify or take custody of the external
     * Article that produced the observed phenomenon. It reasons about
     * information collected by Overdex during the observation.
     *
     * @param article The canonical record from the Reality Timeline.
     * @return A [BattleEvent] if the article contains recognizable battle testimony;
     *         otherwise, null.
     */
    suspend fun interpret(article: RealityArticle): BattleEvent? {
        val payload = article.payload as? RawTestimony ?: return null
        val sourceId = article.sourceId.id

        return when {

            sourceId == "SPECIES_WITNESS" && payload.data is String -> {
                val pokemon = pokemonKnowledge.getPokemonByName(payload.data)

                BattleEvent(
                    timestamp = article.perceivedAt,
                    type = BattleEventType.POKEMON_IDENTIFIED,
                    actor = BattleActor.ENEMY,
                    message = pokemon?.name ?: payload.data,
                    pokemonId = pokemon?.id,
                    confidence = Confidence(ConfidenceLevel.OBSERVED)
                )
            }

            else -> null
        }
    }
}