package com.example.screentranslator

import java.text.Normalizer

/**
 * Single source of truth for translation text identity.
 *
 * Deduplication and [TranslationCache] must agree on what counts as the same source text.
 * Sharing this normalizer is what guarantees that.
 *
 * Normalization stays deliberately conservative: Unicode form, line endings, whitespace, and
 * trim only. It must not try to repair OCR typos, because that would silently merge two
 * genuinely different strings.
 */
object TranslationTextNormalizer {

    fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC)
        .replace("\r\n", "\n")
        .lineSequence()
        .joinToString("\n") { line -> line.trim().replace(Regex("[\\t\\u000B\\u000C ]+"), " ") }
        .trim()
}
