package com.example.overdex

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.example.overdex.battle.archive.ArchiveDirectoryManager
import com.example.overdex.battle.observation.DroidballRuntimeMarker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class InterruptedObservationNotice(
    val recoveredMatchCount: Int,
    val preservedCheckpointCount: Int
)

/**
 * Owns field-session state for the lifetime of the Android process.
 *
 * Android may destroy MainActivity while Pokemon GO is foregrounded without
 * stopping DroidballService. An Activity-owned battle ViewModel would then be
 * cleared in the middle of the match, leaving the still-running capture service
 * with no Timeline consumer. The process store keeps that owner attached until
 * the service and process actually end.
 */
class OverdexApplication : Application(), ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()

    private val _interruptedObservationNotice =
        MutableStateFlow<InterruptedObservationNotice?>(null)
    val interruptedObservationNotice = _interruptedObservationNotice.asStateFlow()

    fun acknowledgeInterruptedObservation() {
        _interruptedObservationNotice.value = null
    }

    override fun onCreate() {
        super.onCreate()

        // A true process death also ends MediaProjection, so no live writer can
        // still own these files. Publish every validated checkpoint as an
        // ordinary recovered match before the archive directory is shown.
        val observationWasInterrupted = DroidballRuntimeMarker.consumeInterruptedCapture(this)
        val recovery = ArchiveDirectoryManager(this).recoverInterruptedMatchCheckpoints()
        recovery.recoveredNames.forEach { name ->
            Log.i("MATCH_ARCHIVE", "Recovered interrupted match checkpoint as $name")
        }
        recovery.failures.forEach { (name, reason) ->
            Log.w("MATCH_ARCHIVE", "Kept unreadable checkpoint $name: $reason")
        }
        if (observationWasInterrupted || recovery.recoveredNames.isNotEmpty() || recovery.failures.isNotEmpty()) {
            _interruptedObservationNotice.value = InterruptedObservationNotice(
                recoveredMatchCount = recovery.recoveredNames.size,
                preservedCheckpointCount = recovery.failures.size
            )
        }
    }
}
