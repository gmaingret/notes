package com.gmaingret.notes.data.model

import com.gmaingret.notes.domain.model.Bookmark

/**
 * API response DTO for a bookmark.
 *
 * Same shape as search results: { id, content, documentId, documentTitle }.
 * The id field is the bullet's UUID (used as the bookmark identifier on DELETE).
 */
data class BookmarkDto(
    val id: String,
    val content: String,
    val documentId: String,
    val documentTitle: String
) {
    fun toDomain(): Bookmark = Bookmark(
        bulletId = id,
        content = content,
        documentId = documentId,
        documentTitle = documentTitle
    )
}
