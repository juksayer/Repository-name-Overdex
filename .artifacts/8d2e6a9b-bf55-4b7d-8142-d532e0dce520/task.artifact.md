# Tasks - Battle Timeline Expansion

- [x] `[x]` Expand Battle Event Taxonomy in `com.example.overdex.battle.timeline.event`
    - [x] `BattleLifecycleEvents.kt`: `BattleStarted`, `BattleEnded`
    - [x] `CombatEvents.kt`: `FastMovePerformed`, `ChargedMoveStarted`, `ChargedMoveResolved`, `ShieldUsed`
    - [x] `StatusEvents.kt`: `PokemonSwitched`, `PokemonFainted`
- [x] `[x]` Create Evidence Implementations in `com.example.overdex.battle.timeline.evidence`
    - [x] `EvidenceTypes.kt`: `VisualEvidence`, `AudioEvidence`, `StateEvidence`
- [x] `[x]` Expand `ObservationSource` in `com.example.overdex.battle.timeline.observer`
    - [x] Add `SCREEN_CAPTURE`, `AUDIO_CAPTURE`, `DROIDBALL`, `REMOTE_PARTNER`, `SYSTEM_INFERENCE`
- [x] `[x]` Verify package structure and placeholder status of `BattleTimelineBuilder`
- [x] `[x]` Build and Deploy
    - [x] Run build: `./gradlew :app:assembleDebug` [SUCCESS]
    - [x] Deploy to device [SUCCESS]
- [x] `[x]` Update Walkthrough
