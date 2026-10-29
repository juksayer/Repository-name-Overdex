package com.example.overdex.presentation

import com.example.overdex.presentation.DroidballSoundCue.EDGE_IMPACT
import com.example.overdex.presentation.DroidballSoundCue.ROLL_START
import com.example.overdex.presentation.DroidballSoundCue.ROLL_STOP
import com.example.overdex.presentation.DroidballSoundCue.SKID
import org.junit.Assert.assertEquals
import org.junit.Test

class DroidballJourneySoundCuesTest {
    @Test fun `hiding a paused docking owner stops its loop and allows visible resume`() {
        val cues = DroidballJourneySoundCues()
        val docking = state(DroidballJourneyPhase.DOCKING)
        assertEquals(listOf(ROLL_START), cues.frame(docking, 100L))
        assertEquals(listOf(ROLL_STOP), cues.hidden(docking))
        assertEquals(emptyList<DroidballSoundCue>(), cues.hidden(docking))
        assertEquals(listOf(ROLL_START), cues.frame(docking, 110L))
        assertEquals(listOf(ROLL_STOP), cues.frame(docking, 600L))
        val next = state(DroidballJourneyPhase.ARRIVING, generation = 2L)
        assertEquals(listOf(ROLL_START), cues.frame(next, 0L))
        assertEquals(emptyList<DroidballSoundCue>(), cues.hidden(docking))
        assertEquals(listOf(ROLL_STOP, SKID), cues.frame(next, 1100L))
    }

    @Test fun `departure expressions stay quiet and its roll starts and stops once`() {
        val cues = DroidballJourneySoundCues()
        val departure = state(DroidballJourneyPhase.DEPARTING)

        assertEquals(emptyList<DroidballSoundCue>(), cues.phaseChanged(departure))
        assertEquals(emptyList<DroidballSoundCue>(), cues.frame(departure, 911L))
        assertEquals(listOf(ROLL_START), cues.frame(departure, 912L))
        assertEquals(emptyList<DroidballSoundCue>(), cues.frame(departure, 912L))
        assertEquals(emptyList<DroidballSoundCue>(), cues.frame(departure, 1500L))
        assertEquals(listOf(ROLL_STOP), cues.frame(departure, DroidballChoreography.DEPARTURE_MILLIS))
        assertEquals(emptyList<DroidballSoundCue>(), cues.frame(departure, 2000L))
    }

    @Test fun `arrival scan is silent between the entrance roll and rightward snap`() {
        val cues = DroidballJourneySoundCues()
        val arrival = state(DroidballJourneyPhase.ARRIVING)

        assertEquals(listOf(ROLL_START), cues.frame(arrival, 0L))
        assertEquals(emptyList<DroidballSoundCue>(), cues.frame(arrival, 909L))
        assertEquals(listOf(ROLL_STOP, SKID), cues.frame(arrival, 910L))
        assertEquals(emptyList<DroidballSoundCue>(), cues.frame(arrival, 1004L))
        assertEquals(emptyList<DroidballSoundCue>(), cues.frame(arrival, 1700L))
        assertEquals(emptyList<DroidballSoundCue>(), cues.frame(arrival, 1982L))
        assertEquals(listOf(ROLL_START), cues.frame(arrival, 1983L))
        assertEquals(listOf(ROLL_STOP), cues.frame(arrival, DroidballChoreography.ARRIVAL_MILLIS))
    }

    @Test fun `skid follows the first braking frame once and remains consumed across visibility changes`() {
        val cues = DroidballJourneySoundCues()
        val arrival = state(DroidballJourneyPhase.ARRIVING)

        assertEquals(listOf(ROLL_START), cues.frame(arrival, 800L))
        assertEquals(listOf(ROLL_STOP, SKID), cues.frame(arrival, 930L))
        assertEquals(emptyList<DroidballSoundCue>(), cues.frame(arrival, 930L))
        assertEquals(emptyList<DroidballSoundCue>(), cues.frame(arrival, 1100L))
        assertEquals(emptyList<DroidballSoundCue>(), cues.hidden(arrival))
        assertEquals(listOf(ROLL_START), cues.frame(arrival, 900L))
        assertEquals(listOf(ROLL_STOP), cues.frame(arrival, 910L))

        val nextArrival = state(DroidballJourneyPhase.ARRIVING, generation = 2L)
        assertEquals(listOf(ROLL_START), cues.frame(nextArrival, 0L))
        assertEquals(listOf(ROLL_STOP, SKID), cues.frame(nextArrival, 910L))
    }

    @Test fun `late or hidden entrance cannot replay its skipped skid`() {
        val arrival = state(DroidballJourneyPhase.ARRIVING)
        for (firstFrame in listOf(910L, 1000L, 1166L, 1167L, 1700L)) {
            val cues = DroidballJourneySoundCues()
            assertEquals(emptyList<DroidballSoundCue>(), cues.frame(arrival, firstFrame))
            assertEquals(emptyList<DroidballSoundCue>(), cues.hidden(arrival))
            assertEquals(listOf(ROLL_START), cues.frame(arrival, 900L))
            assertEquals(listOf(ROLL_STOP), cues.frame(arrival, 910L))
        }

        val hiddenEntrance = DroidballJourneySoundCues()
        assertEquals(listOf(ROLL_START), hiddenEntrance.frame(arrival, 800L))
        assertEquals(listOf(ROLL_STOP), hiddenEntrance.hidden(arrival))
        assertEquals(emptyList<DroidballSoundCue>(), hiddenEntrance.frame(arrival, 950L))
    }

    @Test fun `skid window expires at half the arrival and does not catch up afterward`() {
        val arrival = state(DroidballJourneyPhase.ARRIVING)
        val lastValid = DroidballJourneySoundCues()
        assertEquals(listOf(ROLL_START), lastValid.frame(arrival, 800L))
        assertEquals(listOf(ROLL_STOP, SKID), lastValid.frame(arrival, 1166L))

        val expired = DroidballJourneySoundCues()
        assertEquals(listOf(ROLL_START), expired.frame(arrival, 800L))
        assertEquals(listOf(ROLL_STOP), expired.frame(arrival, 1167L))
        assertEquals(emptyList<DroidballSoundCue>(), expired.frame(arrival, 1200L))
    }

    @Test fun `roll visible after resume can still brake if no skid has been handled`() {
        val cues = DroidballJourneySoundCues()
        val arrival = state(DroidballJourneyPhase.ARRIVING)

        assertEquals(listOf(ROLL_START), cues.frame(arrival, 700L))
        assertEquals(listOf(ROLL_STOP), cues.hidden(arrival))
        assertEquals(listOf(ROLL_START), cues.frame(arrival, 850L))
        assertEquals(listOf(ROLL_STOP, SKID), cues.frame(arrival, 910L))
    }

    @Test fun `late first frames skip expired cues and start only the current movement`() {
        val cues = DroidballJourneySoundCues()
        val arrival = state(DroidballJourneyPhase.ARRIVING)

        assertEquals(emptyList<DroidballSoundCue>(), cues.frame(arrival, 1500L))
        assertEquals(listOf(ROLL_START), cues.frame(arrival, 2200L))

        val alreadyFinished = DroidballJourneySoundCues()
        assertEquals(emptyList<DroidballSoundCue>(), alreadyFinished.frame(arrival, 3000L))
        assertEquals(emptyList<DroidballSoundCue>(), alreadyFinished.phaseChanged(fieldAfter(arrival)))
    }

    @Test fun `jump across the quiet interval stops the old roll before starting the snap`() {
        val cues = DroidballJourneySoundCues()
        val arrival = state(DroidballJourneyPhase.ARRIVING)

        assertEquals(listOf(ROLL_START), cues.frame(arrival, 100L))
        assertEquals(listOf(ROLL_STOP, ROLL_START), cues.frame(arrival, 2200L))
    }

    @Test fun `return and docking sound only during their rolling intervals`() {
        val cues = DroidballJourneySoundCues()
        val returning = state(DroidballJourneyPhase.RETURNING)
        val docking = state(DroidballJourneyPhase.DOCKING, generation = 2L)

        assertEquals(emptyList<DroidballSoundCue>(), cues.frame(returning, 65L))
        assertEquals(listOf(ROLL_START), cues.frame(returning, 66L))
        assertEquals(listOf(ROLL_STOP), cues.frame(returning, DroidballChoreography.EXIT_MILLIS))
        assertEquals(emptyList<DroidballSoundCue>(), cues.phaseChanged(docking))
        assertEquals(listOf(ROLL_START), cues.frame(docking, 0L))
        assertEquals(emptyList<DroidballSoundCue>(), cues.frame(docking, 577L))
        assertEquals(listOf(ROLL_STOP), cues.frame(docking, 578L))
        assertEquals(emptyList<DroidballSoundCue>(), cues.frame(docking, 800L))
    }

    @Test fun `phase changes stop sound without starting an unseen new movement`() {
        val cues = DroidballJourneySoundCues()
        val departure = state(DroidballJourneyPhase.DEPARTING)
        val docking = state(DroidballJourneyPhase.DOCKING, generation = 2L)

        assertEquals(listOf(ROLL_START), cues.frame(departure, 1000L))
        assertEquals(listOf(ROLL_STOP), cues.phaseChanged(docking))
        assertEquals(emptyList<DroidballSoundCue>(), cues.phaseChanged(docking))
        assertEquals(listOf(ROLL_START), cues.frame(docking, 0L))
    }

    @Test fun `new generation frame can precede its collector and stale calls cannot reset it`() {
        val cues = DroidballJourneySoundCues()
        val departure = state(DroidballJourneyPhase.DEPARTING)
        val docking = state(DroidballJourneyPhase.DOCKING, generation = 2L)

        assertEquals(listOf(ROLL_START), cues.frame(departure, 1000L))
        assertEquals(listOf(ROLL_STOP, ROLL_START), cues.frame(docking, 0L))
        assertEquals(emptyList<DroidballSoundCue>(), cues.phaseChanged(docking))
        assertEquals(emptyList<DroidballSoundCue>(), cues.phaseChanged(departure))
        assertEquals(emptyList<DroidballSoundCue>(), cues.frame(departure, 1600L))
        assertEquals(emptyList<DroidballSoundCue>(), cues.frame(docking, 300L))
        assertEquals(listOf(ROLL_STOP), cues.frame(docking, 578L))
    }

    @Test fun `same generation copies duplicate frames and older progress do not replay cues`() {
        val cues = DroidballJourneySoundCues()
        val departure = state(DroidballJourneyPhase.DEPARTING)

        assertEquals(listOf(ROLL_START), cues.frame(departure, 1000L))
        assertEquals(emptyList<DroidballSoundCue>(), cues.phaseChanged(departure.copy(departurePresented = true)))
        assertEquals(emptyList<DroidballSoundCue>(), cues.frame(departure, 800L))
        assertEquals(emptyList<DroidballSoundCue>(), cues.frame(departure, 1000L))
        assertEquals(listOf(ROLL_STOP), cues.frame(departure, 1600L))
        assertEquals(emptyList<DroidballSoundCue>(), cues.frame(departure, 1500L))
    }

    @Test fun `natural arrival completion stops rolling and impacts exactly once`() {
        val cues = DroidballJourneySoundCues()
        val arrival = state(DroidballJourneyPhase.ARRIVING)
        val field = fieldAfter(arrival)

        assertEquals(listOf(ROLL_START), cues.frame(arrival, 2200L))
        assertEquals(listOf(ROLL_STOP, EDGE_IMPACT), cues.phaseChanged(field))
        assertEquals(emptyList<DroidballSoundCue>(), cues.phaseChanged(field))
        assertEquals(emptyList<DroidballSoundCue>(), cues.frame(field, 0L))
        assertEquals(emptyList<DroidballSoundCue>(), cues.frame(arrival, 2333L))
    }

    @Test fun `completion still impacts when a final frame already stopped the roll`() {
        val cues = DroidballJourneySoundCues()
        val arrival = state(DroidballJourneyPhase.ARRIVING)

        assertEquals(listOf(ROLL_START), cues.frame(arrival, 2200L))
        assertEquals(listOf(ROLL_STOP), cues.frame(arrival, DroidballChoreography.ARRIVAL_MILLIS))
        assertEquals(listOf(EDGE_IMPACT), cues.frame(fieldAfter(arrival), 0L))
    }

    @Test fun `early panel opening cannot impact even after a late visible snap frame`() {
        val cues = DroidballJourneySoundCues()
        val arrival = state(DroidballJourneyPhase.ARRIVING)

        assertEquals(listOf(ROLL_START), cues.frame(arrival, 2200L))
        assertEquals(listOf(ROLL_STOP), cues.phaseChanged(fieldAfter(arrival, elapsedMillis = 2200L)))
    }

    @Test fun `unseen completion interrupted arrival and skipped generations do not impact`() {
        val arrival = state(DroidballJourneyPhase.ARRIVING)
        val unseenEnd = DroidballJourneySoundCues()
        assertEquals(listOf(ROLL_START), unseenEnd.frame(arrival, 2000L))
        assertEquals(listOf(ROLL_STOP), unseenEnd.phaseChanged(fieldAfter(arrival)))

        val interrupted = DroidballJourneySoundCues()
        assertEquals(listOf(ROLL_START), interrupted.frame(arrival, 2200L))
        assertEquals(listOf(ROLL_STOP), interrupted.phaseChanged(
            fieldAfter(arrival).copy(phase = DroidballJourneyPhase.RETURNING)))

        val skipped = DroidballJourneySoundCues()
        assertEquals(listOf(ROLL_START), skipped.frame(arrival, 2200L))
        assertEquals(listOf(ROLL_STOP), skipped.phaseChanged(fieldAfter(arrival).copy(generation = 4L)))
    }

    private fun state(phase: DroidballJourneyPhase, generation: Long = 1L) =
        DroidballJourneyState(phase, generation, startedAtNanos = 1_000_000_000L)

    private fun fieldAfter(arrival: DroidballJourneyState,
        elapsedMillis: Long = DroidballChoreography.ARRIVAL_MILLIS) =
        DroidballJourneyState(DroidballJourneyPhase.FIELD, arrival.generation + 1,
            arrival.startedAtNanos + elapsedMillis * 1_000_000L)
}
