package com.example.screentranslator

/**
 * Compatibility shim for the UI/API experiment branch.
 * FloatingService still calls the older method name; keep that call working
 * while TranslationOverlayView exposes the canonical setTranslations API.
 */
fun TranslationOverlayView.setTranslationItems(
    translations: List<TranslationOverlayItem>,
    sourceWidth: Int,
    sourceHeight: Int
) {
    setTranslations(translations, sourceWidth, sourceHeight)
}
