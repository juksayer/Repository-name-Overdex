# First-release scope and accessibility probe boundary

## Change

The [first-release checklist](../plans/first-release.md) defines a small training
and recording beta, with distinct observed evidence, reconstruction and
best-play forecasts. It records acceptance work rather than treating implemented
features as validated. The next battle work remains the suspicious Incinerate
count and settled charge-fill comparison in the existing recordings.

The diagnostic accessibility service, its manifest registration and its XML
configuration now live in `app/src/debug`. Release builds omit the component.
Signal Observatory and Researcher Mode omit its menu entry and physical-control
focus target; navigation callbacks and route registration are guarded as well.
Debug retains the existing probe. Timeline Viewer and Match Calibration remain.

A caller search found `AccessibilityProbeManager` used by its diagnostic screen
and service, not the live battle-observation pipeline. The shared model/manager
and screen source remain in main; this change excludes the actual Android
service and prevents entry to the diagnostic workflow in release. It is not a
general removal of all diagnostic code.

## Verification

From the canonical checkout, with its `.jbr` runtime:

```sh
JAVA_HOME="$PWD/.jbr" PATH="$PWD/.jbr/bin:$PATH" ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease --console=plain
```

- Build succeeded, including release vital lint and both APKs.
- All 1,067 unit tests passed; zero failures, errors or skips.
- Parsed both merged manifests: debug contains one accessibility service, release
  contains none, and both retain `DroidballService`.
- Inspected every packaged DEX and the ZIP resource entries: the probe service
  descriptor and configuration XML are present in debug and absent from release.
- Reviewed release focus order and navigation guards in source; there was no
  device UI test in this slice.
- Documentation links, generator shell syntax and `git diff --check` passed.

APK SHA256 values:

- Debug: `80e9ce343d56d81a30fd0f77d7acd08b6d2f9597976fb0ef4e82efa723ef41c2`
- Unsigned release: `f4941b34a54ee9f937d52b6cda9ec52505c0189731b0f688236a68630e474fc8`

## Remaining boundaries

No phone was updated and no live recognition accuracy was measured. The release
APK is unsigned, not a store-ready artifact. Recording-only controls, privacy and
backup configuration, final distribution scope, asset rights, performance and
Play-policy assessment remain open in the checklist. Removing the diagnostic
service does not establish approval of live battle assistance.
