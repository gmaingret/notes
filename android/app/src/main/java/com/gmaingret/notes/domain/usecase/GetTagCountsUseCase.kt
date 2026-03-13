package com.gmaingret.notes.domain.usecase

import com.gmaingret.notes.domain.model.TagCount
import com.gmaingret.notes.domain.repository.TagRepository
import javax.inject.Inject

/**
 * Use case for retrieving all tag/mention/date chip counts for the authenticated user.
 */
class GetTagCountsUseCase @Inject constructor(
    private val repo: TagRepository
) {
    suspend operator fun invoke(): Result<List<TagCount>> = repo.getTags()
}
