# Pixel 4 Candela review — October 28, 2026

The user confirms this was a trainer practice battle against Candela, with
Scrafty using **Snarl**, Heatmor using **Fire Spin**, and continuous attacks.
These declarations are review ground truth, not new observations inserted into
the archived Timeline. No exact attack totals were supplied.

## Evidence and version boundary

- Archive: `2026-10-28_08-56-11_a66482db-0e54-4206-9fb8-b262ef842934.odxmatch.zip`.
- Desktop copy: `/home/sean/overdex-device-backups/2026-10-28-pixel-candela/`.
- 16,649,337 bytes; SHA-256
  `5ebb08f815b2d2287f726c4ab8cdd924649b955482e4baff4693b2b081291dc9`
  matched the device source. All 2,506 Articles and 435 artifacts verified.
- Recorded on Pixel 4 with build `668f7b8`, before the VS-start change in
  `0ac892f`. This match validates countdown activation, not VS activation.
- Original archive/device data remain unchanged. Companion audio/timeline
  reports and JSON in the desktop copy directory retain detailed Article IDs.

All times below are source times relative to witnessed GO unless explicitly
identified as publication delays. Without continuous video, these are not
measurements from the first moment a signal became visible or audible.

## What worked

Fast Move sound measurements began **2.717 seconds before GO**, during the
countdown. The previous long activation delay did not recur. Of 35 retained
sound measurements, 12 met the strict matching criteria: **five Snarl and seven
Fire Spin matches**. These are matching samples, not counts of separate attacks.

Scrafty's Snarl identity used repeated audio, with source time +3.699 seconds
and publication at +5.632 seconds. Heatmor's Fire Spin identity first used
cadence and was then corroborated by repeated audio and effectiveness. The
user's equipped-move confirmation supports both identities.

The fresh Scrafty / Heatmor / Jirachi roster was confirmed at -15.470 seconds.
No new move selections were saved afterward. The previous Swampert / Quagsire /
Obstagoon loadout correctly stayed out of player move identity and energy.
Actual active appearances were Scrafty -> Heatmor -> Scrafty; Jirachi was
roster-only. Opponent appearances were Combusken -> Magcargo -> Flareon.

Combusken and Magcargo faints were correctly attributed, including Magcargo's
outgoing identity when Flareon's announcement arrived before the ball decrease.
WIN was witnessed at +104.229 seconds.

## Remaining accuracy gaps

| Player | Recorded fused Fast Move uses | Credited generated energy | Charge evidence |
| --- | ---: | ---: | --- |
| Scrafty | 7 | 4 Snarls = 52 | One named Foul Play; another readable in the preserved final crop |
| Heatmor | 17 | 11 Fire Spins = 110 | Three named Thunder Punches = 120 spent |

The energy ledger is incomplete relative to the charged moves. This does not
mean every uncredited candidate should receive energy: some fused uses are
closer together than the legal move duration. Reconcile identity timing,
appearance continuity, phase boundaries, and deduplication without weakening
duration guards or inventing observed attacks. Inferred missing energy must
remain distinguishable from witnessed energy.

At +99.308 seconds, the final announcement crop is human-readable as
**Scrafty used Foul Play!**, although its upper edge is clipped. OCR produced
`Scrafty used FouPay!`, dropping both lowercase l characters. The generic charge
exists, but its named move and energy spend do not. The source crop is preserved
unchanged as `final-charge-99.308.png` in the desktop review directory, SHA-256
`b01bdca8050d219e806df430a21e838241a9041327760d86236ca5b7e4109f4f`.
Crop Article: `369689f9-abf7-41df-ad44-0c2dbcb38d0e`; generic charge Article:
`2e7497b4-88cc-42d6-907a-f88ca99ce389`. This is a concrete bounded missing-letter
resolver regression candidate, not a fix delivered by this review.

No final Flareon faint, zero-ball count, or zero-HP observation was retained.
Its last HP measurement was 26.60% at +90.909 seconds. The final charge and WIN
support a reconstructed ending in Replay, but must not become fabricated raw
faint testimony.

Opponent identity remains weaker: Combusken's Ember is explicitly an assumption,
Magcargo and Flareon have no resolved Fast Move, and no exact opponent energy
was awarded. Recorded opponent use counts are 9 / 1 / 1, not verified totals.

One player HP reading is an outlier: 64.55% -> 3.05% -> 61.17% at +90.330,
+90.495, and +90.909 seconds. The low sample has changed bar geometry. It needs
continuity review rather than being treated as genuine recovery.

## Response time and next acceptance checks

Audio sample publication delay was a median **2.240 seconds**; the last status
reported mean matcher computation **826 ms**, maximum 1,237 ms. Those are
different stages. Its 497 skipped input windows are a workload counter, not a
missed-attack count.

WIN publication took 3.361 seconds. That crop's diagnostic reports 3,083 ms
capture-to-admission versus 236 ms native OCR, pointing to queue/admission delay
as the larger contributor in this example.

The next focused checks should cover the named final-charge repair and
source-by-source energy reconciliation, followed by capture admission latency.
The separate [VS-start update](2026-10-28-vs-audio-start.md) still needs a real
match recorded on its new build. No source code changed during this review;
an additional build or installation was unnecessary.
