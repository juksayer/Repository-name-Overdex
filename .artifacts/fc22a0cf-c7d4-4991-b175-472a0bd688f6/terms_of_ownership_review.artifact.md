# Review: Terms of Ownership Audit

This review examines the current [Terms of Ownership](file:///home/sean/AndroidStudioProjects/Overdex/DexDox/Terms%20Of%20Ownership) against the architectural philosophy established in **Brick XXX** and **Brick 277**.

## Critical Conflicts

> [!WARNING]
> ### 1. The "Observation" Paradox
> `Terms of Ownership` currently lists **Observation** as **Retired**, yet Brick XXX explicitly states that "Observation produces evidence" and "future observations may supersede current understanding."
>
> **Proposed Resolution**: Reactivate **Observation** (un-retire it) but define its scope strictly as the *sensing layer* (Witnessing) that produces evidence.

> [!CAUTION]
> ### 2. The Persistence of "Fact"
> Brick XXX declares: "The term Fact no longer reflects the architectural philosophy of Overdex." However, `Terms of Ownership` does not yet list **Fact** as **Retired**.
>
> **Proposed Resolution**: Officially **Retire** the term **Fact**. Replace it with **Evidence** or **Observation** depending on context.

## Proposed Vocabulary Updates

### New Reserved Terms

| Term | Status | Owner | Definition |
| :--- | :--- | :--- | :--- |
| **Witness** | Reserved | Observation | A component that performs primary recognition/sensing (e.g., `BattleBeginWitness`). |
| **Signal** | Reserved | Instrument | A technical perception broadcast by the sensing layer (e.g., `DroidballSignal`). |
| **Evidence** | Reserved | Observation | The raw artifacts (OCR strings, bitmaps, timestamps) stored in the Battle Workspace. |
| **Knowledge** | Reserved | Knowledge | The current best understanding of the battle (Dynamic Knowledge) or static game data (Reference Knowledge). |
| **Confidence** | Reserved | Intelligence | The metric of certainty assigned to knowledge by the intelligence layer. |

### Updated Statuses

| Term | Current Status | Proposed Status | Reason |
| :--- | :--- | :--- | :--- |
| **Fact** | (None) | **Retired** | Implies certainty; conflicts with the model of confidence and evidence. |
| **Observation** | **Retired** | **Reserved** | Re-established as the primary term for the sensing layer and its output artifacts. |

## Document Alignment

To align the codebase with the **Constitution**, the following documents should be updated as part of the audit:

1.  **[ARCHITECTURE.md](file:///home/sean/AndroidStudioProjects/Overdex/DexDox/ARCHITECTURE.md)**: Replace "Facts flow upward" with "Evidence flows upward".
2.  **[Philosophy.md](file:///home/sean/AndroidStudioProjects/Overdex/DexDox/Philosophy.md)**: Replace "Observation creates facts" with "Observation produces evidence".
3.  **[BattleWorkspace.md](file:///home/sean/AndroidStudioProjects/Overdex/DexDox/BattleWorkspace.md)**: Replace "Facts Before Conclusions" with "Evidence Before Conclusions".

## Next Steps

1.  **Update `Terms of Ownership`** to reflect the re-activation of Observation and the retirement of Fact.
2.  **Execute the Terminology Audit** across the codebase (Renaming `DroidballFact` to `DroidballSignal`).
3.  **Refactor Documentation** to match the new vocabulary.
