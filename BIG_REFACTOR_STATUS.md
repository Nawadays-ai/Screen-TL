# Big refactor status

- Stage 1 workflow/package audit: complete.
- Stage 2 Manga OCR: research complete; implementation is not yet approved.
- Fixed-size `224x224` is only the model input size, not the source bubble size. The implementation must preserve the full detected region and letterbox it.
- Device validation remains pending.
