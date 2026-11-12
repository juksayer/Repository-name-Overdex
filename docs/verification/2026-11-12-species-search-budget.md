# Bounded species-name comparison — November 12, 2026

## Why this change

The Pixel Rocket archive `9762aea9-3009-4297-987b-876f653c2393` has a 58,050 ms publication gap between testimony sequence 2608 (the contaminated Shadow Ball announcement) and sequence 2609. Availability records use another collector and were excluded from this comparison. This localizes a stall after announcement publication; it does not by itself identify every contributor to that interval.

The inline charged-move interpreter calls SpeciesTextResolver with the performer reading `b0 N1ght Slast Gourgeist`. Its previous fuzzy search enumerated every candidate-sized substring and fully evaluated every edit-distance matrix across 1,752 catalogue names. This happens before the testimony collector proceeds to its next record. Existing match processing and pending announcement retries remain unchanged.

## Change

Species fuzzy comparison now evaluates only lengths and edit-distance paths that can satisfy the existing per-name threshold. It stops a comparison when no path can remain within that threshold, and stops a candidate’s window scan at an exact match. It preserves exact scores for accepted candidates and maps all rejected scores to a sentinel above the threshold. It does not change confidence, ambiguity, glyph substitutions, permitted repairs, source clocks, or archive contents. The unlimited distance function remains available for other consumers.

The recorded performer search returned Gourgeist before and after. Initial local one-shot measurements were 437 ms before and 26 ms after. These are indicative computer measurements from separate JVM runs, not a controlled phone benchmark or proof that the full 58-second gap is fixed.

## Verification

SpeciesSearchBudgetTest compares bounded distances against the original full calculation for every pair of strings of length zero through three over A/4/O/0/X at budgets 0, 2 and 4 (73,008 combinations). It also compares the recorded reading’s accepted costs against all 1,752 catalogue names, preserving ties, plus noisy/clipped/glyph examples. The complete name search must still resolve Gourgeist.

Focused species and charged-move tests passed. Full unit suite passed: 2876 tests across 380 suites, 0 failures, 0 errors, 0 skipped. Debug APK build succeeded.

## Remaining boundaries

No installation or new live match in this task. This does not restore the missing final Earth Power/Annihilape faint/win in the original archive, remove HUD contamination, or prove all processing queues are timely. Capture-time evidence stays unchanged. A Pixel trial is needed to measure remaining publication delay. Moving independent interpretation off the shared publication path remains a separate architectural option, not implemented here.

Evidence logs: `/tmp/overdex-species-baseline.log`, `/tmp/overdex-species-focused.log`, `/tmp/overdex-search-final.log`; preserved copies belong in `/home/sean/overdex-device-backups/2026-11-12-pixel-rocket-review`.
