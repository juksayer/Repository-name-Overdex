# Implementation Plan: Suppress Android IME for ODX-FI Input

Implement platform-level suppression of the Android system software keyboard (IME) whenever an ODX-FI Terminal Keyboard session is active, while preserving the standard `TextField` editing behavior (cursor, selection, logic).

## User Review Required

> [!IMPORTANT]
> **System-Wide Invariant**
>
> When the ODX-FI Terminal Keyboard is active, the Android IME must never be shown. The Terminal Keyboard on the LCD is the native ODX-FI input mechanism; the Android IME should never compete for presence on the observation-only CRT.

## Proposed Changes

### [Platform] Global IME Suppression Rule
- Every `TextField` or `BasicTextField` in the application that participates in the handheld interaction model must explicitly suppress automatic IME presentation on focus.

### [Core Components] ui/screens/ChatScreen.kt, OwnedPokemonDetailScreen.kt, TrainerProfileScreen.kt

#### [MODIFY] [ChatScreen.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/screens/ChatScreen.kt)
- Ensure the `TextField` uses `KeyboardOptions(showKeyboardOnFocus = false)`.

#### [MODIFY] [OwnedPokemonDetailScreen.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/screens/OwnedPokemonDetailScreen.kt)
- Ensure the `TerminalEditField` component uses `KeyboardOptions(showKeyboardOnFocus = false)`.

#### [MODIFY] [TrainerProfileScreen.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/screens/TrainerProfileScreen.kt)
- Update all `TextField` usages in dialogs to include `KeyboardOptions(showKeyboardOnFocus = false)`.

## Architecture

| Component | Responsibility |
| :--- | :--- |
| **Compose TextField** | Owns editing state, cursor, selection, and rendering. |
| **IME Management** | Suppressed via `KeyboardOptions` to decouple focus from IME presentation. |
| **ODX-FI Hardware** | The ODX-FI Shell/Terminal Keyboard is the sole owner of input presentation. |

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
- [ ] **Glass Shield**: Confirm the CRT remains clear of system overlays at all times.
