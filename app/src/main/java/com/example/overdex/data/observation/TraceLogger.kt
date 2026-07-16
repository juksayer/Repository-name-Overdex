package com.example.overdex.data.observation

import android.util.Log
import com.example.overdex.model.observation.RecognitionResult
import java.util.concurrent.ConcurrentHashMap

object TraceLogger {
    private const val TAG = "ODX_TRACE"
    private val startTimes = ConcurrentHashMap<String, Long>()

    private fun getElapsed(captureId: String): String {
        val start = startTimes[captureId] ?: return "t=? ms"
        return "t=${System.currentTimeMillis() - start} ms"
    }

    fun logStage(
        captureId: String,
        stage: String,
        species: String = "MISSING",
        family: String = "MISSING",
        cp: String = "MISSING",
        fast: String = "MISSING",
        chgA: String = "MISSING",
        chgB: String = "MISSING",
        confidence: String = "0.0"
    ) {
        val elapsed = getElapsed(captureId)
        val output = StringBuilder()
        output.append("[$captureId][$stage][$elapsed]\n")
        output.append("├── Species        : $species\n")
        output.append("├── Family         : $family\n")
        output.append("├── CP             : $cp\n")
        output.append("├── Fast Move      : $fast\n")
        output.append("├── Charged Move A : $chgA\n")
        output.append("├── Charged Move B : $chgB\n")
        output.append("└── Confidence     : $confidence")
        
        Log.d(TAG, output.toString())
    }

    fun logRegionComplete(
        captureId: String,
        regionId: String,
        recognizers: List<String>,
        results: List<RecognitionResult<*>>
    ) {
        val elapsed = getElapsed(captureId)
        val output = StringBuilder()
        output.append("[$captureId][Region Complete][$regionId][$elapsed]\n")
        output.append("├── Recognizers Run:\n")
        recognizers.forEachIndexed { index, r -> 
            val prefix = if (index == recognizers.lastIndex && results.isEmpty()) "└── " else "│   ├── "
            output.append("$prefix$r\n") 
        }
        output.append("└── Results Produced:\n")
        if (results.isEmpty()) {
            output.append("    └── MISSING (No RecognitionResult)\n")
        } else {
            results.forEachIndexed { index, res ->
                val prefix = if (index == results.lastIndex) "    └── " else "    ├── "
                output.append("$prefix${res.recognizer}: ${res.value} (Conf: ${res.confidence})\n")
            }
        }
        Log.d(TAG, output.toString())
    }

    fun logCaptureStart(captureId: String, expectedCount: Int) {
        startTimes[captureId] = System.currentTimeMillis()
        Log.d(TAG, "[$captureId][Capture Start] Expected Regions: $expectedCount")
    }

    fun logConfidenceTrace(
        captureId: String,
        source: String,
        value: String,
        details: List<String> = emptyList()
    ) {
        val elapsed = getElapsed(captureId)
        val output = StringBuilder()
        output.append("[$captureId][Confidence Trace][$elapsed]\n")
        output.append("├── Source: $source\n")
        
        if (details.isNotEmpty()) {
            details.dropLast(1).forEach { d ->
                output.append("├── $d\n")
            }
            output.append("└── ${details.last()}\n")
        } else {
            output.append("└── NOT IMPLEMENTED (Using Default)\n")
        }
        output.append("Final Confidence: $value")
        Log.d(TAG, output.toString())
    }

    fun logPipelineVerdict(
        captureId: String,
        verdicts: Map<String, String>
    ) {
        val elapsed = getElapsed(captureId)
        val output = StringBuilder()
        output.append("\n[$captureId][Pipeline Verdict][$elapsed]\n")
        val keys = verdicts.keys.toList()
        keys.forEachIndexed { index, key ->
            val prefix = if (index == keys.lastIndex) "└── " else "├── "
            val padding = ".".repeat((20 - key.length).coerceAtLeast(1))
            output.append("$prefix$key$padding${verdicts[key]}\n")
        }
        Log.d(TAG, output.toString())
        
        // Clean up timing for this capture
        startTimes.remove(captureId)
    }
}
