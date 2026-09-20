# Screen-TL — AI Handoff / Context

## Read This First
The repository is the source of truth. Before changing build/signing/APK distribution, read `README.md`, `AI_README.md`, `PROJECT_NOTES.md`, and `SIGNING_SETUP.md` when available.

## Permanent Signing Contract
- `applicationId` must remain `com.example.screentranslator`.
- CI APKs use the permanent development signing key stored in GitHub Actions secrets.
- Never generate a replacement Android signing key as a workaround.
- Never commit `.jks` or private signing material.
- If signing secrets are missing, CI must fail rather than silently use an ephemeral key.

## Current Branch / Main Rule
- Current active experiment: `experiment/ui-api-deepl-gemini`.
- `main` must remain stable unless the user explicitly asks to promote an experiment.
- Current Klip work is **not promoted to main**.
- Build success is not device success; do not call Klip stable before user testing.

## Current Architecture
Package: `com.example.screentranslator`

- `FloatingService.kt`: floating UI, capture lifecycle, OCR/translation orchestration, Manual TL, Real-Time loop, menu, overlay lifecycle.
- `ScreenCaptureManager.kt`: MediaProjection + ImageReader capture.
- `TextLayoutAnalyzer.kt`: OCR layout/font sizing, local background-color estimation and blur-like source replacement.
- `OcrManager.kt`: ML Kit OCR and layout metadata.
- `TranslationManager.kt`: ML Kit / DeepL / Gemini provider selection and main-thread callback marshaling.
- `DeepLTranslationProvider.kt`: real DeepL REST provider; user tested quota consumption and successful translation.
- `GeminiTranslationProvider.kt`: real Gemini REST provider using `gemini-3.5-flash`; latest successful CI build includes this model update.
- `ApiSettings.kt` + `SecureApiKeyStore.kt`: custom API selection/state and Android Keystore AES-GCM credential storage.
- `TranslationOverlayView.kt`: non-touchable overlay renderer, left-anchored normal Manual TL box, local blur-like background, paragraph wrapping, and explicit tolerance ratio support.
- `TranslationHistory.kt`: persistent translation history including provider name.
- `TranslationCache.kt`: cache LRU persisten hasil translation sukses, dipusatkan di `TranslationManager` untuk Manual TL, Real-Time, dan Klip.

## Translation Cache — 2026-09-20
- Cache lookup terjadi sebelum provider dipanggil di `TranslationManager.translate()`; hit tetap mengembalikan callback di main thread.
- Key SHA-256 mencakup versi skema, scope provider efektif (termasuk model Gemini/OpenRouter non-rahasia), bahasa sumber/target, dan source text yang dinormalisasi. API key tidak pernah disimpan di cache atau key.
- Hanya response sukses dan tidak kosong yang disimpan. Cache tidak menyimpan failure, sehingga request gagal selalu dapat dicoba lagi.
- Penyimpanan memakai satu `SharedPreferences` app-private dengan LRU maksimal 500 entri / sekitar 256 KiB serta cache RAM maksimal 100 entri. Tidak ada teks cache yang dicatat ke Logcat.
- Status: implementasi statis selesai; belum diverifikasi oleh GitHub Actions atau uji perangkat.

## Manual TL Baseline
Accepted behavior before Klip:
- capture → OCR → translation → history → overlay;
- overlay coordinate origin was fixed with source-sized window + `FLAG_LAYOUT_IN_SCREEN`;
- source text is covered by opaque reconstructed color/blur-like background;
- normal Manual TL box remains left anchored and can expand rightward to about 1.55x source width;
- text uses `textScaleX = 1.0`;
- paragraph-level OCR/translation work is present on the experiment branch;
- Real-Time flicker remains intentionally paused.

## Klip — Snip-like Manual TL
**Implemented on `experiment/ui-api-deepl-gemini`; not device-tested.** Full handoff/test checklist: `KLIP_HANDOFF.md`.

User requirement:
- Normal tap on the Manual TL/Klip menu action keeps normal Manual TL.
- Hold the button for about **2 seconds** to enter **Klip**.
- A semi-transparent gray temporary mask covers the screen.
- Touch + drag selects a rectangle.
- Only the selected rectangle is cropped, OCRed, translated and overlaid.
- Text outside the selection is untouched.
- First Klip bounding/tolerance experiment is **180% / 1.80x**.
- `Hapus Overlay` is renamed **Cancel**.
- While the Klip mask is active, the existing Cancel button cancels the mask/capture/translation.
- After a Klip overlay is produced, the same Cancel button removes that Klip overlay.

### Klip implementation files
- `KlipButtons.kt`: custom `KlipManualButton` implements a 2000 ms hold while preserving normal tap behavior; `KlipCancelButton` intercepts Cancel only for active Klip/made Klip overlay.
- `KlipSelectionController.kt`: temporary mask, rectangle selection, crop, OCR, coordinate mapping, translation, history and Klip overlay.
- `layout_floating_widget.xml`: Manual action is labeled `✂ Klip`; existing remove-overlay action is labeled `✕ Cancel`.
- `TranslationOverlayView.kt`: overload `setTranslations(..., toleranceRatio)`; normal path uses 1.0, Klip uses 1.80.

### Important implementation decision
Klip intentionally **reuses the existing `FloatingService` ScreenCaptureManager/OcrManager/TranslationManager** through an isolated reflection bridge. Do not create a second MediaProjection from the same cached consent token. This keeps Klip on the already-running foreground capture session and avoids Android 14+ MediaProjection session problems. The reflection bridge is technical debt that can later be replaced with a clean shared controller interface.

### Klip coordinate flow
`touch rectangle → map to capture bitmap → crop → OCR → map OCR boxes back to full-screen overlay coordinates → TranslationManager → TranslationOverlayView`

The temporary mask is removed before capture and a short settle delay is used so the mask itself is not captured.

### First device test checklist
1. Normal tap on Klip still behaves as normal Manual TL.
2. Hold for ~2 seconds → gray mask appears.
3. Cancel is visible and usable while mask is active.
4. Drag a rectangle around one sentence/paragraph.
5. Mask disappears before screenshot capture.
6. Only selected text is translated.
7. Check whether 180% tolerance is too large/small.
8. Test Cancel during mask and after Klip overlay appears.
9. Test normal Manual TL, DeepL/Gemini, History and overlay coordinate alignment for regressions.

## Latest Klip Commits
- `5ec50c1d12f3d6bb3fd6a54da2a0c5d6b1916519` — initial Klip implementation.
- `2ad4632f2b28285f19caeb1705991c67af1c01cd` — refine Klip cancel lifecycle.

## Build Verification
GitHub Actions run **#187**, run ID `34150633062`, commit `2ad4632f2b28285f19caeb1705991c67af1c01cd`, completed **successfully** with artifact `ScreenTranslator-APK` (artifact ID `10029249965`, SHA-256 `5324f13c5cb2aeaae11f79a4ed95948558290c4afe202bca221fd9cc20c2e7ed`).

This is a **build verification only**. The user has not yet tested Klip on-device and will test later.

## Build Policy
- Do **not** run Android/Gradle builds on the user's PC.
- Build verification is performed exclusively through GitHub Actions.
- After code changes, the user will commit and manually trigger or observe the GitHub Actions build.
- Agents may perform static checks that do not invoke Gradle, such as XML validation and `git diff --check`.

## Work Log Policy
- Record every investigation, code change, verification result, blocker, and next action in `WORK_LOG.md`.
- Read the latest entry in `WORK_LOG.md` before continuing work so a later agent can resume without reconstructing context.

## Development Rules
- Inspect actual files before editing.
- Keep experiments off `main` unless explicitly promoted.
- Preserve the working capture/OCR/History pipeline.
- Do not mark device behavior stable before user testing.
- Do not claim a build passed without actual GitHub Actions evidence.
- Do not create a replacement Android signing key without explicit migration approval.
