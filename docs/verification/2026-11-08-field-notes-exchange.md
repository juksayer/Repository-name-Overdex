# Portable field-note exchange

November 8, 2026. Follow-up to local writing and separate syllable annotations.

The exchange page uses the existing shell and protected CRT. Trainers explicitly
mark saved entries to share, preview them, and export a `.odxnotes` file through
Android's document picker. The availability flag is local metadata: it does not
advance the poem's revision or send anything. Received entries can be marked for
onward sharing while retaining original authorship, identity and revision.

Drafts and private notes are excluded from exports. Optional annotations are
limited to bundled notes and shared personal notes, so a private poem's original
line cannot leak through an annotation. A selected-note import includes only its
related annotations when opted in; correction-only import validates all targets.

Incoming files are bounded to 256 KiB before decoding. The strict, versioned
UTF-8 JSON format permits at most 100 notes and 500 annotations, validates types,
identity UUIDs, lengths, revisions and times, and rejects unreasonable nesting.
The same format will be used by nearby transport. Sender and author fields are
reported metadata, not cryptographic authorship verification.

Opening a file produces a preview only. The trainer chooses a current entry or
all eligible new/newer entries to save. The store repeats that review atomically
against its current state. Duplicates are no-ops; older revisions, conflicting
writing and attempts to replace locally authored notes are rejected. Received
writing is saved with its source revision/origin/times and a private local flag.
The editing API cannot revise received text or turn it into local authorship.

Canonical hashes ignore receiver/availability flags and equivalent UUID case,
while preserving all actual identity bits, text case, revision and timestamps.
Existing literal IDs remain stable when receiving newer revisions, preserving
favorites and annotation links. Annotation ID collisions with changed text and
forged local-origin annotations reject the whole save, without partial writes.

## Validation

- Full unit suite: **2,100 tests, zero failures/errors/skips**. Ten codec checks
  and seven import-store checks exercise limits, malformed data, equivalent UUID
  spellings, duplicates, revisions, ownership, atomic failures and recovery.
- Debug app and instrumentation APKs build; `git diff --check` passes.
- API 34 disposable emulator: **OK (10 tests)**. Six exchange control tests
  exercise explicit current/bulk saves, protected decisions, optional annotations,
  physical paging and CRT protection. Four ViewModel tests include actual
  ContentResolver input, bounded-file rejection, preview-only receipt, durable
  received metadata, forwarding, private-data exclusion and the recovered-new-
  draft deletion regression.
- Inspected the full-shell incoming preview: poem, source identity/revision and
  save choices are readable. No physical-device instrumentation was used.

Emulator fixtures restore journal data and relevant preferences. Logs and the
preview are retained outside Git at
`/home/sean/overdex-device-backups/2026-11-08-field-notes-exchange/`.
Installation is deferred until nearby integration is checked. System document
picker interaction and a real trainer transfer remain physical acceptance
boundaries; a successful emulator import is not evidence of delivery to a phone.
