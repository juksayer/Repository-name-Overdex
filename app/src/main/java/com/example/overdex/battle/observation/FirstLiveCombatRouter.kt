package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.timeline.observer.ObserverId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.example.overdex.battle.timeline.observer.ObservationSource as ObserverSource

/** Routes the first preserved in-battle HUD crop into the session boundary. */
class FirstLiveCombatRouter(
    private val session: DroidballSession,
    override val observerId: ObserverId = ObserverId("FIRST_LIVE_COMBAT_ROUTER", ObserverSource.SCREEN_CAPTURE),
    override val name: String = "First Live Combat Router"
) : Observer {
    private var scope: CoroutineScope? = null

    override fun start(match: Match) {
        if (scope != null) return
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { routerScope ->
            routerScope.launch {
                match.articles.collect { article ->
                    val crop = article.payload as? CropCaptured ?: return@collect
                    if (crop.cropProvenance.cropName == BattleCropContracts.opponentHpEvidence.cropName) {
                        session.recordFirstLiveCombat(article)
                    }
                }
            }
        }
    }

    override fun stop() {
        scope?.cancel("Router stopped")
        scope = null
    }
}
