package com.gmaingret.notes.domain.model

/**
 * Domain model for a tag/mention/date chip count.
 *
 * [chipType] is one of "tag", "mention", or "date".
 * [value] is the chip text (e.g. "kotlin" for #kotlin, "alice" for @alice, "2025-01-01" for dates).
 * [count] is how many bullets contain this chip.
 */
data class TagCount(
    val chipType: String,
    val value: String,
    val count: Int
)
