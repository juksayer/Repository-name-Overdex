# Moondrop 21:13 match: final Rock Tomb and missing terminal HP

## Finding

The archive records a win and three earlier faints, but it does not record the
final Cramorant faint. Its last opponent HP measurement is **76.19048% before
Rock Tomb**. There is no later opponent HP sample. The retained evidence therefore
supports an HP capture gap, not a claim that the orange damage trail was measured
as remaining HP.

The final player QTE was recognized, but early charged-HP preparation returned
`NO_REFERENCE`. No final charged-HP crop or scan was retained. The exact ordering
that removed or failed to seed the normal HP reference is not recorded.

## Sources and clocks

- Device: Moondrop `MDPH00124112500414`.
- Match: `b508a605-132a-48a8-a4e1-67514c729fc1`.
- Original device archive:
  `files/match_archives/2026-11-11_21-13-35_b508a605-132a-48a8-a4e1-67514c729fc1.odxmatch.zip`.
- Verified local copy:
  `/home/sean/overdex-device-backups/2026-11-11-moondrop-2113-review/2026-11-11_21-13-35_b508a605-132a-48a8-a4e1-67514c729fc1.odxmatch.zip`.
- Device/local SHA-256:
  `97027bc36e6d3c5e59718307aa708ffca6c29f8179d98a83a1254b2300a45050`.
  Size: 10,345,568 bytes; 370 ZIP entries; 3,131 retained Articles.
- Original user video:
  `/home/sean/Desktop/Screen_recording_20261111_211335.mp4`.
- Extracted archive:
  `/tmp/overdex-moondrop-2113-review/2026-11-11_21-13-35_b508a605-132a-48a8-a4e1-67514c729fc1/`.
- Backup contains `source-provenance.json` and `final-charge-evidence.json`.
  The latter is a selected original-Article excerpt, not a new battle record.

Archive source times below are seconds from Match Start Article
`e95c9def-f857-429d-a882-1ede7ad2822e`, monotonic `18443304147868`.
Publication times are `recordedAt` minus that Article's `perceivedAt`
(`1794453254772` milliseconds). Publication is not the capture clock. Video times
are positions within the separate MP4; they must not be substituted into the
archive's source clock.

## What the archive establishes

| Evidence | Source time | Article |
| --- | ---: | --- |
| Player Gourgeist faint, from player count decrease | +120.636127 | `3a4e6dac-2587-40fa-90f7-b3d326d8694e` |
| Opponent count is one remaining, after previously measured three | +123.227109 | `3d81171d-7fdd-4e08-a3b8-4bf4465b574b` |
| Opponent Quagsire faint, aggregate count attribution | +123.227109 | `db96e6e9-93ee-4143-9bea-a8b51cf3f848` |
| Opponent Annihilape faint, aggregate count attribution | +123.227109 | `c65bf6b6-c158-49e4-b90f-40046aea2724` |
| Last opponent HP: 0.7619048 | +130.786260 | `71bd7d45-2d95-401a-a353-3a640a31abe7` |
| Last opponent ball count: one remaining | +136.024546 | `78cf85b1-efd1-4931-a8e6-fdc9b66b46e2` |
| First accepted final Rock Tomb announcement | +138.709973 | `24d5c83a-978f-4b2e-a674-691cecc3f335` |
| Clean repeated Rock Tomb announcement | +139.474074 | `69f78c8e-4a93-4da9-8d23-3175c76b2923` |
| Raw outcome crop reads `WIN!` | +146.976940 | `add7cbe7-736f-49cd-978c-0209f16bdbc4` |
| Raw outcome crop reads `YOU WIN!` | +152.977169 | `2ee2ab29-7b93-4c8b-b680-d028f905a27d` |
| MatchEnded WIN, supported by YOU_WIN_WITNESS | +152.977169 | `0ba3a326-1b0e-40bd-8653-0773bad0c658` |

The two aggregate opponent faints establish losses by the count-decrease capture;
they do not supply the individual finishing-hit times. There is no final opponent
zero-ball measurement and no Cramorant faint Article. The win is independently
supported by result text; it does not backfill an invented faint or zero-HP frame.

The last opponent HP geometry is `(441,694)-(634,712)` on a `672×1493` captured
surface. Its source precedes the first final Rock Tomb announcement by 7.924
seconds. Holding this last measurement in replay cannot show the subsequent
charged damage faithfully.

## Final preparation failure

Scheduling Article `67b62448-18d6-4bef-add2-8fce3d93e6d5` records:

```text
stage=EARLY_PREPARATION kind=PLAYER_QTE_PROGRESS result=NO_REFERENCE
recognizedAt=18581445169184 receivedAt=18581446034338
recognitionDelayMs=2418 windowToken=none
```

Its QTE crop `e70d4cff-38bb-4771-99e8-0a12d5c17d74` was captured at
**+135.722650**. Recognition completed at **+138.141021**; the diagnostic was
published at +138.427. The canonical accepted `GREAT` Article is
`ef4ab365-9c6e-4acf-8e16-ca20925cbbfa` at the same source time.

The last archived normal HP is only **4.936390384 seconds** older than this QTE
capture. Its width, position, and age are eligible for a normal opponent reference
under the current five-second policy, if it survives the ownership/reset path.
An archived measurement is not proof that the live policy retained that seed.

Relevant ordering evidence:

- First Cramorant badge `a1633907-0236-400e-bc5c-34f615df3ea8`: capture
  +126.530275, publication +131.809. Coordinator delivery diagnostic
  `e0fe5471-9501-4cc9-b603-ed6626922c8d` has its own processing clock +132.065545.
- The last normal HP has capture +130.786260 and publication +135.164.
  Normal HP seeds its live policy before separate publication; these publication
  times alone do not order the seed against the species subscriber's reset.
- A repeated Cramorant badge `72a808bd-6619-4b5e-a07e-e12b0d387840` has capture
  +132.676416 and publication +137.506.
- Delayed `Go, Cramorant!` `2e1f0003-88ea-4e0a-89eb-ffe698c226ab` has capture
  +125.510176 and publication +137.639. It opens redundant ENTRY windows 43/44
  at processing times +137.681757/+137.681858. Those openings do **not** reset
  Live HP: the installed/current Live HP handler excludes ENTRY and CRY from
  its SpeciesCheckMeasured reset reasons.

The first changed-species delivery can clear geometry that was already seeded
from a newer captured frame. That is a plausible source-order failure to test,
not a demonstrated reset timestamp in this archive. A repeated same-species badge
does not normally reset; the older entry species is rejected by `lastSpeciesAt`.
The redundant ENTRY windows are separate wasted work, not proof of this HP gap's
cause.

## Retained images and video evidence

There are 22 retained `CHARGED_IMPACT_HP_CAPTURE` crops. The last is
`a89f6ea9-2e7e-455e-8d83-392f088a4044`, source **+101.002944**. No retained crop
at +138–141 covers the final centered HP bar. The archive's only full frame is
`84ec73d4-fed5-4190-a2d6-e916074a6ec8`, source +114.916, too early.

Outcome crops at +137.303 and +140.313 could geometrically cover that area, but
only their latency diagnostics survive: `25ca112f-b538-4786-958c-c0305aab0dfb`
and `86175680-ce7f-434d-a764-a8a1a536f2f7`. Both report `nonblank=false`; their
crop Articles and PNGs are not retained in this compact archive. The retained
Rock Tomb announcement crops cover text above the HP bar.

Separate video review reports a green bar at **178.299**, an orange-only bar at
roughly 75% width from **178.554 through 179.300**, fading by **179.550**, followed
by native HP/badge disappearance at **180.023** and conspicuous faint particles
by **180.551**. Closer frame-by-frame review separates the earlier camera pullback
from the later collapse: Cramorant's final sprite remnants persist until
**181.091**, and are no longer identifiable at **181.147**. No decoded native
badge frame shows zero remaining balls. The opponent-only badge that reappears
at **180.109** is potentially retained Overdex presentation and is excluded from
independent evidence. See the [native badge visibility review](2026-11-11-final-native-badge-visibility.md)
for exact PTS, the sampling limit, and durable copies of all 39 native-resolution
analysis frames. These are video-derived observations, not Articles captured by
Overdex.

The user's supplied testimony is that Camerupt's Rock Tomb finished Cramorant,
and the bar never visibly dropped below the supplied orange-bar screenshot before
fading. That testimony agrees with the reviewed video sequence. It remains
separate from automatically witnessed facts. Orange-only is deliberately unknown
in `ChargedImpactHpBarMeasurer`; missing green pixels amid damage animation are
not a trustworthy numerical zero.

## What this test does and does not validate

The general early preparation handoff ran: two incoming windows prepared and
each acquired a green HP sample (source +53.297484 and +85.550514). Other
preparations still show recognition delays of roughly 7–10 seconds. The final
QTE found no reference, so no final charged-HP success is demonstrated.

No retained OCR reads `No Protect Shields remaining!`, and there is no
`OUTCOME_SHIELD_PROMPT_TEXT_WITNESS` Article. This match therefore does not
validate the newly installed outcome-crop shield-prompt handoff.

The next bounded test should reproduce first Cramorant identification after
normal HP from a newer captured frame has already been seeded. Verify both
delivery orders against the same source-time appearance boundary: old geometry
must be discarded, newer geometry belonging to the confirmed appearance must
not disappear merely because recognition arrived late. Include a genuine later
switch/faint to prove that unrelated geometry cannot cross the boundary. If
existing diagnostics cannot distinguish these orders, add a sparse reset/seed
provenance diagnostic before claiming the cause is fixed.

This review changed no battle records, phone settings, or installed package.
It does not claim the missing Cramorant faint or final charged HP is fixed.

Follow-up: [source-time charged HP reference retention](2026-11-11-charged-hp-reference-ownership.md)
reproduces and fixes the reference-loss ordering in tests, including identity
delivery during a normal scan. It does not prove that this exact ordering caused
the archived failure or reconstruct the absent terminal measurement.

Further read-only review: the [QTE capture timing audit](/home/sean/overdex-device-backups/2026-11-11-moondrop-2113-review/qte-terminal-capture-timing-analysis.md)
compares eleven usable charged phases across six original archives. For this
match's final Rock Tomb, early QTE recognition was available before the used-move
source capture, whereas typed used-move publication arrived after the raw-image
window's deadline. The report proposes one bounded QTE image budget and records
the existing sampler's sparse actual coverage. It implements no new capture or
faint inference. The [short USB audio check](2026-11-11-moondrop-usb-audio-check.md)
also establishes a recording option for future comparisons without interrupting
Overdex's screen capture in the tested menu interval.

The two retained player terminal crops (+28.285116 and +29.995365) are 417×941
territories, 1.710248 seconds apart. Independent HP observations between them
show that frames continued to arrive elsewhere; complete producer starvation
does not explain this gap. The terminal collector synchronously copies,
compresses, hashes, writes, syncs and verifies each PNG on its auxiliary
dispatcher. Its direct artifact path does not acquire the store's frame-cache
lock. Encoding/I/O cost and dispatcher contention remain possible causes,
not measured causes: per-attempt rejection and preservation-duration diagnostics
are absent. Increasing the nominal sampling rate alone has no demonstrated
benefit. A follow-up must measure actual frame admission and persistence cost
as well as fixing the late activation window.
