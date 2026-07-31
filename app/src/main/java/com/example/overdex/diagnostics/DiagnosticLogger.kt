package com.example.overdex.diagnostics

import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.compositionLocalOf
import java.util.concurrent.atomic.AtomicLong

object DiagnosticLogger {
    val LocalCurrentRoute = compositionLocalOf<String?> { null }
    private val sequenceCounter = AtomicLong(0)

    fun nextSequenceNumber(): Long = sequenceCounter.incrementAndGet()

    fun logInput(instanceId: String, button: String, route: String?, dispatch: Boolean = true) {
        val seq = nextSequenceNumber()
        val time = SystemClock.elapsedRealtime()
        Log.d("InputDebug", "seq=$seq time=$time inst=$instanceId route=${route ?: "unknown"} button=$button dispatch=$dispatch")
    }

    fun logLifecycle(instanceId: String, event: String, route: String?) {
        val seq = nextSequenceNumber()
        val time = SystemClock.elapsedRealtime()
        Log.d("Lifecycle", "seq=$seq time=$time inst=$instanceId event=$event route=${route ?: "unknown"}")
    }

    fun logNav(action: String, before: String?, popped: Boolean, after: String?) {
        val seq = nextSequenceNumber()
        val time = SystemClock.elapsedRealtime()
        Log.d("NavDebug", "seq=$seq time=$time action=$action before=${before ?: "null"} popped=$popped after=${after ?: "null"}")
    }
}
