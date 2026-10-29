# Droidball journey audio

Requested October 29, 2026: use the Sonic spin/rolling effect while Droidball
rolls, skid when he brakes, and a pinball bumper for right-edge contact.

The trainer supplied these three files from the local folder
`Downloads/Sega Genesis - Sonic the Hedgehog - Miscellaneous - Sound Effects/Sonic 1/`.
These originals are preserved unchanged and are the current runtime sources:

| File | Role | Source SHA-256 |
| --- | --- | --- |
| `sonic_spin.wav` | Roll | `c81739b10a98190031ba653dc089a8daf474f3823462e80ddc811fbad550ea79` |
| `skid.wav` | Arrival braking | `dd45d7496a9629b3f8a5bde3e38b7796939ea24033bf95084971daf0632ec075` |
| `sonic_pinball_bumper.wav` | Right-edge contact | `52bf9294e0909a09610c0a2f723470b0464b2a91a38782f9565de183b6b9f9e8` |

Runtime copies are mono PCM16, 22,050 Hz, with the original speed and pitch,
low-pass filtering before downsampling and 3 ms fades at the boundaries.
Together the three runtime WAVs occupy 134,168 bytes.

Rebuild with `python3 tools/build_droidball_sounds.py`. Originals in this folder
are not packaged into the Android app. Runtime effects follow media volume and
prohibit playback capture, keeping presentation audio out of screen-capture audio.

## Earlier candidate retained for provenance

`sonic_roll_original.wav` preserves the unmodified rolling sample from the Sonic
fan project [Pascal-Sonic](https://github.com/GalaxyShad/Pascal-Sonic). The pinned
[source WAV](https://raw.githubusercontent.com/GalaxyShad/Pascal-Sonic/6200814fdae8fbdd4d1bcad6b83cdf4a033e2b78/sounds/roll.wav)
is distinct from that project's spin-charge and spin-release sounds. It is no
longer the runtime source after the user supplied their chosen files. It is a
Sonic/SEGA sound asset. The repository's source-code license does not establish
a separate redistribution license for this audio; no such license is asserted here.

- Original: stereo PCM16, 44,100 Hz, 2.019932 seconds, 356,360 bytes.
- SHA-256: `4bca16984cc0bd40e87baf5e015bd7802469c168c3bccc03d7a9094b27dacf3e`.
