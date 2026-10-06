# Species checks and playback audio

The live identity target is 1.5 seconds. This is a measured target, not an accuracy guarantee or permission to guess a name.

ML Kit and the species catalog are warmed during observation startup. Small badge crops try the padded treatment first, based on real-device samples; a successfully resolved treatment ends that OCR pass.

Species name strips are sampled every 150 ms during independent player/opponent check windows. VS, each new countdown glyph, entry announcements, cry-candidate evidence, and newly empty HP bars request checks. Repeated cues do not extend an existing window. A new entry can supersede a recovery check. Two agreeing readings from distinct captured frames confirm identity. Disagreements, confirmations, deadline expiry, and delivery to presentation are recorded as SpeciesCheckMeasured articles. The 1.5-second target is reported once as TIMED_OUT, but the same check continues until agreement or a new entry. A slow read is not discarded merely for missing the target. OCR from a superseded window still cannot supply current identity.

A confirmed side rests for five seconds before a recovery check, to recover from missed cues. Unresolved checks continue at the sampling cadence after the missed target. This fallback means identity remains recoverable without making an announcement or an audible cry a mandatory gate. Crop artifacts still reach the Timeline before recognition. Existing evidence is not deleted.

The recorded elapsed interval begins at the triggering article's monotonic capture time (or recovery-window opening); it includes trigger-processing delay. Identity delivery is recorded after updating the HUD presentation state, not after a rendered display frame. A cue may precede readable text, so these figures must not be described as a direct measurement of first-visible-text latency. Testing the 1.5-second live requirement requires comparison with the screen recording as well as these articles.

Fast-move cadence received before initial identity is kept in a bounded live working set and revisited after identification. The original Timeline remains complete. Entry/empty-HP boundaries discard pending attribution to the previous appearance and reject cadence intervals crossing that boundary. Repeated confirmation of the same identity does not reset or recount energy. Timing across missed, ambiguous transitions is not proof of unseen actions.

Audio capture prefers AudioPlaybackCapture filtered to the installed Pokemon GO UID and game/media/unknown playback usages. It uses the existing MediaProjection grant and RECORD_AUDIO permission, with 48 kHz mono PCM16, a 500 ms rolling prebuffer, and 700 ms post-cue audio. Cue arrival governs the rolling window; delayed visual recognition can miss sound outside the prebuffer.

Microphone capture is a fallback only if playback initialization/start fails. A running but silent playback stream remains labeled QUIET_OR_UNAVAILABLE; silence cannot distinguish an idle game, muted effects, or a runtime capture restriction. Every new audio artifact records its capture source and peak level. AudioInputStatus records source and stream state. Older archives default to MICROPHONE and an unspecified level.

Cry candidates remain experimental rankings. Fast-move clips are no longer passed to cry matching. Compact cry-reference envelopes are cached instead of reopening the full reference corpus for every clip. Sound timing uses the actual sample rate. These changes do not establish cry classification accuracy.

Verification: scheduler target reporting, stale-read rejection, independent sides, pre-identity cadence recovery, boundary rejection, PCM signal levels, audio sample-rate timing, and archive compatibility have focused unit coverage. Device OCR tests use preserved Camerupt, Sealeo, and Vaporeon badge samples and the bundled species catalog. Real-match latency and successful Pokemon GO audio capture remain device-session acceptance checks.

## September 27 match review and replay motion

Match `05204400-92b5-4e57-97a4-02bb5a331f35` (full export `2026-09-26_22-55-36.odxmatch.zip`) contained 3,191 crops but zero active-HP crops or fast-move records. Both HP capture witnesses reported unavailable. OCR preserved correct species text, but 98 species windows timed out and none confirmed; hard timeout rejection prevented identity delivery. Playback capture stayed QUIET_OR_UNAVAILABLE. Replay species recovery is not evidence that live identification succeeded.

A missed species latency target now preserves the active check and its agreement history. Accepted charged-move/Get Ready testimony can establish the battle surface independently of countdown recognition. HP trackers and cadence detectors no longer reset on reconfirmation of the same species.

Replay animates each individually timed FastMoveEnergyDerived record with observedCompletedUses=1: an eight-pixel hop and the custom Overdex type icon traveling between sprite centers. Concurrent actions on both sides remain visible. Identification alone, aggregate energy totals, and unknown-type moves do not invent animations. All motion derives from the replay cursor, including paused/scrubbed positions.

This does not establish full fast-move recall: the reviewed match has no HP footage to recover those events from. Cadence detection needs successive pulses/excursions, so the initial hit and capture gaps remain limitations. A new device recording must demonstrate HP crops, cadence measurements, identified moves and individually timed uses before claiming complete attack coverage.

Validation for this change: 31 focused unit tests passed across replay projection, species checks, battle-surface confirmation, fast-move inference and both cadence detectors. Debug APK built successfully. Full per-attack capture coverage remains unverified on a new live match.
