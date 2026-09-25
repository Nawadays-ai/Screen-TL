package com.example.screentranslator

import java.text.Normalizer

/**
 * Single source of truth for translation text identity.
 *
 * Deduplication and [TranslationCache] must agree on what counts as the same source text.
 * Sharing this normalizer is what guarantees that.
 *
 * Normalization stays deliberately conservative: Unicode form, line endings, whitespace, and
 * trim. It must not try to repair OCR typos, because that would silently merge two
 * genuinely different strings.
 *
 * The one rule beyond trimming is dropping a single space when both neighbours are CJK. OCR
 * keeps re-deciding whether a space sits after `、`/`。` or inside `【】`, so three captures of
 * one unchanged paragraph produced three different cache keys and always missed. Japanese does
 * not use spaces as word separators, so removing them cannot change meaning, while Latin text
 * such as "REAL-TIME" keeps its spaces untouched.
 */
object TranslationTextNormalizer {

    private val WHITESPACE_RUN = Regex("[\\t\\u000B\\u000C ]+")

    /**
     * CJK symbols and punctuation (、。「」【】), hiragana, katakana, katakana phonetic
     * extensions, CJK extensions A and B, unified ideographs, and the fullwidth/ halfwidth
     * forms block.
     */
    private const val CJK_RANGES =
        "\u3000-\u303F\u3040-\u309F\u30A0-\u30FF\u31F0-\u31FF\u3400-\u4DBF\u4E00-\u9FFF\uFF00-\uFFEF"

    /**
     * Matches only a literal space, never a line break, so line wrapping stays part of the
     * identity and two differently wrapped paragraphs are not silently merged.
     */
    private val CJK_SINGLE_SPACE = Regex("(?<=[$CJK_RANGES]) (?=[$CJK_RANGES])")

    fun normalize(text: String): String {
        val collapsed = Normalizer.normalize(text, Normalizer.Form.NFC)
            .replace("\r\n", "\n")
            .lineSequence()
            .joinToString("\n") { line -> line.trim().replace(WHITESPACE_RUN, " ") }
            .trim()
        return CJK_SINGLE_SPACE.replace(collapsed, "")
    }
}
