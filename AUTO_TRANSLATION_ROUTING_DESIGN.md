# Auto Translation Routing Design

Branch: `experiment/firefox-manga-ocr`
Base: `experiment/ui-api-deepl-gemini`
Status: design / implementation plan

## Goal

Screen-TL should choose the appropriate OCR, layout strategy, preprocessing, translation engine, and rendering strategy automatically for the text currently being translated.

The user should **not manually choose Normal vs Manga OCR mode**. The application should inspect the captured text/image and route the translation flow to the most suitable pipeline.

Gemini and DeepL remain intact from the Gemini API baseline. Qwen is not part of this experiment because its on-device inference was too slow and frequently timed out/crashed on the target device.

## 1. Smart Translation Router

Proposed pipeline:

```text
Capture
  ↓
Text / image analysis
  ↓
Translation Mode Detector
  ├─ Normal
  └─ Manga
       ↓
OCR Router
  ├─ Google ML Kit OCR
  └─ Manga OCR (downloadable optional model)
       ↓
Smart Layout Analyzer
       ↓
Text Preprocessor
       ↓
Translation Engine Router
       ↓
Renderer
  ├─ Normal overlay
  └─ Manga clean-text rendering / inpainting
```

The routing decision belongs to the current translation operation, not to a permanent user setting.

### Routing principles

- Normal text should use the existing Google ML Kit OCR.
- Manga-like text should use Manga OCR when its model is available.
- If Manga OCR is required but not installed, the app must not silently fail. It should request/download the required OCR model or direct the user to the OCR model download control.
- Only one local OCR/translation model should be loaded into RAM at a time where applicable.
- Downloaded models may coexist on storage.
- The router should expose a diagnostic reason for every route decision so repeated failures can be compared.

## 2. Automatic Mode Detection

Do not expose a Normal/Manga mode selector as the primary control.

The detector should use OCR/layout signals such as:

- presence of Japanese text;
- vertical-writing detection;
- multiple narrow vertical text regions;
- speech-bubble-like compact regions;
- text orientation;
- word/line geometry;
- density and spacing of text regions;
- confidence/quality signals from the initial OCR pass.

A practical first implementation can use a lightweight heuristic detector rather than a large ML classifier.

Example:

```text
Japanese + vertical columns          → Manga route
Japanese + compact bubble regions    → Manga route
Normal horizontal paragraph          → Normal route
English/Chinese/Indonesian paragraph → Normal route
Ambiguous                             → Normal route initially
```

The detector should be conservative. Normal Google ML Kit remains the safe fallback.

## 3. Dual OCR Router

### Normal route

Use the existing Google ML Kit text recognition implementation.

Expected ordering:

**left → right, then top → bottom**

Example:

```text
[A] [B] [C]
[D] [E] [F]
```

Result:

```text
A B C D E F
```

### Manga route

Use Manga OCR for manga-style Japanese text, including:

- speech bubbles;
- vertical Japanese writing;
- compact manga text;
- text where standard OCR segmentation is likely to lose reading order.

Expected ordering:

**right → left, then top → bottom**

Example:

```text
[C] [B] [A]
[F] [E] [D]
```

Result:

```text
C B A F E D
```

For vertical writing, columns must be treated as layout units. Characters/words inside each vertical column are read **top → bottom**, while columns are ordered **right → left**.

## 4. Manga OCR Model Installation

Manga OCR is not bundled into the APK by default.

Two supported paths:

### First-use path

1. Router detects manga-style text.
2. Manga OCR model is not installed.
3. App shows a clear notification/dialog explaining that Manga OCR is required.
4. User starts the download.
5. Model is stored in app-private storage.
6. Download is verified before becoming usable.
7. The current translation continues/retries after the model becomes available.

### Settings path

Provide a dedicated **Download OCR Model** control in Settings.

Important: this control downloads the model; it does **not** select Manga mode.

The user should never have to manually choose Manga OCR as a translation mode.

Suggested states:

```text
Belum terpasang → Download OCR
Downloading      → progress
Terpasang       → Hapus Model
Gagal           → Coba Lagi
```

## 5. Smart Layout Analysis

OCR output should be treated as geometry + text, not as an already-correct sentence order.

Create a separate layout-analysis stage that:

- groups words into lines;
- groups lines into paragraphs/bubbles;
- detects columns;
- detects reading direction;
- sorts words/lines according to the selected route;
- produces translation units.

Normal:

```text
x ascending, then y ascending
→ left to right, top to bottom
```

Manga:

```text
a column's x position descending
→ right to left
then y ascending inside each column
→ top to bottom
```

The analyzer should preserve the original bounding boxes so translated text can still be rendered at the correct location.

## 6. Text Preprocessing

Translation should receive coherent text units rather than arbitrary OCR fragments.

### Normal mode

If OCR produces:

```text
Ini adalah
sebuah contoh
kalimat.
```

Preprocessing should produce one paragraph unit:

```text
Ini adalah sebuah contoh kalimat.
```

One paragraph should normally become **one translation request**.

### Manga mode

All words/characters belonging to the same speech bubble should be combined into one translation unit.

Example:

```text
こ
ん
に
ち
は
```

becomes:

```text
こんにちは
```

and only then is it sent to the translator.

This is important for translation quality because the translator receives context instead of isolated OCR fragments.

## 7. Translation Engine Routing

The translation engine should also be selected automatically according to the current operation and configured capabilities.

The user should see translation engines in Settings, but engine selection and OCR/mode selection are separate concepts.

Current/target engines:

- Google ML Kit — default/safe on-device translation.
- Firefox Translation — experimental offline/mobile-first translation target for this branch.
- Gemini — existing API integration; must remain untouched by this experiment.
- DeepL — existing API integration; must remain untouched by this experiment.

The router should eventually be able to produce a decision such as:

```text
OCR: Manga OCR
Layout: Manga RTL
Preprocess: Bubble grouping
Translation: Firefox Translation
Render: Inpaint + transparent boundary
```

or:

```text
OCR: Google ML Kit
Layout: Normal LTR
Preprocess: Paragraph grouping
Translation: configured engine
Render: Normal overlay
```

The exact translation-engine priority should remain compatible with existing API settings unless explicitly changed later.

## 8. Manga Background Inpainting

For Manga route, test replacing source text with the underlying bubble/background before rendering the translation.

Pipeline:

```text
Manga OCR bounding region
        ↓
Estimate bubble/background color and texture
        ↓
Remove / inpaint source text
        ↓
Clean manga background
        ↓
Render translated text directly into cleaned area
```

The first implementation should target speech bubbles with relatively uniform backgrounds because they provide the highest chance of producing a clean result.

The inpainting implementation must not destroy artwork unnecessarily. It should operate only within the text region/bubble region determined by the OCR/layout analysis.

If robust inpainting cannot be achieved for a region, the renderer should have a safe fallback rather than leaving damaged pixels.

## 9. Manga Boundary Rendering

Manga mode should **not draw the normal colored translation boundary/box**.

The OCR bounding box remains an internal geometry object used for:

- text placement;
- bubble grouping;
- inpainting bounds;
- layout calculations.

The final rendered translation should have no visible artificial box when inpainting succeeds.

Desired result:

```text
Original manga bubble
       ↓
source text removed
       ↓
translation rendered in the same bubble
       ↓
looks like the translated text belongs to the original manga
```

This is an experiment and must be validated visually on real manga screenshots.

## 10. Failure and Fallback Rules

The router must fail safely.

Examples:

- Manga detected but Manga OCR unavailable → request model download; do not silently pretend Normal OCR succeeded.
- Manga OCR fails → record diagnostic and fall back to Google ML Kit only if the fallback is likely useful.
- Inpainting fails → keep a safe visible translation rendering rather than leaving corrupted/erased artwork.
- Firefox Translation unavailable → use the configured fallback translation engine according to existing settings.
- Any local model failure must release its RAM resources.

No model should remain loaded in RAM after it has been disabled or after an unrecoverable failure.

## 11. Persistent Diagnostics

Every automatic routing decision should be recorded in the existing performance/diagnostic history without storing source or translated text.

Recommended fields:

- selected route: Normal/Manga;
- OCR engine;
- detection reason/signals;
- layout strategy;
- number of OCR regions/words/units;
- preprocessing grouping count;
- translation engine;
- model availability/install state;
- model load/prepare duration;
- inference/translation duration;
- inpainting duration and success/failure;
- render duration;
- total duration;
- exact failure/timeout message where applicable.

This allows repeated failures to be compared rather than treated as isolated incidents.

## 12. Implementation Order

Do not implement everything at once. Build and verify in stages:

1. Create route/data abstractions without changing existing Normal behavior.
2. Implement Smart Layout Analyzer and deterministic Normal LTR ordering.
3. Implement Manga RTL/vertical ordering.
4. Implement paragraph/bubble text preprocessing.
5. Add Manga OCR model store/download lifecycle.
6. Integrate Manga OCR behind the router.
7. Integrate Firefox Translation as the experimental Manga-capable offline translation engine.
8. Add automatic translation-engine routing while preserving Gemini/DeepL.
9. Implement Manga inpainting for uniform speech-bubble backgrounds.
10. Add transparent Manga renderer/boundary behavior.
11. Add persistent routing diagnostics and compare repeated failures.
12. Run real-device tests and benchmark Normal vs Manga paths.

## Acceptance Criteria

### Normal

- Existing Google ML Kit behavior remains stable.
- Reading order is left → right, top → bottom.
- Paragraphs become coherent translation units.
- Existing Gemini/DeepL functionality remains intact.

### Manga

- No manual Manga mode selection is required.
- Manga-like text is detected and routed automatically.
- Manga OCR can be downloaded on demand.
- Vertical Japanese is ordered right → left by columns and top → bottom within each column.
- Each speech bubble becomes one translation unit.
- Firefox Translation can be selected/used as the experimental translation engine.
- Source text can be removed from simple bubble backgrounds.
- Manga translations render without an artificial colored boundary when inpainting succeeds.
- Failures are persisted in diagnostics with enough metadata to determine whether the same problem recurs.

## Scope Protection

This branch is based on the Gemini API baseline. Do not modify or regress Gemini/DeepL behavior while building the Firefox/Manga OCR experiment.

Qwen is intentionally excluded from this experiment because the target-device tests showed unacceptable inference latency and repeated timeouts/crashes.
