package com.gmaingret.notes.domain.repository

import com.gmaingret.notes.domain.model.Bookmark

/**
 * Repository interface for bookmark CRUD operations.
 */
interface BookmarkRepository {
    suspend fun getBookmarks(): Result<List<Bookmark>>
    suspend fun addBookmark(bulletId: String): Result<Unit>
    suspend fun removeBookmark(bulletId: String): Result<Unit>
}
