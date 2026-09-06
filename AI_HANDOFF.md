# Screen-TL — AI Handoff / Context

## Read This First
This file is the handoff context for any AI assistant continuing development of Screen-TL. Read it before changing code. The repository is the source of truth; inspect current files before assuming the code still matches this document.

## Project Goal
Build an Android screen translator with:
- floating button over other apps;
- Manual TL: capture one screen → OCR → translate → eventually show translated text as overlay at OCR bounding boxes;
- Real-Time TL: monitor/capture the screen, detect changed text, OCR only when useful, translate changed/new text, cache results, and display overlays;
- selectable source/target languages;
- selectable translation engines, with Google ML Kit on-device translation currently working and DeepL/Gemini planned.

## Current Architecture
Package: `com.example.screentranslator`

Important files:
- `MainActivity.kt`: permissions, language/API selectors, starts foreground service, displays Translation History.
- `FloatingService.kt`: floating UI, MediaProjection capture manager lifecycle, OCR/translation orchestration, Manual TL.
- `ScreenCaptureSession.kt`: holds MediaProjection result code and Intent data in memory.
- `ScreenCaptureManager.kt`: screen capture implementation; inspect current repository version before editing.
- `OcrManager.kt`: Google ML Kit OCR; returns `DetectedText(text, left, top, right, bottom)`.
- `TranslationManager.kt`: Google ML Kit on-device translation; maps Indonesian/Japanese/Mandarin/English and downloads model when needed.
- `TranslationHistory.kt`: persistent history using SharedPreferences, max 50 entries.
- `layout_floating_widget.xml`: floating button/submenu UI.
- `activity_main.xml`: main settings screen plus Translation History.

## Known Working Behavior
- Overlay permission works.
- MediaProjection permission works.
- Floating button works and can be dragged.
- Manual screenshot capture works and has been verified with actual screen dimensions.
- OCR works, including Japanese/Chinese/Latin recognizers.
- Translation has successfully produced a result.

## Known Limitations / Bugs To Address
- Toast messages are not reliable for testing over other apps and are unsuitable for multi-line translation output. Do not use Toast as the primary result channel.
- The previous test showed only the current time visibly because Toast output was truncated/obscured. Translation results should go to History or a future overlay.
- Translation History was initially RAM-only and did not receive results. It has now been changed to persistent SharedPreferences and `FloatingService` writes completed translation batches into it.
- Current Manual TL intentionally translates only `detectedTexts.take(3)` for early testing. This should eventually become configurable or process all useful detections with filtering/cache.
- Realtime button currently only toggles its UI state; actual realtime capture/OCR/translation loop is not implemented yet.
- Overlay translation UI is not implemented yet.

## Important Current Flow
`MainActivity`:
1. user selects source/target language;
2. user presses Play;
3. overlay permission is checked/requested;
4. MediaProjection permission is requested;
5. `ScreenCaptureSession.save(resultCode, data)`;
6. `FloatingService` starts.

`FloatingService`:
1. initializes `OcrManager(sourceLanguage)`;
2. initializes `TranslationManager(sourceLanguage, targetLanguage)`;
3. initializes `ScreenCaptureManager` from `ScreenCaptureSession`;
4. Manual TL calls `captureOnce`;
5. OCR returns `DetectedText` items with bounding boxes;
6. translation model is prepared;
7. up to three detected lines are translated sequentially;
8. completed results are written to `TranslationHistory` with timestamp and language pair.

## History Contract
`TranslationHistory.initialize(applicationContext)` must be called before `add/getAll/clear`.
History entries are strings. Current format:

```text
[HH:mm:ss]
SOURCE → TARGET

original text
→ translated text
```

History keeps at most 50 entries and persists across app restarts.

## Memory / Screenshot Policy
Manual screenshots are held as Bitmaps for OCR and are recycled by `OcrManager` on completion. Do not add permanent screenshot files unless there is a clear product requirement. The desired behavior is that screenshots do not accumulate in storage.

## Development Rules
- Inspect the actual current repository before modifying code.
- Prefer small, testable changes.
- Preserve working Screen Capture and OCR while adding new features.
- Do not blindly overwrite files based only on this handoff document; code may have advanced.
- After each meaningful change, explain what changed and what the user should test.
- The user's GitHub Actions setup automatically builds an APK after changes, so a build result is an important validation signal.
- Avoid introducing dependencies unless necessary.
- Keep the app functional before polishing UI.

## Next Recommended Milestone
1. Verify the current History implementation builds and works after Manual TL in another app.
2. If History works, implement the first translation overlay using `DetectedText` bounding boxes and translated strings.
3. Keep History as a debug/testing feature while overlay is developed.
4. Then implement real-time change detection and translation caching.

## Communication Context
The project owner prefers step-by-step changes when manually editing code, but has authorized the AI with repository access to make changes directly. When making direct repository changes, always summarize the files changed, the purpose, and the expected test result.
