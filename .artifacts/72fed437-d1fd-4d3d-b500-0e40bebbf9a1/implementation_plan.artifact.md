# Implementation Plan - IME Trigger Hypothesis Testing

This plan aims to isolate the specific variable or combination of variables that causes the Android IME to appear in `ChatScreen.kt` despite `showKeyboardOnFocus = false`.

## Goal
Determine the specific trigger for the Android IME using isolated experiments.

## Operational Protocol
> [!IMPORTANT]
> **RESTORATION REQUIREMENT**: After each experiment, the temporary diagnostic change MUST be reverted (using `git checkout` or equivalent) before proceeding to the next experiment. This ensures a clean baseline for every test.

## Proposed Experiments

### 1. Experiment B: `readOnly` Transition Without Programmatic Focus
- **Setup**: Modify `LaunchedEffect(interactionMode)` in `ChatScreen.kt` to **NOT** call `requestFocus()`.
- **Action**: Trigger `interactionMode = Editing`.
- **Goal**: Is changing editability alone sufficient to trigger the IME?

### 2. Experiment A: `requestFocus()` Alone (Baseline Editable)
- **Setup**: Modify `TextField` in `ChatScreen.kt` to be permanently editable (`readOnly = false`).
- **Action**: Trigger `interactionMode = Editing` (which calls `requestFocus()`).
- **Goal**: Is `requestFocus()` alone sufficient to trigger the IME on an already editable field?

### 3. Experiment D: Focus a Non-TextField Control
- **Setup**: Add a focusable `Box` or `Button` near the input area. Change `LaunchedEffect` to request focus on this new element instead of the `TextField`.
- **Action**: Trigger `interactionMode = Editing`.
- **Goal**: Does Android react to any programmatic focus transition, or is it specific to `TextField`?

### 4. Experiment C: Sequenced Transition
- **Setup**: Insert a temporary delay in `LaunchedEffect(interactionMode)` sufficient to separate the editability transition from the focus request.
- **Action**: Trigger `interactionMode = Editing`.
- **Goal**: Is the timing or simultaneous combination of the two events (focus + editability change) responsible?

## Results Matrix

| Experiment | IME Visible | Terminal KB Functional | ImeTracker Event | Conclusion |
| :--- | :--- | :--- | :--- | :--- |
| **B**: State Change only | | | | |
| **A**: Focus on Baseline Editable | | | | |
| **D**: Focus Non-TextField | | | | |
| **C**: Sequenced Events | | | | |

## Verification Plan

For each experiment, record exactly these three observations:
1. **Android IME Visible**: Yes / No
2. **Terminal Keyboard Functional**: Yes / No
3. **ImeTracker Event Observed**: Yes / No

### Execution
- Deploy app for each experiment.
- Monitor Logcat and visual keyboard appearance.

## Success Condition
Identify the **minimal set of conditions** required for the Android IME to appear.
