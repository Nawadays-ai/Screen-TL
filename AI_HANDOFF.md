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
- `ScreenCaptureManager.kt`: screen capture implementation plus diagnostic logging.
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
- Manual TL results are persisted to History; the latest device test produced `23:04`, `Status`, and `Succese` entries.
- The hard three-line limit was removed; Manual TL now processes every OCR line returned by the OCR manager.

## Current Bugs / Unverified Behavior

### Capture/OCR
Earlier tests suggested OCR might be seeing only phone time/status-bar content rather than the target application. `MainActivity` now requests `MediaProjectionConfig.createConfigForDefaultDisplay()` on Android 14+ so the intended capture scope is the full default display.

The previous `detectedTexts.take(3)` limit has been removed. The next device test should use an app with many obvious text lines and confirm that more than three detected/translated lines reach History.

Neither complete-screen capture nor complete-screen OCR coverage should be marked fully verified until another device test confirms it.

### History
History was previously empty. The service now explicitly calls `TranslationHistory.initialize(applicationContext)`, and writes use `commit()` with `ScreenTL-History` diagnostics. This distinguishes persistence failure from an upstream pipeline failure.

### Toast
Toast is only a short status/error channel. It is not the product output. Do not try to solve cross-app translation by making Toast larger or longer.

### Translation Overlay
The first overlay implementation is now present but **not yet device-verified**.

Current behavior:
- `FloatingService` collects each translated line together with its OCR bounding box.
- After all translations finish, `TranslationOverlayView` is added as a full-screen `TYPE_APPLICATION_OVERLAY`.
- The overlay is `FLAG_NOT_TOUCHABLE`, so touches should pass to the target app.
- Before a new Manual TL capture, the old translation overlay is cleared and Screen-TL's own floating UI is hidden so those elements do not become OCR input.

Main verification risks:
- bitmap coordinates may not map 1:1 to overlay coordinates on every device/orientation;
- translated text may be too large/small for some OCR boxes;
- long translations are currently shortened to fit one line;
- system bars and unrelated UI are not yet filtered.

### Realtime
The realtime button only changes UI state. The actual realtime capture/OCR/translation/cache loop does not exist yet.

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

If the sequence stops, diagnose the first missing stage.

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
4. Manual TL clears the previous translation overlay and hides the floating UI;
5. `captureOnce` gets a fresh frame;
6. OCR returns `DetectedText` items with bounding boxes;
7. translation model is prepared;
8. every detected OCR line is translated sequentially;
9. completed results are written to persistent History;
10. translated lines are displayed at their OCR bounding boxes using `TranslationOverlayView`;
11. the overlay remains until the next Manual TL capture or service shutdown.

## Latest Changes — 2026-09-06

### Removed three-line limit
- Removed `detectedTexts.take(3)` from `FloatingService.kt`.
- Reason: the user's device test showed exactly three OCR/history results, and code inspection confirmed the application itself was limiting the pipeline to three lines.

### Added first translation overlay
- Added `TranslationOverlayView.kt`.
- `FloatingService.kt` now carries OCR coordinates through the translation pipeline and renders the translated result over the target screen.
- The overlay is non-touchable.
- Screen-TL's floating UI is hidden before capture and the previous translation overlay is cleared before each Manual TL operation.
- This implementation is code-complete for the first milestone but remains device-unverified.

## Development Rules
- Inspect the actual repository before modifying code.
- Prefer small, testable changes.
- Preserve working capture/OCR while diagnosing the pipeline.
- Do not mark a feature `[x]` until it is verified.
- After every meaningful code change, update the change log and roadmap.
- Keep `AI_README.md` as the operational rules for future AI sessions.
- Update this handoff when architecture, bugs, or priorities change.
- Always tell the owner what changed, why, what was verified, and what must be tested next.
- APK builds are manual during debugging; do not assume every push creates a build. Run the Android build workflow manually after a logical batch of changes.

## Next Recommended Milestone
1. Build the latest commit.
2. Test Manual TL on another app with many obvious text lines.
3. Confirm History contains all detected translations, not an artificial three-line cap.
4. Confirm translated text appears directly over the target text.
5. Touch and scroll the target app to confirm the overlay does not consume interaction.
6. Run Manual TL again and confirm the old overlay is removed before capture and replaced by the new result.
7. Inspect the four `ScreenTL-*` Logcat tags if anything fails.
8. If overlay positions are offset, diagnose display dimensions, status-bar insets, orientation, and bitmap-to-view scaling.
9. After Manual TL + overlay are stable, implement filtering and then realtime capture/change detection/cache.
