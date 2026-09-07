# Screen-TL — AI Handoff / Context

## Read This First
Read `README.md`, `AI_README.md`, `PROJECT_NOTES.md`, and `SIGNING_SETUP.md` before changing build/signing/APK distribution. The repository is the source of truth; inspect current files before assuming this document matches the implementation.

## Permanent Android Signing Contract
`SIGNING_SETUP.md` is the permanent signing contract for Screen-TL.

- Keep `applicationId` as `com.example.screentranslator`.
- CI APKs must use the same permanent development signing key.
- Never generate a replacement Android signing key to solve an update conflict.
- Never commit `.jks`/private signing material.
- GitHub Actions signing credentials are repository secrets; AI agents should not expect to read their values.
- If signing secrets are missing, CI should fail rather than silently falling back to an ephemeral debug key.
- Check the APK certificate before recommending uninstall/reinstall.

## Project Goal
Build an Android screen translator with a floating button over other apps.

Manual TL:
`capture one screen → OCR → analyze text layout → translate → replace source text with overlay`

Real-Time TL:
`capture repeatedly → OCR/translate → refresh overlay`

Google ML Kit on-device translation is the current baseline. DeepL and Gemini are integrated as API providers on the current experiment branch.

## Current Architecture
Package: `com.example.screentranslator`

- `MainActivity.kt`: permissions, language selectors, foreground service, full-display MediaProjection consent on Android 14+.
- `FloatingService.kt`: floating UI, capture lifecycle, OCR/translation orchestration, Manual TL, Real-Time loop, menu, overlay lifecycle.
- `ScreenCaptureManager.kt`: MediaProjection + ImageReader capture.
- `TextLayoutAnalyzer.kt`: font-aware source mask, background-color estimation, and local blur patch creation.
- `OcrManager.kt`: ML Kit OCR plus layout/blur metadata.
- `TranslationManager.kt`: provider abstraction for built-in ML Kit, DeepL, and Gemini.
- `BuiltInTranslationProvider.kt`: Google ML Kit on-device translation.
- `DeepLTranslationProvider.kt`: real DeepL REST translation integration.
- `GeminiTranslationProvider.kt`: real Gemini REST translation integration for the separately configured custom API.
- `ApiSettings.kt`: selected manual provider plus encrypted custom API credentials/state.
- `SecureApiKeyStore.kt`: Android Keystore + AES-GCM encrypted API-key storage.
- `TranslationOverlayView.kt`: full-screen non-touchable translation renderer with bounded translation box and local blur replacement.
- `TranslationHistory.kt`: persistent SharedPreferences history.

## UI / API Experiment — Current Branch
This work is isolated on `experiment/ui-api-deepl-gemini` until explicitly promoted.

- Main screen uses History and Settings icon buttons instead of text buttons.
- Settings contains the manual provider selector, with **Google ML Kit and DeepL only**; Gemini is not a manual-dropdown option.
- Gemini is configured separately as a custom API.
- Custom API keys are stored using Android Keystore + AES-GCM.
- `Cek` appears only when a key is present.
- `Gunakan` appears only after a successful check.
- Enabling one custom API disables the other.
- Disabling a custom API restores the selected built-in/manual provider.
- DeepL is implemented as an actual REST translation provider, not a cosmetic UI option.

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

## Build / Verification State
The last verified successful build before the blur/bounding-box iteration was GitHub Actions run `34112476978`, `assembleDebug`, artifact `ScreenTranslator-APK`, artifact ID `10014932485`, SHA-256 `9f5d8e06f18dd1fecaa6c559594978f25c3efe7424158cbf160bb82b80df9458`.

The permanent-signing configuration is now present on the current experiment branch, but GitHub Actions repository secrets have not been confirmed by the AI integration. Do not claim a signed-build success until an actual Actions run completes successfully.

## Development Rules
- Inspect actual repository files before edits.
- Preserve the working capture/OCR/History pipeline unless a functional bug requires changes.
- Do not mark device behavior stable before user testing.
- Update `README.md`, `PROJECT_NOTES.md`, and `AI_HANDOFF.md` after meaningful architecture changes.
- Explain changed files, reason, verification state, and next test.
- Manual TL is current priority.
- Real-Time flicker is paused.
- Never claim a build passed without a real build result.
- Never create a replacement Android signing key without explicit migration approval.
