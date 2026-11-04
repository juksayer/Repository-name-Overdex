package com.example.overdex.battle.observation

import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.battle.timeline.observer.ObservationSource
import com.example.overdex.battle.timeline.observer.ObserverId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps charge-phase text observable when GO was missed. Independent combat or
 * a witnessed charged phase admits sampling; this never establishes Match Start
 * or changes the session's canonical phase. The crop retains its own cadence.
 */
class BattlePhaseTextSampling : Observer {
    override val observerId = ObserverId("BATTLE_PHASE_TEXT_SAMPLING", ObservationSource.SCREEN_CAPTURE)
    override val name = "Battle Phase Text Sampling"
    override val managesAvailability = true
    private var admission = BattleInProgressAdmission()
    private var scope: CoroutineScope? = null
    private var accepting = true

    @Synchronized internal fun observe(article: RealityArticle) {
        if (accepting) admission.observe(article)
    }

    @Synchronized fun isEnabled(nowNanos: Long = System.nanoTime()): Boolean =
        accepting && admission.allows(nowNanos)

    override fun start(match: Match) {
        val workers = synchronized(this) {
            if (scope != null) return
            accepting = true
            admission = BattleInProgressAdmission()
            CoroutineScope(Dispatchers.Default + SupervisorJob()).also { scope = it }
        }
        // Subscribe before crop capture starts so a short phase cue is not lost.
        workers.launch(start = CoroutineStart.UNDISPATCHED) {
            match.articles.collect { article -> observe(article) }
        }
    }

    override fun stop() {
        val workers = synchronized(this) {
            accepting = false
            admission = BattleInProgressAdmission()
            scope.also { scope = null }
        }
        workers?.cancel("Battle phase text sampling stopped")
    }
}
