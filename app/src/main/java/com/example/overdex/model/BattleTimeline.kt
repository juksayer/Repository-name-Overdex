package com.example.overdex.model

import androidx.compose.runtime.mutableStateListOf
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.battle.custody.ActiveHpBarMeasured
import com.example.overdex.battle.custody.AudioInputStatus
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.OpponentBattleResourceCountMeasured

/**
 * A chronological record of battle events used for real-time UI updates.
 * 
 * Unlike the canonical domain model, this implementation uses [mutableStateListOf]
 * to provide reactive updates to Compose observers during a live session.
 */
class BattleTimeline {
    private val _records = mutableStateListOf<TimelineRecord>()
    /** The current list of semantic events (compatibility view). */
    val events: List<BattleEvent> get() = _records.filterIsInstance<BattleEvent>()
    
    /** The complete list of records (articles and events). */
    val records: List<TimelineRecord> get() = _records

    /** Records a new factual record in the timeline. */
    fun record(record: TimelineRecord) {
        _records.add(record)
        
        when (record) {
            is BattleEvent -> {
                // Instant console verification for development
                android.util.Log.d("BATTLE_TIMELINE", "[${record.actor}] ${record.type} (#${record.pokemonId ?: "N/A"})")
            }
            is RealityArticle -> {
                val detail = when (val payload = record.payload) {
                    is CropCaptured -> "crop=${payload.cropProvenance.cropName} sha256=${payload.artifact.sha256.take(12)}"
                    is AudioInputStatus -> "source=${payload.captureSource} state=${payload.state}"
                    is OpponentBattleResourceCountMeasured -> "${payload.resource}=${payload.visibleCount}/${payload.maximumCount}"
                    is ActiveHpBarMeasured -> "side=${payload.side} fill=${"%.3f".format(java.util.Locale.ROOT, payload.filledFraction)} bounds=${payload.barLeft},${payload.barTop},${payload.barRight},${payload.barBottom}"
                    else -> payload::class.simpleName.orEmpty()
                }
                android.util.Log.d("BATTLE_TIMELINE", "[OBSERVATION] ${record.sourceId.id} $detail")
            }
        }
    }

    fun clear() {
        _records.clear()
    }
}
