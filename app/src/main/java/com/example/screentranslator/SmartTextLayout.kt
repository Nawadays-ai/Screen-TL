package com.example.screentranslator

import kotlin.math.abs

object SmartTextLayout {
    fun sort(items: List<DetectedText>, mangaMode: Boolean): List<DetectedText> {
        if (items.size <= 1) return items
        val medianHeight = items.map { (it.bottom - it.top).coerceAtLeast(1) }.sorted().let { it[it.size / 2] }
        val rowTolerance = (medianHeight * 0.65f).coerceAtLeast(8f)
        return items.sortedWith(Comparator { a, b ->
            val sameRow = abs(a.top - b.top) <= rowTolerance
            if (sameRow) {
                if (mangaMode) b.left.compareTo(a.left) else a.left.compareTo(b.left)
            } else {
                a.top.compareTo(b.top)
            }
        })
    }
}
