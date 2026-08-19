package com.example.overdex.battle.interpretation

import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.reality.RealityArticle
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
object BattleInterpreter {

    /**
     * Interprets a reality article into a battle-specific event.
     * 
     * @param article The canonical record from the Reality Timeline.
     * @return A [BattleEvent] if the article contains recognizable battle testimony; 
     *         otherwise, null.
     */
    fun interpret(article: RealityArticle): BattleEvent? {
        val payload = article.payload as? RawTestimony ?: return null
        val sourceId = article.sourceId.id

        return when {
            // Narrow recognition of SpeciesWitness testimony
            sourceId == "SPECIES_WITNESS" && payload.data is String -> {
                BattleEvent(
                    timestamp = article.perceivedAt,
                    type = BattleEventType.POKEMON_IDENTIFIED,
                    actor = BattleActor.ENEMY,
                    message = payload.data,
                    confidence = Confidence(ConfidenceLevel.OBSERVED)
                )
            }
            else -> null
        }
    }
}
