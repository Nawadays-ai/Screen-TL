# Screen-TL Work Log

This file is the chronological handoff log for ongoing work. Add an entry for every investigation, change, verification, blocker, and next action.

## 2026-09-20 — Persistent translation cache

### Investigation and design
- Read the repository README/handoff documents and inspected the active translation call sites. Manual TL, Real-Time, and Klip all delegate requests through `TranslationManager.translate()`.
- Added the cache at that single boundary so no pipeline duplicates cache logic and no OCR/overlay/history lifecycle changes are needed.
- Cache identity is SHA-256 of a schema version, effective provider scope (including non-secret Gemini/OpenRouter model configuration), source language, target language, and Unicode/whitespace-normalized source text. API credentials are excluded.

### Change
- Added `TranslationCache.kt`: app-private `SharedPreferences` persistent LRU, bounded to 500 entries / approximately 256 KiB, with a 100-entry in-memory LRU front cache.
- `TranslationManager` now returns a cached successful translation before calling ML Kit, DeepL, Gemini, or OpenRouter; successful non-empty provider responses are cached. Failures are never cached.
- Initialized the cache from both `MainActivity` and `FloatingService`, matching the existing persistent-store pattern.
- Cache diagnostics only record hit metadata and character counts through the existing performance trace; source/translation text is not logged.
- History Manual TL dan Klip menampilkan `Cache: semua/sebagian/tidak ada kalimat dari cache (hit/total)` agar pengujian perangkat dapat membuktikan cache hit tanpa mengandalkan Logcat atau metrik provider.

### Verification and next action
- Per user instruction and repository policy, no Gradle/Android build was run locally. Static diff validation remains required before commit.
- The user should commit, let GitHub Actions build the APK, then verify repeated Manual TL, Klip, and (when applicable) Real-Time text no longer consumes provider requests while provider/language changes still produce distinct results.

## 2026-09-20 — Build policy and start-button crash investigation

### Completed
- Added the build policy to `AI_HANDOFF.md`: Android/Gradle builds must run only through GitHub Actions; the user commits and manually triggers or observes those builds.
- Added this work-log policy to `AI_HANDOFF.md`.
- Performed static inspection of the start-button flow: `MainActivity` requests overlay permission, requests MediaProjection consent, stores the consent result in `ScreenCaptureSession`, then starts `FloatingService`.
- Inspected `FloatingService`, `ScreenCaptureManager`, manifest permissions, and floating-widget layout without running Gradle or an Android build locally.

### Current blocker
- The app crashes after the user presses **Mulai**, but no device crash stack trace has been supplied. Source inspection alone cannot identify the failing Android framework call safely.

### Next action
- Obtain the `FATAL EXCEPTION` / `AndroidRuntime` stack trace from Android Studio Logcat immediately after reproducing the crash.
- Use the stack trace to identify the exact failing class and line, make the smallest targeted fix, record it here, then let the user commit and verify through GitHub Actions.

## 2026-09-20 — Start-button crash: floating menu view type

### Evidence
- Device Logcat reported: `ClassCastException: MaterialCardView cannot be cast to LinearLayout` in `FloatingService.onCreate` at line 99.
- The minimal UI redesign changed `layoutSubMenu` in `layout_floating_widget.xml` to `MaterialCardView`.

### Change
- Changed `FloatingService.layoutSubMenu` from `LinearLayout` to its common base type, `View`. The service only uses this reference for visibility, measurement, and `FrameLayout.LayoutParams`, all of which are available on `View`.

### Verification and next action
- Static inspection confirms every `layoutSubMenu` use is compatible with `View`.
- The user should commit this change and run the GitHub Actions build. After installing that APK, test **Mulai** again and capture a fresh Logcat trace only if it still crashes.

## 2026-09-20 — Exit-button crash: OkHttp cleanup on the main thread

### Evidence
- Device Logcat reported `NetworkOnMainThreadException` while stopping `FloatingService`.
- Stack trace: `FloatingService.onDestroy` → `TranslationManager.close` → `DeepLTranslationProvider.close` → `OkHttp ConnectionPool.evictAll` attempted to close a TLS socket on the Android main thread.

### Change
- Changed `TranslationManager.close()` to run provider cleanup on a named daemon background thread.
- This covers DeepL, Gemini, and OpenRouter because all three providers use the same OkHttp connection-pool cleanup pattern.
- Existing Gemini model rotation behavior was left unchanged.

### Verification and next action
- Static inspection confirms `TranslationManager.close()` is called by `FloatingService` during service restart and destruction, both on the main thread.
- The user should commit this change, run GitHub Actions, then confirm that **Keluar** closes the floating service without a crash.

## 2026-09-20 — Gemini model rotation: high-demand response

### Change
- Extended Gemini model rotation to handle HTTP `503` in addition to HTTP `429`.
- A `503` response means the current model is temporarily unavailable/high demand; the provider now reports the status and retries using the next configured model.
- The existing sticky model behavior remains: the first model that succeeds becomes the starting model for later requests.

### Next action
- The user should commit this change and verify it through GitHub Actions.
