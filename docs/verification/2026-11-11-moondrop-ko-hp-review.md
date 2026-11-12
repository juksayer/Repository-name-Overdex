# Moondrop KO and HP review — November 11, 2026

## Source evidence

User recording: `/home/sean/Desktop/Screen_recording_20261111_161531.mp4`.
It is 816×1808, 198.143 seconds, with variable frame spacing and no audio stream.
First captured GO is at video 27.388567 seconds. Video elapsed times are useful
for comparison, not a replacement for archived monotonic source clocks.

Read-only copy of match `f66d7125-3616-4e44-86c8-c1af6469dd35`:
`2026-11-11_16-15-22_f66d7125-3616-4e44-86c8-c1af6469dd35.odxmatch.zip`,
SHA-256 `31d6111cb5ca9bc94bdd328433009250ed58789264fb62bad479789a1776f38b`.
The archive and process-exit evidence are backed up under
`/home/sean/overdex-device-backups/2026-11-11-moondrop-1615-review/`.
Detailed frame review and derivative images are under
`/tmp/overdex-moondrop-1615-l1uca3ql/video-review/`.

The [interruption diagnosis](2026-11-11-moondrop-replay-interruption.md) separately
establishes that Android terminated Overdex during a Google Play services update.
No retained Overdex exception explains that interruption. The video ends during
Replay and does not itself capture the process termination.

## What the video establishes

| Source-video time | Observation | Permitted conclusion |
| --- | --- | --- |
| 27.389 s | Turtonator versus Roselia at GO | Roselia was the opponent lead; first-known Tinkaton in Replay is incomplete opening identification. |
| About 43 s | Go, Camerupt; three player balls still bright | Living player switch. Camerupt's archive identity arrives later, so some earlier attacks are attached to Turtonator. |
| 57.764–60.505 s | Tinkaton has about 14% red HP; Camerupt's fire attack lands; sprite shrinks and disappears; empty outline and one faded ball | Tinkaton fainted after a finishing Fast Move. |
| 65.031 s | Mismagius bar is about 77% cyan with an orange damage trail, partially overlaid by purple/white effects | The nearby archived 39.89% reading is contradicted by the visible bar and subsequent stable measurements. |
| 88.024–89.563 s | Earth Power lands on Mismagius; shrinking/disappearance, empty bar and second faded ball | Mismagius fainted after Earth Power. |
| 90.507–98.010 s | Roselia returns alive; play interrupts, black transition, YOU WIN | Two opponent KOs followed by the user-reported concession. Do not fabricate a third KO. |

No player KO is visible. Camerupt survives at roughly 45% HP. Approximate visual
fractions are not damage-formula calculations or new archived measurements.

## Located failures and changes

The original archive contains 122 active HP measurements, 13 canonical Fast Move
uses, no opponent Poké Ball count measurements and no faint Articles. It retains
one readable opponent-ball crop showing two surviving balls; compact retention
alone does not establish how many uncited capture attempts occurred.

An isolated valid-outline Mismagius reading at source Match +37.637 s reports
39.89%, followed by several nearby readings around 77.7%. Replay's minimum-HP
rule previously held that first low value through later real damage. The new
reconciliation excludes an isolated nonzero fill only when three distinct nearby
measurements strongly contradict it within the same appearance and geometry.
It retains single terminal zeros, sustained low plateaus, distant/insufficient
evidence, and independent border pulses or attacks from the same frame. Switch,
faint, unresolved count-loss and charged-phase boundaries prevent borrowing HP
from a later appearance. Original Articles and timestamps remain unchanged.

The regression fixture contains 294 exact original Articles, including every
original species-check diagnostic. Its adjacent
provenance file records the archive and fixture hashes. The recorded regression
checks the later 77.7% and roughly 55% levels, retained source bytes, and reordered
input; synthetic counterexamples check rejection boundaries.

The live pixel reader also reproduced false 39%, 52% and 62% endpoints from
actual frames at video 64.827–65.252 seconds. A bright effect washed out the
remaining fill's color, so merely stopping at the last saturated green pixel
reported damage that had not happened. Partial endpoints now require a short
visible dark interior or orange damage trail immediately beyond them. The
reader abstains on all five recorded obscured test frames and retains the
later roughly 78% endpoint with its visible orange trail. This does not estimate
HP under an effect. Existing ordinary, tight-box, blue-border and recorded
tracking cases passed alongside the new cases (44 focused tests in this run,
including eight resource-confirmation tests).

Unknown-duration Fast Move presentation previously started its neutral flight
at the witnessed hit, placing its arrival after the HP loss. It now ends at that
impact, with a bounded impact hold, within the current appearance/phase. An
entry-time hit is an impact-only marker. No move identity, cadence, energy,
source HP timestamp or additional attack is inferred. 107 focused Replay tests
passed, including the updated HP archive fixture.

The [resource confirmation review](2026-11-11-resource-count-confirmation.md)
records the missed initial baseline and too-short confirmation opportunity.
Its actual video-strip regression reads 3 → 2 → 1 and attributes only Tinkaton
and Mismagius, retaining Roselia alive. These recovered test inputs do not
rewrite the original compact archive's missing count observations.

Tinkaton's confirmed red bar was followed by an orange-only damage remainder
for more than the old 500 ms anchor lifetime. The empty outline then moved down
about 15 pixels as the sprite shrank. The low-HP terminal watch now preserves a
confirmed red arm through one fixed three-second burst and allows bounded
one-bar-height motion (capped at 16 pixels). Healthy ordinary bars no longer
invoke this narrow sampler. A missing outline remains unavailable, and zero
still requires complete paired edges/caps and a visibly dark interior. Both
ordinary and charge-camera paths mask the sampler's exact expanded rectangle
against Overdex overlays.

For Mismagius, Get Ready reached its reader 7.5 seconds after capture, while
Earth Power's announcement arrived after the empty-bar frames. The accepted
QTE NICE cue arrived earlier and had a normal opponent reference only 4.484
seconds older than its source image. That cue can now prepare the bounded
opponent charge-camera watch. Repeated grades do not renew its window; older
delayed Get Ready cannot replace it. Actual recorded centered EMPTY frames at
video 89.151, 89.187 and 89.279 seconds are available to the existing centered
reader; the later 89.563-second ordinary-position bar is outside that search.
This approximately 128 ms sampled interval emphasizes why real-device capture
and processing cadence still need verification.

Droidball now opens once at accepted Match Start for the current active capture
session. The live handoff includes the otherwise forgotten transit-to-field step
when the shell is hidden and the start is newer than that journey. Later manual
collapse remains respected. This does not start capture or create a Match.

## Validation status

Focused Replay HP and actual custody→Match→session presentation tests have passed.
The combined terminal/charge/Replay-focused run passed 100 tests. A full-suite
check exposed two missing source visual Articles in the older faint/audio test
fixture. Both were restored byte-for-value from the original 2,642-Article
archive and the fixture hash/provenance updated. The existing attack-count and
side-proof assertions now pass using both the standalone fixture and the full
original archive; no production behavior was changed to satisfy that test.

Final verification after that fixture correction and the entry-boundary HP
guard: **2,620 unit tests, zero failures/errors/skips**, and `assembleDebug`
succeeded. The complete original faint-attribution archive also passed all five
of its recorded regression tests, with 2,642 unchanged Articles confirmed.

The update was installed with data-preserving replacement on Moondrop
`MDPH00124112500414` at device time **2026-11-11 18:48:51**. Capture was stopped
and the launcher was foreground before replacement. The installed base APK's
SHA-256 matches the build:
`b9b24f77f9d1946ecd53034f71bb21278c19e317f73e215facce15f249bb7224`.
Cold launch returned `Status: ok`; the ODX-Fi home screen was visually checked.
The original reviewed archive's on-device hash remains unchanged.

Before installation, 51 archive files plus match records, calibration profiles,
database, settings and related user state were copied into the verified
421,701,632-byte `pre-update-records-and-settings.tar` in the backup directory
above. It is a records/settings backup, not a copy of the 23 GB loose artifact
store. Its SHA-256 is
`9bf942deb4e32c9d38a588e0d50aba333cefe507ed70e828090ef5fec4e15cb5`.
Build/test provenance and the final build log are retained beside it.

Live-device capture acceptance remains pending. Start observation on this
Moondrop, leave/collapse Droidball closed before the next recorded battle, and
check automatic opening at Match Start. A Fast Move KO and a charged-move KO
will exercise the two observation paths. Compare their captured HP, count and
faint evidence with the video and Replay; a successful launch alone does not
verify them. A Fast Move that jumps straight from above 25% HP to zero without
a captured red frame remains outside the deliberately narrow red trigger.
The existing compact archive does not gain invented missed KO Articles.
