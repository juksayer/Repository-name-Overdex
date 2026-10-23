# October 23 individual battles (12:40 / 12:44)

User confirmed continuous attacks in both independent battles. These are not paired perspectives and counts must not be compared as the same encounter. Both devices had 36ea52e installed. Originals remain on phones; backups are `/home/sean/overdex-device-backups/2026-10-23-individual/`.

| Device | Match ID | Articles | Verified artifacts | SHA-256 |
| --- | --- | ---: | ---: | --- |
| Pixel 4 | 9f1375a3-e2c9-4fd5-8f1d-533d15f24450 | 1558 | 227 | 69dd4262433d3c22fc5e8296e8cc63cb25bee5134cd6b7240ce62f84e6aa8d7e |
| Moondrop | df2137bb-cfda-4529-8cca-1fd84ed19af5 | 2125 | 268 | e05e9f57e3fb0c58bb1d0f46f9b9dc8253a542c33978d0ff65932dc71c32a316 |

Both archive structures, article counts, and every declared artifact hash verified. Both record GO and WIN. Opponents were Combusken, Magcargo, Flareon.

## Findings

Pixel records eight Swampert uses (configured Mud Shot), three Hydro Cannons, six Combusken uses and one Magcargo use. Eight Mud Shots at nine energy cannot fund three 40-energy Hydro Cannons from zero: at least fourteen Mud Shots are needed. This establishes undercounting, not the exact number missed. Combusken's Ember is only a higher-energy tie-break hypothesis on Pixel, not confirmed identity.

Moondrop records fourteen Turtonator uses (Incinerate), two player Dragon Pulses, and an opponent Combusken Flamethrower. Combusken Ember has effectiveness support; Flareon Fire Spin has border/motion corroboration. Opponent counts are four Combusken, two Magcargo, five Flareon, not externally verified true counts. Repeated losses and pulses may still be separately counted in some short intervals.

Measurement gaps remain: Moondrop opponent-HP measurements are absent in the 35–40 s bucket; Pixel opponent measurements are absent in 30–40 s. These may include unrecognized charged phases or tracker/capture availability gaps; do not assume idle play from silence. The user explicitly reports continuous attacks.

## Confirmed continuity regression and repair

In Pixel's Flareon entry, the opponent bar arrives at x=331, y=638 at +42.14 s with fill .80851066. At +42.27 s, geometry is within the same-position tolerance and fill decreases to .75132275. The 250 ms geometry-settling window introduced in 36ea52e continuously replaced the baseline, swallowing this real-looking settled loss. The numerical fixture `flareon-entry.tsv` retains original GO-relative source timestamps, rectangles, fill and confidence from +41 to +44 s.

The detector now resets only on a geometry discontinuity. Subsequent samples use the existing 90 ms HP-loss stability check immediately, rather than continually replacing the baseline for 250 ms. The new recorded regression recovers the +42.27 s loss, while the earlier idle-camera regression still produces no false 93.6% loss and the six-Incinerate fixture still counts six. This repairs a specific regression, not all missing attacks. Raw archived articles remain unchanged; no attacks are manufactured from the energy deficit.

Validation: 1,056 JVM tests passed with zero failures/errors/skips; debug APK built. New live-battle verification remains pending.
