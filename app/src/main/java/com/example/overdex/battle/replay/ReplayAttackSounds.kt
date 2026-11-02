package com.example.overdex.battle.replay

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Reconstructed playback cues, never evidence about the captured match. */
data class ReplayAttackSoundEvent(val atNanos: Long, val moveName: String, val charged: Boolean)

internal fun replayAttackSoundsBetween(events: List<ReplayAttackSoundEvent>, from: Long, to: Long) =
    if (to <= from) emptyList() else events.filter { it.atNanos > from && it.atNanos <= to }

/** Recognition excerpts are not complete replay effects. Unmarked legacy assets remain playable. */
internal fun replayAttackSoundFiles(manifestText: String, moveNames: Set<String>): List<Pair<String, String>> {
    val requested = moveNames.map(String::lowercase).toSet()
    val result = mutableListOf<Pair<String, String>>()
    val references = Json.parseToJsonElement(manifestText).jsonObject.getValue("references").jsonArray
    for (value in references) {
        val reference = value.jsonObject
        val replayEligible = reference["replayEligible"]
        if (replayEligible != null && replayEligible != JsonPrimitive(true)) continue
        val name = reference.getValue("moveName").jsonPrimitive.content.lowercase()
        if (name in requested) {
            // Preserve variant order and the loader's existing fallback when
            // one eligible asset cannot be opened.
            result += name to reference.getValue("file").jsonPrimitive.content
        }
    }
    return result
}

/** Loads only the moves used in this replay; all playback uses media volume. */
class ReplayAttackSounds(private val context: Context) {
    private val pool = SoundPool.Builder().setMaxStreams(8).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val samples = mutableMapOf<Pair<Boolean, String>, Int>()
    private val ready = mutableSetOf<Int>()
    private val streams = mutableListOf<Int>()
    private var released = false

    init { pool.setOnLoadCompleteListener { _, id, status -> synchronized(this) {
        if (!released && status == 0) ready += id
    } } }

    suspend fun prepare(events: List<ReplayAttackSoundEvent>) = withContext(Dispatchers.IO) {
        for (charged in listOf(false, true)) {
            val directory = if (charged) "charged_move_sounds" else "fast_move_sounds"
            val names = events.filter { it.charged == charged }.map { it.moveName.lowercase() }.toSet()
            if (names.isEmpty()) continue
            val references = try {
                context.assets.open("$directory/manifest.json").bufferedReader().use {
                    replayAttackSoundFiles(it.readText(), names)
                }
            } catch (e: java.io.IOException) {
                android.util.Log.w("ReplayAttackSounds", "Audio manifest unavailable: $directory", e)
                continue
            }
            for ((name, file) in references) {
                synchronized(this@ReplayAttackSounds) {
                    if (!released && (charged to name) !in samples) {
                        try { context.assets.openFd("$directory/$file").use {
                            samples[charged to name] = pool.load(it, 1)
                        } } catch (e: java.io.IOException) {
                            android.util.Log.w("ReplayAttackSounds", "Audio unavailable: $name", e)
                        }
                    }
                }
            }
        }
    }

    @Synchronized fun play(event: ReplayAttackSoundEvent, speed: Float) {
        if (released) return
        val id = samples[event.charged to event.moveName.lowercase()] ?: return
        if (id !in ready) return // Never play a late load against a different scene.
        val stream = pool.play(id, .65f, .65f, 1, 0, speed.coerceIn(.5f, 2f))
        if (stream != 0) streams += stream
        // Stream IDs are short-lived; bound bookkeeping during long compilations.
        if (streams.size > 32) streams.removeAt(0)
    }
    @Synchronized fun stop() { if (!released) streams.forEach(pool::stop); streams.clear() }
    @Synchronized fun release() { if (!released) { stop(); released = true; pool.release(); ready.clear(); samples.clear() } }
}
