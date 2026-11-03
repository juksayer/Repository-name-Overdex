# Replay charge focus, phase cues and projectile depth — November 3, 2026

## Result

Replay uses either side's recorded CHARGING interval for player-forward framing:
player scale 1.20, opponent scale 0.85, player shift +20 physical pixels and
opponent shift −10 physical pixels. The existing 250 ms cursor-driven edge ramp
applies to position and scale; pause and reverse scrub reproduce the same pose.
A missing or unknown charge side does not start the effect.

The opponent scales about the visible top of its artwork, accounting for PNG
alpha padding and ContentScale.Fit letterboxing. This preserves head height as
it shrinks instead of pivoting about the image center. Existing attack rises
remain independent; KO animation keeps its original center pivot. Source sprite
files, sprite boxes, HP layout and team resources are not rewritten.

Fast and charged type glyphs render before the combatant sprite layer in both
directions. Actual body alpha masks incoming icons; transparent gaps around
limbs and tails continue to reveal the projectile. Incoming Fast Move paths now
end at the player's lower rear-sprite plane (24 dp plus 25 physical pixels),
rather than above the player's head. Fast projectile accessibility bounds follow
the same rendered position. Attached charge glyphs follow their performer's
horizontal focus shift and retain their original charge/flight animation.

## Charge boundaries and live capture

The model already begins CHARGING at recorded Get Ready / Attack Incoming cues.
The old UI selected only PLAYER preparations for the focus effect. It now uses
either known side, beginning its ramp at the source-time cue rather than waiting
for a named move's release announcement.

Replay also accepts explicit `Use a Protect Shield?` and
`No Protect Shields remaining!` text from the retained announcement/result text
sources, retaining `SHIELD_PROMPT` provenance and the original cue time. Generic
shield mentions, unrelated OCR sources and silence are not start evidence. Open
preparations remain bounded; no completed attack is invented from a prompt.

The separately calibrated QteProgressTextCrop previously had capture wiring but
no OCR consumer. It now uses the existing verified-image reader under a distinct
QTE_PROGRESS_TEXT_WITNESS source at the existing 500 ms capture cadence. Only
the early Get Ready / incoming-warning consumers accept that text lane. Species
and charged-move-used readers remain on the original announcement lane. Raw
Nice / Great / Excellent text is retained; grades do not invent a Get Ready
instant. Overlapping crop readings cannot repeatedly rearm the same visible cue.
All source capture times and crop/raw-OCR predecessor references are preserved.

## New match review and limitations

Copied the six new Moondrop archives since November 2 evening without changing
the phone originals. All ZIPs and **2,434 declared artifact hashes** verified,
covering 21,526 Articles. Sources, review sheets and logs remain private under:

`/home/sean/overdex-device-backups/2026-11-03-replay-charge-focus/`

None of those six archives contains typed Get Ready, Attack Incoming or a
charged-move-used/energy-spent event. Their announcement OCR is mostly empty.
Retained QTE images in the 19:22 battle visibly cut off the upper halves of
NICE / GREAT / EXCELLENT: AnnouncementCrop is y467..539 in a 672×1493 capture.
This confirms a crop problem; it does not prove an exact unrecorded transition.
The dedicated QTE band uses its saved/default calibration and is now actually
read. No saved calibration profile was silently moved.

Explicit no-shields text retained by the result reader can support some older
opponent preparations. No new player start cue is fabricated for these archives.
The newest Rocket archive also contains raw `Grunt Defeated` without MatchEnded;
that separate result-reader issue remains outside this replay-framing change.

A prior October 28 recording contains both sides' cues: Attack Incoming → Grass
Knot is about 3.16 seconds, and Get Ready → Iron Head about 6.37 and 8.04 seconds.
These illustrate why preparation needs to start from the early cue rather than
only the named-use article. They are not a reason to widen the existing bounded
association window without evidence.

## Verification and installation

- Full Kotlin/JVM suite: **1,630 tests across 245 suites**, zero failures,
  errors or skips in the final run. Debug and debug-test APK builds passed.
- API 34 `Overdex_API34_Check`: **OK (5 tests)**. Two new rendered cases use real
  padded Raichu/Combusken art, check +20/−10 physical pixels, visible-head anchor,
  growth/shrink, reverse restoration, and both incoming projectile kinds. The
  opaque-body test derives its mask from the source alpha, excluding translucent
  filtered edges. Three existing charge-type tests also pass, including two
  recorded archive fixtures and deterministic scrubbing.
- New model and custody tests cover both early-cue sides, delayed move names,
  explicit shield prompts, bounded incomplete preparations, unrelated-text
  rejection, cross-crop deduplication, and original source time/reference chains.
  Those routing tests do not measure native OCR accuracy on new battle images.
- Installed on Moondrop `MDPH00124112500414` after confirming no Droidball service
  was running. The installed APK hash matches the tested artifact. Directory,
  Match Summary and recorded-match inspection opened; no phone instrumentation
  or new live battle was performed.
- Readable critical-state backup preceded installation. All 17 team/calibration/
  HUD settings stayed identical. Logical rows stayed identical: 1,026 species
  and 29 owned Pokémon. All six source archives remain hash-identical. Firebase
  renewed cached credentials/heartbeat data; stable account state is identical,
  and no stale credential/cache data was restored.

Tested/installed APK SHA-256:
`824f91830e8bde713468a3004e0c46832abcf09fc9231726dc8dddf5fc16b39e`.

A fresh physical match is still needed to establish whether the dedicated QTE
band reads the early cue promptly and reduces the capture gap. The rendering
checks demonstrate framing and masking; they cannot recover omitted testimony
or establish live recognition latency.
