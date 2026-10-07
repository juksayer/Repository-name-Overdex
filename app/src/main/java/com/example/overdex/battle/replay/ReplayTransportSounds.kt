package com.example.overdex.battle.replay

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.SystemClock
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
    private val fastForward = pool.load(context, R.raw.replay_vhs_fast_forward, 1)
    private var longFormPlayer: MediaPlayer? = null
    private var lastFastForwardAtMillis = 0L

    fun insert() = playLongForm(R.raw.replay_vhs_tape_insert, volume = 0.42f)
    fun play() {
        stopLongForm()
        emit(play)
    }
    fun pause() {
        stopLongForm()
        emit(pause)
    }
    fun tick() = emit(tick, 0.45f)
    fun scrub() {
        stopLongForm()
        val now = SystemClock.elapsedRealtime()
        if (now - lastFastForwardAtMillis < FAST_FORWARD_RETRIGGER_MILLIS) return
        lastFastForwardAtMillis = now
        emit(fastForward, 0.48f)
    }
    fun reset() = playLongForm(R.raw.replay_cassette_rewind, volume = 0.42f)
    fun stop() {
        stopLongForm()
        emit(stop)
    }
    fun release() {
        stopLongForm()
        pool.release()
    }

    private fun emit(sound: Int, volume: Float = 0.7f): Int =
        pool.play(sound, volume, volume, 1, 0, 1f)

    private fun playLongForm(@RawRes sound: Int, volume: Float) {
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

    private companion object {
        const val FAST_FORWARD_RETRIGGER_MILLIS = 900L
    }
}
