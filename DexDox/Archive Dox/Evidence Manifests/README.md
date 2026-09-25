# External Evidence Manifests

Raw observation evidence is preserved outside the source checkout so large capture sets do not become application source or inflate every clone of Overdex.

Each manifest records the repository-relative path and SHA-256 identity of every archived file. To verify a restored archive, place its contents at one common root and run:

```bash
sha256sum --check 2026-09-25-countdown.sha256
```

`2026-09-25-countdown.sha256` covers the countdown crops, countdown template sources, calibration snapshot, and associated tar archives recovered from the working checkout on 2026-09-25. All 717 entries passed verification when archived.
