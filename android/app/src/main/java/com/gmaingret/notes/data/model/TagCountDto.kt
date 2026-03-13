package com.gmaingret.notes.data.model

import com.gmaingret.notes.domain.model.TagCount

/**
 * API response DTO for a tag/mention/date chip count.
 *
 * Mirrors the server TagCount shape: { chipType, value, count }.
 * chipType is one of "tag", "mention", "date".
 */
data class TagCountDto(
    val chipType: String,
    val value: String,
    val count: Int
) {
    fun toDomain(): TagCount = TagCount(
        chipType = chipType,
        value = value,
        count = count
    )
}
