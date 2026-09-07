# Screen-TL — AI Handoff / Context

## Read This First
This file is the handoff context for any AI assistant continuing development of Screen-TL. Read `README.md`, `AI_README.md`, and `PROJECT_NOTES.md` before changing code. The repository is the source of truth; inspect current files before assuming the code still matches this document.

## Project Goal
Build an Android screen translator with:
- floating button over other apps;
- Manual TL: capture one screen → OCR → translate → show translated text directly over the source text;
- Real-Time TL: repeatedly capture the screen, OCR/translate it, and refresh the overlay without blocking the target app;
- selectable source/target languages;
- selectable translation engines, with Google ML Kit on-device translation as the baseline and DeepL/Gemini planned.

## Current Architecture
Package: `com.example.screentranslator`

Important files:
- `MainActivity.kt`: permissions, language/API selectors, starts foreground service, requests full-display MediaProjection on Android 14+.
- `FloatingService.kt`: floating UI, MediaProjection capture manager lifecycle, OCR/translation orchestration, Manual TL, Real-Time TL loop, floating menu, and translation overlay lifecycle.
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
- The hard three-line limit was removed; Manual TL processes every OCR line returned by OCR.
- Real-Time basic capture → OCR → translation → overlay was verified by the user.
- The user has now requested that Real-Time work be paused while Manual TL overlay behavior is refined.

## Manual TL Current Flow
1. Manual TL is requested.
2. Existing translation overlay is removed completely from WindowManager.
3. Floating button remains available.
4. `ScreenCaptureManager.captureOnce()` requests the next ImageReader frame.
5. Capture timeout is 3 seconds.
6. After a frame arrives, a separate 30-second watchdog covers OCR/model/translation.
7. OCR returns all detected lines.
8. Translation model is prepared.
9. Each detected line is translated sequentially.
10. Results are written to persistent History.
11. Translated text + OCR coordinates are displayed as an overlay.
12. A `Hapus Overlay` menu button becomes available.

The user currently considers the Manual TL pipeline sufficiently good. Do not change the capture/OCR/History pipeline unless required by a functional bug.

## Latest Manual Overlay Requirement
The user wants the translation to behave like the Google Translate screenshot example:
- translated text should directly cover the source text;
- the source language/text should not remain visible underneath;
- text size should follow the source OCR box as closely as possible;
- if the translation is longer, reduce/adapt the text size only as needed;
- keep the overlay non-touchable.

### Implementation
`TranslationOverlayView.kt` commit `1116dc9a2f5f197b10bdf2113ad88366c8bdb2dd`:
- uses an opaque black background for each OCR region;
- starts text sizing from the OCR box height;
- shrinks text when the translation is wider than the source region;
- clips text to the source OCR region;
- keeps rounded corners and padding small.

**Important:** This patch has **not been tested by the user yet**. Do not mark it stable.

## Floating Menu Requirement
The user wants the floating menu to avoid stacking over the FAB:
- when FAB is on the left side, menu should open to the right;
- when FAB is on the right side, menu should open to the left;
- when FAB is near the bottom, menu should be placed above it;
- specifically, bottom-right should produce an upper-left menu arrangement.

### Implementation
`layout_floating_widget.xml` commit `7197c6b01541727a4c0b9e106b05afefb0dc16d8` adds:
- Real-Time
- Manual TL
- Hapus Overlay (hidden by default)
- Keluar

`FloatingService.kt` commit `f77d90e10a12372524e3d5e5997213ba198ea8c5` calculates menu placement from the current FAB coordinates and screen dimensions.

**Important:** The adaptive menu has **not been tested by the user yet**.

## Hapus Overlay Requirement
Behavior:
- before Manual TL produces a result: button hidden;
- after Manual TL successfully displays overlay: button visible;
- tapping Hapus Overlay removes only the translation overlay;
- service remains alive;
- History remains untouched;
- after overlay removal: button hidden again.

This is implemented in `FloatingService.kt`, but is **not yet user-tested**.

## Real-Time TL — Paused
The first Real-Time loop was implemented in commit `2f446e40fac1b5094f4a71ba72570ba7afb03060` and verified by the user at the basic functional level.

Flow:
`Real-Time aktif → prepare model → capture frame → OCR → sequential translation → overlay update → wait ~1.2s → repeat`

### Known Real-Time Bug: Overlay Flicker
Observed behavior:
- overlay appears for about 2 seconds;
- overlay disappears;
- it appears again after another couple of seconds;
- some cycles have a gap of about 4 seconds.

Likely cause: the current Real-Time scheduling path removes the overlay before every capture. This leaves a real empty period while capture/OCR/translation runs.

**Status: deliberately paused. Do not mark fixed.**

Planned fix later:
- keep one overlay WindowManager window alive;
- avoid `removeView()`/re-add on every frame;
- control overlay contents/visibility without destroying the window.

Do not spend the current milestone on Real-Time unless the user explicitly asks to resume it.

## History
History uses `TranslationHistory.initialize(applicationContext)` from the service and `commit()` for writes. Manual TL uses History as persistent output. Real-Time currently does not add frame-by-frame entries.

## APK Update / Build
`app/build.gradle.kts` uses `versionCode = 2` and `versionName = "1.1"`.

The user still reports an update conflict. The likely remaining cause is APK signing identity: a fresh GitHub Actions debug build can use a different debug keystore from the already-installed APK. A higher `versionCode` cannot fix a signature mismatch.

The user wants the AI to perform builds whenever possible. However, the currently available GitHub connector does not expose a `workflow_dispatch` action, so **never claim a new build passed unless a real workflow result is available**.

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
- Do not mark a feature `[x]` until it is verified by the user/device.
- After every meaningful code change, update the change log and roadmap.
- Keep `AI_README.md` as the operational rules for future AI sessions.
- Update this handoff when architecture, bugs, or priorities change.
- Always tell the owner what changed, why, what was verified, and what must be tested next.
- If a change is implemented but not device-tested, explicitly mark it `[~]` / unverified.
- Current priority is Manual TL. Real-Time flicker is paused.

## Next Milestone
1. Build/test the latest Manual TL overlay and floating menu changes.
2. Verify source text is completely covered by translation.
3. Verify font sizing on short and long translations.
4. Move the FAB to left, top-right, bottom-left, and bottom-right and verify menu placement.
5. Verify Hapus Overlay appears only after Manual TL overlay exists and disappears after clearing it.
6. Once stable, implement the user's planned new Manual TL mode.
7. Resume Real-Time flicker work later.
