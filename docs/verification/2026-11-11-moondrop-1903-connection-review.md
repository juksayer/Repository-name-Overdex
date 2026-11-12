# Moondrop evening matches: recording suitability and KO evidence

## Sources and validation scope

User video: `/home/sean/Desktop/Screen_recording_20261111_190345.mp4`.
Both matches were played after the Moondrop update at 18:48:51, with installed
APK provenance recorded in the [preceding review](2026-11-11-moondrop-ko-hp-review.md).
The device remains on that update. Read-only archive copies, device-matching
SHA-256 hashes, full artifact verification, and detailed video/archive reports
are preserved in `/home/sean/overdex-device-backups/2026-11-11-moondrop-1903-review/`.

| Match record | ID | Articles / verified artifacts | Recorded player counts | Named faint Articles | Result |
| --- | --- | --- | --- | --- | --- |
| 19:02:19 | bb769d90-6c82-4bd4-ae81-1a620c069c83 | 3,547 / 419 | 3 → 2 → 1 | Turtonator, Camerupt | LOSS |
| 19:06:50 | e6fa7745-8827-4fd0-a4ec-659d1bbf7ad7 | 3,492 / 412 | 3 → 1 | None | LOSS |

Both archives pass structure and artifact-hash verification. Neither original
archive was altered. This review changes documentation only; it does not install
another build or rerun unchanged app tests.

## What is usable

The video includes both complete matches and explicit GOOD EFFORT screens.
Approximate video-time player faint sequences are:

| Match | Pokémon | Video time | Visible context |
| --- | --- | --- | --- |
| 1 | Turtonator | 52–53 s | Ordinary play, shrinking/disappearance and replacement prompt |
| 1 | Camerupt | About 140 s | After Ninetales's Weather Ball |
| 1 | Gourgeist | About 170 s | After Ninetales's Weather Ball |
| 2 | Turtonator | About 252 s | After Stunfisk's Mud Bomb |
| 2 | Gourgeist | About 324 s | After Pelipper's Weather Ball |
| 2 | Camerupt | About 358 s | After Stunfisk's Mud Bomb |

No opponent faint is visible. Altaria → Ninetales and Stunfisk → Pelipper →
Stunfisk are living switches; opponent counts remain three. At video 346.044 s
and 354.753 s, native-resolution images show a very thin red sliver in
Stunfisk's bar. It later attacks. Downsampled contact sheets hide the sliver:
these frames must not be labeled empty HP or used as zero-HP fixtures.

The first HUD visibly expands around video 22.289 s after GO at 19.851 s, with
no visible tap on the collapsed ball in the inspected frames. The second HUD
is already open during countdown 3 at 208.979 s, before GO at 211.063 s.
Both archived overlay-open events cite VS_SCREEN, so this footage supports
visible presentation but does not independently isolate the new Match Start
fallback branch. No Replay is opened in the recording.

## Concrete remaining gaps

The charged-impact reader captured real centered EMPTY player HP during the
second Gourgeist's faint: Article `9d77191a-675b-4122-b600-bf7ed49bea28`, source
Match +112.623 s, published 0.791 s later. Player count one is independently
confirmed by frames at +114.912 s and +115.693 s. The preceding recorded count
is three; the intermediate two was missed.

[TeamLossResolver](../../app/src/main/java/com/example/overdex/battle/inference/TeamLossResolver.kt)
currently enumerates only count decreases of exactly one. Consequently this
confirmed two-loss interval remains unresolved and produces no named faint
Articles. [LiveTeamLossHistory](../../app/src/main/java/com/example/overdex/battle/inference/LiveTeamLossHistory.kt)
uses positive HP for survival exclusion; EMPTY is not an attribution input.
The next attribution change should retain the two-loss interval and resolve
only owners supported by independent history. It must not invent the missing
count-two observation, an individual faint order, or exact earlier KO times.

Final-hit HP is still missing in both matches. The first final Gourgeist reading
is about 18.5% at Match +147.152 s; the second final Camerupt reading is about
72.0% at +143.987 s. Neither archive contains the final zero/count-zero/faint.
The final charged announcements were published 6.142 s and 4.756 s after their
source images; the first final incoming cue took 9.508 s. These measured local
delays merit a separate reader-timing investigation. They do not establish that
the game's network connection caused the missed observations.

## Recording recommendation

Keep and use this recording before requesting another. It establishes a real
centered-zero capture, correct first-match faint ownership, an aggregate-loss
attribution gap, missed final-hit HP, and valuable living-switch/tiny-HP
counterexamples. A cleaner connection cannot by itself fix the resolver's
single-loss restriction.

The 816×1808 recording has 3,250 frames across about 368.6 seconds, averaging
8.82 frames/s. Median frame spacing is 106 ms and the largest gap is 436 ms.
There is no audio stream. This limits subsecond move/HP comparison and prevents
cry/audio validation; it does not make the visible faint sequences unusable.
After addressing the known gaps, one smoother recording with game audio, if
available, would be useful for timing acceptance. Network-like spinner frames
are visible, but exact outage durations and causation are not established here.
