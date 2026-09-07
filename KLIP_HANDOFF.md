# Screen-TL — Klip (Snip-like Manual TL)

## Status
Implemented as an experiment on `experiment/ui-api-deepl-gemini`. **Not device-tested yet.** Do not promote to `main` until the user tests the APK.

## User requirement
- A normal tap on the Manual TL menu action keeps the existing Manual TL behavior.
- Holding the Manual TL button for about **2 seconds** enters a new mode named **Klip**.
- Klip shows a temporary semi-transparent gray mask over the screen.
- User touches and drags a rectangular selection.
- Only the selected rectangle is captured/cropped, OCRed, translated, and overlaid.
- Text outside the selected rectangle must not be translated.
- First Klip overlay tolerance experiment is **180% (1.80x)**.
- The existing `Hapus Overlay` action is renamed **Cancel**.
- While Klip temporary mask is active, the existing Cancel button becomes **Cancel Klip** behavior: it removes the mask/selection and aborts any pending Klip capture/translation.
- Menu label for the feature is **Klip**.

## Implementation
### `KlipManualButton.kt` / `KlipCancelButton`
- `KlipManualButton` keeps normal click behavior but detects a custom 2000 ms hold.
- Long press starts `KlipSelectionController`.
- `KlipCancelButton.performClick()` intercepts the existing FloatingService click only while Klip is active; otherwise the original overlay-removal click continues to work.

### `KlipSelectionController.kt`
- Reuses the already-running `FloatingService` instances of `ScreenCaptureManager`, `OcrManager`, and `TranslationManager` through a small reflection bridge.
- This avoids creating a second MediaProjection session from the same screen-capture consent token.
- The reflection bridge is intentionally isolated so it can later be replaced by a clean shared controller interface.
- The existing floating menu/root is temporarily moved above the mask so the existing Cancel button remains clickable.
- Selection coordinates use a full-screen `TYPE_APPLICATION_OVERLAY` with `FLAG_LAYOUT_IN_SCREEN` + `FLAG_LAYOUT_NO_LIMITS`, matching the capture/overlay coordinate strategy already used for the accepted coordinate fix.
- The mask is removed before capture and a short settle delay is used so the gray mask is not included in the screenshot.
- The selected screen rectangle is mapped to capture bitmap coordinates, cropped, OCRed, then OCR boxes are mapped back into full-screen coordinates.
- Translation is still delegated to the existing `TranslationManager`, so ML Kit / DeepL / Gemini behavior is shared with Manual TL.
- History records the provider as `TL: <provider> (Klip)`.
- Klip overlay uses `TranslationOverlayView.setTranslations(..., toleranceRatio = 1.80f)`.

### `TranslationOverlayView.kt`
- Existing normal Manual TL path still calls `setTranslations(..., 1.0f)`.
- New overload accepts a tolerance ratio.
- For Klip, the initial experiment uses a 1.80x bounding/tolerance region while preserving the source-left anchor.
- Normal Manual TL keeps the existing approximately 1.55x rightward expansion behavior.

## Important Android capture constraint
Do **not** make Klip create a second `MediaProjection` from the same cached consent `Intent`. Android 14+ requires user consent per capture session and a `MediaProjection` instance can only create one virtual display session. Reusing the existing `ScreenCaptureManager` owned by the running foreground service is therefore intentional.

## Expected first device test
1. Open Screen-TL menu.
2. Tap Manual TL normally once: it should behave as before.
3. Open menu again and hold **Klip** for ~2 seconds.
4. Gray temporary mask should appear while the floating menu/Cancel remains usable.
5. Drag a rectangle around one sentence/paragraph.
6. Release.
7. Mask should disappear before capture.
8. Only selected text should be OCRed/translated.
9. Check whether 180% tolerance is visually too large/small.
10. Test Cancel while the mask is active.

## Known risk areas to verify
- Window Z-order when temporarily re-adding the floating root above the mask.
- Touch coordinates vs MediaProjection bitmap coordinates on the user's device, especially status/navigation bars.
- Crop scaling on different resolutions/orientations.
- Whether 180% tolerance is visually appropriate; tune gradually after the user's test.
- Cancel during translation callbacks; no stale Klip overlay should remain.
- Klip should not regress normal Manual TL, DeepL, Gemini, History, or the accepted overlay coordinate fix.

## Branch / promotion rule
Keep this work on `experiment/ui-api-deepl-gemini` until the user explicitly asks to promote it. Build success is not device success.
