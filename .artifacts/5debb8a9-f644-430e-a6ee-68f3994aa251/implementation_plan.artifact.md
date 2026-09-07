# Implementation Plan: Correct Capture Geometry and Metadata

Correct the Droidball capture geometry and recording metadata to ensure full display coverage and removal of memory padding from published bitmaps.

## User Review Required

> [!IMPORTANT]
> This change modifies how display dimensions are discovered and how screen buffers are converted to Bitmaps. It ensures that the published bitmaps match the physical screen resolution without padding.

## Proposed Changes

### [Observatory Component]

#### [MODIFY] [DroidballService.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/observation/DroidballService.kt)

- **Dimension Discovery**: Implement runtime discovery of full display bounds using `WindowManager.maximumWindowMetrics` (API 30+) or `Display.getRealMetrics` (Legacy).
- **Projection Setup**: Use these discovered full display bounds for `ImageReader` and `VirtualDisplay` instead of `resources.displayMetrics`.
- **Image Conversion (Padding Removal)**:
    - Update `onImageAvailableListener` to handle buffer row padding.
    - If `rowStride` exceeds the requested width (indicating padding), create a temporary padded bitmap, copy pixels, and then create a clean sub-bitmap.
    - Skip the double-copy if no padding is present.
- **Resource Management**:
    - Wrap image acquisition and conversion in `try-finally` to guarantee `image.close()` is called for every acquired frame.
    - Explicitly `recycle()` temporary padded bitmaps.
- **Diagnostics**: Retain and update `ODX_CAPTURE_GEOMETRY` logs to show the corrected dimensions.

#### [MODIFY] [ObservationRecorder.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/debug/observatory/ObservationRecorder.kt)

- **Metadata Resolution**: Update `captureMetadata` to use the same full-display discovery logic as `DroidballService` for the `screenResolution` field.

## Verification Plan

### Automated Tests
- None.

### Manual Verification
1. Deploy Droidball and verify the `ODX_CAPTURE_GEOMETRY` logs.
2. Confirm "Final Published Bitmap" dimensions match the "maximumWindowMetrics" exactly (e.g., 1080x2400).
3. Verify that the published bitmap no longer includes the 8-pixel padding columns.
4. Export a match recording and check the `screenResolution` in the manifest/metadata.
