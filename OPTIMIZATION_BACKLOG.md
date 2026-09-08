# Screen-TL — Optimization Backlog

This document records deferred optimization/refactor ideas for a later return to the project.

## 1. Optimize Capture → OCR → Translation latency
- Current end-to-end processing can take more than ~2 seconds.
- First profile the real bottleneck before changing architecture.
- Review unnecessary waits, bitmap copies, and stage handoffs.
- Keep translation ordering deterministic and avoid parallelization that could cause provider/rate-limit issues unless profiling shows it is safe.

## 2. Refactor FloatingService.kt
- It currently owns too many responsibilities.
- Candidate future controllers: floating menu, manual translation, real-time loop, overlay lifecycle, and orchestration.
- Defer the large refactor until there is a clear benefit because regression risk is significant.

## 3. Replace Klip reflection bridge
- KlipSelectionController currently accesses FloatingService internals through reflection.
- Future goal: explicit interfaces/callbacks/shared controller APIs.
- Preserve the single MediaProjection/session architecture while refactoring.

## 4. Review Klip lifecycle cleanup
- Audit cleanup after Klip cancel, capture failure, OCR failure, and translation failure.
- Ensure service/view references are released when no longer needed.

## 5. Revisit overlap grouping only if needed
- Current grouping is O(n²).
- This is acceptable for normal OCR box counts and is not currently considered a bottleneck.
- Only optimize with spatial indexing/sweep-line logic if profiling or unusually dense OCR proves it necessary.

## 6. Preserve current MediaProjection architecture
- One VirtualDisplay per MediaProjection session.
- Configuration changes should use VirtualDisplay.resize() + setSurface() rather than creating another virtual display from the same projection.
- Keep callback/lifecycle handling compatible with Android 14+.

## 7. Keep OCR logging privacy-conscious
- Do not restore full OCR text logging to Logcat.
- Prefer metadata such as character counts, bounds, and timing when debugging.

## Completion / agent handoff rule
When a backlog item is implemented and verified successfully:
- mark it `COMPLETED` with the verification date and commit, or remove it if no historical note is useful;
- future agents must treat completed items as finished and must NOT repeatedly ask whether they should be implemented;
- only reopen a completed item when there is a new regression, new evidence, or an explicitly changed requirement.

Build success alone is not device success where device behavior matters. For those items, require both successful CI build and appropriate device verification before marking complete.
