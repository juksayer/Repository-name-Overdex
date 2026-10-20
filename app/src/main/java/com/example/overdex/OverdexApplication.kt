package com.example.overdex

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.example.overdex.battle.archive.ArchiveDirectoryManager
import com.example.overdex.battle.observation.DroidballRuntimeMarker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
    private val recoveryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun acknowledgeInterruptedObservation() {
        _interruptedObservationNotice.value = null
    }

    override fun onCreate() {
        super.onCreate()

        // A true process death also ends MediaProjection, so no live writer can
        // still own these files. Snapshot the old files now; never recover a
        // new session's journal while background recovery is running.
        val observationWasInterrupted = DroidballRuntimeMarker.consumeInterruptedCapture(this)
        val archives = ArchiveDirectoryManager(this)
        val checkpoints = archives.interruptedCheckpointFiles()
        recoveryScope.launch {
            val recovery = archives.recoverInterruptedMatchCheckpoints(checkpoints)
            recovery.recoveredNames.forEach { name ->
                Log.i("MATCH_ARCHIVE", "Recovered interrupted match checkpoint as $name")
            }
            recovery.failures.forEach { (name, reason) ->
                Log.w("MATCH_ARCHIVE", "Kept unreadable checkpoint $name: $reason")
            }
            runCatching { archives.trimCompletedArchives() }
                .onFailure { Log.w("MATCH_ARCHIVE", "Unable to apply recording retention", it) }
            if (observationWasInterrupted || recovery.recoveredNames.isNotEmpty() || recovery.failures.isNotEmpty()) {
                _interruptedObservationNotice.value = InterruptedObservationNotice(
                    recoveredMatchCount = recovery.recoveredNames.size,
                    preservedCheckpointCount = recovery.failures.size
                )
            }
        }
    }
}
