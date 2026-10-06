package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.FastMoveEffectiveness
import com.example.overdex.battle.custody.FastMoveEffectivenessWitnessed
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
            normalized == "EREFFECTIVE" -> EffectivenessTextReading(
                FastMoveEffectiveness.SUPER_EFFECTIVE,
                0.72f
            )
            else -> null
        }
    }
}

/**
 * Reads only the effectiveness meaning from existing preserved text testimony.
 * The broad text source cannot locate an HP-bar side, so direction remains null
 * and is resolved downstream from the two active species when it is unique.
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
                    if (article.sourceId.id !in TEXT_SOURCES) return@collect
                    val text = (article.payload as? RawTestimony)?.data as? String ?: return@collect
                    val reading = FastMoveEffectivenessTextResolver.resolve(text) ?: return@collect
                    match.custody.submitTestimony(
                        sourceId = sourceId,
                        payload = FastMoveEffectivenessWitnessed(
                            effectiveness = reading.effectiveness,
                            damagedSide = null,
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

    private companion object {
        val TEXT_SOURCES = setOf("ANNOUNCEMENT_WITNESS", "MATCH_OUTCOME_TEXT_WITNESS")
    }
}
