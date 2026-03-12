package com.gmaingret.notes.domain.usecase

import com.gmaingret.notes.domain.model.SearchResult
import com.gmaingret.notes.domain.repository.SearchRepository
import javax.inject.Inject

/**
 * Use case for searching bullets across all user documents.
 */
class SearchBulletsUseCase @Inject constructor(
    private val repo: SearchRepository
) {
    suspend operator fun invoke(query: String): Result<List<SearchResult>> = repo.search(query)
}
