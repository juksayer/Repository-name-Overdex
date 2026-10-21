# October 14 replay-open crash and QTE review

## Scope and source records

Reviewed the five new internal Moondrop archives copied to `/tmp/overdex-review-20261014-replay-crash/`. All archive manifests, timelines, and declared artifact hashes passed inspection. Original phone files were read only.

| Start | Match ID | Articles | Result | Verified artifacts |
| --- | --- | ---: | --- | ---: |
| 13:13:40 | b5cbd643-c6a3-4129-86df-01c87b4b2621 | 2,167 | WIN | 657 |
| 13:15:38 | 3a72baf6-8c7e-43f6-b7f0-584a204123cb | 2,390 | WIN | 679 |
| 13:23:03 | d99c6fbd-e2b8-49fb-9583-78c54403c2c0 | 116 | Stopped before battle | 0 |
| 13:23:50 | e25d5a4a-c39e-4acc-ae5a-f5e579b0ff48 | 2,616 | WIN | 792 |
| 13:38:38 | 2216a735-7b79-4d40-bcda-645a55030dd7 | 2,170 | WIN | 569 |

The newest recording identifies Abomasnow, then Camerupt on the player side, and Sneasel, Sealeo, and Vaporeon on the opponent side. It contains Get Ready cues and two damaged Rock Tomb announcements. Retained full frames clearly show Rock-type QTE bubbles and a Rock Tomb announcement. It has no accepted countdown glyph or `MatchStarted` article, despite a supporting start observation and later battle evidence. It also has no fused Fast Move uses, although raw HP and motion measurements exist. Fixing replay initialization does not repair those live evidence gaps or invent historical testimony.

## Reproduced crash and correction

The phone recorded the same `NullPointerException` twice when opening replay, at 13:40:58 and 13:42:13. The stack runs from `reconstructAnnouncementTracks()` into lazy `endNanos`, which iterates `recordedChargedMoveUses` before its constructor initializer has run.

The cycle occurs when a species catalogue is available, there is no confirmed Match start, and the archive contains a result or faint. The first screen composition can work with an empty catalogue; the later recomposition after reference data loads crashes. Earlier tests with confirmed starts or empty catalogues missed this case.

Identity recovery now uses the final recorded article time as its fallback roster boundary. Presentation duration remains separate and can still extend through the finishing attack and KO pose. No archive timestamp, result, or identity is changed.

`ReplayMissingMatchStartTest` first reproduced the null failure with a small completed-match case. It now also covers an empty archive and the actual latest recording before and after catalogue loading. The recorded fixture retains 1,178 original articles, omits media and unrelated measurements, zeroes wall dates, and shifts monotonic time to the recording origin. Source IDs, payloads, and relative timing are retained.

`RecordedReplayOpenDeviceTest` reads all five original archives on the phone, constructs replay with real catalogue IDs, samples scenes through each recording, and checks serialization remains unchanged. It also opens the production replay screen for the newest match with asynchronous species and move lookups.

## Live QTE audio investigation

The user clarified that the four-pop limit occurs in live Pokémon GO. The live service uses a Pokémon GO UID-filtered `AudioRecord` playback capture, with one read loop at 48 kHz. No audio-focus request, stream-volume mutation, audio-mode change, or live sound playback was found in this path. The audio-service snapshot lists Overdex's `REMOTE_SUBMIX` recorder as not silenced. These findings do not rule out device or performance interaction and do not prove a Pokémon GO defect.

Two preserved 13-second charged-effect excerpts from the latest match contain nonzero signal through QTE/impact portions. They start about 2 seconds after the corresponding Get Ready cues, so they are insufficient to verify every bubble pop. Their source references are:

- `841ad6b9056ab8b36cf43efed07353c17e28b20a81f2765ea92afa4fa02eab9f.wav`
- `151e060f6980e3a3f249a9ff4ad869e3248561a8fd6a9e0dbb04cbb2d8dc536b.wav`

No audio behavior was changed on this evidence. The useful comparison is the same practice QTE with Droidball fully stopped and with it running, keeping game sound settings unchanged. If the limit only occurs with Overdex, separately examine capture load and HUD touch interception; do not assume missing audio means a missed bubble.

## QTE type-icon evidence

Ten retained full frames near the two QTEs were copied for inspection. Frame times were located from filesystem timestamps and are approximate, not canonical event times. `qte-contact.png` in the review directory shows visible Rock symbols, Nice/Great/Excellent text, and the following charge announcement.

One clear source frame is `7926b300cf84c398a0b5fe7d030f1e069dfcba810b259f54d2c054938dd0e433.png` at 672×1493. Several bubbles cross the center horizontally below the HUD. This supports a dedicated central QTE observation region. The existing `PokemonGoTypeIconMatcher` has 18 symbol references but currently serves stationary team badges; this review does not activate it for moving QTE bubbles.

A QTE reader should locate complete bubbles within the region, normalize scale/rotation, compare the interior symbol across all 18 references, and retain repeated agreement with a runner-up margin. Color can corroborate. A type observation belongs to the current player charge episode and can narrow legal moves; it cannot alone distinguish two charged moves of the same type. Only one clear repeated symbol is needed for type confirmation, rather than counting every bubble. Samples from additional types are needed before choosing a universal crop or claiming classification accuracy.

## Validation

- Full unit suite: 749 tests, zero failures/errors/skips. App and instrumentation APK builds passed.
- The initial phone test was rejected by JUnit because its expression-body method returned the screenshot encoder's Boolean. Its return type was corrected to Unit; no app-code change was needed for this test-harness repair.
- Final physical test passed in 15.842 seconds. All five original archives opened with real catalogue data and were sampled through their presentation ranges. The production replay screen then rendered the newest archive after asynchronous reference lookup, showing Camerupt and Vaporeon. Screenshot: `/tmp/overdex-review-20261014-replay-crash/recorded-replay-open.png`.
- App installed with replacement installation and launched on Moondrop `MDPH00124112500414`. App SHA-256: `cdab42460f3b7dace4ddb9e9effb3809516e1b0e46dde2ca7db986c2eec29818`; final test APK SHA-256: `80e4c098510aa95716134bc7520e94db431c8a2fe9e91169f38edbe5c93fe734`.
- All 11 calibration preference/profile files are byte-identical before and after installation and phone testing. App data was not cleared.
- `git diff --check` passed. Logs are `full-build.log`, `device-test-build.log`, and `device-replay-open-final.log` in the review directory. No commit or push was performed.
- Live bubble-audio cause and moving QTE icon classification remain unverified. No audio fix or live QTE type witness is claimed by this crash correction.
