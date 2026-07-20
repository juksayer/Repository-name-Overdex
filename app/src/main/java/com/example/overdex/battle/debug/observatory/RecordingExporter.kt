package com.example.overdex.battle.debug.observatory

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Handles exporting [ObservationRecording] objects to external storage for analysis.
 */
object RecordingExporter {
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT)

    /**
     * Exports a recording to a JSON file.
     * 
     * @param context Android context for accessing external storage.
     * @param recording The recording to export.
     * @return The [File] object representing the exported JSON.
     */
    fun exportToJson(context: Context, recording: ObservationRecording): File {
        val fileName = "observation_recording_${dateFormat.format(Date(recording.startTime))}.json"
        val file = File(context.getExternalFilesDir(null), fileName)
        val jsonString = RecordingSerializer.serialize(recording)
        file.writeText(jsonString)
        return file
    }

    /**
     * Exports a recording to a human-readable plain text log.
     * 
     * @param context Android context for accessing external storage.
     * @param recording The recording to export.
     * @return The [File] object representing the exported text file.
     */
    fun exportToPlainText(context: Context, recording: ObservationRecording): File {
        val fileName = "observation_recording_${dateFormat.format(Date(recording.startTime))}.txt"
        val file = File(context.getExternalFilesDir(null), fileName)
        
        val sb = StringBuilder()
        sb.append("OBSERVATION RECORDING\n")
        sb.append("Session ID: ${recording.sessionId}\n")
        sb.append("Start: ${Date(recording.startTime)}\n")
        sb.append("End: ${Date(recording.endTime)}\n")
        sb.append("------------------------------------------------\n\n")
        
        recording.events.forEach { event ->
            sb.append("#${String.format(Locale.ROOT, "%05d", event.sequenceNumber)} ")
            sb.append("+${String.format(Locale.ROOT, "%.3f", event.relativeTimestamp / 1000.0)}s ")
            sb.append("[${event.sourceType.name}] ")
            sb.append("${event.payload}\n")
            sb.append("------------------------------------------------\n")
        }
        
        file.writeText(sb.toString())
        return file
    }
}
