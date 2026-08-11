package com.example.overdex.battle.witness

import com.example.overdex.battle.observation.Match
import com.example.overdex.battle.observation.Observer
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.data.BattleCalibration
import com.example.overdex.model.observation.ObservationInput
import com.example.overdex.model.observation.RecognitionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.example.overdex.battle.timeline.observer.ObservationSource as ObserverSource


class GoodEffortWitness(
    private val input: ObservationInput,
    private val calibration: BattleCalibration,
    override val observerId: ObserverId =
        ObserverId("GOOD_EFFORT_WITNESS", ObserverSource.SCREEN_CAPTURE),
    override val name: String = "Good Effort Witness"
) : Observer {

    private var scope: CoroutineScope? = null

    override fun start(match: Match) {
        if (scope != null) return

        val newScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        scope = newScope

        newScope.launch {
            input.supply { bitmap ->
                // Good Effort recognition will go here
            }
        }
    }

    override fun stop() {
        scope?.cancel("Observer stopped")
        scope = null
    }

    private fun isMatch(result: RecognitionResult<String>): Boolean {
        if (result.confidence < 1.0f) return false

        return result.value
            ?.trim()
            ?.uppercase()
            ?.replace(Regex("[^A-Z! ]"), "")
            ?.contains("GOOD EFFORT") == true
    }
}