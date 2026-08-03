# Walkthrough — Fix MediaProjection Callback Crash (Android 15)

Fixed a crash in `DroidballService` occurring on Android 15 due to a missing `MediaProjection.Callback` registration.

## Changes Made

### Battle Observation Component

#### [DroidballService.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/observation/DroidballService.kt)
- **Defined `projectionCallback`**: Created a `MediaProjection.Callback` implementation that logs when the projection is stopped and ensures the service stops itself gracefully.
- **Registered Callback**: Updated `setupMediaProjection` to register the callback immediately after acquiring the `MediaProjection` token. This satisfies the requirement introduced in Android 14 (API 34) and enforced in Android 15.
- **Unregistered Callback**: Ensured the callback is cleaned up in `onDestroy` to prevent leaks.
- **Added Import**: Included `android.util.Log` for the new logging statement.

## Verification Results

### Automated Tests
- Executed `./gradlew :app:assembleDebug`: **Build Successful**.

### Manual Verification Required
- Deploy to an Android 15 device or emulator.
- Launch the ODX-FI and grant screen capture permissions.
- Verify that the service no longer crashes with an `IllegalStateException` and the observation flow begins correctly.
