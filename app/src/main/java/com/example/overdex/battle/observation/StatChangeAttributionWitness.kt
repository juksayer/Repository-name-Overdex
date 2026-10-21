package com.example.overdex.battle.observation

import android.content.Context
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.inference.StatChangeInference
import com.example.overdex.battle.inference.StatMoveEffect
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.battle.timeline.observer.ObservationSource
import kotlinx.coroutines.*
import org.json.JSONObject

/** Joins independent cue and charged-move Articles, including when OCR is delivered later. */
class StatChangeAttributionWitness(private val context: Context) : Observer {
    override val observerId = ObserverId("STAT_CHANGE_ATTRIBUTION", ObservationSource.AUDIO_CAPTURE)
    override val name = "Stat Change Attribution"
    private var scope: CoroutineScope? = null
    override fun start(match: Match) {
        if (scope != null) return
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { scope -> scope.launch {
            val moves = context.assets.open("gamemaster.json").bufferedReader().use { JSONObject(it.readText()).getJSONArray("moves") }
            val effects = buildList {
                for (index in 0 until moves.length()) {
                    val move = moves.getJSONObject(index)
                    val buffs = move.optJSONArray("buffs") ?: continue
                    val target = move.optString("buffTarget")
                    if (target != "self" && target != "opponent") continue
                    listOf("ATTACK", "DEFENSE").forEachIndexed { statIndex, stat ->
                        val stages = buffs.optInt(statIndex)
                        if (stages != 0) add(StatMoveEffect(move.getString("name"), stat, stages, target == "self"))
                    }
                }
            }
            val inference = StatChangeInference(effects)
            match.articles.collect { article ->
                inference.accept(article).forEach { derived ->
                    match.custody.submitTestimony(SourceId(observerId.id), derived.payload,
                        derived.sound.perceivedAt, null,
                        listOf(derived.sound.id.value) + derived.moves.map { it.id.value },
                        requireNotNull(derived.sound.monotonicTimeNanos))
                }
            }
        } }
    }
    override fun stop() { scope?.cancel(); scope = null }
}
