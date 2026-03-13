package com.gmaingret.notes.data.repository

import com.gmaingret.notes.data.api.SearchApi
import com.gmaingret.notes.domain.model.SearchResult
import com.gmaingret.notes.domain.repository.SearchRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production implementation of [SearchRepository].
 *
 * Wraps the Retrofit call in try/catch and returns Result.success / Result.failure
 * so that ViewModels never need to handle exceptions.
 */
@Singleton
class SearchRepositoryImpl @Inject constructor(
    private val searchApi: SearchApi
) : SearchRepository {

    override suspend fun search(query: String): Result<List<SearchResult>> = try {
        val results = searchApi.search(query).map { it.toDomain() }
        Result.success(results)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
