# Controlled settled-fill review

## Sources and context

Primary: Moondrop match `0330b719-8c57-45bc-9f41-247bf016a452` and
`Screen_recording_20261023_175949.mp4` (816x1808, approximately 359 seconds).
The archive has 7,247 Articles and 871 verified artifacts. It contains no new
fill-height Articles because this phone had not yet received that build.

The nearby `cc52aaa6` archive has 3,970 Articles and 278 verified artifacts.
The earlier recording is `Screen_recording_20261023_174854.mp4`; its matching
17:48:45 `2fd2a62c` archive was also copied. Backups are under
`/home/sean/overdex-device-backups/2026-10-23-settled-fill/`.

User says the last match followed the instructions more accurately, with
connection issues mostly at the beginning of the last recording and the end of
the earlier recording. Treat that as contextual evidence. Do not substitute the
requested attack pattern for what actually happened or label compressed visual
updates duplicate attacks solely on their spacing.

The earlier archive verified 365 artifacts. Sampling the last 55 seconds of its
251-second recording shows a black game area with the HUD still visible at about
206, 211, 216, 221, 226, and 231 seconds, then a trainer/result scene by 236–246
seconds. Those samples establish a substantial visual interruption, not its cause
or exact continuous duration. Do not infer inactivity from that unavailable field.
The contact sheet is retained as `earlier-ending.jpg` in the backup directory.

## Timing and measurements

Three independent announcement-image matches yield archive-relative time minus
video time of 86.719, 86.772, and 86.753 seconds. Image differences are small but
this is approximate alignment, not a frame-exact clock calibration.

At video 111.036 and 112.061 seconds, both button separators independently resolve
to approximately 80 under the existing experimental height/layer model. The
archive has four 20-energy player gains at that point, before any player spend.
The first gain precedes a long pause; the other three follow later. Thus this
is useful even though the four moves were not performed as one uninterrupted run.

At 125.082 and 126.065 seconds, following the first Brutal Swing, the brightness
reader rejects the lighter fill. A local-contrast trial recovers the left
separator at approximately +34px. Its layer alternatives include about 10, 45,
and 80 energy: consistent with 45 after spending 35, but not independently unique.
The right-button separator remains unknown. Do not claim a confirmed two-button
45-energy reading.

At video 150–153 seconds, only the right separator is read; its alternatives are
roughly 15 and 70. The left appears fully filled but supplies no unique interior
separator. At 154.052 seconds, both separators resolve to approximately 90 while
the counted ledger has already reached its 100 cap. This later mismatch must
remain visible rather than being used to overwrite either source.

## Specific counting suspect

Two player Incinerate uses appear at archive-relative 231.598 and 232.691 seconds,
only 1.093 seconds apart. The bundled reference specifies Incinerate as 5 turns,
2,500ms, 20 energy. The first has HP damage plus charge-fill support; the second
has only an HP-border pulse. Both receive energy credit. Their raw records and
predecessor IDs are retained in the numeric report.

An extra 20-energy credit could explain part of the later disagreement. This is
not yet a confirmed duplicate: evidence latency, network-delayed visual updates,
and attribution/timestamp errors remain possible. A blanket minimum-interval
filter would risk deleting real attacks whose visual evidence arrives late.
Future fusion work should reconcile those witnesses with fill plateaus and their
source-frame timing, preserving raw testimony.

## Offline reader improvement and validation

`inspect_charge_fill_layers.py --method contrast` adds an optional local-contrast
experiment for narrow lines on light upper fills. The default brightness method
and Android reader are unchanged. Tests cover the newly exposed bright-upper-fill
case and reject uniform bright disks and multiple lines. All 18 charge-analysis
tests pass. Numeric evidence and source hashes are tracked in
`charge-fill-height/0330b719-settled-fill-review.json`.

No new Android code was changed or built. The existing tested APK from the live
fill-height feature was installed successfully on the idle Moondrop, serial
MDPH00124112500414, after checking that it had no active service. APK SHA256:
637593fecdcf25ae8fa7022d0b96167490970ec36ea581715d29823baec7cebc.
That install does not retrofit height measurements into these older archives.

Next: investigate the close damage/fill versus border-only use pair, while making
settled-button comparison tolerate light fills and leave connection-affected
transitions uncertain. Existing recordings are sufficient to start that work.

Follow-up: the [late-flash investigation](2026-10-23-incinerate-late-flash.md)
reviewed the video and reproduced this pair as a duplicate caused by the fixed
fusion ceiling. The regression now produces five uses for the five HP-loss cycles
in the selected interval; the recomputed 90-energy checkpoint agrees with the
two-button measurement. The original archive remains unchanged.
