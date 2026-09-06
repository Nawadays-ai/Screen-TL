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
- `FloatingService.kt`: floating UI, MediaProjection capture manager lifecycle, OCR/translation orchestration, Manual TL.
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

## Current Bugs / Unverified Behavior

### Capture/OCR
The latest device test reported that OCR sees only the phone's current time/status-bar text rather than the text in the other application. A likely cause is that the MediaProjection capture configuration was not explicitly requesting the full default display on Android 14+. `MainActivity` now requests `MediaProjectionConfig.createConfigForDefaultDisplay()` on Android 14+.

This is a hypothesis/fix to verify, not a claimed final solution.

### History
History was previously empty. The service now explicitly calls `TranslationHistory.initialize(applicationContext)`, and writes use `commit()` with `ScreenTL-History` diagnostics. This distinguishes persistence failure from an upstream pipeline failure.

### Toast
Toast is only a short status/error channel. It is not the product output. Do not try to solve cross-app translation by making Toast larger or longer.

### Overlay
Translation overlay over the target application is not implemented yet.

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
4. Manual TL calls `captureOnce`;
5. OCR returns `DetectedText` items with bounding boxes;
6. translation model is prepared;
7. up to three detected lines are translated sequentially;
8. completed results are written to persistent History.

## Development Rules
- Inspect the actual repository before modifying code.
- Prefer small, testable changes.
- Preserve working capture/OCR while diagnosing the pipeline.
- Do not mark a feature `[x]` until it is verified.
- After every meaningful code change, update the change log and roadmap.
- Keep `AI_README.md` as the operational rules for future AI sessions.
- Update this handoff when architecture, bugs, or priorities change.
- Always tell the owner what changed, why, what was verified, and what must be tested next.

## Next Recommended Milestone
1. Build the latest commit.
2. Test Manual TL on another app with obvious text.
3. Inspect the four `ScreenTL-*` Logcat tags.
4. Verify that the captured bitmap contains the target app, not only system/status-bar content.
5. If the frame is still wrong, diagnose capture timing, display dimensions, orientation, and Android/device-specific MediaProjection behavior.
6. If the frame is correct, diagnose OCR and filtering.
7. Confirm History receives a completed translation.
8. Implement the first overlay using OCR bounding boxes.
9. Only after Manual TL + overlay are stable, implement realtime capture/change detection/cache.
