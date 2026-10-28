package com.example.overdex.diagnostics

import android.util.Log
import com.example.overdex.battle.custody.ActiveHpBarMeasured
import com.example.overdex.battle.custody.AudioInputStatus
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.OpponentBattleResourceCountMeasured
import com.example.overdex.battle.custody.PlayerPokeBallCountMeasured
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.model.BattleEvent
import com.example.overdex.model.TimelineRecord
import java.util.Locale

/** Presentation only. The stored source ID remains the archive/routing identity. */
fun sourceJobLabel(sourceId: String): String =
    sourceId.split('_').filterNot { it == "WITNESS" }.joinToString("_")

fun signalLabel(typeName: String): String =
    typeName.removeSuffix("Witnessed").removePrefix("Witness")

/** Formats supplied fields; never decides whether a record is true or observed. */
object BattleRecordDiagnostics {
    fun log(record: TimelineRecord) {
        format(record)?.let { Log.d("BATTLE_TIMELINE", it) }
    }

    fun format(record: TimelineRecord): String? = when (record) {
        is BattleEvent -> "[${record.actor}] ${record.type} (#${record.pokemonId ?: "N/A"})"
        is RealityArticle -> {
            val signal = signalLabel(record.payload::class.simpleName.orEmpty())
            val detail = when (val payload = record.payload) {
                is CropCaptured -> "crop=${payload.cropProvenance.cropName} sha256=${payload.artifact.sha256.take(12)}"
                is AudioInputStatus -> "source=${payload.captureSource} state=${payload.state}"
                is PlayerPokeBallCountMeasured -> "PLAYER POKE_BALLS=${payload.visibleCount}/${payload.maximumCount}"
                is OpponentBattleResourceCountMeasured -> "${payload.resource}=${payload.visibleCount}/${payload.maximumCount}"
                is ActiveHpBarMeasured -> "side=${payload.side} fill=${"%.3f".format(Locale.ROOT, payload.filledFraction)} bounds=${payload.barLeft},${payload.barTop},${payload.barRight},${payload.barBottom}"
                else -> ""
            }
            // An Article can contain a measurement, configuration, availability,
            // or a separately derived conclusion. Do not call all of them observations.
            "[ARTICLE] ${sourceJobLabel(record.sourceId.id)} signal=$signal" +
                if (detail.isEmpty()) "" else " $detail"
        }
        else -> null
    }
}
