# Timeline Implementation Classification

This document identifies and classifies existing "Timeline" implementations in the project to establish the landscape for Brick 4.

## Classification Definitions

*   **Current**: Actively part of a surviving architectural layer or domain.
*   **Historical**: Retired or superseded implementation preserved for context or awaiting cleanup.
*   **Unknown**: Purpose or status is ambiguous and requires clarification.

---

### Battle Domain (Match History)

#### [Current] [`com.example.overdex.battle.timeline.BattleTimeline`](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/timeline/BattleTimeline.kt)
*   **Responsibility**: Canonical domain model for a single battle's history.
*   **Status**: Used as the post-reconciliation immutable ledger.
*   **Contract**: Consumes [`TimelineEvent`](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/timeline/event/TimelineEvent.kt) (interface).

#### [Current] [`com.example.overdex.model.BattleTimeline`](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/BattleTimeline.kt)
*   **Responsibility**: Real-time UI updates during a live session.
*   **Status**: Reactive implementation using `mutableStateListOf`.
*   **Contract**: Consumes [`BattleEvent`](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/BattleEvent.kt) (data class).

---

### Social Domain (Trainer Link)

#### [Current] [`com.example.overdex.data.SharedTimelineRepository`](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/data/SharedTimelineRepository.kt)
*   **Responsibility**: Chronological feed of social events (Milestones, Notes) shared between linked partners.
*   **Status**: Independent domain (Social/Trainer Profile).
*   **Contract**: Consumes [`SharedEvent`](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/SharedEvent.kt).

---

### Debug/Instrumentation (Flight Recorder)

#### [Current] [`com.example.overdex.ui.screens.observatory.TimelineList`](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/screens/observatory/TimelineList.kt)
*   **Responsibility**: Visualizes the stream of high-fidelity captured data in the "Signal Observatory" tool.
*   **Status**: Diagnostic/Researcher tool.
*   **Contract**: Consumes [`RecordedEvent`](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/debug/observatory/RecordedEvent.kt).

---

### UI Components

#### [Current] [`com.example.overdex.ui.screens.BattleTimelineScreen`](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/screens/BattleTimelineScreen.kt)
*   **Responsibility**: Log-style view of battle events.
*   **Target**: Displays `model.BattleTimeline`.

#### [Current] [`com.example.overdex.ui.screens.SharedTimelineScreen`](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/screens/SharedTimelineScreen.kt)
*   **Responsibility**: Social feed view for partner events.
*   **Target**: Displays `SharedTimelineRepository`.

---

## Summary of Findings

| Implementation | Scope | Record Type | status |
| :--- | :--- | :--- | :--- |
| `battle.timeline.BattleTimeline` | Battle (Archive) | `TimelineEvent` | Current |
| `model.BattleTimeline` | Battle (Live UI) | `BattleEvent` | Current |
| `SharedTimelineRepository` | Social (Partner) | `SharedEvent` | Current |
| `observatory.TimelineList` | Diagnostic | `RecordedEvent` | Current |

> [!NOTE]
> No implementations were definitively identified as **Historical** or **Unknown** in the primary source paths, though `model.BattleTimeline` and `battle.timeline.BattleTimeline` represent a known architectural split between "Live" and "Archive" states.
