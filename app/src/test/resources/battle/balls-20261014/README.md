# Recorded Poké Ball visibility regression

Source: Moondrop match `17f61baa-b1fb-4eb0-aef1-1f3746333dea`, recorded October 14, 2026. These are the original 105 × 32 dedicated opponent Poké Ball crops, without resizing or image processing.

- `before.png`: 15:33:41.971 CDT, source article `44aee580-8772-4ce3-ae94-9d4a3bcbc65d`, SHA-256 `2c03d73d18035f785955a5cccc5c3e1a88aec994fbfc3c6a5541244dbd53e948`.
- `hidden-first.png`: 15:33:42.322 CDT, source article `41421bae-d26a-4d5d-b97c-43817fafa25b`, SHA-256 `1a0db919d2af63508e6fdbb2a317905bce7ac6a66051d095178da4f55fdf5100`.
- `hidden-second.png`: 15:33:42.743 CDT, source article `8d7020c3-036f-445a-8b87-04c9d346b0c8`, SHA-256 `12f143d8299e89651c82075f4df2353234491986063b1d1b31eb99af43171bf9`.
- `returned.png`: 15:33:56.865 CDT, source article `5b6edb15-6696-43e7-860e-32a9314e03a7`, SHA-256 `bca20af7d2b76e9acc4f1bfc62ef03cc83ded8fbb1c28f3b70d68b21602c3e2b`.

The two hidden frames contain a pale rounded corner and black background, not Poké Ball silhouettes. The old counter returned zero for both, so the repeated-count witness falsely fainted Vaporeon. Expected sequence: one survivor, unavailable, unavailable, one survivor. Preserve unavailable as null, not zero.
