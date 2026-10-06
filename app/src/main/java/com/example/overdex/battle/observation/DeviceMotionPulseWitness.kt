package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.DeviceMotionPulseMeasured
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.timeline.observer.ObservationSource
import com.example.overdex.battle.timeline.observer.ObserverId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/** Preserves one sensor pulse per testimony article; interpretation is downstream. */
class DeviceMotionPulseWitness(
    private val isEnabled: () -> Boolean,
    override val observerId: ObserverId = ObserverId(
        "DEVICE_MOTION_PULSE_WITNESS",
        ObservationSource.DROIDBALL
    ),
    override val name: String = "Device Motion Pulse Witness"
) : Observer {
    override val managesAvailability: Boolean = true
    private var scope: CoroutineScope? = null
    private var match: Match? = null
    private var lastAvailability: Boolean? = null

    override fun start(match: Match) {
        if (scope != null) return
        this.match = match
        val sourceId = SourceId(observerId.id)
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                DroidballService.motionPulseCaptureAvailable
                    .filterNotNull()
                    .distinctUntilChanged()
                    .collect { available ->
                        lastAvailability = available
                        match.custody.submitAvailability(sourceId, available, System.currentTimeMillis())
                    }
            }
            witnessScope.launch {
                DroidballService.motionPulses.collect { pulse ->
                    if (!isEnabled()) return@collect
                    match.custody.submitTestimony(
                        sourceId = sourceId,
                        payload = DeviceMotionPulseMeasured(
                            durationNanos = pulse.durationNanos,
                            peakLinearAccelerationMetersPerSecondSquared =
                                pulse.peakLinearAccelerationMetersPerSecondSquared,
                            rmsLinearAccelerationMetersPerSecondSquared =
                                pulse.rmsLinearAccelerationMetersPerSecondSquared,
                            sampleCount = pulse.sampleCount
                        ),
                        timestamp = pulse.capturedAtWallTimeMillis,
                        confidence = null,
                        evidenceReferences = emptyList(),
                        monotonicTimeNanos = pulse.startedAtMonotonicTimeNanos
                    )
                }
            }
        }
    }

    override fun stop() {
        if (lastAvailability != false) {
            match?.custody?.submitAvailability(
                SourceId(observerId.id),
                false,
                System.currentTimeMillis()
            )
        }
        scope?.cancel("Witness stopped")
        scope = null
        match = null
        lastAvailability = null
    }
}
