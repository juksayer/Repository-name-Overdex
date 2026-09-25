package com.example.overdex.battle.replay

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.example.overdex.R

/** Original, local transport sounds for replay interaction; never Match evidence. */
class ReplayTransportSounds(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build()
        )
        .build()
    private val play = pool.load(context, R.raw.replay_transport_play, 1)
    private val pause = pool.load(context, R.raw.replay_transport_pause, 1)
    private val tick = pool.load(context, R.raw.replay_transport_tick, 1)
    private val reset = pool.load(context, R.raw.replay_transport_reset, 1)
    private val stop = pool.load(context, R.raw.replay_transport_stop, 1)

    fun play() = emit(play)
    fun pause() = emit(pause)
    fun tick() = emit(tick, 0.45f)
    fun reset() = emit(reset)
    fun stop() = emit(stop)
    fun release() = pool.release()

    private fun emit(sound: Int, volume: Float = 0.7f) {
        pool.play(sound, volume, volume, 1, 0, 1f)
    }
}
