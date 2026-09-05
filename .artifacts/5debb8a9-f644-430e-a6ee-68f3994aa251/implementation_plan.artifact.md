# Plan: Troubleshoot Android Studio Logcat for Overdex

The user reports that `adb logcat` via terminal correctly shows logs for the **Overdex** app, but the Logcat window within Android Studio remains empty. I have verified that the app is running and producing logs on both the emulator and a physical device.

## User Review Required

> [!IMPORTANT]
> This plan focuses on diagnosing the interaction between the Android Studio IDE and the ADB server. Please verify which device you are expecting logs from in the steps below.

## Proposed Steps

### 1. Verify Device and Process Selection
Android Studio Logcat requires explicit selection of the target device and process.
*   Check the **Device** dropdown in the Logcat window. Ensure it matches the device you are viewing in the terminal (e.g., `emulator-5554` or `MD_PH_001`).
*   Check the **Process** dropdown. Ensure `com.example.overdex` is selected. If it says "No Process" or shows a dead process (grayed out), select the active one.

### 2. Validate Logcat Filters
The "New Logcat" in Android Studio uses a query-based filtering system.
*   Try clearing the filter bar completely to see if any logs appear.
*   If using a filter, ensure it is correct: `package:com.example.overdex` or `package:mine`.
*   Note that I observed some runtime logs using the tag `example.overdex` (truncated package name). Try searching for `tag:example.overdex` to see if those appear.

### 3. Check for Multiple ADB Versions
I detected two versions of `adb` on the system:
*   `/usr/bin/adb` (Version 35)
*   `/home/sean/Android/Sdk/platform-tools/adb` (Version 37)
If the terminal and Android Studio are using different versions, they may conflict.

### 4. Restart ADB Server
Force a refresh of the connection:
*   In Android Studio: **View > Tool Windows > Device Manager**, then click the triple-dot menu and select **Restart ADB Server**.
*   Or via terminal: `adb kill-server && adb start-server`.

### 5. Verify Build Variant
*   Ensure the app was deployed as a **debug** build. Release builds often strip `Log.d` and `Log.v` statements depending on R8 rules.

## Verification Plan

### Manual Verification
1.  Clear all filters in Android Studio Logcat.
2.  Select the device `emulator-5554`.
3.  Select the process `com.example.overdex`.
4.  Trigger a log-producing action (e.g., restart the app) and check for output.
