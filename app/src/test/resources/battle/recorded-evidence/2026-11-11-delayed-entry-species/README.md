# Delayed entry after completed species checks

This is an immutable excerpt from the second Moondrop match in the user's
`Screen_recording_20261111_190345.mp4` recording, Match
`e6fa7745-8827-4fd0-a4ec-659d1bbf7ad7`. It records a delayed `Go, Stunfisk!`
announcement reopening both species readers after newer badge images already
confirmed Camerupt and Stunfisk.

`articles.json` contains 39 original Article objects in archive order, including
the complete transitive `evidenceReferences` and `predecessorIds` dependencies.
Each object is copied as a literal JSON substring. The array wrapper is new;
this is not a complete match archive. `provenance.json` supplies roles, clocks,
original archive/timeline hashes, fixture hash, and each original object hash.
No images were copied. Original artifact paths, hashes, dimensions, and OCR
word boxes remain in the Articles.

## Measured sequence

Times below are seconds after the original monotonic Match Start
`10825626046183ns`. Image capture times and processing times are different clocks
in the evidence chain; a coordinator diagnostic's monotonic time records its
processing, not the image it confirmed.

| Event | Image/cue source time | Coordinator processing time | Evidence |
| --- | ---: | ---: | --- |
| Prior charge starts check windows 49/50 | 126.572072 | 135.912518 / 135.912675 | `precedingCharge`, `precedingPlayerWindowOpened`, `precedingOpponentWindowOpened` |
| Player badge confirms Camerupt, window 49 | 138.008671 | 139.926966 | `playerCoveringBadgeCrop`, `playerCoveredWindowConfirmed` |
| Opponent badge confirms Stunfisk, window 50 | 138.763102 | 141.054145 | `opponentCoveringBadgeCrop`, `opponentCoveredWindowConfirmed` |
| Delayed `Go, Stunfisk!` opens windows 51/52 | 131.581772 | 143.134599 / 143.134713 | `delayedEntryAccepted`, `redundantPlayerWindowOpened`, `redundantOpponentWindowOpened` |
| Opponent reread confirms the same Stunfisk | 143.652308 | 145.520420 | `redundantOpponentBadgeCrop`, `redundantOpponentWindowConfirmed` |
| Player reread confirms the same Camerupt | 145.256779 | 146.315968 | `redundantPlayerBadgeCrop`, `redundantPlayerWindowConfirmed` |
| Final Mud Bomb independently opens charge checks 53/54 | 142.727025 | 147.488563 / 147.488639 | `finalChargeUsedTyped`, `finalChargePlayerWindowOpened`, `finalChargeOpponentWindowOpened` |

The covering confirmations cite their original badge crops. They need not have
separate repeated species Articles: the live pipeline accepts an unchanged
species to complete its check, while suppressing duplicate OCR signatures and
duplicate typed identities. This is why a scheduling regression must use the
accepted crop's capture time, not only the latest emitted species Article.

## Minimal regression

1. Open player/opponent windows using the preceding charge and their recorded
   processing times. Retain the recorded expiry before late recognition.
2. Accept the two covering badge reads using their **capture** clocks and exact
   crop IDs at the recorded confirmation processing times.
3. Deliver the older entry announcement at its recorded later processing time.
   Both sides have newer accepted badge coverage; it must not start another
   species assignment or clear their resolved identity.
4. Deliver the later genuine charge separately. Its source time is after both
   covering badge images, so it remains eligible to request new checks.

Keep separate synthetic tests for one-side-only coverage, a genuinely newer
entry/faint/switch, and an old badge result completing after a newer unresolved
appearance. This recording does not establish those alternate sequences.

## Queue evidence and limits

The final Mud Bomb announcement records `queueMs=3983`, `nativeReadMs=394`, and
`totalMs=4564`. Its estimated admission is Match +146.828025s, calculated from
the source clock plus `captureToAdmissionMs`. The redundant opponent read was
admitted around +144.572308s (776ms native, 941ms processing); the redundant
player read around +145.801779s (356ms native, 494ms processing). Thus both ran
while the final announcement waited. Their combined native time is 1.132s and
combined reserved processing time approximately 1.435s. Millisecond duration
truncation prevents claiming nanosecond precision for these derived times.

Species recognition uses the shared urgent OCR lane. Announcement recognition
uses the transient lane; active reads are not preempted and the scheduler has
bounded urgent bursts. Removing an already fulfilled assignment is narrower
than changing global priority, but this excerpt does not prove all of the
announcement's delay came from these two jobs or that removing them alone
would recover the missing final HP/faint. The subsequent no-shields prompt
handoff also supplies a genuine newer phase cue and may change future workload.
No missing terminal HP value, faint, or new result has been added here.
