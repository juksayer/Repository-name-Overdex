# Evening Moondrop: retained finishing pixels, missing testimony

The new November 7 evening battle still lacks its final attack and faint in
the canonical archive. Read-only inspection of the loose source-frame store
does, however, recover visible **Camerupt used Rock Tomb!**, preceded by Get
Ready and a Rock-type NICE QTE. Those source pixels corroborate the user's
initially uncertain recollection. They have not been inserted into the archive
as a newly observed move, exact impact, damage, zero HP or faint.

## Integrity and acceptance boundary

- New Match: `368fc54f-5f4a-41ea-af65-7a43b371aca5`, recorded at 20:20:22 local.
  This is the only new internal archive after the 15:30 controlled test.
- Phone and desktop copies agree: 11,041,686 bytes, SHA-256
  `dde358e99ee07fd26dc09e5875a26879d010f5200e1da4371d934c7ec564842b`.
- Manifest and Timeline agree on 3,274 Articles; all 473 declared artifacts
  verify. The archive contains 475 ZIP entries, including 3 full frame PNGs.
- Twelve additional terminal full frames were copied from the phone's loose
  artifact store. Every copied file's content hash matches its SHA-256 filename.
  These are separate source evidence, not implicit Articles in the compact ZIP.
- No device files, preferences, calibration, service state or archives were
  changed. The read-only service check found no running Overdex service and no
  active MediaProjection. The installed app's last update was 16:05:40 local.
- No newly dated video appeared in the inspected common recording folders.
  This is not a claim to have searched every possible location on the phone.

Private copies, hashes, terminal crop and full-frame contact sheets, exact
calibration overlays, extracted terminal audio and JSON analyses remain under
`/home/sean/overdex-device-backups/2026-11-07-evening-final-hits/`.

## Recorded sequence

Times are seconds after the archive's recorded `MatchStarted`, monotonic
`161798751767412` ns. They describe original source times, not OCR publication.

| Event | Source time | Retained basis |
| --- | ---: | --- |
| Player Turtonator faint | 54.765 | Player ball-count decrease |
| Player Gourgeist entry | 55.530 | `Go, Gourgeist!` |
| Opponent Altaria faint | 58.280 | Opponent ball-count decrease with cry support |
| Opponent Azumarill identified | 60.860 | Side-located species badge |
| Gourgeist Seed Bomb | 71.124 | Named announcement and charged-energy Article |
| Azumarill Ice Beam | 85.666 | Legal-pool repair of raw `lce Beam` |
| Player Camerupt identified | 102.855 | Side-located species badge |
| Attack incoming | 106.194 | Accepted announcement |
| Azumarill Ice Beam | 113.214 | Legal-pool repair of raw `lce Beam` |
| Last accepted QTE grade, NICE | 119.855 | Dedicated QTE progression text |
| Whimsicott entry | 126.964 | `Go, Whimsicott!` |
| Opponent Azumarill faint | 127.875 | Opponent ball-count decrease |
| Last player Fast Move use | 131.224 | Opponent HP damage tick |
| Last opponent Fast Move use | 132.236 | Player HP damage, artifact and cadence support |
| Last opponent HP | 133.100 | Whimsicott approximately 44.680852% |
| Last player HP | 140.122 | Camerupt approximately 3.1746034% |
| WIN | 147.345 | Accepted result with retained YOU WIN pixels |

There is no final Rock Tomb announcement, final charge spend, final HP loss,
zero-fill HP reading or Whimsicott faint Article. The approximately 14.245 seconds
from the last opponent HP to WIN therefore remain unresolved in the canonical
event stream. Result-only replay cannot turn that interval into a documented KO.

The three intermediate faint Articles are present. The final-hit capture gap
must be distinguished from any independent replay scheduling or rendering bug.

## What the extra source frames show

The sparse full frames show the earlier Camerupt Earth Power against Azumarill,
Azumarill's empty outlined bar, Whimsicott's entry, ordinary Incinerate impacts,
then Get Ready, NICE with Rock-type bubbles, and **Camerupt used Rock Tomb!**.
A subsequent frame shows Whimsicott low to the ground without its visible HP
bar, followed by a black transition and the result sequence.

For identification only, the relevant loose files have local modification times
20:23:23.809 (Get Ready), 20:23:26.593 (NICE), 20:23:29.797 (Rock Tomb banner),
and 20:23:31.781 (Whimsicott low to the ground). File modification time is not a
canonical capture timestamp. Encoding/queue delay varies; these times do not
establish exact QTE onset, impact, faint or archive alignment. Only a few static
later transition crops match exactly; no precise terminal-attack source clock
is claimed from those separate files.

The ZIP's three retained full frames belong to Team Select and source 106.358 s.
Its last HP evidence crop is source 127.370 s. There is no archived broad HP or
full source frame spanning the final QTE. Loose-store retention was essential
to this review; a compact archive alone cannot supply these missed pixels.

## Calibration and OCR scheduling

The recovered frames are exactly 672 × 1493. Their actual text is contained in
the saved crop regions:

- Announcement crop: X 50–622, Y 326–375. Final Rock Tomb text is approximately
  X 130–543, Y 334–358; Get Ready also fits fully.
- QTE progression crop: X 80–591, Y 388–500. Final NICE is approximately
  X 237–434, Y 411–472, fully inside that region.

Exact unmodified crops and outlined geometry checks are retained privately.
The inspected geometry does not support moving these calibration boxes as the
repair. Archived blank sky samples were taken at other times; they do not show
that the readable Rock Tomb or final NICE frame was admitted to OCR and rejected.

The previous scheduling repair materially reduces source-to-publication age in
these two recordings, without establishing complete transient coverage:

| Accepted text stream, including blanks | Earlier 15:30 test median / max | New evening median / max |
| --- | ---: | ---: |
| Announcement | 7.479 / 14.803 s, 25 readings | 1.567 / 4.261 s, 53 readings |
| QTE progression | 12.351 / 19.582 s, 12 readings | 1.763 / 3.696 s, 46 readings |

New Announcement latency diagnostics separately report median/max queue wait
2.011/7.265 s and selected-crop age at admission 0.665/1.958 s. QTE reports
1.647/8.170 s queue wait and 0.726/1.926 s selected-crop age. Queue wait describes
the pending worker, while source age describes the subsequently selected frame;
they must not be summed as if the same stale frame waited through both.

Terminal admitted Announcement samples are source 128.367, 135.940, 142.321 and
145.462 s, all blank. This includes gaps of 7.573 and 6.381 seconds. Terminal QTE
samples at 130.312 and 139.482 s are blank, 9.170 seconds apart. The final NICE
and Rock Tomb visible in loose source frames are not positively retained by
those accepted readings. Faster publication of a fresh selected frame still
does not cover a brief banner or grade between admissions.

## HP and audio coverage

The HP feed remains enabled through the result check. From 137.839–142.870 s,
18 locator passes find one player bar and zero opponent bars; the opponent
misses are 17 `NO_CANDIDATE` and one `UNASSIGNED_CANDIDATES`. All 34 passes from
142.870–147.885 s report no bar candidates on either side. There is no evidence
that a valid final damage Article was merely dropped by Replay, or that HP
witnesses shut off before the finishing sequence.

Four typed cry-candidate Articles cover countdown/entry cues; none covers the
terminal sequence. Raw terminal audio is nevertheless retained, including clips
starting at 139.316 and 141.308 s, each 1.2 seconds long. Those clips are extracted
for focused follow-up; this review does not claim they have been recognized as
a particular cry, charge effect or finishing impact.

This review identifies separate remaining acquisition targets: admission gaps
for transient announcement/QTE pixels, terminal HP/empty-outline acquisition,
and cry/effect interpretation. It does not rewrite any archived Article or
award an unobserved move, energy gain, energy spend, damage or faint.
