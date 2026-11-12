# Pixel Blanche and Great League archive review — November 12

Read-only review of the two new archives after the shared-HP installation. Pixel APK SHA-256 remains `771d6f293c00b7cd845e6ca06710bbbd731feb1b17f7771268bdaf8446968f47`. No installation, service stop, archive rewriting, or production edits performed.

Evidence: `/mnt/overdex-backup/archives/overdex-device-backups/2026-11-12-pixel-noon-review`. Desktop copies matched device SHA-256; the standard inspector verified every included artifact. Original ZIPs, extracted timelines and inspector reports are retained.

| Match | Archive | Bytes | Articles | Verified artifacts |
| --- | --- | ---: | ---: | ---: |
| 12:10 Blanche (Sneasel, Sealeo, Vaporeon) | ede620a8-2068-4601-8376-e11c48358039 | 18139998 | 3766 | 291 |
| 12:15 Great League, per trainer (Alakazam, Exeggutor witnessed) | d6143667-345e-4443-83c5-f78ba98389c3 | 13280671 | 2757 | 213 |

Blanche SHA-256: `270b9e708c88920a401447701e3dc565f46e33459d61795e39fb99ca6b19b3e6`.
Great League SHA-256: `f6cc3b197b4510e9a4af03bda735da5e414ef9f0b95a6430d8da18605bad7f66`.

## Blanche

- MatchStarted exists. All three opponent species have identity testimony. Vaporeon's badge source was 73.83 seconds from record start, recorded 3.65 seconds later. The selected-crop recognition diagnostic reports 868 ms to admission, 854 ms native OCR across two passes, 2097 ms total.
- Charged-impact HP measurements exist: yellow fill about 27% at 55.73–55.93 seconds, a complete empty bar at 70.81, and about 91% fill at 73.50–74.22 while the next opponent is appearing. Do not treat that last rise as healing or proof of correct appearance attribution; the new species is independently witnessed near this interval. Without source video comparison, this demonstrates the lane produced measurements, not that the expanded territory specifically fixed a formerly missed handoff.
- Opponent ball counts include 3 → 2 → 1, yet no `ActivePokemonFaintedWitnessed` article exists. This requires separate resolver review; count observations alone do not tell us why a derived faint is missing.
- 48 FastMoveUseObserved articles exist, not a verified real attack count. No MatchEnded. Session-stopped testimony exists.
- HP article capture-to-record latency: median 81.420 s, nearest-rank-by-index p95 120.836 s, maximum 126.986 s. Late player ball counts reached the ledger roughly 179–181 seconds after source capture. These are event publication delays, not network lag estimates.

## Great League

- MatchStarted exists. Alakazam and Exeggutor are witnessed opponents. One faint article names Alakazam from the three-to-two opponent ball-count decrease. No other faint or MatchEnded article is present.
- Raw identity from “Go, Camerupt!” has source time 88.37 seconds and reaches article creation about 103.44 seconds later. This alone does not prove the exact first rendered HUD frame.
- 29 FastMoveUseObserved articles exist; no final-hit completeness claim is possible without a recording or trainer description.
- HP capture-to-record latency: median 4.606 s, p95 51.240 s, maximum 53.047 s. A visual-capture-gap article reports about 0.826 s; it does not explain the much larger ledger delays.
- Monotonic HP diagnostics independently locate substantial delay after submission: cumulative opponent source-to-submission mean 0.291 s, submission-to-timeline mean 14.893 s and maximum 52.302 s. At the last diagnostic, 34 submissions remained pending and the oldest was 52.152 seconds old. This supports publication backlog rather than merely slow pixel measurement. Diagnostic bounded retention/stop timing means it is not a complete census of all final archive latency.

## Conclusion and next boundary

These are valid tests of the new installed build, but not passing recognition acceptance. The next priority is the shared testimony-processing path and final drain behavior. Keep the HP geometry change separate from latency claims. The shared lane already has publication diagnostics; use those before adding redundant instrumentation. Inspect the serial collector's expensive work and preservation/stop barriers, then change one measured stage. Missing results and derived faints must remain missing in the source archive rather than being filled from assumed outcomes.

No accompanying video was supplied in this turn. Team/mode mapping combines the trainer's report with the witnessed species; actual outcomes, final attack timing and full opponent roster remain unverified.
