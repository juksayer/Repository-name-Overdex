# QTE-triggered terminal image coverage

Status: implemented in the working tree; focused/full tests and debug build
passed. Installed and launch-verified on the Pixel 4; the Moondrop is unchanged.
Actual sampling coverage, resource cost and KO recognition remain live-device
acceptance questions.

## Problem established by saved evidence

The November 11 21:13 Moondrop match has no opponent terminal-region crops for
Camerupt's final Rock Tomb against Cramorant. Its native team badge disappears
before the conspicuous faint particles; the reviewed recording contains no
native zero-ball frame. The opponent-only badge that reappears is potentially
Overdex's retained image and cannot supply independent count evidence. See the
[native badge review](2026-11-11-final-native-badge-visibility.md).

Waiting for typed move-used testimony is too late in this example. The final
accepted GREAT crop was captured at source +135.722650469 s and its native
recognition was ready at +138.141021316 s. Used was captured at +138.709973239 s
but its typed testimony published near +143.080 s, after its old three-second
capture deadline. There was no usable final Get Ready phase in the archive.
The independent HP reader also reported `NO_REFERENCE`; that is not a reason to
discard a valid request to preserve raw images.

A [six-archive timing audit](/home/sean/overdex-device-backups/2026-11-11-moondrop-2113-review/qte-terminal-capture-timing-analysis.md)
found eleven usable first-archived-grade/Used pairs, with source gaps of
1.677–5.146 s. Typed Used publication missed the prior three-second deadline in
ten pairs. A player NICE followed by opponent Carbink's Rock Slide was excluded
because an incoming-attack phase intervened. These are sparse observed timing
samples, not a guarantee about every animation or network stall.

## Implemented scheduling and ownership

`AcceptedChargeCapturePreparation` now has independent HP and TERMINAL_VISUAL
consumer slots. Both receive the same validated match-owned cue; attaching or
detaching one cannot replace the other. Delivery does not retain a bitmap or
create a callback queue. Reader generation checks reject callbacks from an old
start/stop cycle.

The dedicated player-QTE reader's accepted NICE, GREAT or EXCELLENT cue can open
one `PLAYER_QTE_PROGRESS` window over the opponent receiving territory. It does
not wait for successful HP preparation, canonical accepted-text publication, or
typed Used. Accepted canonical QTE testimony is a fallback and cannot replenish
an existing phase. Unaccepted native OCR variants and the same words from an
unrelated reader do not establish this ownership.

| Budget | Ordinary capture | Player-QTE capture |
| --- | --- | --- |
| Source-clock duration | 3 seconds | 8.5 seconds from the first accepted grade crop |
| Minimum admitted source spacing | 100 ms | 250 ms |
| Maximum attempts | 30 | 34 |
| Maximum age of an individual image | 500 ms | 500 ms |

The QTE budget spans the largest observed grade-to-Used interval plus the
existing three-second terminal tail and a small margin. It is an experimental
collection budget, not an assertion that a grade is QTE completion. A cue
recognized late receives only the remaining original source interval. The
Cramorant cue therefore has a fixed deadline of +144.222650469 s; recognition
does not move that deadline forward. Tests simulate new image receipts within
this interval; they do not recover pixels missing from the original archive.

Repeated grades cannot renew the same window, even after it expires. Used can
close the owned phase without adding another impact window, and a return cue
cannot stack an additional tail onto the QTE budget. A genuinely newer owned
phase can replace the old phase. An intervening incoming attack retires player
QTE ownership so a later opponent Used cannot inherit the wrong recipient.
Without a later phase/Used boundary, a missing Used is not permission to reopen
the same phase indefinitely from more grades.

Appearance boundaries remain source-timed: an earlier identity delivered late
does not retire an already newer QTE window, while a genuinely newer player or
opponent appearance does. Stop, ignore, match end, or a valid coordinate-space
change closes the assignment. Future/pre-session image timestamps cannot
change dimensions or establish new boundaries. Expiry/end also marks capture
unavailable without requiring another frame to arrive. Old worker callbacks
cannot republish availability or crops into a restarted observer.

## Custody and measured costs

Image preservation remains synchronous crop/PNG work on the auxiliary frame
collector, with no queue of frames awaiting encoding. This change does not
claim faster PNG encoding, smaller images, or a resolved storage bottleneck.
Successful actual crops continue through `CropCaptured`; the neutral sequence
summary cites the published crop articles and their original source clocks.
The shared sequence payload accepts up to 34 frames for the new QTE case;
ordinary gate assignments remain capped at 30.

One cumulative `TERMINAL_VISUAL_CAPTURE_DIAGNOSTICS` raw record is emitted for
each tracked closed window. It records:

- Received, admitted, rejected, stale and out-of-window receipts; work started,
  completed, pending and peak in-flight counts.
- Forward source receipt gaps, source reorders, same-stream publication
  advances/skips, and unknown publication metadata.
- Retained PNG count/bytes and preserved-source spacing, with ordered accounting
  even if completion callbacks are reordered.
- Total/maximum attempt, crop-copy and PNG-preservation elapsed costs, plus
  thread CPU cost or an explicit unavailable count.

Retained PNGs are distinct from published `CropCaptured` articles; the existing
sequence summary reports actual published images. A zero-receipt window is
still diagnostic evidence and uses its opening source clock rather than an
invented image time. Ordinary drainage waits for pending work. Forced shutdown
labels a partial record and incomplete gap accounting explicitly. The tracker
has bounded numeric state and compact pending IDs, not retained source images.

The old archive preserved only two player terminal crops, 1.710248 s apart. That
does not identify encoding as the sole cause. Requested 250 ms spacing is a
maximum attempt rate, not evidence of achieved 4 Hz. New measurements must show
whether delivery, unreadability, copying, preservation, or contention limits
the actual retained sequence.

## Verification and remaining acceptance

The implemented tests cover the exact saved Cramorant cue clocks, fixed source
deadlines, the 34-attempt ceiling, duplicate grades, canonical fallback,
Used-first delivery, the Carbink opposite-phase counterexample, late identity
delivery, true appearance changes, coordinate changes, future/pre-session
frames, and lifecycle restart. Consumer tests cover independent replacement,
detach, closure and callback failure. Diagnostic tests cover zero/partial
windows, reordered/concurrent completion, failed captures, bounded storage and
distinct retained-versus-published stages.

| Check | Current evidence |
| --- | --- |
| Source review of three lifecycle/ordering findings | Corrected: obsolete availability, expiry/end availability, and invalid dimension timestamps |
| Focused test execution | Passed: 80 tests in 7 suites |
| Full unit suite | Passed: 2,777 tests in 365 suites; 0 failures, errors or skips |
| Debug APK build | Passed; combined full-suite/build run completed in 1m 27s |
| Device installation of this change | Pixel 4 installed and launch-verified at 2026-11-11 22:47:20.888790 −06:00; installed APK hash matches the verified build |
| Real-match capture continuity and cost | Not yet measured |
| Automatic shrink/departure/faint classification | Not implemented by this change |

Durable execution logs are the [focused log](/home/sean/overdex-device-backups/2026-11-11-pixel-qte-terminal-update-224621/overdex-qte-capture-focused.log)
and [full-suite/build log](/home/sean/overdex-device-backups/2026-11-11-pixel-qte-terminal-update-224621/overdex-qte-capture-full.log).
Full-suite counts were independently checked
against `app/build/test-results/testDebugUnitTest/TEST-*.xml`, and the full log
ends with `BUILD SUCCESSFUL in 1m 27s`. The durable
[build verification](/home/sean/overdex-device-backups/2026-11-11-pixel-qte-terminal-update-224621/build-validation.json)
records those totals and the built artifact hash.

The verified `app/build/outputs/apk/debug/app-debug.apk` is 276,775,438 bytes;
SHA-256:
`7db1b5d29f48527ea53f8f79e221cff949fae2b611c81d1ada87e2f8ac7be6eb`.
The Pixel 4 (`99261FFAZ0017J`) received this build using `install -r`, without
data clearing. The installed APK hash matches the above hash, as recorded in
[installation evidence](/home/sean/overdex-device-backups/2026-11-11-pixel-qte-terminal-update-224621/installation.json).
Launch succeeded with PID 15519, no added crash-buffer entries, and a
[settled launch screenshot](/home/sean/overdex-device-backups/2026-11-11-pixel-qte-terminal-update-224621/pixel-launch-settled.png)
showing the normal ODX-Fi home menu, including Droidball, Overdex, current team,
and match history.

The scoped pre-install backup was fully read/hash verified: 111 files,
including 32 match archives. Its
[scope record](/home/sean/overdex-device-backups/2026-11-11-pixel-qte-terminal-update-224621/backup-summary.json)
covers records, archives, calibration, settings, databases and small metadata;
the loose `files/artifacts` store remained untouched on the phone. The
[post-install comparison](/home/sean/overdex-device-backups/2026-11-11-pixel-qte-terminal-update-224621/retention-check.json)
found all 111 protected files byte-identical before launch, with no added,
missing or changed files. This check is scoped to those files.

Droidball was not started and no live match was performed for this installation.
The Moondrop remained disconnected and unmodified.

A real match must still demonstrate sufficient actual frames across baseline,
hit/camera transition, shrink and departure, alongside acceptable frame supply,
CPU and archive size. A stalled charge can exceed this budget, and a 250 ms
sampler can miss a briefer transition. Neither tests nor requested cadence prove
that the final KO is now observed.

This work adds no HP value or damage amount, invents no faint, and rewrites no
original archive entry. Living recall, ordinary camera scaling, Shadow aura,
occlusion, and fading native UI remain counterexamples that a future visual
classifier and evidence-combination rule must handle independently.
