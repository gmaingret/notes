package com.gmaingret.notes.domain.usecase

import com.gmaingret.notes.domain.model.Bookmark
import com.gmaingret.notes.domain.repository.BookmarkRepository
import javax.inject.Inject

/**
 * Use case for retrieving all bookmarks for the authenticated user.
 */
class GetBookmarksUseCase @Inject constructor(
    private val repo: BookmarkRepository
) {
    suspend operator fun invoke(): Result<List<Bookmark>> = repo.getBookmarks()
}
