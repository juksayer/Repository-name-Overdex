# Source checkpoint and Trainer Comms direction

Prepared October 20, 2026 on `odx-recovery`, following `1ba1c13`, in the canonical
checkout `/home/sean/AndroidStudioProjects/Overdex`.

## Scope

The preceding commit included tracked changes but left required implementation
files untracked. This checkpoint adds the existing production dependencies,
reference audio and manifests, notification/artwork resources, unit and device
tests, curated fixtures, review summaries, and local analysis tools. It also
preserves the previously requested Pocket Pokédex prototype's source files;
its generated site and distribution archives remain ignored.

It includes the requested Match Sight removal, the refreshed code/navigation
inventory, and the [Trainer Comms sharing direction](../plans/trainer-comms-sharing.md).
The latter is a plan, not a new deployed messaging feature. Trainer/coach sharing,
groups, `.odxmatch` attachments, and future tournament coordination belong in
Profile → Trainer Comms, rather than a revived standalone Trainer Timeline.

Local runtimes, credentials, build output, Python caches, and raw device-review
folders are excluded. All 84 files in `docs/verification/evidence/` remain on disk.
Existing review reports may reference those local-only captures. Curated test
fixtures are committed separately so tests do not depend on that directory.

## Checkpoint-only build

Exported the staged Git tree into a separate temporary directory using `git archive`.
The exported source contained no local `.jbr`, `local.properties`, raw review
evidence, or existing Android build output. The build used the machine's existing
JDK, Android SDK, and Gradle dependency cache; this was not a dependency-download
or new-machine SDK installation test.

The first run found seven failing tests because two test classes loaded ten
screenshots from the local-only diagnostics folder. Copied those exact images to
[`app/src/test/resources/battle/recorded-evidence`](../../app/src/test/resources/battle/recorded-evidence/),
recorded SHA-256 and source paths, and changed the test paths. Pixels, assertions,
and original diagnostic captures were unchanged. No Kotlin unit or device test
retains a reference to the local evidence directory or a `/home/sean` or `/tmp` path.

Re-exported the staged files and verified:

- `:app:testDebugUnitTest`: **959 tests across 174 suites**, zero failures,
  errors, or skips.
- `:app:assembleDebug` and `:app:assembleDebugAndroidTest`: successful.
- Python stereo-inspection tools: **four tests passed**.
- Pocket Pokédex build: **1,025 entries and 1,025 local sprites**.
- All 80 production reference-audio manifest entries resolve to files with
  matching SHA-256. Candidate JSON, XML, WAV, and PNG signature checks passed.
- The ten promoted regression images match their retained originals byte for byte.
- Recognizable OpenAI, GitHub, AWS, Google API, and private-key patterns were not
  found in the new candidate text files. This is a limited scan, not a security
  audit of repository history or service configuration.
- The edited documentation's local links and `git diff --cached --check` pass.

Local build logs: `/tmp/overdex-checkpoint-build-initial.log` and
`/tmp/overdex-checkpoint-build.log`. Final changes after the passing export are
documentation only.

No phone installation, live battle, device-test execution, cloud configuration
change, message sending, or push was performed as part of this checkpoint. Build
success does not establish recognition accuracy or remote message delivery.
