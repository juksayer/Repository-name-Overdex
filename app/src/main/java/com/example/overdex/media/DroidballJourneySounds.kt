package com.example.overdex.media

import android.content.Context
import android.util.Log
import com.example.overdex.presentation.DroidballJourney
import com.example.overdex.presentation.DroidballJourneySoundCues
import com.example.overdex.presentation.DroidballJourneyState
import com.example.overdex.presentation.DroidballSoundCue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** One process owner prevents overlapping sounds from shell/navigation recomposition. */
object DroidballJourneySounds {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val cues = DroidballJourneySoundCues()
    private var journeyJob: Job? = null

    fun prepare(context: Context) {
        if (journeyJob != null) return
        DroidballJourneySoundPlayer.prepare(context.applicationContext)
        journeyJob = scope.launch {
            DroidballJourney.state.collect { state -> play(cues.phaseChanged(state)) }
        }
    }

    /** Called by the currently drawing owner; never by a separate audio timer. */
    fun frame(state: DroidballJourneyState, elapsedMillis: Long) {
        val current = DroidballJourney.state.value
        if (current.generation != state.generation || current.phase != state.phase) return
        play(cues.frame(state, elapsedMillis))
    }

    fun hidden(state: DroidballJourneyState) {
        val current = DroidballJourney.state.value
        if (current.generation != state.generation || current.phase != state.phase) return
        play(cues.hidden(state))
        DroidballJourneySoundPlayer.stop()
    }

    private fun play(events: List<DroidballSoundCue>) {
        events.forEach { cue ->
            when (cue) {
                DroidballSoundCue.ROLL_START -> DroidballJourneySoundPlayer.startRoll()
                DroidballSoundCue.ROLL_STOP -> DroidballJourneySoundPlayer.stopRoll()
                DroidballSoundCue.EDGE_IMPACT -> DroidballJourneySoundPlayer.impact()
                DroidballSoundCue.SKID -> DroidballJourneySoundPlayer.skid()
            }
            Log.d("DROIDBALL_SOUND", cue.name)
        }
    }
}
