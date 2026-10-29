# Moondrop previous-build match and repeated attack animation — October 29, 2026

## Recording and ground truth

The user confirms continuous attacks and reminds us that Pokémon GO's Fast Move
animation can stutter, repeatedly playing the beginning of the animation. This
is evidence about visual behavior, not proof of additional completed attacks.

Reviewed match `b933d564-5d8c-433f-a052-afc6d0ce39b8`, opened at 14:39:33 CDT.
The copied archive matches the phone SHA-256; all **2,076 articles and 211 declared
artifacts** verify. The installed recording build's APK hash matches the earlier
`0e3cf57` installation:
`3e3a1ddc9673f2918253a63fca94f0fc76b02f197e412e3933d2026c07cd1e2d`.

Player roster is Golbat, Greninja and Victreebel; Golbat is the witnessed active
combatant. Opponent badge images establish Combusken, Magcargo and Flareon. The
opening audio-only Pikachu hypothesis is contradicted by those images and must
not be counted as a fourth actual opponent. The preserved result clearly shows
**YOU WIN! at 14:44:17.770**.

## Attack observations and the narrow correction

The original fused ledger has **24 player Golbat uses, 8 opponent Combusken uses,
and 2 opponent Magcargo uses**. These are recorded observations, not a verified
complete attack count. No Flareon use was established; that does not prove
Flareon was idle. Energy was credited for 19 player uses; Combusken's Ember choice
remains an explicit tie-break assumption rather than exact opponent energy.

At match-relative +21.667s, +21.941s and +22.223s, three separate Golbat uses
were inferred despite its trusted one-second Wing Attack cycle. The middle use
contains a repeated border pulse while HP remains on the first hit's plateau.
Pairwise loss/pulse matching had already separated the first pair, leaving this
repeat as a standalone hit. The last HP decrease is a separate measurement and
may include delayed damage; it must not be discarded solely on elapsed time.
The existing energy timing guard already rejects additional impossible credits.

The new fusion rule folds a border-only repeat into an already paired loss/pulse
when all of the following hold:

- A trusted move duration is known, and the repeat lies within less than half
  that cycle and within the existing association window, measured from the
  original loss rather than chained from each repeat.
- At least three continuous HP samples show the same plateau and geometry.
- No distinct intervening HP decrease or battle boundary contradicts the merge.

The repeat and plateau Articles remain predecessors, with the repeat labeled
`HP_BORDER_REPEAT_ON_PLATEAU`. Correlated support does not increase confidence.
Real half-second attacks, changed geometry, absent continuity and unknown
durations remain separate. The rule does not rewrite archived Articles; Replay
still retains already-canonical duplicate uses from this old recording.
Repeated-pulse receipt times retain the existing 1.2-second reconsideration
grace, so late switches or distinct HP steps can split a provisional association
before it is published.

## Independent recognition gaps

- The preserved GO image predates the fallback MatchStarted by **460.069ms** on
  the source monotonic clock. Its OCR arrived just after fallback publication.
- Shadow Ball and Poison Fang announcements are visually supported, with
  publication delays of **2.171s** and **3.950s**. A further GREAT progression
  supports another QTE, but its named release was not established.
- BLOCKED above Golbat and a player shield decrease support a missed opposing
  charge; its name remains unknown.
- Get Ready is faint inside the Announcement crop; NICE/GREAT are clipped at
  its top. Their missing interpretation prevents the QTE icon phase from opening.
- All **12 charged-button name readings are empty**. Viewed crops show ground
  and circle edges instead of names. Current saved geometry is preserved; a full
  battle-frame placement audit is needed before changing it.
- Six strict Wing Attack audio matches and one Rock Throw match provide support.
  Original capture is 48 kHz stereo; Fast Move analysis copies use 8 kHz mono.
  Twenty of 21 retained WAVs contain nonzero samples.

The earlier 13:36 recording `badd7399-8d1c-460f-a85b-1a82bbf090aa` is a Pumpkaboo
encounter/navigation session, not a second battle. Its 532 articles and 72
artifacts verify; it establishes no attacks or MatchStarted despite raw scene
measurements. It is retained separately for comparison.

## Evidence and acceptance boundary

Original archives, source crops, lifecycle/energy reports, latency summaries,
settings backups and installation provenance are retained at
`/home/sean/overdex-device-backups/2026-10-29-moondrop-previous-build-review/`.

The regression tests reproduce the recorded plateau sequence and delivery-order
variations, preserve the next measured decrease, protect half-second attacks,
and verify that repeat support does not inflate confidence. Passing these checks
does not establish the true full-match attack count or eliminate every possible
stutter/latency artifact. A new controlled recording is the live acceptance test.

## Final verification and installation

- **40 focused inference tests pass**, including a delayed pulse followed by a
  late defender replacement or distinct HP decrease.
- Full suite: **1,438 tests / 227 suites**, zero failures/errors/skips. Debug APK
  build passes. No UI changes are part of this correction.
- Installed on the idle Moondrop and Pixel 4. Device APK hashes match the built
  APK. All **16 Moondrop and 6 Pixel** calibration/profile, current-team and HUD
  files retain their before-install hashes. Original match archives are intact.
- This installation does not constitute a new live-battle count validation or
  repair the old recording's canonical duplicate events.

Installed APK SHA-256:
`30fcac1b76b0e2a88ee4a9c411c2a87a2b0606567c5f223a71d90ba5e865f255`.
