package com.example.overdex.battle.interpretation

import com.example.overdex.battle.custody.AttackIncoming
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.model.BattleActor
import com.example.overdex.model.BattleEvent
import com.example.overdex.model.BattleEventType
import com.example.overdex.model.BattleResult
import com.example.overdex.model.Confidence
import com.example.overdex.model.ConfidenceLevel
import java.util.UUID

/**
 * The "Stenographer": Responsible for transcribing neutral [RealityArticle]
 * records into semantic [BattleEvent]s and interpreting raw testimony into derived articles.
 *
 * The Interpreter uses provenance (SourceId) and payload type to recognize
 * specific phenomena for transcription.
 */
class BattleInterpreter(
    private val pokemonKnowledge: PokemonKnowledge
) {

    /**
     * Interprets a raw RealityArticle from ATTACK_INCOMING_WITNESS and produces a derived RealityArticle
     * with payload [AttackIncoming] if the text matches the attack warning pattern.
     * 
     * Preserves ATTACK_INCOMING_WITNESS provenance on the source article, identifies BATTLE_INTERPRETER
     * as the source of the derived article, assigns a new article ID, and links source ancestry via predecessorIds.
     */
    fun interpretAttackIncoming(article: RealityArticle): RealityArticle? {
        if (article.sourceId.id != "ATTACK_INCOMING_WITNESS") return null
        val payload = article.payload as? RawTestimony ?: return null
        val rawText = (payload.data as? String) ?: return null
        
        val normalized = rawText.uppercase().trim().replace(" ", "")
        return if (normalized.contains("ATTACKINCOMING")) {
            RealityArticle(
                id = ArticleId(UUID.randomUUID().toString()),
                perceivedAt = article.perceivedAt,
                recordedAt = System.currentTimeMillis(),
                sourceId = SourceId("BATTLE_INTERPRETER"),
                payload = AttackIncoming,
                predecessorIds = listOf(article.id),
                matchId = article.matchId,
                confidence = null
            )
        } else {
            null
        }
    }

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
                val pokemon = pokemonKnowledge.getPokemonByName(payload.data) ?: return null

                BattleEvent(
                    timestamp = article.perceivedAt,
                    type = BattleEventType.POKEMON_IDENTIFIED,
                    actor = BattleActor.ENEMY,
                    message = pokemon.name,
                    pokemonId = pokemon.id,
                    confidence = Confidence(ConfidenceLevel.OBSERVED),
                    sourceArticleId = article.id
                )
            }

            sourceId == "PLAYER_SPECIES_WITNESS" && payload.data is String -> {
                val pokemon = pokemonKnowledge.getPokemonByName(payload.data) ?: return@interpret null

                BattleEvent(
                    timestamp = article.perceivedAt,
                    type = BattleEventType.POKEMON_IDENTIFIED,
                    actor = BattleActor.PLAYER,
                    message = pokemon.name,
                    pokemonId = pokemon.id,
                    confidence = Confidence(ConfidenceLevel.OBSERVED),
                    sourceArticleId = article.id
                )
            }

            sourceId == "YOU_WIN_WITNESS" -> {
                val payloadText = (payload.data as? String)?.trim()?.uppercase() ?: ""
                if (payloadText in setOf("YOU WIN!", "YOU WIN", "YOU WVIN!", "YOU WVIN")) {
                    BattleEvent(
                        timestamp = article.perceivedAt,
                        type = BattleEventType.BATTLE_ENDED,
                        result = BattleResult.WIN,
                        sourceArticleId = article.id
                    )
                } else {
                    null
                }
            }

            sourceId == "GOOD_EFFORT_WITNESS" -> {
                val payloadText = (payload.data as? String)?.trim()?.uppercase() ?: ""
                if (payloadText.contains("GOOD EFFORT")) {
                    BattleEvent(
                        timestamp = article.perceivedAt,
                        type = BattleEventType.BATTLE_ENDED,
                        result = BattleResult.LOSS,
                        sourceArticleId = article.id
                    )
                } else {
                    null
                }
            }

            else -> null
        }
    }
}
