package com.gmaingret.notes.domain.usecase

import com.gmaingret.notes.domain.repository.BookmarkRepository
import javax.inject.Inject

/**
 * Use case for removing a bookmark from a bullet.
 */
class RemoveBookmarkUseCase @Inject constructor(
    private val repo: BookmarkRepository
) {
    suspend operator fun invoke(bulletId: String): Result<Unit> = repo.removeBookmark(bulletId)
}
