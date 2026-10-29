package com.example.overdex.media

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.example.overdex.R

/** Short presentation effects owned by the process, independent of any Activity. */
object DroidballJourneySoundPlayer {
    private const val ROLL_GAIN = 0.35f
    private const val SKID_GAIN = 0.40f
    private const val IMPACT_GAIN = 0.45f

    private var soundPool: SoundPool? = null
    private var rollSoundId = 0
    private var impactSoundId = 0
    private var skidSoundId = 0
    private var rollReady = false
    private var impactReady = false
    private var skidReady = false
    private var rollRequested = false
    private var rollStreamId = 0
    private var accentStreamId = 0

    /** Warm local samples without retaining the caller's Activity or playing audio. */
    @Synchronized
    fun prepare(context: Context) {
        if (soundPool != null) return

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            // Keep Overdex's presentation effects out of playback capture for GO recognition.
            .setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_NONE)
            .build()
        val pool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(attributes)
            .build()
        soundPool = pool
        pool.setOnLoadCompleteListener { loadingPool, sampleId, status ->
            synchronized(this) {
                if (soundPool !== loadingPool) return@synchronized
                when (sampleId) {
                    rollSoundId -> {
                        rollReady = status == 0
                        playRequestedRoll()
                    }
                    impactSoundId -> impactReady = status == 0
                    skidSoundId -> skidReady = status == 0
                }
            }
        }
        val applicationContext = context.applicationContext
        rollSoundId = pool.load(applicationContext, R.raw.droidball_roll, 1)
        impactSoundId = pool.load(applicationContext, R.raw.droidball_edge_impact, 1)
        skidSoundId = pool.load(applicationContext, R.raw.droidball_skid, 1)
    }

    /** Loop only while the current movement still requests a roll. */
    @Synchronized
    fun startRoll() {
        rollRequested = true
        playRequestedRoll()
    }

    @Synchronized
    fun stopRoll() {
        rollRequested = false
        if (rollStreamId != 0) soundPool?.stop(rollStreamId)
        rollStreamId = 0
    }

    /** An unloaded impact is dropped: a late thunk would no longer match the contact. */
    @Synchronized
    fun impact() {
        if (impactReady) playAccent("EDGE_IMPACT", impactSoundId, IMPACT_GAIN)
    }

    @Synchronized
    fun skid() {
        if (skidReady) playAccent("SKID", skidSoundId, SKID_GAIN)
    }

    /** Cancel pending playback as well as any active movement or impact effect. */
    @Synchronized
    fun stop() {
        stopRoll()
        if (accentStreamId != 0) soundPool?.stop(accentStreamId)
        accentStreamId = 0
    }

    private fun playAccent(name: String, soundId: Int, gain: Float) {
        val pool = soundPool ?: return
        if (accentStreamId != 0) pool.stop(accentStreamId)
        accentStreamId = pool.play(soundId, gain, gain, 1, 0, 1f)
        Log.d("DROIDBALL_SOUND", "$name playback accepted=${accentStreamId != 0}")
    }

    // Every caller holds this object's monitor, including SoundPool's load callback.
    private fun playRequestedRoll() {
        if (!rollRequested || !rollReady || rollStreamId != 0) return
        rollStreamId = soundPool?.play(rollSoundId, ROLL_GAIN, ROLL_GAIN, 1, -1, 1f) ?: 0
        Log.d("DROIDBALL_SOUND", "ROLL playback accepted=${rollStreamId != 0}")
    }
}
