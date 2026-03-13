package com.gmaingret.notes.data.model

import com.gmaingret.notes.domain.model.SearchResult

/**
 * API response DTO for a search result.
 *
 * Mirrors the server's search result JSON shape: { id, content, documentId, documentTitle }
 */
data class SearchResultDto(
    val id: String,
    val content: String,
    val documentId: String,
    val documentTitle: String
) {
    fun toDomain(): SearchResult = SearchResult(
        bulletId = id,
        content = content,
        documentId = documentId,
        documentTitle = documentTitle
    )
}
