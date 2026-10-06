package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.FastMoveEffectiveness
import com.example.overdex.battle.custody.FastMoveEffectivenessWitnessed
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.battle.timeline.observer.ObservationSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal data class EffectivenessTextReading(
    val effectiveness: FastMoveEffectiveness,
    val confidence: Float
)

/** Converts one OCR phrase into one typed effectiveness signal. */
internal object FastMoveEffectivenessTextResolver {
    fun resolve(rawText: String): EffectivenessTextReading? {
        val normalized = rawText.uppercase().filter(Char::isLetter)
        return when {
            "NOTVERYEFFECTIVE" in normalized -> EffectivenessTextReading(
                FastMoveEffectiveness.NOT_VERY_EFFECTIVE,
                0.98f
            )
            "SUPEREFFECTIVE" in normalized -> EffectivenessTextReading(
                FastMoveEffectiveness.SUPER_EFFECTIVE,
                0.98f
            )
            // ML Kit sometimes loses the first three letters while the phrase
            // fades. This fragment cannot be produced by "NOT VERY EFFECTIVE".
            normalized.endsWith("EREFFECTIVE") && "VERY" !in normalized -> EffectivenessTextReading(
                FastMoveEffectiveness.SUPER_EFFECTIVE,
                0.72f
            )
            else -> null
        }
    }
}

/**
 * Reads only the effectiveness meaning from existing text testimony. Dedicated
 * HP sources carry a damaged side; broad announcement text leaves direction
 * unknown for downstream resolution from the active species and aligned hit.
 */
class PersistedFastMoveEffectivenessWitness(
    override val observerId: ObserverId = ObserverId(
        "FAST_MOVE_EFFECTIVENESS_TEXT_WITNESS",
        ObservationSource.SCREEN_CAPTURE
    ),
    override val name: String = "Fast Move Effectiveness Text Witness"
) : Observer {
    private var scope: CoroutineScope? = null

    override fun start(match: Match) {
        if (scope != null) return
        val sourceId = SourceId(observerId.id)
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                match.articles.collect { article ->
                    val damagedSide = FastMoveEffectivenessSources.damagedSide(article.sourceId.id)
                    if (article.sourceId.id !in FastMoveEffectivenessSources.acceptedTextSources) return@collect
                    val text = (article.payload as? RawTestimony)?.data as? String ?: return@collect
                    val reading = FastMoveEffectivenessTextResolver.resolve(text) ?: return@collect
                    match.custody.submitTestimony(
                        sourceId = sourceId,
                        payload = FastMoveEffectivenessWitnessed(
                            effectiveness = reading.effectiveness,
                            damagedSide = damagedSide,
                            recognizedText = text
                        ),
                        timestamp = article.perceivedAt,
                        confidence = reading.confidence,
                        evidenceReferences = listOf(article.id.value),
                        monotonicTimeNanos = article.monotonicTimeNanos ?: return@collect
                    )
                }
            }
        }
    }

    override fun stop() {
        scope?.cancel("Witness stopped")
        scope = null
    }

}

/** Source identity supplies direction without asking OCR to infer screen geometry. */
internal object FastMoveEffectivenessSources {
    const val PLAYER_TEXT = "PLAYER_HP_EFFECTIVENESS_TEXT_WITNESS"
    const val OPPONENT_TEXT = "OPPONENT_HP_EFFECTIVENESS_TEXT_WITNESS"
    const val PLAYER_CAPTURE = "PLAYER_HP_EFFECTIVENESS_CAPTURE"
    const val OPPONENT_CAPTURE = "OPPONENT_HP_EFFECTIVENESS_CAPTURE"

    val acceptedTextSources = setOf(
        "ANNOUNCEMENT_WITNESS",
        "MATCH_OUTCOME_TEXT_WITNESS",
        PLAYER_TEXT,
        OPPONENT_TEXT,
    )

    fun damagedSide(sourceId: String): ActivePokemonSide? = when (sourceId) {
        PLAYER_TEXT -> ActivePokemonSide.PLAYER
        OPPONENT_TEXT -> ActivePokemonSide.OPPONENT
        else -> null
    }
}
