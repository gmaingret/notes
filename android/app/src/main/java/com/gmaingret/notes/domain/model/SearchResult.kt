package com.gmaingret.notes.domain.model

/**
 * Domain model for a search result.
 *
 * [bulletId] maps to the server's "id" field (bullet UUID).
 * [documentTitle] is used to show which document the result belongs to.
 */
data class SearchResult(
    val bulletId: String,
    val content: String,
    val documentId: String,
    val documentTitle: String
)
