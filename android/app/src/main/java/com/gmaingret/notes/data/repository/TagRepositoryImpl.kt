package com.gmaingret.notes.data.repository

import com.gmaingret.notes.data.api.TagApi
import com.gmaingret.notes.domain.model.TagBullet
import com.gmaingret.notes.domain.model.TagCount
import com.gmaingret.notes.domain.repository.TagRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production implementation of [TagRepository].
 *
 * Wraps all API calls in try/catch and returns Result.success / Result.failure
 * so that ViewModels never need to handle exceptions.
 */
@Singleton
class TagRepositoryImpl @Inject constructor(
    private val tagApi: TagApi
) : TagRepository {

    override suspend fun getTags(): Result<List<TagCount>> = try {
        val tags = tagApi.getTags().map { it.toDomain() }
        Result.success(tags)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun getBulletsByTag(type: String, value: String): Result<List<TagBullet>> = try {
        val bullets = tagApi.getBulletsByTag(type, value).map { it.toDomain() }
        Result.success(bullets)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
