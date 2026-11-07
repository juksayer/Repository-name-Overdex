# Blanche recording: retained faints and missing finishing attacks

November 7, 2026. The Moondrop Blanche recording supplies the first live review
after midpoint-first HP acquisition and fill-consistency checks. The user's
Replay reports exposed two separate projection failures: verified late faints
were discarded, and accepted QTE progression text was ignored. Both repairs
preserve the original archive. Missing finishing attacks remain capture gaps.

## Original evidence

- Match `a04cb9fd-a9f1-4709-9ce3-e1d5b5f29080`, 11:45:33 local time.
- ZIP size 6,575,486 bytes; phone/desktop SHA-256 agree:
  `e822bf03f5a9f847fa2bc9d67586733c7e9aad114da330a256a3ee35491dddef`.
- All 218 declared artifacts verify; 1,609 Articles and 26 HP summaries.
- Installed capture APK matches the previously tested
  `90ecb1227a12835779e54effd79d46ab9b0042ac64ef1a2e4c18e4b11f3f73b8` build.
- Supporting `Screen_recording_20261107_114543.mp4`: 816 × 1808, 124.5074 s,
  2,582 variable-rate frames averaging 20.7377 FPS. No audio track; the other
  tracks are Android metadata. The archive independently retains 36 audio clips.
- Actual GO/MatchStarted source: `133952701416983` ns. The archive observes GO
  about 0.71 s after its first external-video appearance, within that same glyph
  episode. It does not reproduce the previous missing-start failure.

Private original media, hashes, source crops, PTS-aligned native frames, metrics,
and verification logs remain under
`/home/sean/overdex-device-backups/2026-11-07-blanche-review/`.

## Why intermediate faints disappeared

| Outgoing → replacement | Replacement source after GO | Faint source after GO | Delay |
| --- | ---: | ---: | ---: |
| Sneasel → Sealeo | 18.681 s | 22.844 s | 4.163 s |
| Sealeo → Vaporeon | 50.927 s | 53.431 s | 2.504 s |

Both named faint Articles cite the proper opponent identity and Poké Ball count
decrement. `ReplayKnockoutTrack` previously discarded an outgoing actor's faint
when it arrived more than two seconds after the replacement identity. The user
therefore saw replacements without either recorded fall.

The existing short-delay rule remains. A longer delay now qualifies only with
direct custody links to the immediately outgoing appearance's same-side species
identity and exactly two valid same-side, maximum-three Poké Ball readings that
decrease by one. The earlier reading must precede replacement within that
appearance; the later reading must have the faint's original source time. Other
sides, shields, multiple-ball losses, mismatched clocks/matches, stale previous
appearances and ambiguous IDs cannot qualify. A stable unchanged count after
replacement preserves a voluntary switch instead of retrospectively calling it
a knockout.

The supported fall is fitted into the 650 ms before the original replacement,
with provenance retained. Original faint clocks, identity arrival, resource
counts and Articles remain unchanged. Camerupt's voluntary switch is preserved.
This repairs visibility of recorded faints; it cannot supply an unrecorded
finishing Fast Move or charged strike.

## Why the ending became a long stare

Last accepted HP is record-relative source 105.006991621 s: Camerupt 39.893618%,
Vaporeon 49.20635%. Recorded WIN is 124.204141546 s, a 19.19715 s gap.

Retained images align external video to record-relative source by
`source ≈ video + 11.427 s`, conservatively ±0.04 s. Native PTS confirms:

| Final real event | Video bracket |
| --- | --- |
| Charged controls fade | 92.539756–92.595567 s |
| Get Ready appears | 93.676944–93.729611 s |
| Earth Power clearly legible | 100.266367–100.300244 s |
| Ground impact | 102.023667–102.419344 s |
| Vaporeon collapse/disappearance | 102.527544–103.030811 s |
| First You Win | 106.560278–106.683200 s |

The archive has only one named charged announcement despite four player charged
sequences in the video: two Brutal Swings, Dragon Pulse and Earth Power. Its last
retained Announcement sample at source111.433594 precedes the readable Earth
Power banner; the next at118.536123 follows the attack and faint. This establishes
a retained-sample gap, not a proven OCR rejection of readable Earth Power pixels.
Compact exports can omit uncited captures, so retained sample counts alone do
not prove the entire live frame stream stopped.

The final NICE is positively retained at source109.421899083 s, Article
`ff83b1c3-6d42-49ad-860f-fb36ffc584e0`, with accepted OCR and cited crop. Replay
ignored that evidence because it recognized Get Ready and shield prompts but
not QTE progression as preparation cues.

## QTE progression repair

Exact NICE/GREAT/EXCELLENT tokens from the accepted dedicated progression-text
stream now establish **player QTE in progress at their actual source times**.
OCR-raw/HUD/unrelated text cannot qualify. Replay shows the actual grade and
charge focus without inventing an earlier Get Ready, earlier grades, physical
haptics, selected move, type, energy spend, impact or damage.

Repeated grades merge into an existing episode; they do not extend its original
ten-second open bound. A later named charge may close that episode. An accepted
ordinary attack, replacement, fresh preparation, opponent charge or result
prevents carrying old progress into a later episode. The first ordinary action
is retained without a fictional strike tail. A grade briefly lingering over a
completed move banner does not reopen a second QTE.

The final Earth Power name, its damage and the final faint still lack archive
testimony. NICE cannot manufacture those facts. Result-only defeat still uses
the neutral end pose and hides stale HP rather than supplying a zero-HP sample,
Poké Ball decrement or knockout witness. No external-video events were inserted
into the original Timeline.

## HP acceptance and remaining performance work

All **188 accepted readings** reached the Timeline: 91 player + 97 opponent,
with no final pending submissions. The opening's nine accepted pre-GO samples
are near 99%; the earlier countdown-fragment ~20% false loss does not recur here.

This battle's 259 locator passes average 348.4 ms elapsed / 104.5 ms thread CPU,
with 153 recovery passes. Geometry remains 92.43% of locator CPU; total monitor
admission is 13.875 ms. Enabled feed gaps are 847/1,105 (76.65%). Different battle
phases, pre-GO work and transitions confound whole-session comparisons with the
prior recording. Every five-second diagnostic window crosses a phase boundary;
strict ordinary timing aggregates therefore remain unavailable. Neither a
midpoint speedup nor its causal regression is established.

Video shows a centered opponent bar during the final impact without accepted
geometry. Recovery already scans the full width, so saved crop X cannot alone
explain the miss. Frozen-frame detector behavior, source sampling and the
500 ms centered-ownership limit remain separate investigation targets.

The read-only species-location analyzer associates 72/188 source samples into
five independent side-specific profiles: player Turtonator (20), Camerupt (22);
opponent Sneasel (8), Sealeo (13), Vaporeon (9). Unknown phase and missing/freshness-limited
identity still abstain; these are not automatically applied home-position presets.

## Validation and installation

All **1,948 unit tests pass**, with no failures, errors or skips. The final app
and Android-test APKs build. Tests include both actual late Blanche faints,
mirror sides, rejected resource/identity links, voluntary switches, returning
actors, source-time preservation, grade episode boundaries, no invented impacts
or energy, lingering banner text, and archive immutability.

The reduced fixture retains 18 exact original Articles and no media. Separately,
the emulator render test uses the **full 1,609-Article archive**, with production
ReplayBattlefield/sprite assets, and reports **OK (1 test)**. Both intermediate
faints render before replacement, Camerupt's actual NICE is visible, and the
unobserved final faint/impact remain absent. The rendered sheet is retained
privately as `replay-blanche-evidence.png`. This is recorded-match replay
acceptance, not live capture accuracy proof.

The tested app was installed on the stopped Moondrop with `adb install -r` and
launched successfully. Its installed hash matches the tested APK:
`672d952eba976975c0ba0b1f34afcf142cac71e46b0d8be91e1f5e51dfbf88bc`.
The Android-test APK hash is
`1b96be3cf79d898c27d9d460b78c19a6b54ab7616c92137f0d81aaad0e66705d`.
No instrumentation was run on the user's phone. The disposable emulator was
stopped afterward.

Readable before/after backups each contain 405 files / 326,533,120 tar bytes,
covering databases, preferences and app files except the existing loose artifact
store (`files/artifacts`, left untouched). All 403 other files are byte-identical;
only the Firestore cache and installation profile marker changed. No files were
added or removed from that inventory. Teams, owned cards, calibration, preferences
and saved match archives are preserved. Detailed APK hashes, inventory comparisons,
render output and test logs remain in the private review folder.
