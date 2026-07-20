package com.example.overdex.battle.timeline.observer

/**
 * Defines the physical or digital origin of an observation within the battle timeline.
 */
enum class ObservationSource {
    /** Visual evidence from screen capture. */
    SCREEN_CAPTURE,
    /** Audio evidence from microphone or system sounds. */
    AUDIO_CAPTURE,
    /** Data from the Droidball background service. */
    DROIDBALL,
    /** Evidence shared by a remote battle partner. */
    REMOTE_PARTNER,
    /** Data derived by the system's reasoning engine. */
    SYSTEM_INFERENCE,
    /** Manual observation entered by the trainer. */
    TRAINER,
    /** Data provided by a tournament organizer or external referee. */
    ORGANIZER,
    /** Internal system-generated event. */
    SYSTEM
}
