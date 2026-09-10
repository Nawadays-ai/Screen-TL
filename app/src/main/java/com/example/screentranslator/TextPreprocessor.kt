package com.example.screentranslator

object TextPreprocessor {
    fun normalize(text: String, mangaMode: Boolean): String {
        var value = text.replace("\r\n", "\n").replace('\r', '\n')
        value = value.replace(Regex("[ \t]+"), " ")
        if (mangaMode) {
            // Manga OCR often returns a line break for visual columns/lines.
            // A single bubble is one translation unit, so remove those breaks.
            value = value.split('\n').joinToString("") { it.trim() }
        } else {
            value = value.split('\n').joinToString(" ") { it.trim() }
        }
        return value.trim()
    }
}
