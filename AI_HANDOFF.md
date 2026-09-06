# Screen-TL — AI Handoff / Context

## Read This First
This file is the handoff context for any AI assistant continuing development of Screen-TL. Read `README.md`, `AI_README.md`, and `PROJECT_NOTES.md` before changing code. The repository is the source of truth; inspect current files before assuming the code still matches this document.

## Project Goal
Build an Android screen translator with:
- floating button over other apps;
- Manual TL: capture one screen → OCR → translate → show translated text as an overlay at OCR bounding boxes;
- Real-Time TL: monitor/capture the screen, detect changed text, OCR only when useful, translate changed/new text, cache results, and display overlays;
- selectable source/target languages;
- selectable translation engines, with Google ML Kit on-device translation as the baseline and DeepL/Gemini planned.

## Current Architecture
Package: `com.example.screentranslator`

Important files:
- `MainActivity.kt`: permissions, language/API selectors, starts foreground service, requests full-display MediaProjection on Android 14+.
- `FloatingService.kt`: floating UI, MediaProjection capture manager lifecycle, OCR/translation orchestration, Manual TL, and translation overlay lifecycle.
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
- An earlier device test persisted Manual TL results to History: `23:04`, `Status`, and `Succese`.
- The hard three-line limit was removed; Manual TL now processes every OCR line returned by the OCR manager.

## Current Bugs / Unverified Behavior

### Manual TL currently being stabilized
The first overlay build introduced a regression observed on the user's device: after pressing Manual TL, ML Kit translation downloaded its model, then no result appeared, History stayed unchanged, and the floating button disappeared. Launching the app again did not restore the floating button until the app was force-stopped.

The latest code now separates the two failure windows:
- the floating button is no longer hidden before `captureOnce` receives a frame;
- `ScreenCaptureManager` can cancel a pending capture;
- Manual TL capture timeout is 3 seconds;
- after a frame arrives, a separate 30-second processing watchdog covers OCR/translation callbacks;
- duplicate Manual TL requests are blocked while one is pending;
- OCR/translation/save/display calls have defensive exception handling;
- the floating button is restored on timeout, failure, and completion.

This is not device-verified yet. Do not mark Manual TL stable until a fresh APK has been tested.

### Capture/OCR
Earlier tests suggested OCR might be seeing only phone time/status-bar content rather than the target application. `MainActivity` now requests `MediaProjectionConfig.createConfigForDefaultDisplay()` on Android 14+ so the intended capture scope is the full default display.

The previous `detectedTexts.take(3)` limit has been removed. The next device test should use an app with many obvious text lines and confirm that more than three detected/translated lines reach History.

Neither complete-screen capture nor complete-screen OCR coverage should be marked fully verified until another device test confirms it.

### History
History was previously empty. The service now explicitly calls `TranslationHistory.initialize(applicationContext)`, and writes use `commit()` with `ScreenTL-History` diagnostics. This distinguishes persistence failure from an upstream pipeline failure.

### Toast
Toast is only a short status/error channel. It is not the product output. Do not try to solve cross-app translation by making Toast larger or longer.

### Translation Overlay
The first overlay implementation is present but **not yet device-verified as stable**.

Current behavior:
- `FloatingService` collects each translated line together with its OCR bounding box.
- After all translations finish, `TranslationOverlayView` is added as a full-screen `TYPE_APPLICATION_OVERLAY`.
- The overlay is `FLAG_NOT_TOUCHABLE`, so touches should pass to the target app.
- Before a new Manual TL capture, the previous translation overlay is cleared.
- The floating UI remains visible during the actual capture and is hidden only after a fresh frame is received, preventing a visibility change from causing a capture timeout. Because of this, OCR may still see the floating button in the captured frame; filtering remains a later task.

Main verification risks:
- bitmap coordinates may not map 1:1 to overlay coordinates on every device/orientation;
- translated text may be too large/small for some OCR boxes;
- long translations are currently shortened to fit one line;
- system bars and unrelated UI are not yet filtered;
- overlay window creation must be verified on the target device.

### Realtime
The realtime button only changes UI state. The actual realtime capture/OCR/translation/cache loop does not exist yet.

### APK update/install
`app/build.gradle.kts` now uses `versionCode = 2` and `versionName = "1.1"`. The previous APK used `versionCode = 1`.

The user still reports an update conflict after the version bump. Therefore the remaining likely cause is APK signing identity: a debug APK built on GitHub Actions can be signed with a CI-generated debug keystore that differs from the key used by the already-installed APK. A higher `versionCode` cannot fix a signature mismatch. Do not claim the update problem is solved until an APK built with the same signing key can install over the existing package.

## Diagnostic Logging
Use Logcat tags:
- `ScreenTL-Capture`
- `ScreenTL-OCR`
- `ScreenTL-Service`
- `ScreenTL-History`

Expected Manual TL sequence:

`Manual translation requested`
→ `captureOnce requested`
→ `Fresh screen frame captured successfully`
→ `Capture callback received`
→ `OCR started`
→ `OCR completed: N lines detected`
→ `Translation model ready`
→ `Translating ...`
→ `Translation success ...`
→ `Translation completed; saving history entry ...`
→ `History add: saved=true ...`
→ `Translation overlay updated: N items`

If the sequence stops, diagnose the first missing stage. Capture timeout is now 3 seconds; processing has a separate 30-second watchdog.

## Important Current Flow
`MainActivity`:
1. user selects source/target language;
2. user presses Play;
3. overlay permission is checked/requested;
4. MediaProjection is requested; on Android 14+ the default display configuration is requested;
5. `ScreenCaptureSession.save(resultCode, data)`;
6. `FloatingService` starts.

`FloatingService`:
1. initializes `TranslationHistory` from the service context;
2. initializes `OcrManager` and `TranslationManager`;
3. initializes `ScreenCaptureManager` from `ScreenCaptureSession`;
4. Manual TL clears the previous translation overlay;
5. `captureOnce` gets a fresh frame while the floating button remains visible;
6. after the frame arrives, the floating button is hidden and OCR starts;
7. OCR returns `DetectedText` items with bounding boxes;
8. translation model is prepared;
9. every detected OCR line is translated sequentially;
10. completed results are written to persistent History;
11. translated lines are displayed at their OCR bounding boxes using `TranslationOverlayView`;
12. the floating button is restored and the overlay remains until the next Manual TL capture or service shutdown.

## Latest Changes — 2026-09-06

### Manual TL watchdog refinement
- `FloatingService.kt`: reduced the capture timeout from 10 seconds to 3 seconds.
- `FloatingService.kt`: added a separate 30-second processing watchdog after a frame is received.
- `FloatingService.kt`: added a visible status message after capture succeeds: `Screenshot didapat. Memproses OCR...`.
- `FloatingService.kt`: processing watchdog restores the floating button if OCR/translation never calls back.
- This distinction is intentional: a slow translation-model download must not be mistaken for a capture timeout.

### Fixed Manual TL capture lifecycle
- Added `ScreenCaptureManager.cancelPendingCapture()`.
- Changed `FloatingService` so the floating UI is not hidden before a frame is received.
- Added pending-capture timeout with automatic UI recovery.
- Added duplicate-request protection for Manual TL.
- Added defensive exception handling around OCR, translation preparation/invocation, and result saving/display.
- Ensured the floating button is restored on success and failure.
- Device verification is still required.

### Fixed APK update versioning
- Changed `app/build.gradle.kts` from `versionCode = 1`, `versionName = "1.0"` to `versionCode = 2`, `versionName = "1.1"`.
- The user still reports a conflict when updating, so signing-key consistency is now the next APK-install investigation.

### Removed three-line limit
- Removed `detectedTexts.take(3)` from `FloatingService.kt`.
- Reason: the user's device test showed exactly three OCR/history results, and code inspection confirmed the application itself was limiting the pipeline to three lines.

### Added first translation overlay
- Added `TranslationOverlayView.kt`.
- `FloatingService.kt` now carries OCR coordinates through the translation pipeline and renders the translated result over the target screen.
- The overlay is non-touchable.
- The previous implementation hid Screen-TL's floating UI before capture; this was changed after device testing showed the Manual TL flow could become stuck with the button invisible.
- Overlay remains device-unverified.

## Development Rules
- Inspect the actual repository before modifying code.
- Prefer small, testable changes.
- Preserve working capture/OCR while diagnosing the pipeline.
- Do not mark a feature `[x]` until it is verified.
- After every meaningful code change, update the change log and roadmap.
- Keep `AI_README.md` as the operational rules for future AI sessions.
- Update this handoff when architecture, bugs, or priorities change.
- Always tell the owner what changed, why, what was verified, and what must be tested next.
- The owner wants the AI to run the build workflow itself whenever a test build is needed. Do not ask the owner to manually trigger the build if the connected GitHub tooling can perform it. Never claim a build passed unless the actual workflow result was checked.

## Next Recommended Milestone
1. Build the latest commit and verify the APK artifact.
2. Investigate signing identity so the new APK can update the existing installation without a signature conflict.
3. Test Manual TL on another app with many obvious text lines.
4. Confirm the floating button remains available if capture stalls and returns after any failure.
5. Confirm History contains all detected translations, not an artificial three-line cap.
6. Confirm translated text appears directly over the target text.
7. Touch and scroll the target app to confirm the overlay does not consume interaction.
8. Run Manual TL again and confirm the old overlay is removed before capture and replaced by the new result.
9. If anything fails, inspect the first missing stage in the `ScreenTL-*` Logcat sequence.
10. If overlay positions are offset, diagnose display dimensions, status-bar insets, orientation, and bitmap-to-view scaling.
11. After Manual TL + overlay are stable, implement filtering and then realtime capture/change detection/cache.
