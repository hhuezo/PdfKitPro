package com.hhuezo.pdfconverter.data

data class RecentPdf(
    val uri: String,
    val displayName: String,
    val sizeBytes: Long,
    val lastOpenedAt: Long,
    val lastPageIndex: Int = 0,
)
