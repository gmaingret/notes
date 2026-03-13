package com.gmaingret.notes.data.api

import com.gmaingret.notes.data.model.SearchResultDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for the search endpoint.
 *
 * GET /api/search?q= returns a list of bullets matching the query across all user documents.
 */
interface SearchApi {
    @GET("api/search")
    suspend fun search(@Query("q") query: String): List<SearchResultDto>
}
