# BattleEngine

The battle engine coordinates the entire data lifecycle within Overdex, ensuring that raw screen crops, audio pulses, and sensor measurements are securely ingested into custody, compiled into a canonical timeline, and presented as real-time tactical intelligence.

---

## Data Lifecycle

```text
GO Screen & Audio Capture (DroidballService)
        │
        ▼
Crop & Audio Witnesses (CropCaptureWitness, LiveHpWitnesses, CryWitnesses)
        │
        ▼
TestimonyCustody (Immutable Evidence & Source Availability Ledger)
        │
        ▼
RealityArticle & RealityTimeline (Canonical Chronological Match Ledger)
        │
        ▼
Match Engine & Inference Systems (FastMoveUse, Energy, QTE, Announcements)
        │
        ├─────────────────────────────────┐
        ▼                                 ▼
DroidballOverlayPresentation       MatchArchiveExporter (.odxmatch)
(Live Overlay HUD)                        │
                                          ▼
                                   MatchReplayModel (Replay & Forensic Analysis)
```

---

## Core Responsibilities

- **Session & Match Lifecycle Coordination**: Managing battle scope via `DroidballSession` and `Match.kt` from pre-battle detection (`PersistedVsScreenWitness`, `PersistedTeamSelectWitnesses`), countdown match start (`CountdownGoGeometry`), combat flow, to match completion (`PersistedOutcomeTextWitness`).
- **Witness Signal & Custody Management**: Receiving raw testimony from independent visual and audio witnesses into `TestimonyCustody`, verifying content-addressed SHA-256 digests via `CropArtifactStore` and `AudioArtifactStore`.
- **Real-Time Inference & Resource Accounting**: Deducting fast move executions (`FastMoveUseInference`), energy accumulation (`FastMoveEnergyInference`), charged move announcements (`ChargedMoveAnnouncementInference`), and opponent resource tracking (`PersistedOpponentBattleResourceWitness`, `OpponentPokemonFaintWitness`).
- **Timeline Publication & Archival**: Publishing immutable `RealityArticle` envelopes to `RealityTimeline` and exporting complete `.odxmatch` archive packages through `MatchArchiveExporter`.

---

## Intelligence & Presentation Layers

The engine supplies evidence to downstream components that interpret and present the match without altering the underlying custody record:

1. **Live Overlay Presentation (`DroidballOverlayPresentation`)**: Renders real-time health bar gauges, energy progress bars, fast move cadence indicators, and active combatant details on the floating HUD overlay.
2. **Replay & Forensic Accounting (`MatchReplayModel`)**: Uses archived `RealityArticle` packages to reconstruct deterministic match playback, providing energy tracking (`ReplayEnergyTrack`) and team resource timelines (`ReplayTeamResources`).

---

## Design Philosophy

**Invisible decision support.**

The engine quietly observes and preserves evidence so the trainer can focus on gameplay. It surfaces only the information that reduces cognitive load, answering the core question: **"What decision does the player need to make right now?"**

```text
Start Droidball Session
        │
        ▼
Match Begins (Vs Screen / Countdown)
        │
        ▼
Ingest Witness Testimony into Custody
        │
        ▼
Publish RealityArticles to RealityTimeline
        │
        ▼
Run Inference & Update Live Overlay
        │
        ▼
Match Concludes -> Export .odxmatch Archive
```
