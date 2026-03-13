package com.gmaingret.notes.domain.repository

import com.gmaingret.notes.domain.model.TagBullet
import com.gmaingret.notes.domain.model.TagCount

/**
 * Repository interface for tag browsing operations.
 */
interface TagRepository {
    suspend fun getTags(): Result<List<TagCount>>
    suspend fun getBulletsByTag(type: String, value: String): Result<List<TagBullet>>
}
