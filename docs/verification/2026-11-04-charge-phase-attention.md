# Charge-phase text attention when GO was missed

November 4, 2026.

## Problem and behavior

The reviewed Moondrop recording contains no `MatchStarted` Article. Its QTE
progress-text capture stayed unavailable because registration required the
session's `BATTLE_ACTIVE` phase. The shield-button text fallback independently
required a `MatchStarted`-only boolean. Missing GO could therefore keep both
fallback paths off throughout an otherwise observed battle.

`BattleInProgressAdmission` now reuses the existing source-time combat admission
rules while excluding VS/countdown-only attention. Both active species followed
by a valid persistent HP loss, an established Match Start, or a typed charged
phase can supply the required proof. Species alone, full HP, border flashes,
inconsistent loss measurements, VS, and countdown glyphs cannot admit these
readers. Admission is not a new Match Start and does not prove ordinary Fast
Move play.

`BattlePhaseTextSampling` controls only QTE progress-text capture. It is registered
before that capture, preserves its 500 ms interval, and stops attention at the
result, restart, ignored-screen, session-stop, or observer-stop boundaries. The
canonical session phase and match clock remain independent.

`BattleShieldDecisionTextEvidence` source-checks charge-label readings before
passing them to the existing shield-text recognizer. Typed incoming-attack cues
retain the first supporting label's source time and cite both battle-admission
and label evidence. Cached fragments, ordinary move names, blanks, and labels
without source timestamps are not shield cues. Original Articles are preserved.

## Recorded evidence

A private checker replayed the original relevant typed Articles in publication
order, retaining their envelopes. The source ZIP SHA-256 remained
`79cbaf9c8760c2ae05f72e80178002cd289aa49a7fb715000b7951d091156164`.

- Both active species and a valid player HP loss supply admission at **17.100
  seconds after BattleOverlayOpened**.
- The new attention gate is enabled before delivery of the retained **34.170
  second** incoming-warning crop.
- No `MatchStarted` or `AttackIncoming` Article was inserted into that recording.
- The recorded result closes current attention.

This proves restored sampling eligibility on the recorded inputs. It does not
reconstruct unavailable QTE crops or demonstrate that the live reader recognized
the warning.

## Recognition boundary

The faded warning's original RAW and threshold-185 OCR passes both contain zero
words. The reader-availability repair does not repair that contrast failure.
Private local-contrast treatments make the visible lettering clearer, but no new
image treatment is enabled by this checkpoint. Native OCR verification must
establish recovery of the actual source and rejection of controls before an
image-treatment change is shipped.

Initial native experiments encountered an unavailable optional OCR model on the
API 34 emulator and a low-memory test-process exit during the newer emulator's
startup. These are test-environment failures, not successful recognition or proof
that the original pixels cannot be read. Source images and archives remain
unchanged. A subsequent native run also timed out waiting for model availability;
it produced no OCR result. No image treatment was promoted from these probes.

## Validation

Focused JVM checks cover late entry, full and paired shield labels, source versus
delivery ordering, insufficient battle proof, lifecycle boundaries, cached text,
and the registered witness's custody chain. The full JVM suite passed **1,691
tests across 249 suites**, with no failures, errors, or skipped tests. The final
debug APK built successfully, with SHA-256
`627354e2a150650bd609225f98cc4e3b46e5c9c72ec3034b6c0789105f1527c2`.
A new real battle remains the live acceptance boundary.

The APK was installed and launched on Moondrop `MDPH00124112500414` after
confirming Droidball was stopped. The installed APK hash matches the built APK.
All 338 backed-up settings/collection/reference files remain present; 337 are
byte-identical, with only the Firestore cache changed. Collection, team and
calibration files and the match-archive inventory are unchanged.

One inherited nonblocking limitation remains: a stale charged-completion or
Match Start Article can clear newer shield fragments even when admission rejects
that Article as pre-restart. This can suppress a cue; it cannot create one.
