package com.gmaingret.notes.data.repository

import com.gmaingret.notes.data.api.AddBookmarkRequest
import com.gmaingret.notes.data.api.BookmarkApi
import com.gmaingret.notes.domain.model.Bookmark
import com.gmaingret.notes.domain.repository.BookmarkRepository
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production implementation of [BookmarkRepository].
 *
 * Wraps all API calls in try/catch and returns Result.success / Result.failure
 * so that ViewModels never need to handle exceptions.
 */
@Singleton
class BookmarkRepositoryImpl @Inject constructor(
    private val bookmarkApi: BookmarkApi
) : BookmarkRepository {

    override suspend fun getBookmarks(): Result<List<Bookmark>> = try {
        val bookmarks = bookmarkApi.getBookmarks().map { it.toDomain() }
        Result.success(bookmarks)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun addBookmark(bulletId: String): Result<Unit> = try {
        val response = bookmarkApi.addBookmark(AddBookmarkRequest(bulletId))
        if (response.isSuccessful) {
            Result.success(Unit)
        } else {
            Result.failure(HttpException(response))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun removeBookmark(bulletId: String): Result<Unit> = try {
        val response = bookmarkApi.removeBookmark(bulletId)
        if (response.isSuccessful) {
            Result.success(Unit)
        } else {
            Result.failure(HttpException(response))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
