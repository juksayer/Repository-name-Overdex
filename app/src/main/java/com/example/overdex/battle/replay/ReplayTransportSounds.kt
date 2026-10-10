package com.example.overdex.battle.replay

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import androidx.annotation.RawRes
import com.example.overdex.R

/** Local transport sounds for replay interaction; never Match evidence. */
class ReplayTransportSounds(context: Context) {
    private val appContext = context.applicationContext
    private val mediaAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(mediaAttributes)
        .build()
    private val play = pool.load(context, R.raw.replay_transport_play, 1)
    private val pause = pool.load(context, R.raw.replay_transport_pause, 1)
    private val tick = pool.load(context, R.raw.replay_transport_tick, 1)
    private val stop = pool.load(context, R.raw.replay_transport_stop, 1)
    // Steady motor section (3–5 s) of the user's cassette recording, crossfaded
    // into a loop. The original recording and its transport clunks stay intact.
    private val scrubWhir = pool.load(context, R.raw.replay_scrub_whir, 1)
    private var longFormPlayer: MediaPlayer? = null
    private var transportStream = 0
    private var scrubStream = 0
    private val handler = Handler(Looper.getMainLooper())
    private val finishScrub = Runnable { endScrub() }

    fun insert() = playLongForm(R.raw.replay_vhs_tape_insert, volume = 0.42f)
    fun play() {
        endScrub()
        stopLongForm()
        emit(play)
    }
    fun pause() {
        endScrub()
        stopLongForm()
        emit(pause)
    }
    fun tick() = emit(tick, 0.45f)
    fun scrub() {
        stopLongForm()
        pool.stop(transportStream)
        if (scrubStream == 0) scrubStream = pool.play(scrubWhir, 0.48f, 0.48f, 1, -1, 1f)
        handler.removeCallbacks(finishScrub)
        // Also handles the shell's drag callback, which has no release callback.
        handler.postDelayed(finishScrub, 180L)
    }
    fun endScrub() {
        handler.removeCallbacks(finishScrub)
        pool.stop(scrubStream)
        scrubStream = 0
    }
    fun reset() = playLongForm(R.raw.replay_cassette_rewind, volume = 0.42f)
    fun stop() {
        endScrub()
        stopLongForm()
        emit(stop)
    }
    fun release() {
        endScrub()
        stopLongForm()
        pool.release()
    }

    private fun emit(sound: Int, volume: Float = 0.7f): Int =
        pool.play(sound, volume, volume, 1, 0, 1f).also { transportStream = it }

    private fun playLongForm(@RawRes sound: Int, volume: Float) {
        endScrub()
        stopLongForm()
        longFormPlayer = MediaPlayer.create(
            appContext,
            sound,
            mediaAttributes,
            AudioManager.AUDIO_SESSION_ID_GENERATE,
        )?.apply {
            setVolume(volume, volume)
            setOnCompletionListener { completed ->
                if (longFormPlayer === completed) longFormPlayer = null
                completed.release()
            }
            start()
        }
    }

    private fun stopLongForm() {
        longFormPlayer?.release()
        longFormPlayer = null
    }
}
