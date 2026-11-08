package com.example.overdex.media

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.example.overdex.R

/** A quiet, local paper rustle. The journal owns and releases this player. */
class FieldNotesPageTurnSound(context: Context) {
    private val lock = Any()
    private var released = false
    private var loaded = false
    private val pool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                // Presentation audio must not become input to battle recognition.
                .setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_NONE)
                .build(),
        )
        .build()
    private val soundId: Int

    init {
        pool.setOnLoadCompleteListener { _, _, status ->
            synchronized(lock) { if (!released) loaded = status == 0 }
        }
        soundId = pool.load(context.applicationContext, R.raw.field_notes_page_turn, 1)
    }

    fun turn() = synchronized(lock) {
        // Drop an early request instead of playing it late on the wrong page.
        if (!released && loaded) pool.play(soundId, 0.35f, 0.35f, 1, 0, 1f)
        Unit
    }

    fun release() = synchronized(lock) {
        if (!released) {
            released = true
            loaded = false
            pool.release()
        }
    }
}
