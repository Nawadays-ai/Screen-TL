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
- `FloatingService.kt`: floating UI, capture lifecycle, OCR/translation orchestration, Manual TL, Real-Time loop, menu, overlay lifecycle.
- `ScreenCaptureManager.kt`: MediaProjection + ImageReader capture.
- `TextLayoutAnalyzer.kt`: converts ML Kit line geometry into font-aware mask geometry and estimates background color.
- `OcrManager.kt`: ML Kit OCR; returns `DetectedText` with mask coordinates, source font size estimate, and background color.
- `TranslationManager.kt`: Google ML Kit on-device translation.
- `TranslationOverlayView.kt`: full-screen non-touchable renderer for translated text.
- `TranslationHistory.kt`: persistent SharedPreferences history.

## Known Working Behavior
- Overlay permission works.
- MediaProjection permission works.
- Floating button works and can be dragged.
- Screenshot/Bitmap can be produced.
- OCR supports Japanese, Chinese, and Latin.
- Manual TL capture → OCR → translation → History → overlay was verified by the user before the latest visual rewrite.
- The hard three-line OCR limit was removed previously.
- Real-Time basic capture → OCR → translation → overlay was verified by the user.
- Real-Time work is currently paused.

## Important Regression Evidence
The user tested build/commit `e1c7006c495d4ea1e8056ad251b86492d54bf939` and reported:
- source text remained visible under the overlay;
- translation text size did not follow source size;
- floating menu still stacked over the FAB;
- `Hapus Overlay` did not appear.

Therefore the previous visual patch must NOT be considered successful.

## New Manual Overlay Architecture — 2026-09-07
The old renderer only knew the tight OCR line bounding box and used a fixed black background. This was insufficient for reliable source replacement.

New flow:
`Capture → ML Kit OCR → TextLayoutAnalyzer → Translation → TranslationOverlayView`

### TextLayoutAnalyzer
File: `TextLayoutAnalyzer.kt`

For each OCR line it:
1. reads the line bounding box;
2. reads element bounding boxes and estimates the source glyph/font height from their median height;
3. expands the OCR box by a small font-aware margin to form a replacement mask;
4. samples pixels immediately around that mask;
5. uses the median RGB values as the background color.

The intent is to cover the source glyphs while preserving the approximate local background instead of placing a fixed black rectangle.

Commit: `e0ec39f595f2bb66e09d46d5be469fd7ae6deaab`.

### OcrManager
`DetectedText` now carries:
- `left/top/right/bottom` for the expanded replacement mask;
- `sourceTextSizePx`;
- `backgroundColor`.

Commit: `28dab21490b73a5c94848971259c9096207cca43`.

### TranslationOverlayView
Renderer behavior:
- fills the expanded mask with the sampled background color;
- starts translation font size from `sourceTextSizePx`;
- scales down only when the translation does not fit the available width/height;
- clips drawing to the replacement mask;
- remains `FLAG_NOT_TOUCHABLE`.

Commit: `513e43e1f8c190903f2d1dc539028f0fed9e3e28`.

**Status: not yet tested by the user.**

## Floating Menu Architecture
The previous XML had a 48dp root and forced the menu outside it with `translationX`, which made positioning unreliable.

New layout:
- root `FrameLayout` uses `wrap_content`;
- submenu has no `translationX`;
- WindowManager root size is changed to fit menu + FAB when open;
- menu is placed right of FAB when FAB is on left;
- menu is placed left of FAB when FAB is on right;
- menu is placed above when FAB is in the lower half;
- bottom-right therefore becomes an upper-left menu arrangement relative to the FAB.

Commit layout: `e59a2b3adef5ff5391dcda1a12df8f0d6d19ec5e`.

`FloatingService.kt` was rebuilt around the same behavior and now transfers the new OCR layout metadata into `TranslationOverlayItem`.

Commit service: `e85ef0b3d7e5881c282dc98e77108d554e389f8f`.

## Hapus Overlay
Required behavior:
- hidden before a Manual TL overlay exists;
- visible after Manual TL creates a non-empty overlay;
- tapping it removes only the overlay;
- service remains alive;
- History remains intact;
- button becomes hidden again.

Implemented in `FloatingService.kt`; **not yet device-tested**.

## Manual TL Current Flow
1. Remove any old overlay.
2. Keep FAB available.
3. Request one MediaProjection frame.
4. Capture timeout: 3 seconds.
5. OCR with TextLayoutAnalyzer metadata.
6. Prepare ML Kit translation model.
7. Translate every detected line sequentially.
8. Save combined result to History.
9. Create/update non-touchable overlay using font/background metadata.
10. Show Hapus Overlay.
11. Restore normal floating UI state.

Do not alter capture/OCR/History behavior without a functional reason.

## Real-Time TL — Paused
Basic Real-Time works but has known flicker because the current loop removes the overlay before each capture. Do not spend this milestone on flicker unless the user explicitly asks.

## APK Update / Build
`versionCode = 2`, `versionName = "1.1"`.

The user still reports APK update conflict. Most likely cause remains signing-key mismatch between different debug builds.

The user wants the AI to perform builds whenever possible. The available GitHub connector does not expose `workflow_dispatch`, so never claim a new build passed unless a real workflow result exists.

## Diagnostic Logging
Use tags:
- `ScreenTL-Capture`
- `ScreenTL-OCR`
- `ScreenTL-Service`
- `ScreenTL-History`

## Development Rules
- Inspect actual repository files before edits.
- Preserve working capture/OCR/History pipeline unless the bug requires changing it.
- Do not mark untested device behavior as stable.
- Update `README.md`, `PROJECT_NOTES.md`, and this handoff after meaningful architecture changes.
- Explain changed files, reason, verification state, and next test to the owner.
- Manual TL is the current priority.
- Real-Time flicker is paused.

## Next Test
Use the APK containing commits after `e1c7006` and verify:
1. Manual TL still completes and History is saved.
2. Source text is actually covered by the replacement mask.
3. Background is no longer a fixed black rectangle on the tested screen.
4. Translation font size follows the source.
5. Long translation shrinks rather than becoming oversized.
6. FAB on left/right/bottom-right places menu without overlap.
7. Hapus Overlay appears after Manual TL, removes overlay, and then disappears.
