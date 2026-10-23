# October 23 controlled paired battle

## Custody and ground truth

Copied read-only from Moondrop MDPH00124112500414 and Pixel 4 99261FFAZ0017J to `/home/sean/overdex-device-backups/2026-10-23-controlled-energy/`. Original device archives were not modified or removed.

User confirmed: three Incinerates, five-second pause, three more, pause, charged move; repeated against another opponent, then repeated the pattern with Alolan and ordinary Raichu. Exact later tap counts and timing were not independently measured.

- `2026-10-23_09-47-00_c8590382-65e9-4a18-befc-cc5a97d2b317.odxmatch.zip`: 2376 articles; SHA-256 `24f00f7c1896f9d2855543655b038fd53291bcd867ff9d99f230c6e4d56b98f0`.
- `2026-10-23_09-52-02_6f3adf17-5091-45a6-94ee-a48d9974ded0.odxmatch.zip`: 118 articles; SHA-256 `2015a018ae81c72cb9dc2619e6cdeae3d09b337d6bcb76335af79e160bcd5272`.
- `2026-10-23_10-49-37_13777ce9-74c2-4a7c-8832-5d64b4be7b70.odxmatch.zip`: 7939 articles; SHA-256 `5fce112709eeb8c1cbbb47972e39a7ccc8dbd5ccd494023fe2d9164742468a21`.
- `moondrop.odxmatch.zip`: 4952 articles; SHA-256 `1aaf5ccb4de31b8368496b032279fc3be23565f6c6c5a45bbb12f49b7420763c`.

## Opening control succeeds

Times below are relative to each recording's own observed GO, not a shared hardware clock.

| Hit | Moondrop player | Pixel opponent |
| --- | ---: | ---: |
| 1 | 2.99 s | 3.13 s |
| 2 | 5.52 s | 5.47 s |
| 3 | 7.92 s | 7.89 s |
| 4 | 16.49 s | 16.36 s |
| 5 | 18.86 s | 18.93 s |
| 6 | 21.53 s | 21.38 s |

Both timelines preserve six uses and six +20 energy derivations. These are generated-energy increments, not an uncapped balance: absent spending, six Incinerates generate 120 but stored energy caps at 100. Neither records extra attacks between the groups or between 24 and 45 seconds. Moondrop's six HP drops are 9.52%, 9.52%, 8.99%, 9.52%, 9.52%, 9.52%. The idle Pixel player has no Fast Move uses in the entire controlled recording; Moondrop likewise has no opponent uses.

## Later discrepancies remain

- At 45–57 s, Pixel records eight Turtonator attacks versus three on Moondrop. Several extra events depend on border pulses without corresponding damage.
- At 57–68 s, Moondrop records Get Ready at 57.22 s and Brutal Swing at 64.73 s, with zero Fast Move uses in that interval. Pixel records five opponent uses at 58.80, 59.96, 60.64, 61.16 and 62.30 s, then Brutal Swing at 64.58 s. Its timeline lacks the corresponding charge-control cues. This is a detection/gating failure, not proof the opponent generated energy during QTE.
- Pixel records named charged spends for Brutal Swing, Trailblaze and Thunder Punch; Moondrop records only Brutal Swing. Neither names the earlier charge that the user describes after the opening six attacks. Do not manufacture its name or cost.
- Both phones record eleven uses in the Alolan segment; ordinary Raichu has six on Moondrop versus three on Pixel. These totals need finer evidence review and are not accepted as true tap counts.
- Moondrop's roster mistakenly contains Greattusk, then Delibird in slot 2, while slot 3 is Alolan Raichu. The Delibird source frame is visibly VS: the slot crop contains trainer clothing, not a party card. The original party screenshot shows costumed ordinary Raichu in slot 2, nicknamed `great`, and shiny Alolan Raichu in slot 3. The OCR resolver expanded that nickname to Greattusk. Saved configurations name both Raichu slots without their form and place Spark/Trailblaze in slot 2 rather than the observed Alolan slot 3. This combination defeats configured move binding and leaves later energy unresolved.
- The earlier Pixel 09:47 recording is a separate battle with Greninja/Lucario/Obstagoon, WIN, four named charged spends. The 09:52 archive is a short prebattle session with no MatchStarted or result; it is not another completed match.

## Bounded fix

Roster OCR and visual publication now require source time within the existing two-second agreement window of confirmed Team Select. VS/countdown/start/result evidence closes the source window. Delayed processing of a valid earlier frame remains allowed. This prevents the recorded later VS frame from publishing Delibird; it does not claim to repair every bad initial crop or retrospectively rewrite the old roster.

Next: inspect Pixel shield-decision/HP crops and bound energy credit by supported attack cycles; validate Raichu slots and configured moves before another controlled test. Replay must not silently import the other phone's observations into its original timeline.

## Validation

Focused Team Select tests passed. Full JVM suite: 1,046 tests, zero failures/errors/skips. Debug APK build passed. Not installed: the Moondrop still has an active Droidball service; leave that session intact. No new battle has yet validated this gate.
