# Implementation Plan - Low-Risk Hygiene

Establish a baseline of code cleanliness by addressing technical debt, deprecations, and unused code identified by IDE inspections, while preserving infrastructure required for future expansion.

## Hygiene Rules

> [!CAUTION]
> **Preserve Uncertainty**: If there is any uncertainty that a component (function, variable, or class) may be used later or is referenced indirectly, do not delete it during this hygiene pass.

> [!IMPORTANT]
> **Foundational Identifiers**: Unused identifiers that serve as core infrastructure (e.g., `sessionId`) will be preserved. These are intentional parts of the architecture and will be annotated or documented to clarify their purpose.

## Proposed Changes

### UI Components
#### [MODIFY] [PokedexComponents.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/components/PokedexComponents.kt)
- Remove unused import: `androidx.compose.ui.layout.layout`.
- Remove unused function: `LightDot`.
- Replace deprecated `Divider` with `HorizontalDivider`.
- Remove redundant qualifier: `androidx.compose.animation.AnimatedVisibility`.
- Modernize `delay(30)` to use `Duration` overload.
- Reorder `modifier` parameters to be the first optional parameter where recommended.

### ViewModels
#### [MODIFY] [PokedexViewModel.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/PokedexViewModel.kt)
- Remove redundant qualifier: `com.example.overdex.model.BattleTimeline`.
- Add clarifying parentheses to complex boolean expressions.
- Use named arguments for boolean literals in `MutableStateFlow`.
- Add trailing commas to multi-line function calls/definitions.
- Remove unused functions: `startDroidBallService` and `stopDroidBallService` (confirmed dead code after migration to `ObservationSession`).

### Battle Domain
#### [MODIFY] [ObservationSession.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/observation/ObservationSession.kt)
- Preserve `sessionId` as core infrastructure; add `@Suppress("unused")` or documentation to clarify intent.
- Add trailing commas.

#### [MODIFY] [BattleTimeline.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/timeline/BattleTimeline.kt)
- Add trailing commas.

### General Formatting
- Standardize spacing and indentation across modified files.

## Verification Plan
### Automated Tests
- Run `./gradlew :app:assembleDebug` to ensure no regressions in build or logic.
