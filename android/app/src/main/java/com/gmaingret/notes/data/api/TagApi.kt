package com.gmaingret.notes.data.api

import com.gmaingret.notes.data.model.TagCountDto
import com.gmaingret.notes.data.model.TagBulletDto
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Retrofit interface for tag browsing.
 *
 * - GET /api/tags — list all tag/mention/date chips with counts for the authenticated user
 * - GET /api/tags/{type}/{value}/bullets — list bullets matching a specific chip
 */
interface TagApi {
    @GET("api/tags")
    suspend fun getTags(): List<TagCountDto>

    @GET("api/tags/{type}/{value}/bullets")
    suspend fun getBulletsByTag(
        @Path("type") type: String,
        @Path("value") value: String
    ): List<TagBulletDto>
}
