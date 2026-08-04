# Implementation Plan - Brick XXX.2: Terminology Audit (factsRecorded -> eventsWitnessed)

This step continues the terminology audit by renaming `factsRecorded` to `eventsWitnessed` in the `BattleLifecycleAnalysis` model.

## User Review Required

> [!IMPORTANT]
> This change focuses ONLY on the `factsRecorded` -> `eventsWitnessed` transition.

## Proposed Changes

### Battle Model Layer

#### [MODIFY] [BattleLifecycleAnalysis.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/BattleLifecycleAnalysis.kt)
- Rename `factsRecorded` property to `eventsWitnessed`.

## Verification Plan

### Automated Tests
- `./gradlew :app:assembleDebug` to verify project compilation.
