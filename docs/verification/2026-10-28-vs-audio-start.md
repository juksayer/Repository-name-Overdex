# Audio matching from VS — October 28, 2026

The user requested that audio matching begin at the VS screen. A preserved
`VsScreenWitnessed` now opens FastMoveListeningGate at that source frame's
monotonic time. Its article ID is retained as the audio capture's trigger.
Listening continues through opponent waiting, entry cries, and countdown.
Existing countdown, confirmed-combat, and charged-phase fallback openers remain.

The Fast Move identity consumer deliberately excludes VS and countdown openers
from its separate combat gate. Prebattle sounds can therefore be retained and
compared without becoming equipped-move identity, performed attacks, or energy.
Later identity still needs qualifying combat evidence and its existing repeated
sound, ownership, and phase checks. Prebattle matches do not corroborate a later
battle identity. No MatchStarted or GO event is fabricated.

## VS capture must remain available before battle

The previous capture wiring allowed seeking Team Select, accepted Team Select,
and calibration, but excluded navigation idle and the manually armed HUD state.
In the paired recordings reviewed in
[the preceding review](2026-10-28-missed-countdown-audio.md), VS capture stopped
before the actual VS screen appeared. Adding an audio opener alone would not
repair that gap.

VS capture now uses the session's explicit prebattle availability rule, including
navigation idle and manually armed countdown. It retires once a battle surface
is established, combat begins, or the session ends. It does not keep adding VS
OCR work throughout the fight. Existing crop coordinates are unchanged.

## Validation

The audio gate, real reference-audio custody test, and downstream identity test
verify VS activation, retained provenance, no prebattle attacks/energy, rejection
of pre-restart VS evidence, and result closure. Session checks cover the added
prebattle states and retirement conditions.

- Focused audio checks: 84 tests; focused session/surface checks: 16 tests.
- Full suite: **1,243 tests across 206 suites**, zero failures/errors/skips.
  Debug build and `git diff --check` passed.
- Debug APK SHA-256:
  `e2019acb94624df3acc39620fa2520f66f00bd1faf947e0b3ea618f308ba9530`.
- Moondrop update returned installer **Success** before the user disconnected
  it for personal use. Post-install hash/settings/launch checks were not
  completed; the phone was left with the user.
- Pixel started a new recording session, so it was not interrupted or updated.
  Its installed hash remains `a596567260b06b1f28bcd212d225284a6373e5fb1c3d09d6cc38d1f3eefcddb5`
  (the preceding combat-fallback build). Its subsequent Candela match therefore
  cannot validate this VS-start change.

Logs and install-state evidence:
`/home/sean/overdex-device-backups/2026-10-28-vs-audio/`.

Live recognition of VS in a new Pokemon GO match remains the acceptance boundary.
These tests do not establish the new detector's on-phone recognition latency.
