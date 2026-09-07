# Screen-TL — AI Handoff / Context

## Read This First
This file is the handoff context for any AI assistant continuing development of Screen-TL. Read `README.md`, `AI_README.md`, and `PROJECT_NOTES.md` before changing code. The repository is the source of truth; inspect current files before assuming the code still matches this document.

## Project Goal
Build an Android screen translator with:
- floating button over other apps;
- Manual TL: capture one screen → OCR → translate → show translated text as an overlay at OCR bounding boxes;
- Real-Time TL: repeatedly capture the screen, OCR/translate it, and refresh the overlay without blocking the target app;
- selectable source/target languages;
- selectable translation engines, with Google ML Kit on-device translation as the baseline and DeepL/Gemini planned.

## Current Architecture
Package: `com.example.screentranslator`

Important files:
- `MainActivity.kt`: permissions, language/API selectors, starts foreground service, requests full-display MediaProjection on Android 14+.
- `FloatingService.kt`: floating UI, MediaProjection capture manager lifecycle, OCR/translation orchestration, Manual TL, Real-Time TL loop, and translation overlay lifecycle.
- `TranslationOverlayView.kt`: full-screen non-touchable overlay that draws translated text using OCR bounding boxes.
- `ScreenCaptureSession.kt`: holds MediaProjection result code and Intent data in memory.
- `ScreenCaptureManager.kt`: screen capture implementation plus diagnostic logging and pending-capture cancellation.
- `OcrManager.kt`: Google ML Kit OCR; returns `DetectedText(text, left, top, right, bottom)` plus diagnostic logging.
- `TranslationManager.kt`: Google ML Kit on-device translation.
- `TranslationHistory.kt`: persistent history using SharedPreferences, max 50 entries, service-safe initialization.

## Known Working Behavior
- Overlay permission works.
- MediaProjection permission works.
- Floating button works and can be dragged.
- A screenshot/Bitmap can be produced.
- OCR engine is configured for Japanese, Chinese, and Latin.
- Translation manager is configured for Indonesian/Japanese/Chinese/English.
- Manual TL was verified by the user after commit `ff06e2882ee852f51f4deecfd94bb0dbf95da8a3`: capture → OCR → translation → History → overlay works again.
- The hard three-line limit was removed; Manual TL now processes every OCR line returned by the OCR manager.
- Translation overlay can be displayed and is `FLAG_NOT_TOUCHABLE`.
- Real-Time TL basic capture → OCR → translation → overlay loop has been verified by the user on-device.

## Manual TL Current Flow
1. Manual TL is requested.
2. Existing translation overlay is removed completely from WindowManager.
3. Floating button remains visible while waiting for the frame.
4. `ScreenCaptureManager.captureOnce()` requests the next ImageReader frame.
5. Capture timeout is 3 seconds.
6. After a frame arrives, a separate 30-second watchdog covers OCR/model/translation.
7. OCR returns all detected lines.
8. Translation model is prepared.
9. Each detected line is translated sequentially.
10. Results are written to persistent History.
11. Translated text + OCR coordinates are displayed as an overlay.
12. Floating button remains available.

The user currently considers Manual TL sufficiently good. Do not add UI polish to Manual TL unless needed for a functional fix.

## Real-Time TL Current Implementation
Commit `2f446e40fac1b5094f4a71ba72570ba7afb03060` implements the first actual Real-Time loop in `FloatingService.kt`.

Flow:
`Real-Time aktif → prepare model → capture frame → OCR → sequential translation → overlay update → wait ~1.2s → repeat`

Important implementation details:
- `REALTIME_INTERVAL_MS = 1200L`.
- `REALTIME_CAPTURE_DELAY_MS = 150L` is used after removing the previous overlay before requesting the next frame.
- `isRealtimeBusy` prevents overlapping frame processing.
- `isRealtimePreparing` blocks capture until the translation model is ready.
- `realtimeGeneration` invalidates callbacks from an older Real-Time session after stop/restart.
- Starting Real-Time removes the existing overlay first.
- Stopping Real-Time cancels the pending capture, removes the overlay, resets the FAB icon, but does not stop the foreground service.
- Real-Time does not write every frame to History. This is intentional to avoid duplicate History entries.
- There is currently no change detection, OCR filtering, translation cache, or text-result deduplication.

### Real-Time Device Verification
The user has now verified that the basic Real-Time pipeline works on the device: capture → OCR → translation → overlay repeats successfully.

### Current Real-Time Bug: Overlay Flicker
Observed behavior:
- overlay appears for about 2 seconds;
- overlay disappears;
- it appears again after another couple of seconds;
- some cycles have a gap of about 4 seconds.

Likely cause: the current Real-Time scheduling path removes the overlay before every capture. This leaves a real empty period while capture/OCR/translation runs.

**Do not mark this bug fixed.** The intended functional fix is to keep one overlay window alive during Real-Time and avoid `removeView()`/re-add on every frame. The overlay contents/visibility should be controlled without repeatedly destroying the WindowManager window.

## Translation Overlay Visual Polish — Latest Unverified Change
Commit `55233dfe6a1d2d200bae92ed970944418d914ccd` updates only `TranslationOverlayView.kt` visual rendering.

Changes:
- lighter/semi-transparent black background;
- smoother rounded corners;
- consistent horizontal/vertical padding;
- adaptive text size based on OCR box dimensions with min/max bounds;
- text size reduced when translation is too wide;
- long text gets ellipsis instead of spilling out of the OCR box;
- vertical centering;
- clipping to the OCR box;
- no change to capture/OCR/translation pipeline;
- overlay remains non-touchable.

**Important:** This visual patch has **not yet been tested by the user**. Do not mark it stable. The user's screenshot showed the old visual state; the new rendering has not been confirmed visually yet.

## Known Bugs / Unverified Behavior

### Capture/OCR
Earlier tests suggested OCR might be seeing only phone time/status-bar content rather than the target application. `MainActivity` now requests `MediaProjectionConfig.createConfigForDefaultDisplay()` on Android 14+ so the intended capture scope is the full default display.

The previous `detectedTexts.take(3)` limit has been removed.

Neither complete-screen capture nor complete-screen OCR coverage should be marked fully verified until another device test confirms it.

### Translation Overlay
Current behavior:
- `FloatingService` carries each translated line together with its OCR bounding box.
- After translation, `TranslationOverlayView` is added as a full-screen `TYPE_APPLICATION_OVERLAY`.
- The overlay is `FLAG_NOT_TOUCHABLE`, so touches should pass to the target app.
- Before a new Manual TL capture, the previous overlay is removed from WindowManager so it cannot become OCR input.

Main verification risks:
- bitmap coordinates may not map 1:1 to overlay coordinates on every device/orientation;
- tiny OCR boxes may produce very small text;
- long translations are shortened to fit one line;
- system bars and unrelated UI are not yet filtered.

The visual rendering patch is present but unverified.

### History
History uses `TranslationHistory.initialize(applicationContext)` from the service and `commit()` for writes. Manual TL uses History as persistent output. Real-Time currently does not add frame-by-frame entries.

### APK update/install
`app/build.gradle.kts` uses `versionCode = 2` and `versionName = "1.1"`.

The user still reports an update conflict. The remaining likely cause is APK signing identity: a debug APK built on GitHub Actions can be signed with a CI-generated debug keystore that differs from the key used by the already-installed APK. A higher `versionCode` cannot fix a signature mismatch. Do not claim the update problem is solved until an APK built with the same signing key can install over the existing package.

## Diagnostic Logging
Use Logcat tags:
- `ScreenTL-Capture`
- `ScreenTL-OCR`
- `ScreenTL-Service`
- `ScreenTL-History`

## Development Rules
- Inspect the actual repository before modifying code.
- Prefer small, testable changes.
- Preserve working capture/OCR while diagnosing the pipeline.
- Do not mark a feature `[x]` until it is verified.
- After every meaningful code change, update the change log and roadmap.
- Keep `AI_README.md` as the operational rules for future AI sessions.
- Update this handoff when architecture, bugs, or priorities change.
- Always tell the owner what changed, why, what was verified, and what must be tested next.
- The owner wants the AI to run the build workflow itself whenever a test build is needed. Do not claim a build passed unless the actual workflow result was checked.

## Next Recommended Milestone
1. Test the latest overlay visual patch on-device.
2. Fix Real-Time overlay flicker by keeping the overlay WindowManager window alive between frames.
3. Test Real-Time for at least 15–30 seconds with a static page, then change/scroll the target content.
4. Verify stopping Real-Time removes the overlay and leaves the floating service alive.
5. Run Manual TL after Real-Time and confirm Manual TL still works.
6. If stable, add change detection and translation caching.
7. Then add OCR filtering and optimize CPU/battery usage.
8. Only after functional stability, continue broader UI polish.
