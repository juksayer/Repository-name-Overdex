# Fast Move energy duration bounds — October 27, 2026

## Problem and evidence

The user reported rapid partial Charm animation restarts and required energy
accounting to obey move gain, duration, and elapsed battle time. A visual restart
is not another completed use. Conversely, broken animation does not prove that
underlying attacks stopped. Preserve the independent observations and constrain
the energy conclusion.

The bundled `gamemaster.json` defines Charm as three turns (1,500 ms) and eight
energy per use. The reviewed Pixel archive contains 54 Charm energy derivations
and the Moondrop archive contains 73; every one already records one use and eight
energy. The defect was not a single derivation granting 50 energy. Distinct false
use IDs could each receive the ordinary gain without a duration feasibility check.

The live perfect-play forecast also used the larger of elapsed completed cycles
and observed use count. That allowed false observations to exceed its own clock.
Replay already linked energy to accepted animation cycles, but unbounded legacy
aggregates and unknown-duration cycles could still supply a balance. Changing a
move identity could also reset the scheduler's previous-cycle spacing.

## Required distinction

- Reference gain limits each accepted use; repeated testimony cannot multiply it.
- Available combat time limits how many complete move cycles can fit. Charge
  phases, forced-choice pauses, switches, faints, and results bound that time.
- An idle gap does not create a stockpile of unused observed attacks that may be
  credited in a later burst.
- The perfect-play HUD remains a labeled estimate of continuous optimal play.
  Its elapsed-time ceiling does not certify that those attacks occurred.
- Recorded energy requires use evidence. The clock alone never creates an
  observed use or energy Article.
- Original Articles, timestamps, observations, and contradictory evidence remain
  available. A physically possible reconstruction is not proof of exact server
  execution or exact energy when observations are missing.

## Implementation

Live recorded-energy inference uses the reference move's positive turn count and gain, rather
than an observed animation interval. Each newly credited use must fit a complete
non-overlapping cycle on its side. A bounded past-only completion window of
`min(duration / 4, 250 ms)` accommodates capture delay. No completion is scheduled
after the source observation. Rechecking the complete reservation schedule means
this allowance cannot accumulate into extra turns. Long gaps do not bank credit.
Pending uses are evaluated in source-time order when identity arrives later.

Known GO, appearance transitions, charge and decision pauses, faint/replacement
gaps, and result boundaries constrain new reservations. Charge/decision cues are
reconciled in source-time order even if OCR delivers their ending first. A partial
recording without a known beginning may have one first in-flight completion;
subsequent uses still need complete, non-overlapping cycles. Rejected uses retain
their original testimony and cannot gain credit merely because a later clock
update or repeated identity arrives.

The live HUD forecast now counts completed visible cycles. Its independent
`observedUses` count can expose disagreement but no longer overrides the time
ceiling. One bounded initial partial-recording completion is distinct from the
normal clock; known charge/faint boundaries disable that initial allowance.

Replay continues to use the same accepted cycle schedule for animation, HP,
and energy. Changing a move identity or duration within the same active window
does not reset occupied attack time. Only a derivation matching the accepted
cycle's move may supply its gain, once. A later matching derivation can supersede
a mismatched one in presentation without altering either Article. Energy
derivations linked to an unknown-duration cycle, unbounded legacy aggregates,
and unresolved move conflicts make the balance unavailable (`E —`) instead of
presenting an unjustified number. A use without an energy derivation contributes
no gain to the recorded balance. Fainting restores known zero. Raw aggregate
totals remain available as recorded testimony.

An intervening unknown-duration marker also cannot erase an earlier known
cycle's occupied time. This is checked even when presentation jitter moves that
known cycle's impact later than the neutral marker's timestamp.

## Validation boundary

This change is a defense against impossible accounting, not a repair of every
missed or duplicated Fast Move. The separate Charm HP/border-phase pairing issue
remains open. Late-arriving boundaries cannot silently rewrite already-published
energy Articles; full-history Replay and the disposable live forecast can use
the later evidence. A use rejected under an earlier slower move identity is not
automatically revived by a later faster identity; exact retrospective correction
requires a reconciled accounting view rather than rewriting those Articles.
Severe lag or uncertain phase timing can conservatively withhold genuine gains.
Real-device acceptance still requires a subsequent battle.

## Verification and installation

- Focused verification passed 90 tests across eight suites before the final
  known/unknown/known Replay regression was added.
- Final `:app:testDebugUnitTest :app:assembleDebug` passed: **1,192 tests across
  199 suites**, zero failures, errors, or skipped tests. Debug APK build succeeded.
- Regression coverage includes the Charm gain/duration ceiling, sustained early
  jitter, rapid bursts after idle gaps, delayed identities, source-order phase
  cues, partial recordings, historical appearances/faints, independent sides,
  changed move identity, and neutral Replay markers between known cycles.
- `git diff --check` passed. Independent final review found and closed the
  neutral-marker timing-anchor bypass before the final full run.
- Installed the final APK on **Pixel 4** (`99261FFAZ0017J`) and **Moondrop**
  (`MDPH00124112500414`), with no active Droidball service on either device.
  Both installed APK hashes match
  `d6fe00d57c043a23b6e7924988a2299689576d47b29c85882d26d2de77f0f6ed`.
- Before/after hashes are unchanged for `match_calibration_profile_selection.xml`,
  `overmon_calibration.xml`, `team_select_calibration.xml`, and
  `battle_hud_layout.xml` on both devices.
- Build output, test totals, install receipts, and preference hashes are retained
  outside Git in
  `/home/sean/overdex-device-backups/2026-10-27-energy-duration-bounds/`.
- No new live battle was performed during this verification. Installation and
  automated tests do not validate the exact observed Charm count on the phones.
