# Implementation Plan: MatchArchivePackageReader

Implement a specialized ZIP reader for Overdex match archives (`.odxmatch`), ensuring strict validation of content, size, and integrity.

## User Review Required

> [!IMPORTANT]
> The `read()` method will explicitly close the provided `InputStream`. Callers should not attempt to use the stream after calling this method.

## Proposed Changes

### [Battle Archive Component]

#### [NEW] [MatchArchivePackageReader.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/archive/MatchArchivePackageReader.kt)

Create the `MatchArchivePackageReader` object with the following logic:

- **ZIP Processing**: Use `ZipInputStream` to process entries sequentially.
- **Entry Validation**:
    - Expect exactly `manifest.json` and `timeline.json`.
    - Fail if any other entry is found, if entries are duplicated, or if entries are directories.
- **Resource Constraints**:
    - Enforce a **16 MiB** limit on total uncompressed bytes read across all entries.
- **Manifest Decoding**:
    - Use `kotlinx.serialization` with `ignoreUnknownKeys = true`.
    - Validate `archiveFormatVersion == 1`, `archiveType == "overdex-match-archive"`, and `timelineEntry == "timeline.json"`.
- **Timeline Decoding**:
    - Use `MatchArchiveSerializer.deserialize()`.
- **Integrity Checks**:
    - Ensure `manifest.matchId == archive.matchId`.
    - Ensure every `article.matchId` in the archive matches the `archive.matchId`.
    - Ensure `manifest.articleCount == archive.articles.size`.
    - Ensure all `article.articleId` values are unique within the archive.
- **Symmetry**:
    - Use entry names defined in `MatchArchivePackageWriter` (`MANIFEST_ENTRY_NAME` and `TIMELINE_ENTRY_NAME`).

## Verification Plan

### Automated Tests
- No new tests are requested in this work order, but existing tests for `MatchArchivePackageWriter` can be used as a reference for expected archive structure.

### Manual Verification
- Verify that the code compiles and correctly uses `ZipInputStream` and `kotlinx.serialization`.
- Review the byte-counting logic to ensure it accurately enforces the 16 MiB limit.
