# October 19 charge-sequence forecast fixture

`oct19-charge-forecast.json` is an unchanged article subset from match
`908a4239-0f21-4f1f-9491-b477943d8d1e`, recorded on the Moondrop on October 19, 2026
at 18:50 local time. The original archive contains 2,202 articles over 127.24 seconds.

- Source: `2026-10-19_18-50-04_908a4239-0f21-4f1f-9491-b477943d8d1e.odxmatch.zip`
- Source archive SHA-256: `2d3e48a0526405ee4af41c5f42cd165d533a9ed868b22ad1ae8f8b4e9e373413`
- Fixture SHA-256: `a8650f2d214a7323b913b5c3842c9fbeb3e82213150a5ff44bd6bced61dc0221`
- Fixture: 759 articles, including 685 HP measurements, identity changes, move uses,
  move identities, charge announcements/costs, faints and match boundaries.

Each retained article, including timestamps, IDs, payload and predecessor references,
was compared with the original and is unchanged. Some predecessors are outside this
subset; this is a focused test fixture, not a complete archival export.

`RecordedChargeForecastTest` reconstructs positive paired-bar observations from
measurements sharing a capture timestamp. Missing observations expire through the
forecast's freshness limit. It does not create missing-frame testimony or claim
that absence alone proves a QTE.

The test deliberately uses fixed move durations and energy gains to isolate
pause/resume behavior. These are test budgets, not assertions about these species'
actual move balance. It checks that energy stays constant in the recorded Electrode
and Jolteon charge gaps and resumes when sustained battlefield visibility returns.
It does not validate live capture accuracy, attack counts, or audio recognition.
