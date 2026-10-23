package com.example.overdex.battle.replay

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Reconstructed playback cues, never evidence about the captured match. */
data class ReplayAttackSoundEvent(val atNanos: Long, val moveName: String, val charged: Boolean)

internal fun replayAttackSoundsBetween(events: List<ReplayAttackSoundEvent>, from: Long, to: Long) =
    if (to <= from) emptyList() else events.filter { it.atNanos > from && it.atNanos <= to }

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
            val manifest = try {
                context.assets.open("$directory/manifest.json").bufferedReader().use { JSONObject(it.readText()) }
            } catch (e: java.io.IOException) {
                android.util.Log.w("ReplayAttackSounds", "Audio manifest unavailable: $directory", e)
                continue
            }
            val references = manifest.getJSONArray("references")
            for (i in 0 until references.length()) {
                val reference = references.getJSONObject(i)
                val name = reference.getString("moveName").lowercase()
                if (name !in names) continue
                synchronized(this@ReplayAttackSounds) {
                    if (!released && (charged to name) !in samples) {
                        try { context.assets.openFd("$directory/${reference.getString("file")}").use {
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
