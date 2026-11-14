# HP confirmation duplicate and derived entry identity repair

November 14, 2026. Completed Replay corrections; original live observations remain unchanged.

## Reproduced failure and narrow correction

The paired Pixel archive had four animations in the +42–50 second exchange after
the separate camera-pulse fix. Its recording shows three actual opponent hits.
The additional HP-derived use at +43.260 compared a roughly full bar in the entry
camera with the first settled field bar near 85% HP. The bar moved approximately
146 pixels vertically. The following equal HP sample at +44.317 also carried an
independent border-pulse use. The reader's scalar HP recovery did not consider
that geometric discontinuity.

Replay now establishes a fresh HP recovery baseline across substantial bar
relocation, incompatible bar sizes, or changed source dimensions. It preserves
ordinary movement, subsequent settled losses, independent pulses, accepted uses,
and every original HP record. Whole-bar acceptance and tiny surviving fill are
unchanged. This change does not claim no damage occurred during camera movement.

The vertical tolerance is deliberately conservative: at a 192-pixel bar width it
allows 115.2 pixels, preserving an older recorded attack bob of 87 pixels but
rejecting the paired entry jump of 146 pixels. Initial height-only tolerance
failed the unchanged `RecordedFaintAttributionReplayTest`; that failure is retained
in the external first-validation log. This is an empirically bounded Replay
recovery rule, not a generally validated camera classifier.

Pixel retains impacts at +44.317454197, +45.987806759 and +48.344304182. The
original pulse and canonical use survive at their original source time. Its first
animation remains 1.057 seconds later than the initial lower HP reading. This
slice fixes duplicate count, not that timing discrepancy or opponent move naming.
No wider merge window or guessed Incinerate duration was shipped. The diagnostic
now explicitly shows that forcing a 2.5-second duration on the delayed first
pulse can merge away a real following hit.

## Brief Camerupt / Gourgeist identity flip

The paired Moondrop archive reproduced PLAYER Camerupt → Gourgeist → Camerupt at
+64.4–68.8 seconds. Independent badges identify the entry as OPPONENT Gourgeist,
but a stored player-form conclusion derived from the wrong-sided entry bypassed
that correction and reintroduced the false player identity.

Replay now excludes that derived PLAYER identity only when its parent entry is
explicitly corrected to OPPONENT by the independent badge bracket. Missing or
ambiguous bracketing preserves the existing identity. Both source records remain
archived. The corrected Moondrop player stays Camerupt through +64.5–72 seconds;
Pixel's real player Gourgeist entry remains valid. Reversed input order passes.
This removes an artificial appearance boundary and restores a supported later
attack that previously did not receive a separate animation.

## Whole-match preservation comparison

| Paired archive | Fast Move uses before → after | Animations before → after |
| --- | ---: | ---: |
| Pixel | 11 → 10 | 11 → 10 |
| Moondrop | 16 → 15 | 14 → 15 |

Each removed use is HP-only reconstruction across relocation. Original independent
attack sources still reach a rendered cycle, and every surviving use retains its
source timestamp and article IDs. Some reconstructed starting HP fractions change
to the new valid baseline. Moondrop presentation cadence adjusts when the false
appearance boundary is removed; source times do not change. All charged-move uses
compare exactly unchanged. The Moondrop +66–73 exchange retains three attacks.
Video context at 103–110 seconds shows Gourgeist entering against Camerupt and
successive attacks with decreasing Camerupt HP; the saved contact sheet documents
that independent check. No new video observations were injected into the archives.

The source ZIP hashes, all 1,839 Pixel articles and 3,143 Moondrop articles match the
unchanged regression fixtures and review inputs. External reports, comparison
script, original and revised Replay exports, and installation backups live under:

`/mnt/overdex-backup/archives/overdex-device-backups/2026-11-14-hp-confirmation-repair/`

## Rock Tomb and missing final faint: remaining boundary

The user's causal account is supported by the paired recording. A confirmed faint
can support association with an existing move without an empty bar or new damage
measurement. The missing implementation is the explicit move-to-faint association,
not a rule requiring zero HP (see MOVE-05).

- Pixel has the mirror Rock Tomb announcement at +57.418, player Gourgeist entry
  at +64.670, and a player ball-count decrease at +73.596 that resolves Camerupt's
  faint. It lacks an independently accepted charge-side preparation cue for that
  mirror episode. The recording can be used as explicit review evidence, but the
  production local archive currently leaves ownership unresolved.
- Moondrop has Get Ready +49.866, sided player Rock Tomb +57.925, opponent
  Gourgeist entry +64.408, unchanged opponent shield readings around this episode,
  and a later opponent count decrease +81.939. It has the stronger local chain,
  but Replay still does not attach that established faint to Rock Tomb's impact.
- The final losing Gourgeist KO has no accepted terminal faint or zero-ball record
  in either archive. Result-only defeat remains separate from a witnessed faint;
  a LOSS alone cannot distinguish the KO from concession. This repair does not
  invent a third faint.

The charged animation/faint join and final terminal-faint recognition remain
unfinished. No claim that finishing-hit tracking is complete follows from this fix.

## Validation

The final focused run passed **44 tests**, with zero failures or skips. Tests cover the paired archives, reversed ordering, archive
immutability, stable-geometry half-second hits, tiny confirmation-adjacent hits,
finishing hits, tiny surviving HP, the older Carbink/Gourgeist attribution archive,
and the original Sealeo finishing-hit regression. The older regression is unchanged.

Final full validation: **3,016 tests total; 3,010 passed, six optional diagnostic
tests skipped, zero failures or errors**. `:app:testDebugUnitTest :app:assembleDebug`
completed successfully in 3m 8s. `git diff --check` passed. External XML results,
counts, and logs are preserved. This is desktop regression/build proof, not a
claim that the rendered phone Replay or a new live battle has been user-validated.

## Device installation

Installed with app data preserved on **Pixel 4** (`99261FFAZ0017J`) and
**Moondrop** (`MDPH00124112500414`), after confirming no active capture or app
service. Both installed APKs were pulled back and matched the built APK:

`a8a7f86cc2e7fc88e282914b0a23e269af1cd5173bd4be7d78c0eb09f63da6fd`

Both apps launched successfully. Previous APKs and installation logs are on the
backup drive. No commit or push was made. The first user check is the existing
paired match: Pixel +0:42–0:50 for three attacks, Moondrop +1:04–1:09 for stable
player Camerupt identity. Rock Tomb-to-faint linking and the final losing
Gourgeist faint are explicitly not claimed fixed by this build.
