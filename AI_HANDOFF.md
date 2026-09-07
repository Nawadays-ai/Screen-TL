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
- `TextLayoutAnalyzer.kt`: font-aware source mask, background-color estimation, and local blur patch creation.
- `OcrManager.kt`: ML Kit OCR plus layout/blur metadata.
- `TranslationManager.kt`: Google ML Kit translation.
- `TranslationOverlayView.kt`: full-screen non-touchable translation renderer with bounded translation box and local blur replacement.
- `TranslationHistory.kt`: persistent SharedPreferences history.

## Important Regression Evidence
The user tested recent Manual TL builds and reported:
- source remained visible under overlay;
- translation appeared displaced so the screen looked smaller: upper results moved downward and lower results moved upward;
- Screen-TL floating menu was also translated;
- tapping the floating button again did not close the open menu;
- text size itself was initially acceptable;
- after sizing correction, the overlay became closer to the desired size but still needed refinement;
- the user now wants a blur effect and a bounded translation box that may be longer than the source without becoming excessive;
- the user specifically reported that translated text looked vertically/horizontally stretched in an earlier iteration.

Do not treat the current visual result as stable until device-tested.

## Latest Iteration — 2026-09-07: Bounding Box + Local Blur
The user supplied a new screenshot and requested three visual improvements:
1. add blur around/behind the replacement area;
2. allow the translation bounding box to become wider than the source, but not too wide;
3. keep text glyphs natural and avoid stretch.

### Translation Box
`TranslationOverlayView.kt` now treats the rendered rectangle as a controlled translation bounding box:
- it starts at the OCR source width;
- it may expand horizontally when the translation is longer;
- maximum width is approximately `1.55x` the source width;
- the expanded box stays centered on the original source line and is clamped to screen bounds;
- font fitting changes `textSize` uniformly;
- `textScaleX` is kept at `1.0` so glyphs are not squeezed or stretched.

### Local Blur
`TextLayoutAnalyzer.kt` creates a small local blurred patch from the captured source region using downsample/upscale. The goal is to hide source glyph detail while retaining the surrounding local color/texture.

This is **not true backdrop blur of the third-party app window**. It is a local screenshot-patch replacement rendered inside the Screen-TL overlay. This distinction is intentional because the overlay cannot simply ask `RenderEffect` on its own view to blur arbitrary pixels behind it.

`OcrManager.kt` carries this bitmap patch in `DetectedText`.
`FloatingService.kt` transfers ownership of the patch into `TranslationOverlayItem` after successful translation.
`TranslationOverlayView.kt` renders and recycles the patch when the overlay is replaced/removed.

### Build Failure and Fix — 2026-09-07
GitHub Actions run `34118006986` / run #107 failed at `:app:compileDebugKotlin` with:
`FloatingService.kt:528:14 Val cannot be reassigned`.

Cause: `DetectedText.blurredPatch` was declared as `val`, while `FloatingService.kt` intentionally clears it after transferring bitmap ownership into `TranslationOverlayItem`.

Fix: `OcrManager.kt` commit `20e1a2fc472f7d7d67d955429c6de093c7b7c14e` changes `blurredPatch` to `var`, matching the ownership-transfer design. A new push/build is expected from this commit.

### Memory / Cleanup
Blur patches are small but are still bitmaps. Ownership is transferred from `DetectedText` to `TranslationOverlayItem` only when translation succeeds. Failure paths recycle patches that were not transferred. Do not introduce a second cache unless needed.

## Previous Coordinate-Space Fix
`FloatingService.showTranslationOverlay()`:
- creates overlay window at `sourceWidth x sourceHeight` pixels;
- uses `FLAG_LAYOUT_IN_SCREEN`;
- Android P+ uses `LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS`;
- Android R+ calls `setFitInsetsTypes(0)`.

`TranslationOverlayView` maps source coordinates directly to view dimensions without centering/aspect-ratio compensation.

## Manual TL Capture / Menu Filtering
Current mitigation:
1. close submenu;
2. wait `200 ms` (`MANUAL_CAPTURE_UI_SETTLE_MS`);
3. call `captureOnce()`.

This is timing mitigation, not a complete OCR filter. Status bar/FAB/Screen-TL UI filtering remains future work.

## Floating Menu Fix
Current touch behavior:
- `ACTION_DOWN`: records touch position only;
- `ACTION_MOVE`: once movement exceeds threshold, submenu is hidden and dragging continues;
- `ACTION_UP`: simple tap toggles menu open/closed.

## Real-Time — Paused
Basic Real-Time works but flickers because the current loop removes the overlay before each capture. Do not fix this unless the user explicitly asks to resume Real-Time work.

## Build / Verification State
The last verified successful build before the blur/bounding-box iteration was GitHub Actions run `34112476978`, `assembleDebug`, artifact `ScreenTranslator-APK`, artifact ID `10014932485`, SHA-256 `9f5d8e06f18dd1fecaa6c559594978f25c3efe7424158cbf160bb82b80df9458`.

The blur/bounding-box iteration failed once due to the Kotlin ownership declaration above. The fix is now committed as `20e1a2fc472f7d7d67d955429c6de093c7b7c14e`, but that new commit has not yet been verified by a completed Actions run in this handoff.

## Latest Code Commits
- `TextLayoutAnalyzer.kt`: `0478c9f26cc42d74c26ea458d829b50afc6945e9`
- `TranslationOverlayView.kt`: `5f26382a76eb919a61103a73c3eb57c1d9e8119a`
- `FloatingService.kt`: `5973de45f8dc6a77bd6cdfe930427348e53d8915`
- `OcrManager.kt`: `20e1a2fc472f7d7d67d955429c6de093c7b7c14e`

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
After a successful new build, use the APK and verify:
1. Manual TL still completes and History is saved.
2. Upper and lower translation boxes stay exactly over their source text; no screen-shrinking effect.
3. Source is fully hidden under the local blur/mask.
4. Translation box can be a little wider than source but never excessively wide.
5. Translation glyphs remain natural, not stretched.
6. Local blur looks natural on flat and complex backgrounds.
7. Screen-TL menu is absent from the captured/translated frame.
8. FAB second tap closes the menu.
9. Hapus Overlay appears after Manual TL, removes overlay, and disappears.
