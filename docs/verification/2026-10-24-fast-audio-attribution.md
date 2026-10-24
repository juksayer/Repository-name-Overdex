# Fast Move audio attribution and provisional HUD evidence

## Recorded failure

The [11:45 Moondrop match review](2026-10-24-live-result-retest.md) retained a
strict Rock Throw reference match during Magcargo's appearance. It never reached
the HUD as either a preference or a confirmed move. The raw measurement correctly
keeps `attackingSide` unset: the mixed audio stream does not establish ownership
by itself.

Reprocessing the actual Articles in publication order exposed two blockers:

1. An entry check opened for both sides while Turtonator remained active. The
   subsequent player badge reconfirmation was excluded from cadence inference
   to protect the already-resolved player form. Because the canonical player
   identity did not change, no new resolution was emitted. Audio attribution
   could therefore keep waiting for a reconfirmation that had already happened.
2. The existing provisional path required two weaker windows and reference
   scores for every legal alternative. Strictly accepted captures retain the
   leading spectrum and changing-spectrum candidates, omitting low ranks. One
   strict global match consequently could not supply any visible preference,
   even when its leading move was legal on only one known active side.

The fixture preserves 220 original input Articles from match
`d8b9fd1b-47d1-46b6-83f2-61a1cc2efb88`, including the sound, species checks,
reconfirmations, observed uses, and charged phases. Source archive and fixture
hashes are recorded beside it in
`app/src/test/resources/battle/2026-10-24-magcargo-audio.provenance.json`.

## Change and evidence boundaries

- Fresh player badge reconfirmations reach cadence/audio inference using the
  resolved form, team slot, and existing appearance start. Their own source time
  and Article ID remain intact, with the supporting resolution evidence. This
  can close an entry/cry check without manufacturing a switch. Other use/energy
  consumers still receive canonical player appearances as before. A fainted,
  mismatched, or unresolved appearance cannot supply this confirmation.
- One **strictly accepted** reference match may now provide **Favored · low
  confidence** when both active move pools are known and the leading move has
  exactly one legal side. Strict score/margin checks are reapplied. The accepted
  flag alone is insufficient. Shared moves, unknown opposing pools, QTE windows,
  unresolved entries, and pre-appearance audio remain excluded.
- Two disjoint weaker comparisons still require the complete legal comparison
  set and their original margins. The six-second preference expiry and conflict,
  entry, QTE, switch, and reset clearing remain in effect.
- Preference evidence includes the original sound IDs and species/configuration
  context. A raw sound is not rewritten with an inferred side. Preferences do
  not emit FastMoveIdentified, create attacks, or award exact energy.
- The existing confirmation path still requires two distinct strict reference
  occurrences on a unique legal side. A confirmation replaces the provisional
  text with the checkmark through the existing HUD presentation.
- A higher-energy tie-break no longer collapses the provisional candidate list
  to the chosen hypothesis or hides another compatible move's audio preference.
  The energy forecast can continue its labeled assumption; the weak preference
  itself does not select a new move for energy accounting.

No additional audio/OCR worker, catalogue search, capture pass, or PCM retention
was added. Existing archives and source media are unchanged.

## Validation

- **1,101 unit tests pass**, zero failures, errors, or skips. Debug app and
  instrumentation APKs build successfully.
- The actual Magcargo fixture now produces an opponent **Rock Throw preference**
  after the pending identity checks resolve. Ember and Incinerate remain visible
  alternatives. It produces no confirmed Magcargo identity and no exact opponent
  energy award. The serialized source fixture is unchanged after processing.
- Regression coverage includes a strict first sample, missing low-ranked scores,
  forged acceptance flags, shared moves/mirrors, unknown pools, weak conflicting
  samples, overlapping captures, expiry, QTE, later strict confirmation, and
  lower-energy audio evidence surviving a higher-energy tie-break.
- Player reconfirmation tests preserve Alolan Raichu's form, slot, appearance,
  and source time, reject mismatched species/forms, and stop after a faint.
- Existing recorded idle-Camerupt, sparse-Dragonair, Sealeo missed-hit, and
  Greninja impact-fusion regressions pass. The October 23 paired control's first
  45 seconds contain no FastMoveSoundMeasured Articles on either phone, so that
  interval cannot validate acoustic side accuracy. It is not presented as such.
- API 34 emulator: **OK (1 test)** for the actual HUD's low-confidence label,
  retained alternative, and later confirmation checkmark. The saved screenshot
  was inspected: the text fits at the 415-pixel panel width. This test supplies
  presentation state; the separate recorded-input unit test verifies attribution.

Logs and the emulator screenshot are under
`/home/sean/overdex-device-backups/2026-10-24-audio-attribution/`.

Debug APK SHA-256:
`84c6fb1ea2d3cb80ff3945ae13117e8ecf78322dbbffb6514a86207f4ead1ef8`.

## Remaining live boundary

Neither physical phone was visible over USB during verification, so the update
is built and emulator-tested but not installed on the Moondrop or Pixel.
After installation, a new battle should check that a supported audio preference
appears on the correct side, clears during QTE/entry checks, and becomes a
checkmark only when the existing confirmation requirements are met.

The single Rock Throw sample is not proof of Magcargo's actual equipped move or
an exact attack count. Nearby recorded player and opponent impacts do not by
themselves establish which individual audio onset belongs to which hit. More
precise audio/impact association and live validation remain open.
