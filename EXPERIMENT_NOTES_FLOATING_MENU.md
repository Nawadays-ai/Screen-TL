# Experiment Notes — Manual TL Visuals + Floating Menu

## Branch
`experiment/blur-bounding-box`

This branch is for device-tested refinements after the visual result was accepted for `main`. `main` remains the accepted baseline and is not changed by the work below.

## Current visual refinements

- Manual TL source font calibration is now `1.15x` glyph height (previously `1.10x`).
- Translation remains left-anchored to the source.
- Extra translation width grows only to the right, capped by the existing `1.55x` source-width limit and display bounds.
- The tolerance box is separate from the colored fill: when the translation is shorter than the tolerance, the replacement color stops around the translated text; if the translation reaches/exceeds the tolerance, the fill stops at the tolerance edge.
- Background replacement remains fully opaque (`alpha = 255`) so source glyphs cannot ghost through.
- The reconstructed blur-like field samples surrounding color and darkens it by one level. Very dark colors are preserved instead of being darkened further.
- Text color now adapts to the effective darkened background so light/dark text remains readable instead of colliding with similarly colored buttons/backgrounds.

## Why raw screenshot blur is not used

Copying and blurring the raw source patch also copies the original glyphs. That produced a stacked/double-text appearance. The current approach transfers only perimeter color information and reconstructs a local color field in the overlay.

## Landscape / rotation fix

`ScreenCaptureManager` now tracks the active capture dimensions and checks the real display metrics before every capture. If the device rotates, the old virtual-display/ImageReader dimensions are rebuilt to the current width/height before OCR receives the frame. This is intended to prevent portrait-sized coordinates from being reused after switching to landscape.

## Floating button behavior requested

Target behavior:

- If FAB is near the **top**, opening the menu must keep the FAB at exactly its current screen position. The menu should extend **downward**, with the FAB beside the **REAL-TIME** row.
- If FAB is near the **bottom**, opening the menu must keep the FAB at exactly its current screen position. The menu should extend **upward**, with the FAB beside the **KELUAR** row.
- If FAB is near the middle, choose the direction with enough free space rather than moving the FAB.
- Horizontal placement should also choose the side with enough room while preserving the FAB screen coordinates.
- Opening/closing the menu must never re-center or otherwise reposition the FAB.
- Animation should remain minimal: the FAB is stationary and only the menu should move.

A CI workspace successfully produced the intended `positionSubMenu()` patch, but GitHub Actions uses a read-only token in this repository, so that temporary CI commit could not be pushed. The floating-menu code therefore remains unchanged in the repository and must still be applied directly before it is considered implemented.

## Rollback

If the stronger blur/opacity, `1.15x` font calibration, or landscape changes are worse:

1. Do not merge the new experiment commits into `main`.
2. `main` remains the accepted visual baseline from the earlier merge.
3. To cancel only the new font/blur refinement, restore the branch files to their previous experiment commits:
   - `TextLayoutAnalyzer.kt` before `50317742651f7fe8cc11299de2cfb7f03aa6aef5` (1.10x)
   - `TranslationOverlayView.kt` before `7e0aa3eb4ce56958034ddd35df773991a92db9c1` (previous blur field)
4. To cancel the entire blur experiment, do not merge the experiment branch and keep `main` at the accepted baseline. The older pre-blur implementation can then be restored from its documented commits/history.

## Verification rule

No visual change is considered stable until GitHub Actions succeeds and the APK is tested on the device. Test at minimum:
- Portrait Manual TL
- Landscape Manual TL after rotating the device
- FAB near top, middle, and bottom
- Short and long translations against the tolerance box
- Light and very dark source backgrounds

Real-Time flicker remains intentionally paused.
