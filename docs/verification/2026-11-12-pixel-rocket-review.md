# Pixel Rocket battle review — November 12, 2026

Review only: no production changes or installation. Original archive preserved.

## Evidence

- Video: `/home/sean/Desktop/Screen_recording_20261112_103511.mp4`, 178.565 seconds, 816×1712, no audio stream.
- Archive: `2026-11-12_10-34-11_9762aea9-3009-4297-987b-876f653c2393.odxmatch.zip`.
- SHA-256: `9ac743b936ac57ec7ec1c464281ccf4cbe8fc3108559d8edbbda7baffe1a2096`.
- 1,986 articles; all 216 declared artifacts verified.
- Read-only evidence and probe results: `/home/sean/overdex-device-backups/2026-11-12-pixel-rocket-review`.

## Cross Chop at Replay 1:42

User reported Turtonator attacking Mankey with Mankey’s Cross Chop. The archived charged-move record at +101.067 seconds identifies OPPONENT Mankey. A temporary regression over the extracted archive passed (1 test, zero failures/errors/skips): every generated Cross Chop action in the sampled interval belongs to OPPONENT, with Mankey active. This checks the default Replay model, without supplemental crop identities or move-pool maps; it does not prove rendered animation direction.

The Pixel’s paused 1:42 screen also says “Mankey used Cross Chop!” and its marker identifies OPP. The reported reversed visual remains unresolved. No continuous Replay animation was captured or validated. Temporary test source and XML were retained in the evidence directory and removed from the repository test tree.

## Recognition findings

The archive correctly attributes faints to player Turtonator, opponent Mankey, and opponent Hitmontop. The video shows Camerupt’s final Earth Power finishing Annihilape, followed by victory/rewards. The archive has neither that final named attack, Annihilape’s final faint, nor MatchEnded.

Result-reader telemetry reports recovery-lane frame delays reaching 78.044 seconds with 24 pending frames. One Shadow Ball announcement was published 77.824 seconds after its source time. Publication delay is not synonymous with OCR execution cost. HP locator cumulative mean was approximately 375 ms, with a maximum of 8.574 seconds; this is a candidate workload to investigate, not proof of the sole cause.

Native announcement OCR includes overlay prediction text beside “Gourgeist used Shadow Ball,” providing concrete evidence of crop contamination. Final attack assignment and processing backlog need investigation before weakening faint requirements.

Latest retained HP measurements show player approximately 49%, opponent approximately 6%; these precede the final charge sequence. Compact archives omit uncited crops, so the latest retained crop does not establish that screen capture stopped. Later capture activity is present.

## Acceptance boundaries

No fix claimed for the reversed visual or missing final KO. No new live validation, installation, archive rewriting, or commit. Existing recording is sufficient for the next investigation; another match is not required to establish these failures.
