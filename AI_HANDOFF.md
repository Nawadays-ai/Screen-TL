# Screen-TL — AI Handoff / Context

## Read This First
Read `README.md`, `AI_README.md`, and `PROJECT_NOTES.md` before changing code. The repository is the source of truth; inspect current files before assuming this document matches the implementation.

## Project Goal
Build an Android screen translator with a floating button over other apps.

Manual TL:
`capture one screen → OCR → analyze text layout → translate → replace source text with overlay`

Real-Time TL:
`capture repeatedly → OCR/translate → refresh overlay`

Google ML Kit on-device translation is the current baseline. DeepL and Gemini remain planned.

## Current Architecture
Package: `com.example.screentranslator`

- `MainActivity.kt`: permissions, language/API selectors, foreground service, full-display MediaProjection consent on Android 14+.
- `FloatingService.kt`: floating UI, capture lifecycle, OCR/translation orchestration, Manual TL, Real-Time loop, menu, overlay lifecycle.
- `ScreenCaptureManager.kt`: MediaProjection + ImageReader capture.
- `TextLayoutAnalyzer.kt`: font-aware source mask and background-color estimation.
- `OcrManager.kt`: ML Kit OCR plus layout metadata.
- `TranslationManager.kt`: Google ML Kit translation.
- `TranslationOverlayView.kt`: full-screen non-touchable translation renderer.
- `TranslationHistory.kt`: persistent SharedPreferences history.

## Important Regression Evidence
The user tested build/commit `e1c7006c495d4ea1e8056ad251b86492d54bf939` and reported:
- source remained visible under overlay;
- translation size did not follow source;
- floating menu stacked over FAB;
- Hapus Overlay did not appear.

That build must not be treated as successful for the visual requirements.

## New Manual Overlay Architecture — 2026-09-07
The old renderer used a tight OCR line box and fixed black background. The new design is:

`Capture → ML Kit OCR → TextLayoutAnalyzer → Translation → TranslationOverlayView`

### TextLayoutAnalyzer
For every OCR line it:
1. reads the line bounding box;
2. reads element bounding boxes and uses their median height to estimate source glyph/font size;
3. expands the line box with a font-aware margin;
4. samples pixels around that mask;
5. uses median RGB values as the local replacement background.

Commit: `e0ec39f595f2bb66e09d46d5be469fd7ae6deaab`.

### OcrManager
`DetectedText` carries expanded mask coordinates, `sourceTextSizePx`, and `backgroundColor`.

Commit: `28dab21490b73a5c94848971259c9096207cca43`.

### TranslationOverlayView
The renderer paints the sampled background, starts from source font size, reduces size when needed, clips to the replacement mask, and remains `FLAG_NOT_TOUCHABLE`.

Commit: `513e43e1f8c190903f2d1dc539028f0fed9e3e28`.

**Status: not yet device-tested.**

## Floating Menu
Previous XML used a 48dp root plus `translationX`, which caused stacking problems.

New design:
- root uses `wrap_content`;
- submenu has no `translationX`;
- WindowManager root expands to fit menu + FAB;
- FAB left → menu right;
- FAB right → menu left;
- FAB lower half → menu above;
- bottom-right → menu above-left relative to FAB.

Layout commit: `e59a2b3adef5ff5391dcda1a12df8f0d6d19ec5e`.
Service commit: `9dbf8663eaab80f9844c64824964b3e5769e0156`.

**Status: not yet device-tested.**

## Hapus Overlay
Required and implemented:
- hidden when no Manual overlay exists;
- visible after Manual TL creates an overlay;
- removes only the overlay;
- service and History remain alive;
- button hides again after removal.

**Status: not yet device-tested.**

## Manual TL Flow
1. Remove previous overlay.
2. Keep FAB usable.
3. Request one MediaProjection frame.
4. Capture timeout: 3 seconds.
5. OCR all returned lines.
6. Analyze font/mask/background metadata.
7. Prepare ML Kit translation model.
8. Translate every detected line sequentially.
9. Save History.
10. Render non-touchable overlay.
11. Expose Hapus Overlay.

## Real-Time — Paused
Basic Real-Time works but flickers because the current loop removes the overlay before each capture. Do not fix this unless the user explicitly asks to resume Real-Time work.

## APK Update / Build
`versionCode = 2`, `versionName = "1.1"`.

APK update conflict is still suspected to be signing-key mismatch between debug APKs.

Build automation now runs `assembleDebug` automatically on every push to `main`; `workflow_dispatch` remains available. The workflow was changed to use `gradle/actions/setup-gradle@v6` with Gradle 8.2 because the previous wrapper-generation step stalled.

Final build workflow commit: `cedddc4ce6bf26f2e1bf7a36ad609cca568854cd`.

**Build verified:** GitHub Actions completed `assembleDebug` successfully and uploaded artifact `ScreenTranslator-APK` (artifact ID `10014166550`) for commit `cedddc4ce6bf26f2e1bf7a36ad609cca568854cd`.

## Diagnostic Logging
Tags:
- `ScreenTL-Capture`
- `ScreenTL-OCR`
- `ScreenTL-Service`
- `ScreenTL-History`

## Development Rules
- Inspect actual repository files before edits.
- Preserve the working capture/OCR/History pipeline unless a functional bug requires changes.
- Do not mark device behavior stable before user testing.
- Update `README.md`, `PROJECT_NOTES.md`, and `AI_HANDOFF.md` after meaningful architecture changes.
- Explain changed files, reason, verification state, and next test.
- Manual TL is current priority.
- Real-Time flicker is paused.
- Never claim a build passed without a real build result.

## Next Test
Use the built APK and verify:
1. Manual TL still completes and History is saved.
2. Source is fully covered by the replacement mask.
3. Background looks like the original surrounding area rather than a fixed black box.
4. Translation font size follows the source.
5. Long translation shrinks appropriately.
6. FAB left/right/bottom-right opens the menu without overlap.
7. Hapus Overlay appears after Manual TL, removes overlay, and disappears.
