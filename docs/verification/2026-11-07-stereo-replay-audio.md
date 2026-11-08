# Replay attack stereo mix

Reconstructed move effects now use the attacker's side to select channel gains:

| Effect | Left | Right |
| --- | ---: | ---: |
| Player Fast Move | 1.00 | 0.25 |
| Opponent Fast Move | 0.25 | 1.00 |
| Either side's charged move | 1.00 | 1.00 |

`ReplayAttackSoundEvent` carries the side from the existing Fast Move cycle or
charged use. An HP observation belongs to the damaged side, so the tests check
that it produces sound for the opposing attacker. An unresolved side remains
centered. SoundPool receives the gains for each individual playback event;
recognition input, archived audio, cries and transport effects are unchanged.

All 255 currently replay-eligible move WAV references are mono (72 Fast Move,
183 charged). The six captured recognition-only excerpts are not admitted to
replay. No source separation of the original game recording is needed.

The ten focused audio tests pass. The full 1,996-test suite passes without
failures, errors or skips, and the debug APK builds. A listening example made
from the existing Incinerate, Charm and Rock Tomb assets is retained privately
with its gains and hashes. It is a mix demonstration, not evidence from a Match.
An audible phone check needs stereo headphones or speakers; the mathematical
channel and event-owner tests do not establish what a listener hears.
