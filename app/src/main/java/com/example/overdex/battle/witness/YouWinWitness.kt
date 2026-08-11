package com.example.overdex.battle.witness

import com.example.overdex.battle.observation.Match
import com.example.overdex.battle.observation.Observer
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.data.BattleCalibration
import com.example.overdex.model.observation.ObservationInput
import com.example.overdex.model.observation.RecognitionResult
import com.example.overdex.battle.timeline.observer.ObservationSource as ObserverSource

class YouWinWitness(
    private val input: ObservationInput,
    private val calibration: BattleCalibration,
    override val observerId: ObserverId =
        ObserverId("YOU_WIN_WITNESS", ObserverSource.SCREEN_CAPTURE),
    override val name: String = "You Win Witness"
) : Observer {

    // ...

    private fun isMatch(result: RecognitionResult<String>): Boolean {
        if (result.confidence < 1.0f) return false

        return result.value
            ?.trim()
            ?.uppercase()
            ?.replace(Regex("[^A-Z! ]"), "")
            ?.contains("YOU WIN") == true
    }

    override fun start(match: Match) {
        TODO("Not yet implemented")
    }

    override fun stop() {
        TODO("Not yet implemented")
    }
}