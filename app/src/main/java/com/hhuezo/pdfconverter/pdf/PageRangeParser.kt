package com.hhuezo.pdfconverter.pdf

/**
 * Parses page ranges like "1-5, 8, 11-13" into 0-based page indices.
 * Returns null if the input is invalid for the given page count.
 */
fun parsePageRange(input: String, pageCount: Int): List<Int>? {
    if (pageCount <= 0) return null
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return emptyList()

    val pages = linkedSetOf<Int>()
    val parts = trimmed.split(',')
    for (part in parts) {
        val token = part.trim()
        if (token.isEmpty()) continue
        if ('-' in token) {
            val bounds = token.split('-', limit = 2)
            if (bounds.size != 2) return null
            val start = bounds[0].trim().toIntOrNull() ?: return null
            val end = bounds[1].trim().toIntOrNull() ?: return null
            if (start < 1 || end < 1 || start > pageCount || end > pageCount) return null
            val from = minOf(start, end)
            val to = maxOf(start, end)
            for (page in from..to) {
                pages.add(page - 1)
            }
        } else {
            val page = token.toIntOrNull() ?: return null
            if (page < 1 || page > pageCount) return null
            pages.add(page - 1)
        }
    }
    return pages.sorted()
}

fun countPagesInRange(input: String, pageCount: Int): Int {
    return parsePageRange(input, pageCount)?.size ?: 0
}
