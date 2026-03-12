package com.gmaingret.notes.domain.repository

import com.gmaingret.notes.domain.model.SearchResult

/**
 * Repository interface for bullet search.
 */
interface SearchRepository {
    suspend fun search(query: String): Result<List<SearchResult>>
}
