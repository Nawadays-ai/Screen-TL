# Experiment Notes — Manual TL Visuals + Floating Menu

## Branches
- Accepted visual baseline is already on `main`.
- New UI/API work stays isolated on `experiment/ui-api-deepl-gemini` until explicitly approved for `main`.
- The older blur/floating-menu experiment was promoted to `main`; this note is retained as the historical implementation record.

## Current visual refinements

- Manual TL source font calibration is now `1.15x` glyph height (previously `1.10x`).
- Translation remains left-anchored to the source.
- Extra translation width grows only to the right, capped by the existing `1.55x` source-width limit and display bounds.
- The tolerance box is separate from the colored fill: when the translation is shorter than the tolerance, the replacement color stops around the translated text; if the translation reaches/exceeds the tolerance, the fill stops at the tolerance edge.
- Background replacement remains fully opaque (`alpha = 255`) so source glyphs cannot ghost through.
- The reconstructed blur-like field samples surrounding color and darkens it by one level. Very dark colors are preserved instead of being darkened further.
- Text color adapts to the effective darkened background so light/dark text remains readable.

## Why raw screenshot blur is not used

Copying and blurring the raw source patch also copies the original glyphs. That produced a stacked/double-text appearance. The current approach transfers only perimeter color information and reconstructs a local color field in the overlay.

## Landscape / rotation fix

`ScreenCaptureManager` tracks the active capture dimensions and checks the real display metrics before every capture. If the device rotates, the old virtual-display/ImageReader dimensions are rebuilt to the current width/height before OCR receives the frame.

## Floating button behavior

Target behavior:

- If FAB is near the top, opening the menu keeps the FAB at exactly its current screen position and the menu extends downward.
- If FAB is near the bottom, opening the menu keeps the FAB at exactly its current screen position and the menu extends upward.
- If FAB is near the middle, choose the direction with enough free space rather than moving the FAB.
- Horizontal placement chooses the side with enough room while preserving the FAB screen coordinates.
- Opening/closing the menu never re-centers or otherwise repositions the FAB.
- Animation remains minimal: the FAB is stationary and only the menu moves.

This floating-menu geometry is now part of the accepted visual baseline on `main`.

## Current UI/API experiment

The following work is intentionally **not on `main`**:

- Main screen moves History into a dedicated History page and uses icon-only History/Settings controls.
- Main screen keeps source/target language selection horizontal with a centered `>` and uses `Play` for the start action.
- Settings contains the manual provider selector with only Google ML Kit and DeepL API. Gemini is intentionally not a manual-provider dropdown option.
- Gemini and DeepL have separate encrypted API-key storage, real API checks, and enable/disable controls.
- Built-in Google ML Kit remains available when no custom API is active.
- DeepL is implemented as a real REST translation provider, not just a UI option.

### API check/use state bug fixed

Observed on-device: DeepL displayed `API dapat digunakan` after a successful check, but the `Gunakan` button remained hidden. Root cause was the success callback saving the key after setting `verified=true`; the key watcher reset verification to false before the UI rendered.

Fix: save the key first, then set the verified flag. The same ordering was applied to Gemini.

### API action button sizing

The `Cek`/`Gunakan` controls were previously stretched by weighted full-width layout. They are now compact `wrap_content` buttons. `Gunakan` remains hidden until the API check succeeds, then appears beside `Cek`. `Nonaktifkan` remains available only while that API is active.

## Verification rule

No visual/API change is considered stable until GitHub Actions succeeds and the APK is tested on the device. Test at minimum:
- Portrait Manual TL
- Landscape Manual TL after rotating the device
- FAB near top, middle, and bottom
- Short and long translations against the tolerance box
- Light and very dark source backgrounds
- DeepL: key check succeeds, `Gunakan` appears, activation works, translation actually uses DeepL
- Gemini: key check succeeds, `Gunakan` appears, activation works
- Switching active custom API disables the other custom API
- Disabling custom API restores the built-in/manual provider path

Real-Time flicker remains intentionally paused.
