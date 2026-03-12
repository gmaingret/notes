package com.gmaingret.notes.domain.model

/**
 * Domain model for a document (note).
 *
 * Position is Double because the backend uses FLOAT8 midpoint positioning
 * for stable fractional-indexing-based ordering.
 */
data class Document(
    val id: String,
    val title: String,
    val position: Double
)
