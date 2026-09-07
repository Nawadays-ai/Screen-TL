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
The user tested a recent Manual TL build and reported:
- source remained visible under overlay;
- translation appeared displaced so the screen looked smaller: upper results moved downward and lower results moved upward;
- Screen-TL floating menu was also translated;
- tapping the floating button again did not close the open menu;
- text size itself was already acceptable.

Do not treat that visual result as stable.

## Latest Manual Overlay Fix — 2026-09-07
The most important current issue is coordinate-space mismatch between MediaProjection bitmap coordinates and the overlay window.

### Coordinate Space
`FloatingService.showTranslationOverlay()` now:
- creates the overlay window at `sourceWidth x sourceHeight` pixels;
- uses `FLAG_LAYOUT_IN_SCREEN` instead of `FLAG_LAYOUT_NO_LIMITS`;
- on Android P+ uses `LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS`;
- on Android R+ calls `setFitInsetsTypes(0)`.

`TranslationOverlayView` still scales directly from source dimensions to actual view dimensions as a fallback, but it does not center or apply aspect-ratio compensation.

Commit: `5ebb3bfda9a6474e0de027159ce7b2c6725923a`.

### Mask / Background
`TranslationOverlayView.kt` now:
- slightly expands horizontal/vertical padding;
- draws a more solid replacement mask using local sampled background color with alpha 245;
- uses rounded corners;
- keeps source-derived font size and long-text fitting.

Commit: `e6b0bd2fc03a2e55e3335dc2a4121c6155bd6e98`.

True backdrop blur is intentionally not implemented yet. Standard `RenderEffect` blurs the view's own rendered content; it does not automatically blur the underlying third-party app for this overlay use case.

**Status: not yet device-tested after these fixes.**

## Floating Menu Fix
The previous touch listener hid the submenu during `ACTION_DOWN`, then `ACTION_UP` saw it hidden and opened it again. This made a second tap appear unable to close the menu.

Current behavior:
- `ACTION_DOWN`: records touch position only;
- `ACTION_MOVE`: once movement exceeds the drag threshold, submenu is hidden and dragging continues;
- `ACTION_UP`: a simple tap calls the normal open/close toggle.

Commit: `5ebb3bfda9a6474e0de027159ce7b2c6725923a`.

**Status: not yet device-tested after this fix.**

## Manual TL Capture / Menu Filtering
The user reported that the Screen-TL menu itself was being OCR-translated.

Current mitigation:
1. close submenu;
2. wait `200 ms` (`MANUAL_CAPTURE_UI_SETTLE_MS`);
3. call `captureOnce()`.

This gives the WindowManager time to apply the menu's `GONE` state before the frame used for OCR is accepted.

This is a timing mitigation, not yet a full OCR filter. A later milestone can explicitly exclude status bar, FAB, or Screen-TL UI regions from OCR.

## TextLayoutAnalyzer
For every OCR line it:
1. reads the line bounding box;
2. reads element bounding boxes and uses their median height to estimate source glyph/font size;
3. expands the line box with a font-aware margin;
4. samples pixels around that mask;
5. uses median RGB values as the local replacement background.

Commit: `e0ec39f595f2bb66e09d46d5be469fd7ae6deaab`.

## OcrManager
`DetectedText` carries expanded mask coordinates, `sourceTextSizePx`, and `backgroundColor`.

Commit: `28dab21490b73a5c94848971259c9096207cca43`.

## Hapus Overlay
Required and implemented:
- hidden when no Manual overlay exists;
- visible after Manual TL creates an overlay;
- removes only the overlay;
- service and History remain alive;
- button hides again after removal.

**Status: not yet device-tested after latest changes.**

## Manual TL Flow
1. Remove previous overlay.
2. Close the floating submenu.
3. Wait 200 ms for menu state to settle.
4. Keep FAB usable.
5. Request one MediaProjection frame.
6. Capture timeout: 3 seconds.
7. OCR all returned lines.
8. Analyze font/mask/background metadata.
9. Prepare ML Kit translation model.
10. Translate every detected line sequentially.
11. Save History.
12. Render non-touchable overlay in capture pixel space.
13. Expose Hapus Overlay.

## Real-Time — Paused
Basic Real-Time works but flickers because the current loop removes the overlay before each capture. Do not fix this unless the user explicitly asks to resume Real-Time work.

## APK Update / Build
`versionCode = 2`, `versionName = "1.1"`.

APK update conflict is still suspected to be signing-key mismatch between debug APKs.

Build automation runs `assembleDebug` automatically on every push to `main`; `workflow_dispatch` remains available. The workflow uses `gradle/actions/setup-gradle@v6` with Gradle 8.2.

Last verified successful build: commit `cedddc4ce6bf26f2e1bf7a36ad609cca568854cd`, artifact `ScreenTranslator-APK`, artifact ID `10014166550`.

The latest overlay/menu code has not yet been verified by a new GitHub Actions result in this handoff.

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
After the automatic build finishes, use the APK and verify:
1. Manual TL still completes and History is saved.
2. Upper and lower translation boxes stay exactly over their source text; no screen-shrinking effect.
3. Source is fully covered by the replacement mask.
4. Screen-TL menu is absent from the captured/translated frame.
5. FAB second tap closes the menu.
6. Background looks like the original surrounding area and the mask is more solid.
7. Translation font size follows the source.
8. Hapus Overlay appears after Manual TL, removes overlay, and disappears.
