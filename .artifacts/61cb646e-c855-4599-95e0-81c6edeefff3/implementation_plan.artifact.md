# Implementation Plan: Suppress Android IME for ODX-FI Input

Implement platform-level suppression of the Android system software keyboard (IME) whenever an ODX-FI Terminal Keyboard session is active, while preserving the standard `TextField` editing behavior (cursor, selection, logic).

## User Review Required

> [!IMPORTANT]
> **System-Wide Invariant**
>
> When the ODX-FI Terminal Keyboard is active, the Android IME must never be shown. The Terminal Keyboard on the LCD is the native ODX-FI input mechanism; the Android IME should never compete for presence on the observation-only CRT.
>
> **Suppressing the Android IME is part of entering an ODX-FI editing session, not a cleanup action after focus is granted.**

## Proposed Changes

### [Platform] Global IME Suppression Rule
- Every `TextField` or `BasicTextField` in the application that participates in an **ODX-FI Terminal Keyboard editing session** must suppress automatic Android IME presentation.
- Ensure the Android IME is suppressed whenever an ODX-FI editing session is active. Use `KeyboardOptions(showKeyboardOnFocus = false)` where supported by the current Compose version; otherwise use the appropriate platform mechanism to ensure the IME is not triggered on focus.

### [Core Components] ui/screens/ChatScreen.kt, OwnedPokemonDetailScreen.kt, TrainerProfileScreen.kt

#### [MODIFY] [ChatScreen.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/screens/ChatScreen.kt)
- Ensure the `TextField` suppresses automatic IME presentation on focus.

#### [MODIFY] [OwnedPokemonDetailScreen.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/screens/OwnedPokemonDetailScreen.kt)
- Ensure the `TerminalEditField` component suppresses IME on focus.

#### [MODIFY] [TrainerProfileScreen.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/screens/TrainerProfileScreen.kt)
- Update all `TextField` usages in dialogs that participate in handheld editing to suppress IME presentation.

## Architecture

| Component | Responsibility |
| :--- | :--- |
| **Compose TextField** | Owns editing state, cursor, selection, and rendering. |
| **IME Management** | Determines whether the Android IME is presented during an editing session. |
| **TerminalKeyboardController** | Owns keyboard presentation and publishes editing commands. |
| **ODX-FI Hardware** | Presents the only visible keyboard during handheld editing sessions. |

## Verification Plan

### Acceptance Criteria
- [ ] The Android IME is **never visible** during an ODX-FI editing session.
- [ ] The only visible keyboard is the ODX-FI Terminal Keyboard on the LCD.
- [ ] Compose `TextField` retains cursor, selection, and editing behavior while the Android IME remains suppressed.

### Manual Verification
- [ ] **Chat Screen**: Navigate to Message Input and press **A**. Verify the Terminal Keyboard appears, cursor is visible, but Android IME is hidden.
- [ ] **Detail Screen**: Focus NICKNAME. Verify Android IME is hidden.
- [ ] **Profile Screen**: Open "Edit Display Name" or "Edit Trainer Code". Verify Android IME is hidden when the `TextField` appears.
- [ ] **Focus Preservation**: Verify the `TextField` still receives Compose focus (cursor visible, selection functional) even though the Android IME remains hidden.
- [ ] **No IME Flicker**: Verify the Android IME never appears, even briefly, when transitioning into or out of an ODX-FI editing session.
- [ ] **Glass Shield**: Confirm the CRT remains clear of system overlays at all times.
