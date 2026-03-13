package com.gmaingret.notes.data.api

import com.gmaingret.notes.data.model.BookmarkDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Retrofit interface for bookmark CRUD.
 *
 * - GET /api/bookmarks — list all bookmarks for the authenticated user
 * - POST /api/bookmarks — add a bookmark for a bullet (body: { bulletId })
 * - DELETE /api/bookmarks/:bulletId — remove the bookmark for a bullet
 */
interface BookmarkApi {
    @GET("api/bookmarks")
    suspend fun getBookmarks(): List<BookmarkDto>

    @POST("api/bookmarks")
    suspend fun addBookmark(@Body request: AddBookmarkRequest): Response<Unit>

    @DELETE("api/bookmarks/{bulletId}")
    suspend fun removeBookmark(@Path("bulletId") bulletId: String): Response<Unit>
}

data class AddBookmarkRequest(val bulletId: String)
