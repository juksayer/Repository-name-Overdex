# Team Select collection review — October 28, 2026

## User flow

After confirming a Team Select scan, Team & Moves offers **Import This Team**
when all three slots lack compatible owned-card candidates. Mixed/familiar scans
offer individual existing-card choices or new imports. Known candidates are
suggestions; species similarity never silently assigns a specimen UUID. Older
cards with no recorded form require explicit form confirmation.

New-card review includes nickname, optional CP, Shadow/Shiny flags and moves.
The trainer names the team and chooses **Save Team & Cards** to create missing
cards, update the selected cards' reviewed moves/team membership, save a named
lineup and make it current. Cards appear in ALL OWNED and any compatible filtered
binder; binders do not hold duplicate card copies. **Use This Team** remains
available without importing. A failed owned-card read disables collection saving
without blocking explicit move setup.

Every imported card receives its UUID once. Closing/resuming the same scan,
editing and retrying preserve it. A reviewed save does not stop halfway merely
because the panel closes. A completion after battle begins cannot replace the
active lineup. Existing metadata is read afresh in the Room transaction; moves,
canonical form and team membership are updated while nickname, CP, size, flags,
creation time and UUID remain intact. Retried new-card inserts do not overwrite
a card the user has subsequently edited. The Room card writes are atomic; named
team preferences are a following write. If the latter fails, the UI reports that
the cards were saved and keeps the same IDs for retry.

Team Select currently provides species/form identity, not reliable CP, nickname,
Shadow or Shiny identity. These remain explicit review fields. This slice offers
review through Team Select/Team & Moves; it does not open registration prompts
during a live match or retrospectively assign old unlinked battles by species.
Future matches retain the selected card UUID and frozen metadata, keeping later
card edits from rewriting past match records.

## Form preservation

Owned cards previously stored only their shared National Dex number. Room 11→12
adds nullable canonical `speciesName`; domain/entity converters and frozen match
snapshots preserve it. Existing cards stay unknown until reviewed. Exact regional
reference lookup cannot fall back to the ordinary form. Same-ID form changes
clear incompatible move choices without replacing the specimen. Registration
paths with an explicit resolved reference now save its canonical name; legacy
ID-only paths leave it unknown.

Named card-team choices, binder/detail reference loading and current-team
selection respect the stored form. Unsupported exact form artwork stays absent
rather than drawing an ordinary form. Alolan Raichu uses the existing exact
non-shiny front sprite; broader form/shiny artwork remains separate asset work.

## Droidball choreography located

The historical sequence is in `DexDox/Droidball.md:245–261`: undock, roll off the
left edge, request permissions/launch GO, enter with 3–10% overshoot and settle,
tap at Team Select, scan/readiness color, then reveal the HUD. The entry-edge
sentence is damaged, so the GO entry side is not specified reliably. Current
runtime has service deployment, a short spring entrance and edge snapping, but
no continuous shell departure. This work preserves those notes without changing
the departure animation. Existing information must not wait for decorative travel.

## Validation

- **1,356 unit tests / 218 suites**, zero failures, errors or skips. Focused cases
  cover explicit card selection, form rejection, stable IDs, legal moves, retries,
  close/start-during-save, metadata retention and optional continuation.
- Debug app and Android-test APKs built; migration device test compiled but was
  not executed through instrumentation on the personal phone.
- Before installation, saved preferences/profiles and the Room database/WAL were
  copied from the idle Moondrop. The migration SQL was applied to a consistent
  desktop database copy: all **24** cards and every prior column value survived,
  with new legacy form fields null and SQLite integrity OK. This SQL check is
  distinct from Android Room migration validation.
- Installed and normally launched on the idle Moondrop. Room migrated the actual
  database to version 12. A fresh database/WAL copy confirmed all 24 card rows and
  every prior column value unchanged, with integrity OK. All **17** calibration,
  profile, HUD-layout and current-team file hashes remain unchanged. This normal
  launch validates the real migration; it does not exercise the new import UI.
- The Pixel was not updated. No Droidball recording was running or interrupted.
- Evidence is at `/home/sean/overdex-device-backups/2026-10-28-team-collection/`.
- APK SHA-256: `7444fee121e1f456efe3becb5654ebc66a0d198425d0e6dbf894883c510a5b01`.

The next live check is a Team Select scan of an unmatched or changed team: confirm
the roster, inspect the offer, choose an existing card or import, review moves,
name/save, and verify the cards and named lineup after reopening. Compare the
first fresh match snapshot to those card IDs. No new match was played by the agent.
